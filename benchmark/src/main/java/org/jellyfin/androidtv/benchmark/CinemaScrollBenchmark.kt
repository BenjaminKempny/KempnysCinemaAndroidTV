package org.jellyfin.androidtv.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CinemaScrollBenchmark {
	@get:Rule
	val benchmarkRule = MacrobenchmarkRule()

	@Test
	fun dpadScrolling() = benchmarkRule.measureRepeated(
		packageName = CINEMA_PACKAGE,
		metrics = listOf(FrameTimingMetric()),
		compilationMode = CompilationMode.Full(),
		iterations = 5,
		startupMode = StartupMode.COLD,
		setupBlock = { startCinemaHome() },
		measureBlock = { browseCinema() },
	)
}
