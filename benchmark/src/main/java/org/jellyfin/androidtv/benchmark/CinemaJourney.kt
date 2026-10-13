package org.jellyfin.androidtv.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

internal const val CINEMA_PACKAGE = "org.jellyfin.androidtv.benchmark"

internal fun MacrobenchmarkScope.startCinemaHome() {
	pressHome()
	startActivityAndWait()
	check(device.wait(Until.hasObject(By.res("cinema-home-ready")), 30_000)) {
		"Open the benchmark app, sign in to a test library containing movies and select All before running."
	}
	device.waitForIdle()
}

/** Uses navigation only: never activates a play, delete or account button. */
internal fun MacrobenchmarkScope.browseCinema() {
	repeat(8) { device.pressDPadDown() }
	repeat(6) { device.pressDPadRight() }
	repeat(6) { device.pressDPadLeft() }
	repeat(8) { device.pressDPadUp() }
	device.waitForIdle()
}
