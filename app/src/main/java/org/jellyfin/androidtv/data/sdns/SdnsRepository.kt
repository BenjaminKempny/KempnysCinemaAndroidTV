package org.jellyfin.androidtv.data.sdns

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Connection settings for the ScreenDiceNSpice Supabase project. */
data class SdnsConfig(
	val url: String,
	val key: String,
	val profileId: String,
) {
	val hasConnection get() = url.startsWith("http") && key.isNotBlank()
	val isComplete get() = hasConnection && profileId.isNotBlank()
}

/** Error shown to the user. */
class SdnsException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Minimal HTTP abstraction for the Supabase REST (PostgREST) API, replaceable in tests. */
fun interface SdnsTransport {
	/**
	 * @param path path below `/rest/v1/` including the query string
	 * @return the response body
	 */
	suspend fun execute(config: SdnsConfig, method: String, path: String, body: String?): String
}

class OkHttpSdnsTransport(
	private val client: OkHttpClient = OkHttpClient.Builder()
		.connectTimeout(10, TimeUnit.SECONDS)
		.readTimeout(15, TimeUnit.SECONDS)
		.build(),
) : SdnsTransport {
	override suspend fun execute(config: SdnsConfig, method: String, path: String, body: String?): String = withContext(Dispatchers.IO) {
		val request = Request.Builder()
			.url(config.url.trimEnd('/') + "/rest/v1/" + path)
			.header("apikey", config.key)
			.header("Authorization", "Bearer ${config.key}")
			.header("Accept", "application/json")
			.header("Prefer", "return=representation")
			.method(method, body?.toRequestBody(JSON_MEDIA_TYPE))
			.build()

		try {
			client.newCall(request).execute().use { response ->
				val responseBody = response.body?.string().orEmpty()
				if (!response.isSuccessful) throw SdnsException("Supabase-Fehler ${response.code}: ${responseBody.take(200)}")
				responseBody
			}
		} catch (err: javax.net.ssl.SSLException) {
			// Mostly caused by a wrong device clock: the certificate is "not yet valid" or "expired".
			throw SdnsException(
				"Sichere Verbindung fehlgeschlagen – bitte Datum und Uhrzeit des Geräts prüfen (${err.message ?: err.javaClass.simpleName})",
				err,
			)
		} catch (err: IOException) {
			throw SdnsException("Netzwerkfehler: ${err.message ?: err.javaClass.simpleName}", err)
		}
	}

	private companion object {
		val JSON_MEDIA_TYPE = "application/json".toMediaType()
	}
}

/**
 * Reads and writes movies of ScreenDiceNSpice. Only `rating` and `seen` are ever changed on
 * existing rows, all other columns (external ratings, priority, poster, ...) stay untouched.
 */
class SdnsRepository(
	private val config: () -> SdnsConfig,
	private val transport: SdnsTransport = OkHttpSdnsTransport(),
) {
	private val json = Json { ignoreUnknownKeys = true }

	val isConfigured get() = config().isComplete
	val hasConnection get() = config().hasConnection

	suspend fun getProfiles(): List<SdnsProfile> {
		val cfg = requireConnection()
		val body = transport.execute(cfg, "GET", "profiles?select=id,name&order=name.asc", null)
		return json.parseToJsonElement(body).jsonArray.map {
			val obj = it.jsonObject
			SdnsProfile(
				id = obj.string("id") ?: throw SdnsException("Ungültiges Profil"),
				name = obj.string("name").orEmpty(),
			)
		}
	}

	/** Finds the movie in the configured profile, see [SdnsLogic.match]. */
	suspend fun find(query: SdnsMovieQuery): SdnsMovie? {
		val cfg = requireProfile()
		val base = "movies?select=$MOVIE_COLUMNS&profile_id=eq.${cfg.profileId.encode()}"

		val imdbId = SdnsLogic.normalizeImdbId(query.imdbId)
		if (imdbId != null) {
			// ilike without wildcards = case-insensitive equality
			val byImdb = parseMovies(transport.execute(cfg, "GET", "$base&imdb_id=ilike.${imdbId.encode()}", null))
			SdnsLogic.match(byImdb, query)?.let { return it }
		}

		// Title fallback, the normalization can't be expressed in PostgREST so match locally
		val all = parseMovies(transport.execute(cfg, "GET", base, null))
		return SdnsLogic.match(all, query)
	}

	/**
	 * Rates the movie (`rating = X, seen = true`) or only marks it as seen when [rating] is null.
	 * Updates the existing row or inserts a new one, saving twice never creates duplicates.
	 */
	suspend fun submit(query: SdnsMovieQuery, rating: Double?): SdnsMovie {
		val patch = if (rating == null) SdnsLogic.seenPatch() else SdnsLogic.ratePatch(rating)
		val cfg = requireProfile()
		val existing = find(query)

		val response = if (existing != null) {
			transport.execute(cfg, "PATCH", "movies?id=eq.${existing.id.encode()}&select=$MOVIE_COLUMNS", patch.toJson())
		} else {
			if (query.title.isBlank()) throw SdnsException("Film ohne Titel kann nicht angelegt werden")
			insertWithUniqueSlug(cfg, query, rating)
		}

		return parseMovies(response).firstOrNull() ?: throw SdnsException("Supabase hat keinen Datensatz zurückgegeben")
	}

	/** `slug` is globally unique, retry with a new random suffix on the unlikely collision. */
	private suspend fun insertWithUniqueSlug(cfg: SdnsConfig, query: SdnsMovieQuery, rating: Double?): String {
		var attempt = 0
		while (true) {
			val payload = SdnsLogic.insertPayload(cfg.profileId, query, rating)
			try {
				return transport.execute(cfg, "POST", "movies?select=$MOVIE_COLUMNS", payload.toJson())
			} catch (err: SdnsException) {
				val slugConflict = err.message.orEmpty().let { "23505" in it && "slug" in it }
				if (!slugConflict || ++attempt >= MAX_SLUG_ATTEMPTS) throw err
			}
		}
	}

	private fun requireConnection(): SdnsConfig = config().also {
		if (!it.hasConnection) throw SdnsException("ScreenDiceNSpice ist nicht konfiguriert (SUPABASE_URL / SUPABASE_KEY in .env)")
	}

	private fun requireComplete(): SdnsConfig = requireConnection().also {
		if (it.profileId.isBlank()) throw SdnsException("Kein ScreenDiceNSpice-Profil ausgewählt")
	}

	private val resolvedProfiles = java.util.concurrent.ConcurrentHashMap<String, String>()

	/**
	 * Returns the configuration with a UUID as profile id. A profile name (e.g. `PROFILE_ID=Benjamin`)
	 * is resolved via `public.profiles` (case-insensitive) and cached.
	 */
	private suspend fun requireProfile(): SdnsConfig {
		val cfg = requireComplete()
		val value = cfg.profileId.trim()
		if (UUID_REGEX.matches(value)) return cfg.copy(profileId = value)

		val id = resolvedProfiles[value.lowercase()] ?: run {
			suspend fun lookup(filter: String): List<String> {
				val body = transport.execute(cfg, "GET", "profiles?select=id,name&name=ilike.${filter.encode()}", null)
				return (json.parseToJsonElement(body) as? JsonArray).orEmpty()
					.mapNotNull { (it as? JsonObject)?.string("id") }
			}

			// Exact name first, then a unique prefix ("Benjamin" -> "Benjamin Kempny")
			val exact = lookup(value)
			val candidates = exact.ifEmpty { lookup("$value*") }
			when {
				candidates.isEmpty() -> throw SdnsException("ScreenDiceNSpice-Profil „$value“ nicht gefunden")
				candidates.size > 1 -> throw SdnsException("ScreenDiceNSpice-Profil „$value“ ist nicht eindeutig")
				else -> candidates.single().also { resolvedProfiles[value.lowercase()] = it }
			}
		}
		return cfg.copy(profileId = id)
	}

	private fun parseMovies(body: String): List<SdnsMovie> {
		val element = runCatching { json.parseToJsonElement(body) }.getOrElse {
			throw SdnsException("Ungültige Antwort von Supabase", it)
		}
		val array = element as? JsonArray ?: return emptyList()
		return array.mapNotNull { item ->
			val obj = item as? JsonObject ?: return@mapNotNull null
			SdnsMovie(
				id = obj.string("id") ?: return@mapNotNull null,
				title = obj.string("title").orEmpty(),
				year = (obj["year"] as? JsonPrimitive)?.intOrNull,
				imdbId = obj.string("imdb_id"),
				seen = (obj["seen"] as? JsonPrimitive)?.booleanOrNull ?: false,
				rating = (obj["rating"] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() },
			)
		}
	}

	private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)
		?.takeUnless { it is JsonNull }
		?.contentOrNull

	private fun Map<String, Any?>.toJson(): String = buildJsonObject {
		forEach { (key, value) ->
			put(key, when (value) {
				null -> JsonNull
				is Number -> JsonPrimitive(value)
				is Boolean -> JsonPrimitive(value)
				else -> JsonPrimitive(value.toString())
			})
		}
	}.toString()

	private fun String.encode(): String = URLEncoder.encode(this, "UTF-8")

	private companion object {
		const val MOVIE_COLUMNS = "id,title,year,imdb_id,seen,rating"
		const val MAX_SLUG_ATTEMPTS = 3
		val UUID_REGEX = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
	}
}


