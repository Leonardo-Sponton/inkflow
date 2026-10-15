package io.github.leonardosponton.inkflow.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

const val PACKAGE = "io.github.leonardosponton.inkflow"
private const val TIMEOUT = 15_000L
private const val STRESS = "Open Stress · 5000 elements"

/** Seeds the demo projects once (benchmark builds honour the `seed` extra). */
fun MacrobenchmarkScope.seedProjects() {
    device.executeShellCommand("am start -W -n $PACKAGE/.MainActivity --es seed stress")
    device.wait(Until.hasObject(By.desc(STRESS)), TIMEOUT)
}

fun MacrobenchmarkScope.openStressProject() {
    device.wait(Until.hasObject(By.desc(STRESS)), TIMEOUT)
    device.findObject(By.desc(STRESS))?.click()
    device.wait(Until.hasObject(By.desc("Canvas. Draw with the S Pen, navigate with fingers.")), TIMEOUT)
    device.waitForIdle()
}

fun MacrobenchmarkScope.canvas() = device.findObject(By.desc("Canvas. Draw with the S Pen, navigate with fingers."))

/** Finger pans and pinches across the large board. */
fun MacrobenchmarkScope.panAndZoom() {
    val c = canvas() ?: return
    repeat(PANS) {
        c.swipe(if (it % 2 == 0) Direction.LEFT else Direction.UP, SWIPE_PERCENT, SWIPE_SPEED)
    }
    c.pinchClose(PINCH_PERCENT, PINCH_SPEED)
    c.pinchOpen(PINCH_PERCENT, PINCH_SPEED)
    device.waitForIdle()
}

/** Pen strokes through `input stylus` (the default pen tool draws ink). */
fun MacrobenchmarkScope.drawStrokes() {
    val b = canvas()?.visibleBounds ?: return
    repeat(STROKES) { i ->
        val y = b.top + (i + 1) * b.height() / (STROKES + 1)
        device.executeShellCommand("input stylus swipe ${b.left + b.width() / 5} $y ${b.right - b.width() / 5} ${y + 40} 400")
    }
    device.waitForIdle()
}

private const val PANS = 6
private const val SWIPE_PERCENT = 0.6f
private const val SWIPE_SPEED = 2500
private const val PINCH_PERCENT = 0.5f
private const val PINCH_SPEED = 1500
private const val STROKES = 6
