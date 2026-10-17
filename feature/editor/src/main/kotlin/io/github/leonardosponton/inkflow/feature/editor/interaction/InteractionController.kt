package io.github.leonardosponton.inkflow.feature.editor.interaction

import io.github.leonardosponton.inkflow.core.model.ElementId
import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.core.model.SideButtonAction
import io.github.leonardosponton.inkflow.core.model.Vec2
import io.github.leonardosponton.inkflow.domain.canvas.command.AddElements
import io.github.leonardosponton.inkflow.domain.canvas.element.CanvasElement
import io.github.leonardosponton.inkflow.domain.canvas.element.Connector
import io.github.leonardosponton.inkflow.domain.canvas.element.InkBrush
import io.github.leonardosponton.inkflow.domain.canvas.element.InkStroke
import io.github.leonardosponton.inkflow.domain.canvas.element.ShapeNode
import io.github.leonardosponton.inkflow.domain.canvas.element.TextElement
import io.github.leonardosponton.inkflow.domain.canvas.hit.HitTester
import io.github.leonardosponton.inkflow.domain.canvas.ink.GeometricShapeRecognizer
import io.github.leonardosponton.inkflow.domain.canvas.ink.ShapeRecognizer
import io.github.leonardosponton.inkflow.domain.canvas.input.GestureEvent
import io.github.leonardosponton.inkflow.domain.canvas.input.StrokeModifier
import io.github.leonardosponton.inkflow.domain.canvas.input.ToolType
import io.github.leonardosponton.inkflow.feature.canvasrender.Overlay

/**
 * Turns gestures into editing actions according to the active tool. Pure Kotlin: everything
 * platform-specific is behind [EditorEnvironment].
 */
class InteractionController(
    private val env: EditorEnvironment,
    private val recognizer: ShapeRecognizer = GeometricShapeRecognizer(),
) {
    private val quick = QuickConnect(env)
    private val drags = DragOperations(env) { quick.arrowAt(it) != null }
    private var hover: Vec2? = null
    private var hoverEraser = false

    /** Current overlay for the renderer. */
    fun overlay(): Overlay = drags.decorate(selectionOverlay()).copy(
        quickArrows = quick.arrows().map { (dir, p) -> p to dir.unit },
        ports = hoverPorts(),
        brushCursor = brushCursor(),
        eraser = eraserCursor(),
    )

    // ---- canvas host bridge ---------------------------------------------------------------------

    fun fingerActsAsPen(screen: Vec2): Boolean {
        if (env.settings.fingerDrawing) return true
        val kind = env.tools.kind
        if (kind != ToolKind.SELECT && kind != ToolKind.LASSO) return false
        val world = toWorld(screen)
        return selectionBounds()?.inflate(tolerance())?.contains(world) == true || hitTester().elementAt(world, tolerance()) != null
    }

    /** Wet-ink brush for strokes that should become ink, `null` otherwise. */
    fun inkBrushFor(start: GestureEvent.StrokeStart): InkBrush? {
        if (effectiveTool(start) !in INK_TOOLS) return null
        if (quick.arrowAt(toWorld(start.position)) != null) return null // a tap on an arrow, not a dot of ink
        if (env.activeLayer in env.lockedLayers) return null
        return env.tools.activeBrush
    }

    fun onInkFinished(stroke: InkStroke) {
        env.execute(AddElements(listOf(stroke.withLayer(env.activeLayer))))
        env.seal()
    }

    /** Ink-to-shape: replace the wet stroke by a recognized shape. */
    fun onInkHold(points: List<Vec2>): Boolean {
        if (!env.settings.inkToShape) return false
        val shape = recognizer.recognize(points) ?: return false
        val brush = env.tools.activeBrush ?: return false
        val element = RecognizedShapes.toElement(shape, brush, env.newId(), env.activeLayer)
        env.execute(AddElements(listOf(element)))
        env.seal()
        env.selection = setOf(element.id)
        env.invalidate()
        return true
    }

    fun onGesture(e: GestureEvent) {
        when (e) {
            is GestureEvent.StrokeStart -> drags.start(effectiveTool(e), toWorld(e.position), e.tool)
            is GestureEvent.StrokeMove -> drags.move(toWorld(e.position))
            is GestureEvent.StrokeEnd -> drags.end(toWorld(e.position), e.isTap)
            is GestureEvent.StrokeCancel -> drags.cancel()
            is GestureEvent.StrokeHold -> Unit
            is GestureEvent.Tap -> if (e.sideButton) onSideButtonTap(e.position) else onTap(e.position, e.tool)
            is GestureEvent.DoubleTap -> onDoubleTap(e.position, e.tool)
            is GestureEvent.LongPress -> onLongPress(e.position)
            is GestureEvent.MultiFingerTap -> if (e.fingers == 2) {
                env.undo()
            } else if (e.fingers >= THREE) {
                env.redo()
            }
            is GestureEvent.Navigate -> navigate(e)
            GestureEvent.NavigateEnd -> {
                zooming = false
                env.setCamera(env.camera.snappedRotation())
            }
            is GestureEvent.Hover -> {
                hover = toWorld(e.position)
                hoverEraser = e.eraser
            }
            GestureEvent.HoverExit -> hover = null
        }
        env.invalidate()
    }

    // ---- taps -----------------------------------------------------------------------------------

    private fun onTap(screen: Vec2, tool: ToolType) {
        val world = toWorld(screen)
        quick.arrowAt(world)?.let {
            quick.connect(it)
            return
        }
        when (env.tools.kind) {
            ToolKind.SELECT, ToolKind.LASSO -> selectAt(world)
            ToolKind.SHAPE -> if (tool != ToolType.FINGER) drags.placeDefaultShape(world)
            ToolKind.TEXT, ToolKind.STICKY -> if (tool != ToolType.FINGER) drags.placeText(world, null)
            else -> if (tool == ToolType.FINGER) selectAt(world)
        }
    }

    /** Pen side button + tap on an element: offer to delete it with its connectors. */
    private fun onSideButtonTap(screen: Vec2) {
        val hit = hitTester().elementAt(toWorld(screen), tolerance()) { it !is InkStroke && it.layerId !in env.lockedLayers && !it.locked }
        hit?.let { env.confirmDelete(expandGroup(it)) }
    }

    private fun selectAt(world: Vec2) {
        // Text editing opens on a double tap only: a light tap on a selected element keeps it selected.
        val hit = hitTester().elementAt(world, tolerance()) { it.layerId !in env.lockedLayers }
        env.selection = hit?.let { expandGroup(it) }.orEmpty()
    }

    private fun onDoubleTap(screen: Vec2, tool: ToolType) {
        val hit = hitTester().elementAt(toWorld(screen), tolerance())
        when {
            hit != null && isTextual(hit) -> {
                env.selection = setOf(hit.id)
                env.editText(hit.id)
            }
            tool == ToolType.FINGER -> env.fitToContent()
        }
    }

    private fun onLongPress(screen: Vec2) {
        drags.cancel()
        val hit = hitTester().elementAt(toWorld(screen), tolerance())
        if (hit != null && hit.id !in env.selection) env.selection = expandGroup(hit)
        env.openContextMenu(screen, hit?.id)
    }

    private fun isTextual(el: CanvasElement) = el is TextElement || (el is ShapeNode) || (el is Connector)

    private fun expandGroup(el: CanvasElement): Set<ElementId> {
        val g = el.groupId ?: return setOf(el.id)
        return env.session.scene.groupMembers(g).map { it.id }.toSet()
    }

    // ---- navigation -----------------------------------------------------------------------------

    /** True while a pinch is in progress (the renderer scales its cache instead of redrawing). */
    var zooming = false
        private set

    private fun navigate(e: GestureEvent.Navigate) {
        if (e.zoom != 1.0) zooming = true
        var cam = env.camera.panByScreen(e.pan.x, e.pan.y)
        if (e.zoom != 1.0) cam = cam.zoomAround(e.focus, e.zoom, env.viewport)
        if (env.settings.fingerRotation && e.rotation != 0.0) cam = cam.rotateAround(e.focus, e.rotation, env.viewport)
        env.setCamera(cam)
    }

    // ---- overlay helpers ------------------------------------------------------------------------

    private fun selectionOverlay(): Overlay {
        val scene = env.session.scene
        val selected = env.selection.mapNotNull { scene[it] }
        if (selected.isEmpty()) return Overlay()
        val bounds = Rect.unionAll(selected.map { it.bounds })
        val resizable = selected.none { it is Connector }
        return Overlay(
            selection = bounds,
            selectedBounds = if (selected.size > 1) selected.map { it.bounds } else emptyList(),
            handles = resizable && selected.none { it.locked },
            waypoints = (selected.singleOrNull() as? Connector)?.waypoints.orEmpty(),
        )
    }

    private fun hoverPorts(): List<Vec2> {
        val p = hover ?: drags.currentPoint ?: return emptyList()
        if (env.tools.kind != ToolKind.SELECT && env.tools.kind != ToolKind.CONNECTOR) return emptyList()
        val node = hitTester().nodeAt(p, tolerance() * PORT_HOVER) ?: return emptyList()
        return env.session.catalog.shapes.resolve(node.shape).ports.map { it.position(node.frame) }
    }

    private fun brushCursor(): Pair<Vec2, Double>? {
        val p = hover ?: return null
        val brush = env.tools.activeBrush ?: return null
        return if (hoverEraser) null else p to brush.size / 2.0
    }

    private fun eraserCursor(): Pair<Vec2, Double>? {
        drags.eraserCursor()?.let { return it }
        val p = hover ?: return null
        val erasing = env.tools.kind == ToolKind.ERASER || hoverEraser
        return if (erasing) p to env.tools.eraserSize / env.camera.zoom else null
    }

    fun selectionBounds(): Rect? = Rect.unionAll(env.selection.mapNotNull { env.session.scene[it]?.bounds })

    private fun effectiveTool(start: GestureEvent.StrokeStart): ToolKind = when (start.modifier) {
        StrokeModifier.ERASER_TIP -> ToolKind.ERASER
        StrokeModifier.SIDE_BUTTON -> if (env.settings.sideButtonAction == SideButtonAction.ERASER) ToolKind.ERASER else ToolKind.LASSO
        StrokeModifier.NONE -> if (start.tool == ToolType.FINGER && !env.settings.fingerDrawing) ToolKind.SELECT else env.tools.kind
    }

    private fun hitTester(): HitTester = env.session.hitTester(env.hiddenLayers)

    private fun toWorld(screen: Vec2) = env.camera.screenToWorld(screen, env.viewport)

    private fun tolerance() = TOUCH_TOLERANCE_PX / env.camera.zoom

    companion object {
        const val TOUCH_TOLERANCE_PX = 10.0
        private const val PORT_HOVER = 3.0
        private const val THREE = 3
        private val INK_TOOLS = setOf(ToolKind.PEN, ToolKind.HIGHLIGHTER)
    }
}
