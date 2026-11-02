package org.jellyfin.androidtv.data.sdns

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch

class SdnsLogicTests : FunSpec({
	val amelie = SdnsMovie("1", "Amélie", 2001, "tt0211915", seen = false, rating = null)
	val thing1982 = SdnsMovie("2", "The Thing", 1982, null, seen = true, rating = 8.0)
	val thing2011 = SdnsMovie("3", "The Thing", 2011, null, seen = false, rating = null)
	val otherImdb = SdnsMovie("4", "Dune", 2021, "tt1160419", seen = false, rating = null)

	test("match by imdb id has priority and is case-insensitive") {
		SdnsLogic.match(listOf(thing1982, amelie), SdnsMovieQuery("Something else", null, "TT0211915")) shouldBe amelie
	}

	test("title fallback ignores accents, case and punctuation") {
		SdnsLogic.match(listOf(amelie), SdnsMovieQuery("amelie", 2001, null)) shouldBe amelie
		SdnsLogic.match(listOf(amelie), SdnsMovieQuery("  AMÉLIE! ", 2001, null)) shouldBe amelie
	}

	test("title fallback allows ±1 year") {
		SdnsLogic.match(listOf(amelie), SdnsMovieQuery("Amelie", 2002, null)) shouldBe amelie
		SdnsLogic.match(listOf(amelie), SdnsMovieQuery("Amelie", 2000, null)) shouldBe amelie
		SdnsLogic.match(listOf(amelie), SdnsMovieQuery("Amelie", 2003, null)) shouldBe null
	}

	test("same title uses the year to pick the right movie") {
		SdnsLogic.match(listOf(thing1982, thing2011), SdnsMovieQuery("The Thing", 2011, null)) shouldBe thing2011
		SdnsLogic.match(listOf(thing1982, thing2011), SdnsMovieQuery("The Thing", 1982, null)) shouldBe thing1982
	}

	test("title fallback is used when the imdb id is not stored") {
		SdnsLogic.match(listOf(thing2011), SdnsMovieQuery("The Thing", 2011, "tt0905372")) shouldBe thing2011
	}

	test("title fallback never matches a movie with a different imdb id") {
		SdnsLogic.match(listOf(otherImdb), SdnsMovieQuery("Dune", 2021, "tt0087182")) shouldBe null
	}

	test("no match returns null") {
		SdnsLogic.match(emptyList(), SdnsMovieQuery("Anything", 2020, "tt123")) shouldBe null
		SdnsLogic.match(listOf(amelie), SdnsMovieQuery("", null, null)) shouldBe null
	}

	test("invalid imdb ids are ignored") {
		SdnsLogic.normalizeImdbId("  TT0211915 ") shouldBe "tt0211915"
		SdnsLogic.normalizeImdbId("12345") shouldBe null
		SdnsLogic.normalizeImdbId(null) shouldBe null
	}

	test("status") {
		SdnsLogic.status(null) shouldBe SdnsStatus.NOT_FOUND
		SdnsLogic.status(amelie) shouldBe SdnsStatus.WATCHLIST
		SdnsLogic.status(thing1982) shouldBe SdnsStatus.RATED
		SdnsLogic.status(thing1982.copy(rating = null)) shouldBe SdnsStatus.SEEN
	}

	test("patches follow the ScreenDiceNSpice status logic") {
		SdnsLogic.ratePatch(7.5) shouldBe mapOf("rating" to 7.5, "seen" to true)
		SdnsLogic.ratePatch(0.0) shouldBe mapOf("rating" to 0.0, "seen" to true)
		SdnsLogic.seenPatch() shouldBe mapOf("seen" to true)
		SdnsLogic.watchlistPatch() shouldBe mapOf("seen" to false, "rating" to null)
	}

	test("rating validation") {
		shouldThrow<IllegalArgumentException> { SdnsLogic.validateRating(10.5) }
		shouldThrow<IllegalArgumentException> { SdnsLogic.validateRating(-0.5) }
		shouldThrow<IllegalArgumentException> { SdnsLogic.validateRating(Double.NaN) }
		SdnsLogic.validateRating(10.0) shouldBe 10.0
		SdnsLogic.validateRating(7.25) shouldBe 7.3
	}

	test("slugify matches the ScreenDiceNSpice format") {
		SdnsLogic.slugify("Zurück in die Zukunft").shouldMatch(Regex("zuruck-in-die-zukunft-[a-z0-9]{5}"))
		SdnsLogic.slugify("  Amélie & Co.!  ").shouldMatch(Regex("amelie-co-[a-z0-9]{5}"))
		SdnsLogic.slugify("Star Wars: Episode IV").shouldMatch(Regex("star-wars-episode-iv-[a-z0-9]{5}"))
	}

	test("slugify generates different suffixes") {
		(1..20).map { SdnsLogic.slugify("Dune") }.toSet().size shouldBe 20
	}

	test("stars <-> rating like ScreenDiceNSpice") {
		SdnsLogic.starsToRating(0.5) shouldBe 1.0
		SdnsLogic.starsToRating(3.5) shouldBe 7.0
		SdnsLogic.starsToRating(5.0) shouldBe 10.0
		SdnsLogic.ratingToStars(8.6) shouldBe 4.5
		SdnsLogic.ratingToStars(7.0) shouldBe 3.5
		SdnsLogic.ratingToStars(7.5) shouldBe 4.0
		SdnsLogic.ratingToStars(10.0) shouldBe 5.0
		SdnsLogic.ratingToStars(null) shouldBe 0.0
	}

	test("stars are clamped to 0.5 - 5 in 0.5 steps") {
		SdnsLogic.clampStars(0.0) shouldBe 0.5
		SdnsLogic.clampStars(-1.0) shouldBe 0.5
		SdnsLogic.clampStars(5.5) shouldBe 5.0
		SdnsLogic.clampStars(3.3) shouldBe 3.5
		SdnsLogic.clampStars(3.2) shouldBe 3.0
	}

	test("snap to 0.5 steps") {
		SdnsLogic.snapToStep(7.3) shouldBe 7.5
		SdnsLogic.snapToStep(7.2) shouldBe 7.0
		SdnsLogic.snapToStep(12.0) shouldBe 10.0
	}

	test("insert payload contains only allowed fields") {
		SdnsLogic.insertPayload("p1", SdnsMovieQuery("Dune", 2021, "TT1160419"), 8.5, slug = "dune-abc12") shouldBe mapOf(
			"profile_id" to "p1",
			"title" to "Dune",
			"slug" to "dune-abc12",
			"year" to 2021,
			"imdb_id" to "tt1160419",
			"rating" to 8.5,
			"seen" to true,
		)
	}
})

