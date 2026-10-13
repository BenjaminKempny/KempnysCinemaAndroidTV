package org.jellyfin.androidtv.data.contentwarning

import kotlinx.serialization.Serializable

/** The content warning groups shown on the detail page. Order defines the display order. */
@Serializable
enum class ContentWarningCategory {
	SEX_NUDITY,
	VIOLENCE,
	LANGUAGE,
	DRUGS,
	FRIGHTENING,
	OTHER,
	;

	companion object {
		/** Categories that are always listed, even if nothing was reported for them. */
		val primary = listOf(SEX_NUDITY, VIOLENCE, LANGUAGE, DRUGS, FRIGHTENING)
	}
}

@Serializable
enum class ContentWarningSeverity {
	NONE,
	MILD,
	MODERATE,
	SEVERE,
}

/** A concrete scene description (user comment on DoesTheDogDie), optionally with timestamps. */
@Serializable
data class ContentWarningScene(
	val description: String,
	val timestamps: List<String> = emptyList(),
	val spoiler: Boolean = false,
)

/** A single DoesTheDogDie topic (e.g. "a dog dies") that the community confirmed. */
@Serializable
data class ContentWarningTopic(
	val name: String,
	val yesVotes: Int,
	val noVotes: Int,
	val scenes: List<ContentWarningScene> = emptyList(),
)

@Serializable
data class ContentWarningGroup(
	val category: ContentWarningCategory,
	val severity: ContentWarningSeverity,
	val topics: List<ContentWarningTopic>,
)

@Serializable
data class ContentWarningReport(
	val mediaId: Int,
	val title: String? = null,
	val groups: List<ContentWarningGroup>,
) {
	val url get() = "https://www.doesthedogdie.com/media/$mediaId"
	val hasWarnings get() = groups.any { it.severity != ContentWarningSeverity.NONE }
}

/** Result of a content warning lookup as consumed by the UI. */
sealed interface ContentWarningResult {
	data object Loading : ContentWarningResult

	data class Available(val report: ContentWarningReport) : ContentWarningResult

	/** The movie is not listed on DoesTheDogDie or has no votes yet. */
	data object NotFound : ContentWarningResult

	/** Neither an IMDb nor a TMDB id is known for the item. */
	data object MissingIds : ContentWarningResult

	/** No API key configured at build time. */
	data object NotConfigured : ContentWarningResult

	/** Network or server error; nothing cached. */
	data object Error : ContentWarningResult
}

