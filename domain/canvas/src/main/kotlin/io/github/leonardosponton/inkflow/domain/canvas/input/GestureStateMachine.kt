package io.github.leonardosponton.inkflow.domain.canvas.input

import io.github.leonardosponton.inkflow.core.model.Vec2
import kotlin.math.atan2

enum class ToolType { FINGER, STYLUS, ERASER, MOUSE }

enum class PointerAction { DOWN, POINTER_DOWN, MOVE, POINTER_UP, UP, CANCEL, HOVER_MOVE, HOVER_EXIT }

/** One pointer's state in screen pixels. */
data class PointerSample(val id: Int, val tool: ToolType, val position: Vec2, val pressure: Float = 1f)

/**
 * Platform-neutral input event built from a `MotionEvent` by the Android adapter.
 * [canceled] mirrors `FLAG_CANCELED` (the system decided the touch was a palm).
 */
data class InputEvent(
    val action: PointerAction,
    val actionPointerId: Int,
    val pointers: List<PointerSample>,
    val timeMs: Long,
    val stylusButton: Boolean = false,
    val canceled: Boolean = false,
) {
    fun pointer(id: Int): PointerSample? = pointers.firstOrNull { it.id == id }
}

/** Modifier active while a stroke is drawn. */
enum class StrokeModifier { NONE, SIDE_BUTTON, ERASER_TIP }

/** What the gesture layer understood; the editor maps these to tool actions. */
sealed interface GestureEvent {
    data class StrokeStart(val pointerId: Int, val tool: ToolType, val modifier: StrokeModifier, val position: Vec2, val timeMs: Long) :
        GestureEvent

    data class StrokeMove(val pointerId: Int, val position: Vec2, val timeMs: Long) : GestureEvent

    /** [isTap] when the pointer never left the touch slop and lifted quickly. */
    data class StrokeEnd(val pointerId: Int, val position: Vec2, val timeMs: Long, val isTap: Boolean) : GestureEvent

    data class StrokeCancel(val pointerId: Int) : GestureEvent

    /** Pointer paused for the hold time after moving (ink-to-shape trigger). */
    data class StrokeHold(val pointerId: Int, val position: Vec2) : GestureEvent

    /** Pointer pressed and held without moving. */
    data class LongPress(val position: Vec2, val tool: ToolType) : GestureEvent

    data class Tap(val position: Vec2, val tool: ToolType) : GestureEvent

    data class DoubleTap(val position: Vec2, val tool: ToolType) : GestureEvent

    data class MultiFingerTap(val fingers: Int) : GestureEvent

    /** Screen-space navigation step: translate by [pan], scale by [zoom] around [focus], rotate by [rotation] rad. */
    data class Navigate(val pan: Vec2, val zoom: Double, val focus: Vec2, val rotation: Double) : GestureEvent

    data object NavigateEnd : GestureEvent

    data class Hover(val position: Vec2, val eraser: Boolean) : GestureEvent

    data object HoverExit : GestureEvent
}

/** Questions the state machine asks the editor at decision points. */
fun interface InputPolicy {
    /** Should a single-finger drag starting at [position] act like the pen (draw/move) instead of panning? */
    fun fingerActsAsPen(position: Vec2): Boolean
}

data class GestureConfig(
    val touchSlop: Double = 16.0,
    val holdSlop: Double = 6.0,
    val tapTimeoutMs: Long = 300,
    val doubleTapTimeoutMs: Long = 300,
    val doubleTapSlop: Double = 60.0,
    val holdMs: Long = 500,
    val palmGraceMs: Long = 400,
)

/**
 * Pure-Kotlin gesture recognizer. "Pen draws, finger navigates": stylus pointers become strokes,
 * fingers pan/zoom (or draw when [InputPolicy] says so), multi-finger taps are undo/redo.
 * Palm rejection: fingers are ignored while the stylus is down or was hovering recently, and any
 * stroke whose pointer the system cancels is discarded.
 *
 * Time-based events (hold, long press) need [onTimer] calls at [nextDeadline].
 */
class GestureStateMachine(
    private val policy: InputPolicy,
    private val config: GestureConfig = GestureConfig(),
) {
    private sealed interface State {
        data object Idle : State

        /** A stroke in progress (stylus, or finger acting as pen). */
        data class Stroking(
            val pointerId: Int,
            val tool: ToolType,
            val start: Vec2,
            val startTime: Long,
            var last: Vec2,
            var moved: Boolean = false,
            var stillAnchor: Vec2 = start,
            var stillSince: Long = startTime,
            var holdFired: Boolean = false,
            var longPressFired: Boolean = false,
        ) : State

        data class FingerPending(val pointerId: Int, val start: Vec2, val startTime: Long, var longPressFired: Boolean = false) : State

        data class Panning(val pointerId: Int, var last: Vec2) : State

        data class MultiFinger(
            var last: Pinch,
            val startTime: Long,
            var maxFingers: Int,
            var moved: Boolean = false,
            val starts: MutableMap<Int, Vec2>,
        ) : State
    }

    data class Pinch(val centroid: Vec2, val span: Double, val angle: Double)

    private var state: State = State.Idle
    private var lastStylusSeen = Long.MIN_VALUE / 2
    private var stylusHovering = false
    private var lastTap: GestureEvent.Tap? = null
    private var lastTapTime = 0L

    /** Next time [onTimer] should be called, or `null`. */
    fun nextDeadline(): Long? = when (val s = state) {
        is State.Stroking -> when {
            !s.longPressFired && !s.moved -> s.startTime + config.holdMs
            !s.holdFired -> s.stillSince + config.holdMs
            else -> null
        }
        is State.FingerPending -> if (!s.longPressFired) s.startTime + config.holdMs else null
        else -> null
    }

    fun onTimer(nowMs: Long): List<GestureEvent> = when (val s = state) {
        is State.Stroking -> strokeTimer(s, nowMs)
        is State.FingerPending -> if (!s.longPressFired && nowMs - s.startTime >= config.holdMs) {
            s.longPressFired = true
            listOf(GestureEvent.LongPress(s.start, ToolType.FINGER))
        } else {
            emptyList()
        }
        else -> emptyList()
    }

    private fun strokeTimer(s: State.Stroking, nowMs: Long): List<GestureEvent> {
        if (!s.moved && !s.longPressFired && nowMs - s.startTime >= config.holdMs) {
            s.longPressFired = true
            return listOf(GestureEvent.LongPress(s.start, s.tool))
        }
        if (s.moved && !s.holdFired && nowMs - s.stillSince >= config.holdMs) {
            s.holdFired = true
            return listOf(GestureEvent.StrokeHold(s.pointerId, s.last))
        }
        return emptyList()
    }

    fun onEvent(e: InputEvent): List<GestureEvent> {
        val out = mutableListOf<GestureEvent>()
        if (e.pointers.any { it.tool.isStylus() }) lastStylusSeen = e.timeMs
        when (e.action) {
            PointerAction.HOVER_MOVE -> onHover(e, out)
            PointerAction.HOVER_EXIT -> {
                stylusHovering = false
                out += GestureEvent.HoverExit
            }
            PointerAction.CANCEL -> cancelAll(out)
            else -> onTouch(e, out)
        }
        return out
    }

    private fun onHover(e: InputEvent, out: MutableList<GestureEvent>) {
        val p = e.pointers.firstOrNull() ?: return
        if (p.tool.isPen()) {
            stylusHovering = p.tool.isStylus()
            out += GestureEvent.Hover(p.position, eraser = p.tool == ToolType.ERASER || e.stylusButton)
        }
    }

    private fun onTouch(e: InputEvent, out: MutableList<GestureEvent>) {
        val actor = e.pointer(e.actionPointerId) ?: return
        if (e.canceled && (e.action == PointerAction.POINTER_UP || e.action == PointerAction.UP)) {
            cancelPointer(actor.id, out)
            return
        }
        if (actor.tool.isPen()) onPen(e, actor, out) else onFinger(e, actor, out)
    }

    // ---- stylus -------------------------------------------------------------------------------

    private fun onPen(e: InputEvent, p: PointerSample, out: MutableList<GestureEvent>) {
        when (e.action) {
            PointerAction.DOWN, PointerAction.POINTER_DOWN -> {
                cancelAll(out)
                stylusHovering = false
                val modifier = when {
                    p.tool == ToolType.ERASER -> StrokeModifier.ERASER_TIP
                    e.stylusButton -> StrokeModifier.SIDE_BUTTON
                    else -> StrokeModifier.NONE
                }
                state = State.Stroking(p.id, p.tool, p.position, e.timeMs, p.position)
                out += GestureEvent.StrokeStart(p.id, p.tool, modifier, p.position, e.timeMs)
            }
            PointerAction.MOVE -> activeStroke(p.id)?.let { strokeMove(it, p.position, e.timeMs, out) }
            PointerAction.UP, PointerAction.POINTER_UP -> activeStroke(p.id)?.let { endStroke(it, p.position, e.timeMs, out) }
            else -> Unit
        }
    }

    private fun activeStroke(pointerId: Int) = (state as? State.Stroking)?.takeIf { it.pointerId == pointerId }

    private fun strokeMove(s: State.Stroking, p: Vec2, time: Long, out: MutableList<GestureEvent>) {
        if (!s.moved && p.distanceTo(s.start) > config.touchSlop) s.moved = true
        if (p.distanceTo(s.stillAnchor) > config.holdSlop) {
            s.stillAnchor = p
            s.stillSince = time
            s.holdFired = false
        }
        s.last = p
        out += GestureEvent.StrokeMove(s.pointerId, p, time)
    }

    private fun endStroke(s: State.Stroking, p: Vec2, time: Long, out: MutableList<GestureEvent>) {
        val isTap = !s.moved && !s.longPressFired && time - s.startTime <= config.tapTimeoutMs
        out += GestureEvent.StrokeEnd(s.pointerId, p, time, isTap)
        if (isTap) emitTap(GestureEvent.Tap(p, s.tool), time, out)
        state = State.Idle
    }

    // ---- fingers ------------------------------------------------------------------------------

    private fun palmGuard(time: Long): Boolean = stylusHovering || time - lastStylusSeen < config.palmGraceMs

    private fun onFinger(e: InputEvent, p: PointerSample, out: MutableList<GestureEvent>) {
        val s = state
        if (s is State.Stroking && s.tool.isPen()) return // palm while writing
        when (e.action) {
            PointerAction.DOWN -> if (!palmGuard(e.timeMs)) state = State.FingerPending(p.id, p.position, e.timeMs)
            PointerAction.POINTER_DOWN -> onSecondFinger(e, out)
            PointerAction.MOVE -> fingerMove(e, out)
            PointerAction.POINTER_UP -> fingerLifted(e, p, out)
            PointerAction.UP -> fingerUp(e, p, out)
            else -> Unit
        }
    }

    private fun fingers(e: InputEvent) = e.pointers.filter { !it.tool.isPen() }

    private fun onSecondFinger(e: InputEvent, out: MutableList<GestureEvent>) {
        val fs = fingers(e)
        if (fs.size < 2) return
        when (val s = state) {
            is State.Stroking -> {
                out += GestureEvent.StrokeCancel(s.pointerId)
                state = startMulti(fs, e.timeMs)
            }
            is State.FingerPending, is State.Panning -> state = startMulti(fs, e.timeMs)
            is State.MultiFinger -> {
                s.maxFingers = maxOf(s.maxFingers, fs.size)
                fs.forEach { s.starts.putIfAbsent(it.id, it.position) }
                s.last = pinchOf(fs)
            }
            State.Idle -> if (!palmGuard(e.timeMs)) state = startMulti(fs, e.timeMs)
        }
    }

    private fun startMulti(fs: List<PointerSample>, time: Long) =
        State.MultiFinger(pinchOf(fs), time, fs.size, starts = fs.associate { it.id to it.position }.toMutableMap())

    private fun pinchOf(fs: List<PointerSample>): Pinch {
        val a = fs[0].position
        val b = fs[1].position
        val d = b - a
        return Pinch(a.lerp(b, 0.5), d.length.coerceAtLeast(1.0), atan2(d.y, d.x))
    }

    private fun fingerMove(e: InputEvent, out: MutableList<GestureEvent>) {
        when (val s = state) {
            is State.FingerPending -> {
                val p = e.pointer(s.pointerId) ?: return
                if (p.position.distanceTo(s.start) <= config.touchSlop || s.longPressFired) return
                if (policy.fingerActsAsPen(s.start)) {
                    val stroke = State.Stroking(s.pointerId, ToolType.FINGER, s.start, s.startTime, s.start, moved = true)
                    state = stroke
                    out += GestureEvent.StrokeStart(s.pointerId, ToolType.FINGER, StrokeModifier.NONE, s.start, s.startTime)
                    strokeMove(stroke, p.position, e.timeMs, out)
                } else {
                    state = State.Panning(s.pointerId, p.position)
                    out += GestureEvent.Navigate(p.position - s.start, 1.0, p.position, 0.0)
                }
            }
            is State.Stroking -> e.pointer(s.pointerId)?.let { strokeMove(s, it.position, e.timeMs, out) }
            is State.Panning -> e.pointer(s.pointerId)?.let {
                out += GestureEvent.Navigate(it.position - s.last, 1.0, it.position, 0.0)
                s.last = it.position
            }
            is State.MultiFinger -> multiMove(s, e, out)
            State.Idle -> Unit
        }
    }

    private fun multiMove(s: State.MultiFinger, e: InputEvent, out: MutableList<GestureEvent>) {
        val fs = fingers(e)
        if (fs.size < 2) return
        if (!s.moved && fs.any { f -> s.starts[f.id]?.let { f.position.distanceTo(it) > config.touchSlop } == true }) s.moved = true
        if (!s.moved) return
        val now = pinchOf(fs)
        out += GestureEvent.Navigate(now.centroid - s.last.centroid, now.span / s.last.span, now.centroid, now.angle - s.last.angle)
        s.last = now
    }

    private fun fingerLifted(e: InputEvent, p: PointerSample, out: MutableList<GestureEvent>) {
        val s = state as? State.MultiFinger ?: return
        val remaining = fingers(e).filter { it.id != p.id }
        if (remaining.size >= 2) s.last = pinchOf(remaining)
        if (remaining.size == 1 && s.moved) {
            out += GestureEvent.NavigateEnd
            state = State.Panning(remaining[0].id, remaining[0].position)
        }
    }

    private fun fingerUp(e: InputEvent, p: PointerSample, out: MutableList<GestureEvent>) {
        when (val s = state) {
            is State.FingerPending -> if (!s.longPressFired && e.timeMs - s.startTime <= config.tapTimeoutMs) {
                emitTap(GestureEvent.Tap(p.position, ToolType.FINGER), e.timeMs, out)
            }
            is State.Stroking -> endStroke(s, p.position, e.timeMs, out)
            is State.Panning -> out += GestureEvent.NavigateEnd
            is State.MultiFinger -> if (!s.moved && e.timeMs - s.startTime <= config.tapTimeoutMs) {
                out += GestureEvent.MultiFingerTap(s.maxFingers)
            } else if (s.moved) {
                out += GestureEvent.NavigateEnd
            }
            State.Idle -> Unit
        }
        state = State.Idle
    }

    private fun emitTap(tap: GestureEvent.Tap, time: Long, out: MutableList<GestureEvent>) {
        val prev = lastTap
        val isDouble = prev != null && prev.tool == tap.tool && time - lastTapTime <= config.doubleTapTimeoutMs &&
            prev.position.distanceTo(tap.position) <= config.doubleTapSlop
        if (isDouble) {
            out += GestureEvent.DoubleTap(tap.position, tap.tool)
            lastTap = null
        } else {
            out += tap
            lastTap = tap
            lastTapTime = time
        }
    }

    // ---- cancellation -------------------------------------------------------------------------

    private fun cancelPointer(id: Int, out: MutableList<GestureEvent>) {
        val s = state
        if (s is State.Stroking && s.pointerId == id) {
            out += GestureEvent.StrokeCancel(id)
            state = State.Idle
        } else if (s is State.FingerPending && s.pointerId == id) {
            state = State.Idle
        }
    }

    private fun cancelAll(out: MutableList<GestureEvent>) {
        when (val s = state) {
            is State.Stroking -> out += GestureEvent.StrokeCancel(s.pointerId)
            is State.Panning -> out += GestureEvent.NavigateEnd
            is State.MultiFinger -> if (s.moved) out += GestureEvent.NavigateEnd
            else -> Unit
        }
        state = State.Idle
    }

    /** Pointers that draw: stylus, eraser tip, and a mouse (DeX). */
    private fun ToolType.isPen() = this != ToolType.FINGER

    private fun ToolType.isStylus() = this == ToolType.STYLUS || this == ToolType.ERASER
}
