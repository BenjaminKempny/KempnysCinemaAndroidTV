package org.jellyfin.androidtv.data.contentwarning

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class ImdbParentsGuideScraperTests : FunSpec({
	val scraper = ImdbParentsGuideScraper()

	// Trimmed response of the IMDb GraphQL endpoint for "The Matrix" (tt0133093).
	val response = """
		{"data":{"title":{"titleText":{"text":"The Matrix"},"parentsGuide":{"categories":[
			{"category":{"id":"NUDITY"},"severity":{"id":"mildVotes"},
			 "severityBreakdown":[{"votedFor":535,"voteType":"NONE"},{"votedFor":566,"voteType":"MILD"},{"votedFor":61,"voteType":"MODERATE"},{"votedFor":43,"voteType":"SEVERE"}],
			 "guideItems":{"edges":[{"node":{"isSpoiler":true,"text":{"plaidHtml":"Trinity and Neo kiss."}}},
			   {"node":{"isSpoiler":false,"text":{"plaidHtml":"Someone is called a &quot;digital pimp&quot;.<br/>Around 1:05:30."}}}]}},
			{"category":{"id":"VIOLENCE"},"severity":{"id":"moderateVotes"},
			 "severityBreakdown":[{"votedFor":18,"voteType":"NONE"},{"votedFor":106,"voteType":"MILD"},{"votedFor":411,"voteType":"MODERATE"},{"votedFor":73,"voteType":"SEVERE"}],
			 "guideItems":{"edges":[]}},
			{"category":{"id":"PROFANITY"},"severity":{"id":"noneVotes"},"severityBreakdown":[],"guideItems":{"edges":[]}},
			{"category":{"id":"ALCOHOL"},"severity":{"id":"severeVotes"},"severityBreakdown":[],"guideItems":{"edges":[]}},
			{"category":{"id":"SPOILERS"},"severity":null,"severityBreakdown":[],"guideItems":{"edges":[]}}
		]}}}}
	""".trimIndent()

	test("parses categories, severity votes and scenes") {
		val report = scraper.parse("tt0133093", Json.parseToJsonElement(response)).shouldNotBeNull()

		report.title shouldBe "The Matrix"
		report.sources.single().url shouldBe "https://www.imdb.com/title/tt0133093/parentalguide/"
		report.groups.map { it.category } shouldBe listOf(
			ContentWarningCategory.SEX_NUDITY,
			ContentWarningCategory.VIOLENCE,
			ContentWarningCategory.LANGUAGE,
			ContentWarningCategory.DRUGS,
		)

		val nudity = report.groups[0]
		nudity.severity shouldBe ContentWarningSeverity.MILD
		val topic = nudity.topics.single()
		topic.source shouldBe ContentWarningSource.IMDB
		topic.severityVotes[ContentWarningSeverity.SEVERE] shouldBe 43
		topic.scenes shouldHaveSize 2
		topic.scenes[0].spoiler shouldBe true
		topic.scenes[1].description shouldBe "Someone is called a \"digital pimp\".\nAround 1:05:30."
		topic.scenes[1].timestamps shouldBe listOf("1:05:30")

		report.groups[1].severity shouldBe ContentWarningSeverity.MODERATE
		// Without a vote breakdown the summary id is used.
		report.groups[2].severity shouldBe ContentWarningSeverity.NONE
		report.groups[2].topics shouldBe emptyList()
		report.groups[3].severity shouldBe ContentWarningSeverity.SEVERE
	}

	test("unknown titles return no report") {
		scraper.parse("tt9999999", Json.parseToJsonElement("""{"data":{"title":{"parentsGuide":{"categories":null}}}}""")).shouldBeNull()
		scraper.parse("tt9999999", Json.parseToJsonElement("""{"data":{"title":null}}""")).shouldBeNull()
		scraper.parse("tt9999999", Json.parseToJsonElement("""{"errors":[{"message":"boom"}]}""")).shouldBeNull()
	}

	test("html is converted to plain text") {
		ImdbParentsGuideScraper.htmlToText("A <b>bold</b> &amp; &#39;quoted&#39; &#x2014; text") shouldBe "A bold & 'quoted' — text"
		ImdbParentsGuideScraper.htmlToText("&amp;quot;") shouldBe "&quot;"
	}
})

class ContentWarningMergerTests : FunSpec({
	fun topic(name: String, source: ContentWarningSource) = ContentWarningTopic(name, 1, 0, source = source)

	fun report(source: ContentWarningSource, vararg groups: ContentWarningGroup) = ContentWarningReport(
		title = source.name,
		groups = groups.toList(),
		sources = listOf(ContentWarningSourceLink(source, "https://example.com/${source.name}")),
	)

	test("a single source is returned unchanged") {
		val dtdd = report(ContentWarningSource.DOES_THE_DOG_DIE)
		ContentWarningMerger.merge(null, dtdd) shouldBe dtdd
		ContentWarningMerger.merge(dtdd, null) shouldBe dtdd
		ContentWarningMerger.merge(null, null).shouldBeNull()
	}

	test("groups are combined and IMDb severity takes precedence") {
		val imdb = report(
			ContentWarningSource.IMDB,
			ContentWarningGroup(ContentWarningCategory.VIOLENCE, ContentWarningSeverity.MILD, listOf(topic("imdb", ContentWarningSource.IMDB))),
			ContentWarningGroup(ContentWarningCategory.LANGUAGE, ContentWarningSeverity.NONE, emptyList()),
		)
		val dtdd = report(
			ContentWarningSource.DOES_THE_DOG_DIE,
			ContentWarningGroup(
				ContentWarningCategory.VIOLENCE,
				ContentWarningSeverity.SEVERE,
				listOf(topic("a", ContentWarningSource.DOES_THE_DOG_DIE), topic("b", ContentWarningSource.DOES_THE_DOG_DIE)),
			),
			ContentWarningGroup(ContentWarningCategory.LANGUAGE, ContentWarningSeverity.MILD, listOf(topic("slur", ContentWarningSource.DOES_THE_DOG_DIE))),
			ContentWarningGroup(ContentWarningCategory.OTHER, ContentWarningSeverity.MILD, listOf(topic("other", ContentWarningSource.DOES_THE_DOG_DIE))),
		)

		val merged = ContentWarningMerger.merge(imdb, dtdd).shouldNotBeNull()
		merged.title shouldBe "IMDB"
		merged.sources shouldHaveSize 2
		merged.groups.map { it.category } shouldBe listOf(
			ContentWarningCategory.VIOLENCE,
			ContentWarningCategory.LANGUAGE,
			ContentWarningCategory.OTHER,
		)

		val violence = merged.groups[0]
		violence.severity shouldBe ContentWarningSeverity.MILD
		violence.topics.map { it.name } shouldBe listOf("imdb", "a", "b")

		// IMDb says "none", DoesTheDogDie confirmed something: raise to mild.
		merged.groups[1].severity shouldBe ContentWarningSeverity.MILD
		merged.groups[2].severity shouldBe ContentWarningSeverity.MILD
	}
})

