package io.github.leonardosponton.inkflow.feature.canvasrender

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.strokes.Stroke
import androidx.input.motionprediction.MotionEventPredictor
import io.github.leonardosponton.inkflow.core.model.ElementId
import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.core.model.Size
import io.github.leonardosponton.inkflow.core.model.Vec2
import io.github.leonardosponton.inkflow.domain.canvas.element.InkBrush
import io.github.leonardosponton.inkflow.domain.canvas.element.InkStroke
import io.github.leonardosponton.inkflow.domain.canvas.input.GestureConfig
import io.github.leonardosponton.inkflow.domain.canvas.input.GestureEvent
import io.github.leonardosponton.inkflow.domain.canvas.input.GestureStateMachine
import io.github.leonardosponton.inkflow.domain.canvas.input.InputEvent
import io.github.leonardosponton.inkflow.domain.canvas.input.PointerAction
import io.github.leonardosponton.inkflow.domain.canvas.input.PointerSample
import io.github.leonardosponton.inkflow.domain.canvas.input.ToolType

/** Callbacks from the canvas to the editor. All run on the main thread. */
interface CanvasHost {
    fun fingerActsAsPen(screen: Vec2): Boolean

    /** Brush for a new stroke if it should be wet ink, or `null` when the editor handles it. */
    fun inkBrushFor(start: GestureEvent.StrokeStart): InkBrush?

    fun onGesture(event: GestureEvent)

    /** The pen paused during an ink stroke; return `true` if the stroke became a shape (wet ink is dropped). */
    fun onInkHold(worldPoints: List<Vec2>): Boolean

    fun onInkFinished(stroke: InkStroke)

    fun newElementId(): ElementId

    fun onViewportChanged(size: Size)
}

/**
 * The editor surface: draws grid, scene and overlay, hosts Jetpack Ink's front-buffered wet layer
 * and converts `MotionEvent`s into [GestureEvent]s through the pure [GestureStateMachine].
 */
@SuppressLint("ViewConstructor")
class InkCanvasView(context: Context, private val drawer: SceneDrawer) : FrameLayout(context) {
    var host: CanvasHost? = null

    private val wet = InProgressStrokesView(context)
    private val grid = GridDrawer()
    private val overlay = OverlayDrawer()
    private val predictor = MotionEventPredictor.newInstance(this)
    private val gestures = GestureStateMachine(
        policy = { host?.fingerActsAsPen(it) == true },
        config = GestureConfig(touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toDouble()),
    )

    private data class WetStroke(val id: InProgressStrokeId, val brush: InkBrush, val points: MutableList<Vec2> = mutableListOf())

    private val wetByPointer = HashMap<Int, WetStroke>()
    private val wetBrushes = HashMap<InProgressStrokeId, InkBrush>()

    /** Finished wet strokes waiting until the scene draws their committed element. */
    private val pendingHandoff = HashMap<ElementId, InProgressStrokeId>()

    private val worldToScreen = Matrix()
    private val screenToWorld = Matrix()
    private val timer = Runnable { onTimer() }

    var frame: RenderFrame? = null
        set(value) {
            field = value
            value?.let { updateMatrices(it) }
            invalidate()
        }

    init {
        setWillNotDraw(false)
        addView(wet, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        wet.addFinishedStrokesListener(
            object : InProgressStrokesFinishedListener {
                override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) = handleFinished(strokes)
            },
        )
        isFocusable = true
        contentDescription = context.getString(R.string.canvas_description)
    }

    private fun viewport() = Size(width.toDouble(), height.toDouble())

    private fun updateMatrices(f: RenderFrame) {
        val c = f.camera
        worldToScreen.reset()
        worldToScreen.postTranslate(-c.center.x.toFloat(), -c.center.y.toFloat())
        worldToScreen.postScale(c.zoom.toFloat(), c.zoom.toFloat())
        worldToScreen.postRotate(Math.toDegrees(c.rotation).toFloat())
        worldToScreen.postTranslate(width / 2f, height / 2f)
        worldToScreen.invert(screenToWorld)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        frame?.let { updateMatrices(it) }
        host?.onViewportChanged(Size(w.toDouble(), h.toDouble()))
    }

    // ---- drawing --------------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        val f = frame ?: return
        val palette = f.palette
        canvas.clipRect(0, 0, width, height)
        canvas.drawColor(palette.background)
        val visible = f.camera.visibleWorldRect(viewport())
        if (canvas.isHardwareAccelerated) {
            sceneCache.draw(
                canvas,
                f,
                viewport(),
                worldToScreen,
            )
        } else {
            drawScene(canvas, f, visible, worldToScreen)
        }
        canvas.save()
        canvas.concat(worldToScreen)
        val pass = SceneDrawer.Pass(palette, f.camera.zoom, worldToScreen, f.hidden)
        drawer.drawAll(canvas, f.preview, pass)
        overlay.draw(canvas, f.overlay, f.camera.zoom, palette)
        canvas.restore()
        completeHandoff(f)
    }

    /** Grid and committed elements of [area] drawn with [matrix] (world → canvas). */
    internal fun drawScene(canvas: Canvas, f: RenderFrame, area: Rect, matrix: Matrix) {
        canvas.save()
        canvas.concat(matrix)
        grid.draw(canvas, f.grid, area, f.camera.zoom, f.palette)
        val pass = SceneDrawer.Pass(f.palette, f.camera.zoom, matrix, f.hidden)
        val elements = f.index.visible(f.scene, area.inflate(CULL_MARGIN / f.camera.zoom)).filter { it.layerId !in f.hiddenLayers }
        drawer.drawAll(canvas, elements, pass)
        canvas.restore()
    }

    private val sceneCache = SceneCache(this)

    /** Removes wet strokes whose committed element is now part of the drawn scene (no flicker). */
    private fun completeHandoff(f: RenderFrame) {
        if (pendingHandoff.isEmpty()) return
        val done = pendingHandoff.filterKeys { it in f.scene }.toMap()
        if (done.isEmpty()) return
        done.keys.forEach(pendingHandoff::remove)
        postOnAnimation { wet.removeFinishedStrokes(done.values.toSet()) }
    }

    // ---- input ----------------------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            if (event.getToolType(0) != MotionEvent.TOOL_TYPE_FINGER) requestUnbufferedDispatch(event)
            requestFocus()
        }
        predictor.record(event)
        if (event.actionMasked == MotionEvent.ACTION_MOVE) {
            for (h in 0 until event.historySize) dispatch(event, toInput(event, PointerAction.MOVE, h), historical = true)
        }
        dispatch(event, toInput(event, actionOf(event), HISTORY_NONE), historical = false)
        scheduleTimer()
        return true
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        val action = when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_EXIT -> PointerAction.HOVER_EXIT
            else -> PointerAction.HOVER_MOVE
        }
        gestures.onEvent(toInput(event, action, HISTORY_NONE)).forEach { host?.onGesture(it) }
        return true
    }

    private fun dispatch(event: MotionEvent, input: InputEvent, historical: Boolean) {
        val out = gestures.onEvent(input)
        out.forEach { g -> handleGesture(g, event, historical) }
    }

    private fun handleGesture(g: GestureEvent, event: MotionEvent, historical: Boolean) {
        when (g) {
            is GestureEvent.StrokeStart -> startStroke(g, event)
            is GestureEvent.StrokeMove -> wetByPointer[g.pointerId]?.let { w ->
                w.points += toWorld(g.position)
                if (!historical) addToWet(event, g.pointerId, w)
            } ?: host?.onGesture(g)
            is GestureEvent.StrokeEnd -> wetByPointer.remove(g.pointerId)?.let { w -> finishWet(event, g.pointerId, w) }
                ?: host?.onGesture(g)
            is GestureEvent.StrokeCancel -> wetByPointer.remove(g.pointerId)?.let { w ->
                wet.cancelStroke(w.id, event)
                wetBrushes.remove(w.id)
            } ?: host?.onGesture(g)
            is GestureEvent.StrokeHold -> wetByPointer[g.pointerId]?.let { w -> holdWet(event, g.pointerId, w) } ?: host?.onGesture(g)
            else -> host?.onGesture(g)
        }
    }

    private fun startStroke(g: GestureEvent.StrokeStart, event: MotionEvent) {
        val brush = host?.inkBrushFor(g)
        if (brush == null) {
            host?.onGesture(g)
            return
        }
        val index = event.findPointerIndex(g.pointerId)
        if (index < 0) return
        val display = frame?.palette?.adapt(brush.color) ?: brush.color.argb
        val id = wet.startStroke(event, g.pointerId, InkBrushes.toInk(brush, display), screenToWorld, IDENTITY)
        wetBrushes[id] = brush
        wetByPointer[g.pointerId] = WetStroke(id, brush, mutableListOf(toWorld(g.position)))
    }

    private fun addToWet(event: MotionEvent, pointerId: Int, w: WetStroke) {
        if (event.findPointerIndex(pointerId) < 0) return
        runCatching { wet.addToStroke(event, pointerId, w.id, predictor.predict()) }
    }

    private fun finishWet(event: MotionEvent, pointerId: Int, w: WetStroke) {
        if (event.findPointerIndex(pointerId) < 0) {
            wet.cancelStroke(w.id, event)
            return
        }
        wet.finishStroke(event, pointerId, w.id)
    }

    private fun holdWet(event: MotionEvent, pointerId: Int, w: WetStroke) {
        if (host?.onInkHold(w.points.toList()) == true) {
            wet.cancelStroke(w.id, event)
            wetBrushes.remove(w.id)
            wetByPointer.remove(pointerId)
        }
    }

    private fun handleFinished(strokes: Map<InProgressStrokeId, Stroke>) {
        strokes.forEach { (id, stroke) ->
            val brush = wetBrushes.remove(id) ?: return@forEach
            val elementId = host?.newElementId() ?: return@forEach
            val domain = InkBrushes.fromInk(elementId, stroke, brush)
            if (domain == null) {
                wet.removeFinishedStrokes(setOf(id))
                return@forEach
            }
            pendingHandoff[elementId] = id
            host?.onInkFinished(domain)
        }
    }

    private fun onTimer() {
        gestures.onTimer(SystemClock.uptimeMillis()).forEach { g ->
            val wetStroke = (g as? GestureEvent.StrokeHold)?.let { wetByPointer[it.pointerId] }
            if (wetStroke == null) host?.onGesture(g) else holdFromTimer(g as GestureEvent.StrokeHold, wetStroke)
        }
        scheduleTimer()
    }

    private fun holdFromTimer(g: GestureEvent.StrokeHold, w: WetStroke) {
        if (host?.onInkHold(w.points.toList()) != true) return
        wet.cancelStroke(w.id)
        wetBrushes.remove(w.id)
        wetByPointer.remove(g.pointerId)
    }

    private fun scheduleTimer() {
        removeCallbacks(timer)
        val deadline = gestures.nextDeadline() ?: return
        postDelayed(timer, (deadline - SystemClock.uptimeMillis()).coerceAtLeast(0))
    }

    private fun toWorld(screen: Vec2): Vec2 = frame?.camera?.screenToWorld(screen, viewport()) ?: screen

    private fun actionOf(e: MotionEvent): PointerAction = when (e.actionMasked) {
        MotionEvent.ACTION_DOWN -> PointerAction.DOWN
        MotionEvent.ACTION_POINTER_DOWN -> PointerAction.POINTER_DOWN
        MotionEvent.ACTION_MOVE -> PointerAction.MOVE
        MotionEvent.ACTION_POINTER_UP -> PointerAction.POINTER_UP
        MotionEvent.ACTION_UP -> PointerAction.UP
        else -> PointerAction.CANCEL
    }

    private fun toInput(e: MotionEvent, action: PointerAction, history: Int): InputEvent {
        val pointers = (0 until e.pointerCount).map { i ->
            val x = if (history == HISTORY_NONE) e.getX(i) else e.getHistoricalX(i, history)
            val y = if (history == HISTORY_NONE) e.getY(i) else e.getHistoricalY(i, history)
            val p = if (history == HISTORY_NONE) e.getPressure(i) else e.getHistoricalPressure(i, history)
            PointerSample(e.getPointerId(i), toolOf(e.getToolType(i)), Vec2(x.toDouble(), y.toDouble()), p)
        }
        val time = if (history == HISTORY_NONE) e.eventTime else e.getHistoricalEventTime(history)
        val canceled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && (e.flags and MotionEvent.FLAG_CANCELED) != 0
        return InputEvent(
            action = action,
            actionPointerId = e.getPointerId(e.actionIndex),
            pointers = pointers,
            timeMs = time,
            stylusButton =
                (e.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0 || (e.buttonState and MotionEvent.BUTTON_SECONDARY) != 0,
            canceled = canceled,
        )
    }

    private fun toolOf(t: Int) = when (t) {
        MotionEvent.TOOL_TYPE_STYLUS -> ToolType.STYLUS
        MotionEvent.TOOL_TYPE_ERASER -> ToolType.ERASER
        MotionEvent.TOOL_TYPE_MOUSE -> ToolType.MOUSE
        else -> ToolType.FINGER
    }

    private companion object {
        const val HISTORY_NONE = -1
        const val CULL_MARGIN = 32.0
        val IDENTITY = Matrix()
    }
}
