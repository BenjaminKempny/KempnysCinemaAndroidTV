package org.jellyfin.androidtv.data.contentwarning

/** Combines the reports of multiple content warning sources into a single report. */
internal object ContentWarningMerger {
	fun merge(imdb: ContentWarningReport?, dtdd: ContentWarningReport?): ContentWarningReport? {
		if (imdb == null) return dtdd
		if (dtdd == null) return imdb

		val groups = ContentWarningCategory.entries.mapNotNull { category ->
			val imdbGroup = imdb.groups.firstOrNull { it.category == category }
			val dtddGroup = dtdd.groups.firstOrNull { it.category == category }
			if (imdbGroup == null && dtddGroup == null) return@mapNotNull null

			val topics = imdbGroup?.topics.orEmpty() + dtddGroup?.topics.orEmpty()
			ContentWarningGroup(
				category = category,
				severity = mergeSeverity(imdbGroup?.severity, dtddGroup?.severity, dtddGroup?.topics.orEmpty().isNotEmpty()),
				topics = topics,
			)
		}

		return ContentWarningReport(
			title = imdb.title ?: dtdd.title,
			groups = groups,
			sources = imdb.sources + dtdd.sources,
		)
	}

	/**
	 * IMDb severity votes are preferred over the topic-count heuristic of DoesTheDogDie. When IMDb
	 * voters see nothing but DoesTheDogDie confirmed topics, the category is raised to "mild".
	 */
	fun mergeSeverity(
		imdb: ContentWarningSeverity?,
		dtdd: ContentWarningSeverity?,
		dtddHasTopics: Boolean,
	): ContentWarningSeverity = when {
		imdb == null -> dtdd ?: ContentWarningSeverity.NONE
		imdb == ContentWarningSeverity.NONE && dtddHasTopics -> ContentWarningSeverity.MILD
		else -> imdb
	}
}

