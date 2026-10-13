package org.jellyfin.androidtv.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates a Baseline Profile for the Cinema Home Screen and the rest of the application.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

	@get:Rule
	val baselineProfileRule = BaselineProfileRule()

	@Test
	fun generateBaselineProfile() {
		baselineProfileRule.collect(
			packageName = CINEMA_PACKAGE,
			maxIterations = 5,
			profileBlock = {
				startCinemaHome()
				browseCinema()
			}
		)
	}
}
