package io.github.leonardosponton.inkflow.feature.editor

import io.github.leonardosponton.inkflow.core.model.Camera
import io.github.leonardosponton.inkflow.core.model.ElementId
import io.github.leonardosponton.inkflow.core.model.IdGenerator
import io.github.leonardosponton.inkflow.core.model.LayerId
import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.core.model.SideButtonAction
import io.github.leonardosponton.inkflow.core.model.Size
import io.github.leonardosponton.inkflow.core.model.UserSettings
import io.github.leonardosponton.inkflow.core.model.Vec2
import io.github.leonardosponton.inkflow.domain.canvas.command.AddElements
import io.github.leonardosponton.inkflow.domain.canvas.command.Command
import io.github.leonardosponton.inkflow.domain.canvas.element.Connector
import io.github.leonardosponton.inkflow.domain.canvas.element.Endpoint
import io.github.leonardosponton.inkflow.domain.canvas.element.InkBrush
import io.github.leonardosponton.inkflow.domain.canvas.element.InkStroke
import io.github.leonardosponton.inkflow.domain.canvas.element.ShapeNode
import io.github.leonardosponton.inkflow.domain.canvas.element.StrokePoints
import io.github.leonardosponton.inkflow.domain.canvas.input.GestureEvent
import io.github.leonardosponton.inkflow.domain.canvas.input.StrokeModifier
import io.github.leonardosponton.inkflow.domain.canvas.input.ToolType
import io.github.leonardosponton.inkflow.domain.canvas.notation.FollowUp
import io.github.leonardosponton.inkflow.domain.canvas.notation.NotationCatalog
import io.github.leonardosponton.inkflow.domain.canvas.scene.SceneChange
import io.github.leonardosponton.inkflow.domain.canvas.session.EditorSession
import io.github.leonardosponton.inkflow.domain.diagram.BasicShapes
import io.github.leonardosponton.inkflow.domain.diagram.PlainLabel
import io.github.leonardosponton.inkflow.domain.diagram.ShapeTypeKey
import io.github.leonardosponton.inkflow.domain.flowchart.FlowchartNotation
import io.github.leonardosponton.inkflow.domain.flowchart.FlowchartSymbols
import io.github.leonardosponton.inkflow.feature.editor.interaction.EditorEnvironment
import io.github.leonardosponton.inkflow.feature.editor.interaction.EraserMode
import io.github.leonardosponton.inkflow.feature.editor.interaction.InteractionController
import io.github.leonardosponton.inkflow.feature.editor.interaction.ToolKind
import io.github.leonardosponton.inkflow.feature.editor.interaction.ToolState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeEnv : EditorEnvironment {
    private var n = 0
    override val session = EditorSession(NotationCatalog(listOf(FlowchartNotation)), IdGenerator { "g${++n}" })
    private var cam = Camera(Vec2(500.0, 400.0), 1.0)
    override val camera: Camera get() = cam
    override val viewport = Size(1000.0, 800.0)
    override var tools = ToolState()
    override var settings = UserSettings(snapToGrid = false, smartGuides = false)
    override val activeLayer = LayerId.Default
    override val hiddenLayers = emptySet<LayerId>()
    override val lockedLayers = emptySet<LayerId>()
    override var selection: Set<ElementId> = emptySet()
    var undos = 0
    var redos = 0
    var fitted = 0
    var edited: ElementId? = null
    var menu: Vec2? = null
    var followUp: FollowUp? = null

    override fun newId() = ElementId("n${++n}")

    override fun execute(command: Command, coalesceKey: String?): SceneChange = session.execute(command, coalesceKey)

    override fun seal() = session.undo.seal()

    override fun setCamera(camera: Camera) {
        cam = camera
    }

    override fun undo() {
        undos++
    }

    override fun redo() {
        redos++
    }

    override fun fitToContent() {
        fitted++
    }

    override fun editText(id: ElementId) {
        edited = id
    }

    override fun shapePlaced(key: ShapeTypeKey) {
        tools = tools.withRecent(key).copy(kind = ToolKind.SELECT)
    }

    override fun openContextMenu(screen: Vec2, target: ElementId?) {
        menu = screen
    }

    override fun offerFollowUp(followUp: FollowUp) {
        this.followUp = followUp
    }

    override fun invalidate() = Unit
}

class InteractionControllerTest {
    private val env = FakeEnv()
    private val c = InteractionController(env)

    /** World == screen with this camera (center 500,400, zoom 1, viewport 1000×800). */
    private fun p(x: Double, y: Double) = Vec2(x, y)

    private fun start(x: Double, y: Double, tool: ToolType = ToolType.STYLUS, mod: StrokeModifier = StrokeModifier.NONE) =
        GestureEvent.StrokeStart(0, tool, mod, p(x, y), 0)

    private fun drag(from: Vec2, to: Vec2, tool: ToolType = ToolType.STYLUS) {
        c.onGesture(start(from.x, from.y, tool))
        c.onGesture(GestureEvent.StrokeMove(0, from.lerp(to, 0.5), 10))
        c.onGesture(GestureEvent.StrokeMove(0, to, 20))
        c.onGesture(GestureEvent.StrokeEnd(0, to, 30, isTap = false))
    }

    private fun node(id: String, x: Double, y: Double) =
        ShapeNode(ElementId(id), BasicShapes.Rectangle.key, Rect(x, y, x + 100, y + 60), PlainLabel(id))

    @Test
    fun penDrawsInkAndModifiersSwitchTools() {
        assertNotNull(c.inkBrushFor(start(10.0, 10.0)))
        assertNull(c.inkBrushFor(start(10.0, 10.0, mod = StrokeModifier.SIDE_BUTTON)))
        assertNull(c.inkBrushFor(start(10.0, 10.0, mod = StrokeModifier.ERASER_TIP)))
        assertNull(c.inkBrushFor(start(10.0, 10.0, tool = ToolType.FINGER)))
        env.tools = env.tools.copy(kind = ToolKind.HIGHLIGHTER)
        assertEquals(env.tools.highlighter, c.inkBrushFor(start(10.0, 10.0)))
        env.settings = env.settings.copy(sideButtonAction = SideButtonAction.LASSO)
        assertNull(c.inkBrushFor(start(10.0, 10.0, mod = StrokeModifier.SIDE_BUTTON)))
        val pts = StrokePoints.Builder().apply { repeat(5) { add(it * 2f, 0f, it * 5f, 1f) } }.build()
        c.onInkFinished(InkStroke(ElementId("s"), Vec2.Zero, InkBrush(), pts))
        assertEquals(1, env.session.scene.size)
    }

    @Test
    fun inkToShapeReplacesTheStroke() {
        val square = listOf(p(0.0, 0.0), p(100.0, 0.0), p(100.0, 100.0), p(0.0, 100.0), p(0.0, 0.0))
        val pts = square.zipWithNext().flatMap { (a, b) -> (0 until 10).map { a.lerp(b, it / 10.0) } } + square.last()
        assertTrue(c.onInkHold(pts))
        val shape = env.session.scene.ordered.single() as ShapeNode
        assertEquals(BasicShapes.Rectangle.key, shape.shape)
        env.settings = env.settings.copy(inkToShape = false)
        assertTrue(!c.onInkHold(pts))
    }

    @Test
    fun selectTapMoveAndMarquee() {
        env.tools = env.tools.copy(kind = ToolKind.SELECT)
        env.execute(AddElements(listOf(node("a", 100.0, 100.0), node("b", 400.0, 100.0))))
        c.onGesture(GestureEvent.Tap(p(120.0, 120.0), ToolType.STYLUS))
        assertEquals(setOf(ElementId("a")), env.selection)
        drag(p(130.0, 130.0), p(180.0, 150.0))
        assertEquals(150.0, env.session.scene[ElementId("a")]!!.bounds.left, 1e-9)
        c.onGesture(GestureEvent.Tap(p(800.0, 700.0), ToolType.STYLUS))
        assertTrue(env.selection.isEmpty())
        drag(p(50.0, 50.0), p(700.0, 300.0))
        assertEquals(setOf(ElementId("a"), ElementId("b")), env.selection)
        assertNotNull(c.overlay().selection)
        c.onGesture(GestureEvent.Tap(p(160.0, 120.0), ToolType.STYLUS))
        c.onGesture(GestureEvent.Tap(p(160.0, 120.0), ToolType.STYLUS))
        assertEquals(ElementId("a"), env.edited)
    }

    @Test
    fun connectorToolLinksTwoNodesAndFlowchartLabelsDecisions() {
        val decision = ShapeNode(ElementId("d"), FlowchartSymbols.Decision.key, Rect(100.0, 100.0, 260.0, 200.0), PlainLabel("?"))
        env.execute(AddElements(listOf(decision, node("b", 500.0, 100.0))))
        env.tools = env.tools.copy(kind = ToolKind.CONNECTOR)
        drag(p(180.0, 150.0), p(550.0, 130.0))
        val conn = env.session.scene.elements.values.filterIsInstance<Connector>().single()
        assertEquals(ElementId("d"), (conn.source as Endpoint.Attached).node)
        assertEquals(ElementId("b"), (conn.target as Endpoint.Attached).node)
        assertEquals("Yes", conn.label(io.github.leonardosponton.inkflow.domain.canvas.element.LabelPosition.MIDDLE))
        drag(p(800.0, 600.0), p(805.0, 602.0))
        assertEquals(1, env.session.scene.elements.values.filterIsInstance<Connector>().size)
        drag(p(800.0, 600.0), p(900.0, 700.0))
        assertEquals(2, env.session.scene.elements.values.filterIsInstance<Connector>().size)
    }

    @Test
    fun eraserRemovesOrSplitsInk() {
        val pts = StrokePoints.Builder().apply { repeat(21) { add(it * 10f, 0f, it * 5f, 1f) } }.build()
        env.execute(AddElements(listOf(InkStroke(ElementId("s"), Vec2(100.0, 300.0), InkBrush(size = 2f), pts))))
        env.tools = env.tools.copy(kind = ToolKind.ERASER, eraserMode = EraserMode.PARTIAL)
        drag(p(200.0, 250.0), p(200.0, 350.0))
        val pieces = env.session.scene.elements.values.filterIsInstance<InkStroke>()
        assertEquals(2, pieces.size)
        env.tools = env.tools.copy(eraserMode = EraserMode.STROKE)
        drag(p(120.0, 250.0), p(120.0, 350.0))
        assertEquals(1, env.session.scene.elements.values.filterIsInstance<InkStroke>().size)
    }

    @Test
    fun gesturesNavigateUndoAndMenus() {
        c.onGesture(GestureEvent.MultiFingerTap(2))
        c.onGesture(GestureEvent.MultiFingerTap(3))
        assertEquals(1, env.undos)
        assertEquals(1, env.redos)
        c.onGesture(GestureEvent.Navigate(p(100.0, 0.0), 2.0, p(500.0, 400.0), 0.0))
        assertEquals(2.0, env.camera.zoom, 1e-9)
        assertTrue(c.zooming)
        c.onGesture(GestureEvent.NavigateEnd)
        assertTrue(!c.zooming)
        c.onGesture(GestureEvent.DoubleTap(p(1.0, 1.0), ToolType.FINGER))
        assertEquals(1, env.fitted)
        c.onGesture(GestureEvent.LongPress(p(5.0, 5.0), ToolType.STYLUS))
        assertNotNull(env.menu)
        c.onGesture(GestureEvent.Hover(p(10.0, 10.0), eraser = false))
        assertNotNull(c.overlay().brushCursor)
        c.onGesture(GestureEvent.HoverExit)
        assertNull(c.overlay().brushCursor)
    }

    @Test
    fun shapeAndTextToolsPlaceElements() {
        env.tools = env.tools.copy(kind = ToolKind.SHAPE, shape = BasicShapes.Diamond.key)
        c.onGesture(GestureEvent.Tap(p(300.0, 300.0), ToolType.STYLUS))
        assertEquals(BasicShapes.Diamond.key, (env.session.scene.ordered.single() as ShapeNode).shape)
        assertEquals(ToolKind.SELECT, env.tools.kind)
        env.tools = env.tools.copy(kind = ToolKind.STICKY)
        c.onGesture(GestureEvent.Tap(p(600.0, 300.0), ToolType.STYLUS))
        assertEquals(2, env.session.scene.size)
        assertNotNull(env.edited)
        env.tools = env.tools.copy(kind = ToolKind.SHAPE, shape = BasicShapes.Rectangle.key)
        drag(p(100.0, 500.0), p(300.0, 700.0))
        assertEquals(3, env.session.scene.size)
    }

    @Test
    fun fingerActsAsPenOnlyOverSelectableThings() {
        env.execute(AddElements(listOf(node("a", 100.0, 100.0))))
        env.tools = env.tools.copy(kind = ToolKind.SELECT)
        assertTrue(c.fingerActsAsPen(p(120.0, 120.0)))
        assertTrue(!c.fingerActsAsPen(p(900.0, 700.0)))
        env.settings = env.settings.copy(fingerDrawing = true)
        assertTrue(c.fingerActsAsPen(p(900.0, 700.0)))
    }
}
