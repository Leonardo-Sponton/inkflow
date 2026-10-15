package io.github.leonardosponton.inkflow.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Section 8 targets: cold start < 1 s, open a 5 000-element board, 90 fps pan/zoom, stroke
 * latency. Run on the tablet: `./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class EditorBenchmarks {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.COLD,
        iterations = ITERATIONS,
    ) {
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun openLargeProject() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = ITERATIONS,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            seedProjects()
        },
    ) {
        openStressProject()
    }

    @Test
    fun panAndZoomLargeBoard() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = ITERATIONS,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            seedProjects()
            openStressProject()
        },
    ) {
        panAndZoom()
    }

    @Test
    fun drawInk() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = ITERATIONS,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            seedProjects()
            openStressProject()
        },
    ) {
        drawStrokes()
    }

    private companion object {
        const val ITERATIONS = 5
    }
}
