package io.github.leonardosponton.inkflow.domain.canvas

import io.github.leonardosponton.inkflow.core.model.Vec2
import io.github.leonardosponton.inkflow.domain.canvas.input.GestureEvent
import io.github.leonardosponton.inkflow.domain.canvas.input.GestureStateMachine
import io.github.leonardosponton.inkflow.domain.canvas.input.InputEvent
import io.github.leonardosponton.inkflow.domain.canvas.input.PointerAction
import io.github.leonardosponton.inkflow.domain.canvas.input.PointerSample
import io.github.leonardosponton.inkflow.domain.canvas.input.StrokeModifier
import io.github.leonardosponton.inkflow.domain.canvas.input.ToolType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GestureStateMachineTest {
    private var fingerDraws = false
    private val gsm = GestureStateMachine({ fingerDraws })

    private fun p(id: Int, x: Double, y: Double, tool: ToolType = ToolType.FINGER) = PointerSample(id, tool, Vec2(x, y))

    private fun ev(action: PointerAction, actor: Int, t: Long, vararg ps: PointerSample, button: Boolean = false, canceled: Boolean = false) =
        gsm.onEvent(InputEvent(action, actor, ps.toList(), t, button, canceled))

    private fun pen(x: Double, y: Double) = p(0, x, y, ToolType.STYLUS)

    @Test
    fun `pen draws a stroke`() {
        val start = ev(PointerAction.DOWN, 0, 0, pen(10.0, 10.0))
        assertEquals(StrokeModifier.NONE, (start.single() as GestureEvent.StrokeStart).modifier)
        assertTrue(ev(PointerAction.MOVE, 0, 10, pen(50.0, 10.0)).single() is GestureEvent.StrokeMove)
        val end = ev(PointerAction.UP, 0, 20, pen(60.0, 10.0)).single() as GestureEvent.StrokeEnd
        assertEquals(false, end.isTap)
    }

    @Test
    fun `side button and eraser tip set the modifier`() {
        val side = ev(PointerAction.DOWN, 0, 0, pen(0.0, 0.0), button = true).single() as GestureEvent.StrokeStart
        assertEquals(StrokeModifier.SIDE_BUTTON, side.modifier)
        ev(PointerAction.UP, 0, 500, pen(100.0, 0.0))
        val eraser = ev(PointerAction.DOWN, 0, 1000, p(0, 0.0, 0.0, ToolType.ERASER)).single() as GestureEvent.StrokeStart
        assertEquals(StrokeModifier.ERASER_TIP, eraser.modifier)
    }

    @Test
    fun `pen tap then double tap`() {
        ev(PointerAction.DOWN, 0, 0, pen(10.0, 10.0))
        val first = ev(PointerAction.UP, 0, 50, pen(10.0, 10.0))
        assertTrue(first.any { it is GestureEvent.Tap })
        ev(PointerAction.DOWN, 0, 100, pen(12.0, 10.0))
        val second = ev(PointerAction.UP, 0, 150, pen(12.0, 10.0))
        assertTrue(second.any { it is GestureEvent.DoubleTap })
    }

    @Test
    fun `palm touches are ignored while the pen is down or hovering`() {
        ev(PointerAction.HOVER_MOVE, 0, 0, pen(10.0, 10.0))
        assertTrue(ev(PointerAction.DOWN, 1, 10, p(1, 400.0, 400.0)).isEmpty())
        assertTrue(ev(PointerAction.MOVE, 1, 20, p(1, 450.0, 450.0)).isEmpty())
        assertTrue(ev(PointerAction.HOVER_EXIT, 0, 30, pen(10.0, 10.0)).single() is GestureEvent.HoverExit)
        ev(PointerAction.DOWN, 0, 40, pen(10.0, 10.0))
        assertTrue(ev(PointerAction.POINTER_DOWN, 1, 50, pen(10.0, 10.0), p(1, 400.0, 400.0)).isEmpty())
    }

    @Test
    fun `system canceled pointer cancels the stroke`() {
        fingerDraws = true
        ev(PointerAction.DOWN, 1, 10_000, p(1, 0.0, 0.0))
        val started = ev(PointerAction.MOVE, 1, 10_010, p(1, 50.0, 0.0))
        assertTrue(started.first() is GestureEvent.StrokeStart)
        val canceled = ev(PointerAction.UP, 1, 10_020, p(1, 50.0, 0.0), canceled = true)
        assertTrue(canceled.single() is GestureEvent.StrokeCancel)
    }

    @Test
    fun `finger pans and pinch zooms`() {
        ev(PointerAction.DOWN, 1, 10_000, p(1, 100.0, 100.0))
        val pan = ev(PointerAction.MOVE, 1, 10_010, p(1, 150.0, 100.0)).single() as GestureEvent.Navigate
        assertEquals(Vec2(50.0, 0.0), pan.pan)
        ev(PointerAction.POINTER_DOWN, 2, 10_020, p(1, 150.0, 100.0), p(2, 250.0, 100.0))
        val zoom = ev(PointerAction.MOVE, 2, 10_030, p(1, 100.0, 100.0), p(2, 300.0, 100.0)).single() as GestureEvent.Navigate
        assertEquals(2.0, zoom.zoom, 1e-9)
        ev(PointerAction.POINTER_UP, 2, 10_040, p(1, 100.0, 100.0), p(2, 300.0, 100.0)).let {
            assertTrue(it.single() is GestureEvent.NavigateEnd)
        }
        assertTrue(ev(PointerAction.UP, 1, 10_050, p(1, 100.0, 100.0)).single() is GestureEvent.NavigateEnd)
    }

    @Test
    fun `two and three finger taps`() {
        ev(PointerAction.DOWN, 1, 10_000, p(1, 100.0, 100.0))
        ev(PointerAction.POINTER_DOWN, 2, 10_010, p(1, 100.0, 100.0), p(2, 200.0, 100.0))
        ev(PointerAction.POINTER_UP, 2, 10_050, p(1, 100.0, 100.0), p(2, 200.0, 100.0))
        assertEquals(GestureEvent.MultiFingerTap(2), ev(PointerAction.UP, 1, 10_060, p(1, 100.0, 100.0)).single())
        ev(PointerAction.DOWN, 1, 20_000, p(1, 100.0, 100.0))
        ev(PointerAction.POINTER_DOWN, 2, 20_010, p(1, 100.0, 100.0), p(2, 200.0, 100.0))
        ev(PointerAction.POINTER_DOWN, 3, 20_020, p(1, 100.0, 100.0), p(2, 200.0, 100.0), p(3, 300.0, 100.0))
        assertEquals(GestureEvent.MultiFingerTap(3), ev(PointerAction.UP, 1, 20_080, p(1, 100.0, 100.0)).single())
    }

    @Test
    fun `finger tap double tap and long press`() {
        ev(PointerAction.DOWN, 1, 10_000, p(1, 100.0, 100.0))
        assertTrue(ev(PointerAction.UP, 1, 10_050, p(1, 100.0, 100.0)).single() is GestureEvent.Tap)
        ev(PointerAction.DOWN, 1, 10_100, p(1, 100.0, 100.0))
        assertTrue(ev(PointerAction.UP, 1, 10_150, p(1, 100.0, 100.0)).single() is GestureEvent.DoubleTap)
        ev(PointerAction.DOWN, 1, 20_000, p(1, 100.0, 100.0))
        assertEquals(20_500L, gsm.nextDeadline())
        assertTrue(gsm.onTimer(20_100).isEmpty())
        assertTrue(gsm.onTimer(20_500).single() is GestureEvent.LongPress)
        assertTrue(ev(PointerAction.UP, 1, 21_000, p(1, 100.0, 100.0)).isEmpty())
    }

    @Test
    fun `finger acting as pen draws and second finger turns it into a pinch`() {
        fingerDraws = true
        ev(PointerAction.DOWN, 1, 10_000, p(1, 0.0, 0.0))
        val out = ev(PointerAction.MOVE, 1, 10_010, p(1, 40.0, 0.0))
        assertTrue(out[0] is GestureEvent.StrokeStart && out[1] is GestureEvent.StrokeMove)
        val pinch = ev(PointerAction.POINTER_DOWN, 2, 10_020, p(1, 40.0, 0.0), p(2, 140.0, 0.0))
        assertTrue(pinch.single() is GestureEvent.StrokeCancel)
    }

    @Test
    fun `stroke hold fires after pausing and long press before moving`() {
        ev(PointerAction.DOWN, 0, 0, pen(0.0, 0.0))
        assertTrue(gsm.onTimer(500).single() is GestureEvent.LongPress)
        ev(PointerAction.UP, 0, 600, pen(0.0, 0.0))
        ev(PointerAction.DOWN, 0, 1000, pen(0.0, 0.0))
        ev(PointerAction.MOVE, 0, 1100, pen(100.0, 0.0))
        assertEquals(1600L, gsm.nextDeadline())
        assertTrue(gsm.onTimer(1600).single() is GestureEvent.StrokeHold)
        assertTrue(gsm.onTimer(1700).isEmpty())
        assertTrue(ev(PointerAction.CANCEL, 0, 1800, pen(100.0, 0.0)).single() is GestureEvent.StrokeCancel)
    }

    @Test
    fun `hover reports eraser state`() {
        val h = ev(PointerAction.HOVER_MOVE, 0, 0, pen(5.0, 5.0), button = true).single() as GestureEvent.Hover
        assertTrue(h.eraser)
    }
}
