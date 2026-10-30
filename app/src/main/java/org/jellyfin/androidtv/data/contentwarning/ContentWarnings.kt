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

/** Where a content warning originates from. */
@Serializable
enum class ContentWarningSource {
	DOES_THE_DOG_DIE,
	IMDB,
}

@Serializable
data class ContentWarningSourceLink(
	val source: ContentWarningSource,
	val url: String,
)

/** A concrete scene description (DoesTheDogDie comment or IMDb guide item), optionally with timestamps. */
@Serializable
data class ContentWarningScene(
	val description: String,
	val timestamps: List<String> = emptyList(),
	val spoiler: Boolean = false,
)

/**
 * A single reported topic. For DoesTheDogDie this is a community confirmed topic (e.g. "a dog dies")
 * with yes/no votes, for IMDb it is the Parents Guide entry of a category with its severity votes.
 */
@Serializable
data class ContentWarningTopic(
	val name: String,
	val yesVotes: Int,
	val noVotes: Int,
	val scenes: List<ContentWarningScene> = emptyList(),
	val source: ContentWarningSource = ContentWarningSource.DOES_THE_DOG_DIE,
	/** IMDb only: number of votes per severity level. */
	val severityVotes: Map<ContentWarningSeverity, Int> = emptyMap(),
)

@Serializable
data class ContentWarningGroup(
	val category: ContentWarningCategory,
	val severity: ContentWarningSeverity,
	val topics: List<ContentWarningTopic>,
)

@Serializable
data class ContentWarningReport(
	val title: String? = null,
	val groups: List<ContentWarningGroup>,
	val sources: List<ContentWarningSourceLink> = emptyList(),
) {
	val hasWarnings get() = groups.any { it.severity != ContentWarningSeverity.NONE }
}

/** Result of a content warning lookup as consumed by the UI. */
sealed interface ContentWarningResult {
	data object Loading : ContentWarningResult

	data class Available(val report: ContentWarningReport) : ContentWarningResult

	/** The movie is not listed on any enabled source or has no votes yet. */
	data object NotFound : ContentWarningResult

	/** Neither an IMDb nor a TMDB id is known for the item. */
	data object MissingIds : ContentWarningResult

	/** No usable source: no DoesTheDogDie API key and the IMDb Parents Guide is disabled or unusable. */
	data object NotConfigured : ContentWarningResult

	/** Network or server error; nothing cached. */
	data object Error : ContentWarningResult
}

