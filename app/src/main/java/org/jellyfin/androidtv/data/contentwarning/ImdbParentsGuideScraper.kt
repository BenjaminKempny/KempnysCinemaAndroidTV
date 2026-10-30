package org.jellyfin.androidtv.data.contentwarning

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Thrown when IMDb rejects or blocks a Parents Guide request. */
class ImdbParentsGuideException(val code: Int) : IOException("IMDb Parents Guide request failed with HTTP $code") {
	/** IMDb is rate limiting or showing a bot challenge, requests should pause for a while. */
	val isBlocked get() = code == 202 || code == 403 || code == 429
}

/**
 * Scrapes the IMDb Parents Guide (https://www.imdb.com/title/{id}/parentalguide/).
 *
 * The HTML page is protected by a bot challenge, the scraper therefore queries the GraphQL
 * endpoint the IMDb website itself uses to render the page. The endpoint is unofficial and may
 * change at any time; every unexpected response is reported as "no data" instead of crashing.
 * All calls are blocking and must be made off the main thread.
 */
class ImdbParentsGuideScraper {
	private val json = Json { ignoreUnknownKeys = true; isLenient = true }

	fun getReport(imdbId: String): ContentWarningReport? = parse(imdbId, request(imdbId))

	private fun request(imdbId: String): JsonElement {
		val body = buildJsonObject {
			put("query", QUERY)
			putJsonObject("variables") {
				put("id", imdbId)
				put("first", MAX_ITEMS)
			}
		}.toString()

		val connection = URL(GRAPHQL_URL).openConnection() as HttpURLConnection
		try {
			connection.requestMethod = "POST"
			connection.doOutput = true
			connection.connectTimeout = TIMEOUT_MS
			connection.readTimeout = TIMEOUT_MS
			connection.setRequestProperty("Content-Type", "application/json")
			connection.setRequestProperty("Accept", "application/json")
			connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
			connection.setRequestProperty("User-Agent", USER_AGENT)
			connection.setRequestProperty("x-imdb-client-name", "imdb-web-next-localized")
			connection.setRequestProperty("x-imdb-user-language", "en-US")
			connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

			val code = connection.responseCode
			// 202 is returned together with a JavaScript bot challenge instead of data.
			if (code !in 200..299 || code == 202) throw ImdbParentsGuideException(code)
			if (connection.contentType?.contains("json", ignoreCase = true) == false) throw ImdbParentsGuideException(code)

			val text = connection.inputStream.bufferedReader().use { it.readText() }
			return json.parseToJsonElement(text)
		} finally {
			connection.disconnect()
		}
	}

	internal fun parse(imdbId: String, root: JsonElement): ContentWarningReport? {
		val title = ((root as? JsonObject)?.get("data") as? JsonObject)?.get("title") as? JsonObject ?: return null
		val categories = (title["parentsGuide"] as? JsonObject)?.get("categories") as? JsonArray ?: return null
		val name = (title["titleText"] as? JsonObject)?.string("text")

		val groups = categories.mapNotNull { element ->
			val obj = element as? JsonObject ?: return@mapNotNull null
			val category = mapCategory((obj["category"] as? JsonObject)?.string("id")) ?: return@mapNotNull null
			val votes = parseVotes(obj)
			val scenes = parseScenes(obj)

			val severity = when {
				votes.values.any { it > 0 } -> votes.maxBy { it.value }.key
				else -> parseSeverityId((obj["severity"] as? JsonObject)?.string("id"))
					?: if (scenes.isNotEmpty()) ContentWarningSeverity.MILD else ContentWarningSeverity.NONE
			}

			val topics = if (severity == ContentWarningSeverity.NONE && scenes.isEmpty()) emptyList()
			else listOf(
				ContentWarningTopic(
					name = TOPIC_NAME,
					yesVotes = votes.filterKeys { it != ContentWarningSeverity.NONE }.values.sum(),
					noVotes = votes[ContentWarningSeverity.NONE] ?: 0,
					scenes = scenes,
					source = ContentWarningSource.IMDB,
					severityVotes = votes,
				)
			)

			ContentWarningGroup(category = category, severity = severity, topics = topics)
		}

		if (groups.isEmpty()) return null
		return ContentWarningReport(
			title = name,
			groups = groups,
			sources = listOf(ContentWarningSourceLink(ContentWarningSource.IMDB, "https://www.imdb.com/title/$imdbId/parentalguide/")),
		)
	}

	private fun parseVotes(category: JsonObject): Map<ContentWarningSeverity, Int> =
		(category["severityBreakdown"] as? JsonArray).orEmpty()
			.mapNotNull { it as? JsonObject }
			.mapNotNull { vote ->
				val severity = parseSeverityId(vote.string("voteType")) ?: return@mapNotNull null
				severity to (vote.int("votedFor") ?: 0)
			}
			.groupBy({ it.first }, { it.second })
			.mapValues { (_, values) -> values.sum() }

	private fun parseScenes(category: JsonObject): List<ContentWarningScene> =
		((category["guideItems"] as? JsonObject)?.get("edges") as? JsonArray).orEmpty()
			.mapNotNull { ((it as? JsonObject)?.get("node") as? JsonObject) }
			.mapNotNull { node ->
				val html = (node["text"] as? JsonObject)?.string("plaidHtml") ?: return@mapNotNull null
				val text = htmlToText(html).takeIf { it.isNotBlank() } ?: return@mapNotNull null
				ContentWarningScene(
					description = text.ellipsize(MAX_SCENE_LENGTH),
					timestamps = ContentWarningClassifier.extractTimestamps(text),
					spoiler = node.boolean("isSpoiler") == true,
				)
			}
			.take(MAX_ITEMS)

	private fun JsonObject.primitive(key: String) = (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }
	private fun JsonObject.string(key: String) = primitive(key)?.content?.takeIf { it.isNotBlank() }
	private fun JsonObject.int(key: String) = primitive(key)?.content?.trim()?.toDoubleOrNull()?.toInt()
	private fun JsonObject.boolean(key: String) = primitive(key)?.content?.toBooleanStrictOrNull()

	private fun String.ellipsize(max: Int) = if (length <= max) this else take(max - 1).trimEnd() + "…"

	internal companion object {
		const val TOPIC_NAME = "IMDb Parents Guide"
		private const val GRAPHQL_URL = "https://caching.graphql.imdb.com/"
		private const val TIMEOUT_MS = 10_000
		private const val MAX_ITEMS = 25
		private const val MAX_SCENE_LENGTH = 400
		private const val USER_AGENT =
			"Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"

		private val QUERY = """
			query ParentsGuide(${'$'}id: ID!, ${'$'}first: Int!) {
				title(id: ${'$'}id) {
					titleText { text }
					parentsGuide {
						categories {
							category { id }
							severity { id }
							severityBreakdown { votedFor voteType }
							guideItems(first: ${'$'}first) { edges { node { isSpoiler text { plaidHtml } } } }
						}
					}
				}
			}
		""".trimIndent()

		private val tagRegex = Regex("<[^>]+>")
		private val breakRegex = Regex("(?i)<br\\s*/?>")
		private val numericEntityRegex = Regex("&#(x?)([0-9a-fA-F]+);")
		private val whitespaceRegex = Regex("[ \\t\\u00A0]+")
		private val namedEntities = mapOf(
			"&quot;" to "\"", "&apos;" to "'", "&lt;" to "<", "&gt;" to ">", "&nbsp;" to " ",
			"&hellip;" to "…", "&ndash;" to "–", "&mdash;" to "—", "&lsquo;" to "‘", "&rsquo;" to "’",
			"&ldquo;" to "“", "&rdquo;" to "”",
		)

		fun mapCategory(id: String?): ContentWarningCategory? = when (id?.uppercase()) {
			"NUDITY" -> ContentWarningCategory.SEX_NUDITY
			"VIOLENCE" -> ContentWarningCategory.VIOLENCE
			"PROFANITY" -> ContentWarningCategory.LANGUAGE
			"ALCOHOL" -> ContentWarningCategory.DRUGS
			"FRIGHTENING" -> ContentWarningCategory.FRIGHTENING
			else -> null
		}

		/** Accepts both the breakdown ids ("MILD") and the summary ids ("mildVotes"). */
		fun parseSeverityId(id: String?): ContentWarningSeverity? = when (id?.lowercase()?.removeSuffix("votes")) {
			"none" -> ContentWarningSeverity.NONE
			"mild" -> ContentWarningSeverity.MILD
			"moderate" -> ContentWarningSeverity.MODERATE
			"severe" -> ContentWarningSeverity.SEVERE
			else -> null
		}

		/** Minimal HTML to plain text conversion that also works in JVM unit tests (no android.text.Html). */
		fun htmlToText(html: String): String {
			var text = html.replace(breakRegex, "\n").replace(tagRegex, "")
			namedEntities.forEach { (entity, value) -> text = text.replace(entity, value) }
			text = numericEntityRegex.replace(text) { match ->
				val radix = if (match.groupValues[1].isNotEmpty()) 16 else 10
				match.groupValues[2].toIntOrNull(radix)?.let { String(Character.toChars(it)) } ?: match.value
			}
			// Decode &amp; last so encoded entities like "&amp;quot;" are not decoded twice.
			text = text.replace("&amp;", "&")
			return text.lines().joinToString("\n") { it.replace(whitespaceRegex, " ").trim() }.trim()
		}
	}
}

