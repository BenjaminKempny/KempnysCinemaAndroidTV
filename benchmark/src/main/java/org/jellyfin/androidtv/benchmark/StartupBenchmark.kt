package org.jellyfin.androidtv.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

	@get:Rule
	val benchmarkRule = MacrobenchmarkRule()

	@Test
	fun startupNoCompilation() = startup(CompilationMode.None())

	@Test
	fun startupBaselineProfile() = startup(CompilationMode.Partial())

	private fun startup(compilationMode: CompilationMode) {
		benchmarkRule.measureRepeated(
			packageName = "org.jellyfin.androidtv.benchmark",
			metrics = listOf(StartupTimingMetric()),
			compilationMode = compilationMode,
			iterations = 5,
			startupMode = StartupMode.COLD,
			setupBlock = {
				pressHome()
			}
		) {
			startActivityAndWait()
			// Wait for the app to actually display something meaningful before stopping the timer
			device.wait(Until.hasObject(By.res("android:id/content")), 10_000)
		}
	}
}
