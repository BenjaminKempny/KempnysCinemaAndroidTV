package org.jellyfin.androidtv.data.contentwarning

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Thrown for non-successful HTTP responses of the DoesTheDogDie API. */
class DoesTheDogDieException(val code: Int) : IOException("DoesTheDogDie request failed with HTTP $code")

/** A search hit of `GET /dddsearch`. */
data class DoesTheDogDieSearchItem(
	val id: Int,
	val name: String?,
	val releaseYear: Int?,
	val imdbId: String?,
	val tmdbId: Int?,
	val itemType: String?,
)

/**
 * Minimal blocking client for the DoesTheDogDie API (https://www.doesthedogdie.com/api).
 * All calls must be made off the main thread.
 */
class DoesTheDogDieApi(private val apiKey: String) {
	private val json = Json { ignoreUnknownKeys = true; isLenient = true }

	fun searchByImdb(imdbId: String): List<DoesTheDogDieSearchItem> =
		parseSearch(request("dddsearch?imdb=${imdbId.urlEncode()}"))

	fun searchByName(query: String): List<DoesTheDogDieSearchItem> =
		parseSearch(request("dddsearch?q=${query.urlEncode()}"))

	fun getReport(mediaId: Int, title: String?): ContentWarningReport? =
		parseMedia(mediaId, title, request("media/$mediaId"))

	private fun request(path: String): JsonElement {
		val connection = URL("$BASE_URL/$path").openConnection() as HttpURLConnection
		try {
			connection.requestMethod = "GET"
			connection.connectTimeout = TIMEOUT_MS
			connection.readTimeout = TIMEOUT_MS
			connection.setRequestProperty("Accept", "application/json")
			connection.setRequestProperty("X-API-KEY", apiKey)

			val code = connection.responseCode
			if (code !in 200..299) throw DoesTheDogDieException(code)

			val body = connection.inputStream.bufferedReader().use { it.readText() }
			return json.parseToJsonElement(body)
		} finally {
			connection.disconnect()
		}
	}

	private fun parseSearch(root: JsonElement): List<DoesTheDogDieSearchItem> {
		val items = (root as? JsonObject)?.get("items") as? JsonArray ?: return emptyList()
		return items.mapNotNull { element ->
			val obj = element as? JsonObject ?: return@mapNotNull null
			val id = obj.int("id") ?: return@mapNotNull null
			DoesTheDogDieSearchItem(
				id = id,
				name = obj.string("name"),
				releaseYear = obj.int("releaseYear"),
				imdbId = obj.string("imdbId"),
				tmdbId = obj.int("tmdbId"),
				itemType = (obj["itemType"] as? JsonObject)?.string("name"),
			)
		}
	}

	private fun parseMedia(mediaId: Int, fallbackTitle: String?, root: JsonElement): ContentWarningReport? {
		val obj = root as? JsonObject ?: return null
		val stats = obj["topicItemStats"] as? JsonArray ?: return null
		val title = (obj["item"] as? JsonObject)?.string("name") ?: fallbackTitle

		val topics = stats.mapNotNull { element ->
			val stat = element as? JsonObject ?: return@mapNotNull null
			val topic = stat["topic"] as? JsonObject ?: return@mapNotNull null
			val name = topic.string("name") ?: topic.string("doesName") ?: return@mapNotNull null
			val categoryName = (topic["TopicCategory"] as? JsonObject)?.string("name")
				?: (topic["topicCategory"] as? JsonObject)?.string("name")
			val yes = stat.int("yesSum") ?: 0
			val no = stat.int("noSum") ?: 0

			val scenes = (stat["comments"] as? JsonArray).orEmpty()
				.mapNotNull { it as? JsonObject }
				.mapNotNull { comment ->
					val text = comment.string("comment")?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
					Triple(text, comment.int("voteSum") ?: 0, comment.boolean("isSpoiler") == true)
				}
				.sortedByDescending { it.second }
				.take(MAX_SCENES_PER_TOPIC)
				.map { (text, _, spoiler) ->
					ContentWarningScene(
						description = text.ellipsize(MAX_SCENE_LENGTH),
						timestamps = ContentWarningClassifier.extractTimestamps(text),
						spoiler = spoiler || topic.boolean("isSpoiler") == true,
					)
				}

			RawTopic(
				category = ContentWarningClassifier.categorize(name, categoryName),
				topic = ContentWarningTopic(
					name = name.replaceFirstChar { it.uppercase() },
					yesVotes = yes,
					noVotes = no,
					scenes = scenes,
				),
			)
		}

		// A topic counts as present when the community majority voted "yes".
		val confirmed = topics.filter { it.topic.yesVotes > 0 && it.topic.yesVotes > it.topic.noVotes }
		val byCategory = confirmed.groupBy { it.category }

		val groups = ContentWarningCategory.entries.mapNotNull { category ->
			val entries = byCategory[category].orEmpty()
				.map { it.topic }
				.sortedByDescending { it.yesVotes }
			if (entries.isEmpty() && category !in ContentWarningCategory.primary) return@mapNotNull null

			ContentWarningGroup(
				category = category,
				severity = ContentWarningClassifier.severity(entries),
				topics = entries,
			)
		}

		return ContentWarningReport(mediaId = mediaId, title = title, groups = groups)
	}

	private data class RawTopic(val category: ContentWarningCategory, val topic: ContentWarningTopic)

	private fun JsonObject.primitive(key: String) = (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }
	private fun JsonObject.string(key: String) = primitive(key)?.content?.takeIf { it.isNotBlank() }
	private fun JsonObject.int(key: String) = primitive(key)?.content?.trim()?.toDoubleOrNull()?.toInt()
	private fun JsonObject.boolean(key: String) = primitive(key)?.content?.toBooleanStrictOrNull()

	private fun String.urlEncode() = URLEncoder.encode(this, Charsets.UTF_8.name())
	private fun String.ellipsize(max: Int) = if (length <= max) this else take(max - 1).trimEnd() + "…"

	private companion object {
		const val BASE_URL = "https://www.doesthedogdie.com"
		const val TIMEOUT_MS = 10_000
		const val MAX_SCENES_PER_TOPIC = 3
		const val MAX_SCENE_LENGTH = 320
	}
}

/** Maps DoesTheDogDie topics onto the app's content warning categories. */
internal object ContentWarningClassifier {
	private val sexKeywords = listOf(
		"sex", "nud", "naked", "rape", "incest", "porn", "genital", "breast", "erotic", "prostitut", "strip",
	)
	private val drugKeywords = listOf(
		"drug", "alcohol", "drunk", "addict", "overdose", "smok", "needle", "inject", "cocaine", "heroin",
	)
	private val languageKeywords = listOf(
		"slur", "swear", "curse", "cursing", "profan", "language", "n-word", "f-word", "obscen", "homophobic", "racist",
	)
	private val frighteningKeywords = listOf(
		"jump scare", "jumpscare", "scare", "horror", "ghost", "creepy", "clown", "spider", "phobia", "claustro",
		"possess", "demon", "haunt", "shark", "snake", "insect", "bug", "monster", "zombie", "dark", "body horror",
		"paranormal", "flashing", "nightmare", "eye", "teeth",
	)
	private val violenceKeywords = listOf(
		"die", "dies", "death", "dead", "kill", "murder", "violen", "gun", "shoot", "shot", "stab", "tortur",
		"abus", "blood", "gore", "fight", " war ", "suicide", "self-harm", "self harm", "explos", "assault",
		" beat", " hang", "decapitat", "burn", "injur", "kidnap", "cannibal", "execut", "domestic",
	)
	private val severeKeywords = listOf(
		"rape", "sexual assault", "tortur", "gore", "graphic", "suicide", "decapitat", "overdose", "child abuse",
		"cannibal", "incest",
	)

	private val clockTimestamp = Regex("""(?<!\d)(\d{1,2}:\d{2}(?::\d{2})?)(?!\d)""")
	private val minuteTimestamp = Regex("""(?i)(?<![\d:])(\d{1,3})\s?(?:min|mins|minutes|minute|minuten)\b""")

	fun categorize(topicName: String, categoryName: String?): ContentWarningCategory {
		// Padded with spaces so word keywords like " war " also match at the start/end.
		val topic = " ${topicName.lowercase()} "
		val text = "$topic${categoryName.orEmpty().lowercase()} "
		return when {
			sexKeywords.any { it in topic } -> ContentWarningCategory.SEX_NUDITY
			drugKeywords.any { it in topic } -> ContentWarningCategory.DRUGS
			languageKeywords.any { it in topic } -> ContentWarningCategory.LANGUAGE
			frighteningKeywords.any { it in topic } -> ContentWarningCategory.FRIGHTENING
			violenceKeywords.any { it in topic } -> ContentWarningCategory.VIOLENCE
			// Fall back to the DoesTheDogDie category name when the topic itself is ambiguous.
			sexKeywords.any { it in text } -> ContentWarningCategory.SEX_NUDITY
			drugKeywords.any { it in text } -> ContentWarningCategory.DRUGS
			languageKeywords.any { it in text } -> ContentWarningCategory.LANGUAGE
			frighteningKeywords.any { it in text } -> ContentWarningCategory.FRIGHTENING
			violenceKeywords.any { it in text } -> ContentWarningCategory.VIOLENCE
			else -> ContentWarningCategory.OTHER
		}
	}

	/**
	 * DoesTheDogDie only offers yes/no votes, the severity is therefore derived from how many
	 * distinct topics of a category were confirmed and whether any of them is inherently severe.
	 */
	fun severity(topics: List<ContentWarningTopic>): ContentWarningSeverity {
		if (topics.isEmpty()) return ContentWarningSeverity.NONE
		val hasSevereTopic = topics.any { topic -> severeKeywords.any { it in topic.name.lowercase() } }
		val bySeverity = when {
			topics.size >= 4 -> ContentWarningSeverity.SEVERE
			topics.size >= 2 -> ContentWarningSeverity.MODERATE
			else -> ContentWarningSeverity.MILD
		}
		return if (hasSevereTopic && bySeverity < ContentWarningSeverity.MODERATE) ContentWarningSeverity.MODERATE
		else if (hasSevereTopic && topics.size >= 2) ContentWarningSeverity.SEVERE
		else bySeverity
	}

	fun extractTimestamps(text: String): List<String> {
		val clock = clockTimestamp.findAll(text).map { it.groupValues[1] }
		val minutes = minuteTimestamp.findAll(text).map { "${it.groupValues[1]} min" }
		return (clock + minutes).distinct().take(MAX_TIMESTAMPS).toList()
	}

	private const val MAX_TIMESTAMPS = 4
}




