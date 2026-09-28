package org.jellyfin.androidtv.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
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
			packageName = "org.jellyfin.androidtv.benchmark",
			maxIterations = 5,
			profileBlock = {
				pressHome()
				startActivityAndWait()

				// First: Wait for the main UI to render (the tab row or a grid row is a good indicator)
				val hasHeader = device.wait(Until.hasObject(By.text("Alle")), 10_000)
				if (hasHeader) {
					// Give data some time to load in the background
					device.waitForIdle(2000)

					// Simulate scrolling through the catalog (if reachable). This forces Compose
					// and Coil to load images, measure layouts, and execute Recomposition.

					// Focus down to scroll
					repeat(4) {
						device.pressDPadDown()
						device.waitForIdle(500)
					}

					// Focus right in the grid
					repeat(4) {
						device.pressDPadRight()
						device.waitForIdle(300)
					}

					// Back to tabs
					repeat(5) {
						device.pressDPadUp()
						device.waitForIdle(300)
					}

					// Switch tab
					device.pressDPadRight()
					device.waitForIdle(1000)

					// Switch tab again
					device.pressDPadRight()
					device.waitForIdle(1000)
				}
			}
		)
	}
}
