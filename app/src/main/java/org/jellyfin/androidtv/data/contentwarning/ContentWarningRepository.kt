package org.jellyfin.androidtv.data.contentwarning

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jellyfin.androidtv.BuildConfig
import org.jellyfin.sdk.model.api.BaseItemDto
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Looks up DoesTheDogDie content warnings for movies. Movies are identified by their IMDb id
 * (preferred) or TMDB id. Results — including "not listed" answers — are cached in memory and
 * on disk to keep the number of API calls low.
 */
class ContentWarningRepository(
	context: Context,
	private val apiKey: String = BuildConfig.DTDD_API_KEY,
) {
	private val cacheDir = File(context.filesDir, CACHE_DIR)
	private val memoryCache = ConcurrentHashMap<String, CacheEntry>()
	private val mutex = Mutex()
	private val json = Json { ignoreUnknownKeys = true }
	private val api by lazy { DoesTheDogDieApi(apiKey) }

	val isConfigured get() = apiKey.isNotBlank()

	suspend fun getWarnings(item: BaseItemDto): ContentWarningResult = withContext(Dispatchers.IO) {
		val imdbId = item.providerId("Imdb")?.takeIf { it.startsWith("tt") }
		val tmdbId = item.providerId("Tmdb")?.toIntOrNull()
		val cacheKey = when {
			imdbId != null -> "imdb_$imdbId"
			tmdbId != null -> "tmdb_$tmdbId"
			else -> return@withContext ContentWarningResult.MissingIds
		}

		// Serve cached data even without an API key, it may have been bundled in earlier builds.
		readCache(cacheKey)?.let { return@withContext it.toResult() }
		if (!isConfigured) return@withContext ContentWarningResult.NotConfigured

		mutex.withLock {
			// Another coroutine may have fetched the same movie while we waited.
			readCache(cacheKey)?.let { return@withLock it.toResult() }

			try {
				val match = findMedia(imdbId, tmdbId, item)
				val report = match?.let { api.getReport(it.id, it.name ?: item.name) }
				val entry = CacheEntry(
					version = CACHE_VERSION,
					fetchedAt = System.currentTimeMillis(),
					report = report?.takeIf { it.groups.isNotEmpty() },
				)
				writeCache(cacheKey, entry)
				entry.toResult()
			} catch (error: DoesTheDogDieException) {
				Timber.w(error, "DoesTheDogDie lookup failed for %s", cacheKey)
				if (error.code == 401 || error.code == 403) ContentWarningResult.NotConfigured
				else ContentWarningResult.Error
			} catch (error: IOException) {
				Timber.w(error, "DoesTheDogDie lookup failed for %s", cacheKey)
				ContentWarningResult.Error
			} catch (error: IllegalArgumentException) {
				// Malformed JSON (kotlinx.serialization reports parse errors as IAE subclasses).
				Timber.w(error, "DoesTheDogDie returned invalid data for %s", cacheKey)
				ContentWarningResult.Error
			}
		}
	}

	private fun findMedia(imdbId: String?, tmdbId: Int?, item: BaseItemDto): DoesTheDogDieSearchItem? {
		if (imdbId != null) {
			api.searchByImdb(imdbId)
				.firstOrNull { it.imdbId.equals(imdbId, ignoreCase = true) }
				?.let { return it }
		}

		// No IMDb hit: search by title and match the TMDB id, then title + year.
		val title = item.originalTitle?.takeIf { it.isNotBlank() } ?: item.name ?: return null
		val candidates = buildList {
			addAll(api.searchByName(title))
			val name = item.name
			if (name != null && !name.equals(title, ignoreCase = true)) addAll(api.searchByName(name))
		}.distinctBy { it.id }
			.filter { it.itemType == null || it.itemType.contains("movie", ignoreCase = true) }

		if (tmdbId != null) candidates.firstOrNull { it.tmdbId == tmdbId }?.let { return it }
		if (imdbId != null) candidates.firstOrNull { it.imdbId.equals(imdbId, ignoreCase = true) }?.let { return it }

		val year = item.productionYear ?: return null
		return candidates.firstOrNull { candidate ->
			candidate.releaseYear == year &&
				(candidate.name.equals(item.name, ignoreCase = true) || candidate.name.equals(title, ignoreCase = true))
		}
	}

	private fun readCache(key: String): CacheEntry? {
		val entry = memoryCache[key] ?: readDiskCache(key)?.also { memoryCache[key] = it } ?: return null
		val maxAge = if (entry.report != null) FOUND_TTL else NOT_FOUND_TTL
		val expired = entry.version != CACHE_VERSION || System.currentTimeMillis() - entry.fetchedAt > maxAge
		if (expired) {
			memoryCache.remove(key)
			return null
		}
		return entry
	}

	private fun readDiskCache(key: String): CacheEntry? = try {
		File(cacheDir, "$key.json").takeIf { it.exists() }
			?.readText()
			?.let { json.decodeFromString<CacheEntry>(it) }
	} catch (error: Exception) {
		Timber.w(error, "Ignoring corrupt content warning cache %s", key)
		null
	}

	private fun writeCache(key: String, entry: CacheEntry) {
		memoryCache[key] = entry
		try {
			cacheDir.mkdirs()
			File(cacheDir, "$key.json").writeText(json.encodeToString(entry))
		} catch (error: IOException) {
			Timber.w(error, "Unable to persist content warning cache %s", key)
		}
	}

	private fun CacheEntry.toResult(): ContentWarningResult =
		report?.let { ContentWarningResult.Available(it) } ?: ContentWarningResult.NotFound

	private fun BaseItemDto.providerId(name: String): String? = providerIds
		?.entries
		?.firstOrNull { it.key.equals(name, ignoreCase = true) }
		?.value
		?.trim()
		?.takeIf { it.isNotBlank() }

	@Serializable
	private data class CacheEntry(
		val version: Int,
		val fetchedAt: Long,
		val report: ContentWarningReport? = null,
	)

	private companion object {
		const val CACHE_DIR = "content_warnings"
		const val CACHE_VERSION = 1
		val FOUND_TTL = TimeUnit.DAYS.toMillis(30)
		val NOT_FOUND_TTL = TimeUnit.DAYS.toMillis(3)
	}
}

