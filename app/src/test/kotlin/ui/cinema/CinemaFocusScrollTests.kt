package org.jellyfin.androidtv.ui.cinema

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class CinemaFocusScrollTests : FunSpec({
	test("visible play button does not pull the hero above the viewport") {
		CinemaBringIntoViewSpec.calculateScrollDistance(350f, 48f, 480f) shouldBe 0f
		CinemaBringIntoViewSpec.calculateScrollDistance(120f, 320f, 480f) shouldBe 0f
	}

	test("returning from lower sections reveals the entire hero and then stops") {
		val offset = -180f
		val distance = CinemaBringIntoViewSpec.calculateScrollDistance(offset, 320f, 480f)
		distance shouldBe offset
		CinemaBringIntoViewSpec.calculateScrollDistance(offset - distance, 320f, 480f) shouldBe 0f
		// The button inside the now visible hero must agree with that destination.
		CinemaBringIntoViewSpec.calculateScrollDistance(240f, 48f, 480f) shouldBe 0f
	}

	test("hero entering from below moves only enough to show the whole section") {
		val distance = CinemaBringIntoViewSpec.calculateScrollDistance(300f, 320f, 480f)
		distance shouldBe 140f
		CinemaBringIntoViewSpec.calculateScrollDistance(300f - distance, 320f, 480f) shouldBe 0f
	}

	test("oversized content spanning the viewport does not oscillate between edges") {
		CinemaBringIntoViewSpec.calculateScrollDistance(-20f, 600f, 480f) shouldBe 0f
	}
})
