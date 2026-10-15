package io.github.leonardosponton.inkflow.domain.canvas

import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.core.model.Vec2
import io.github.leonardosponton.inkflow.domain.canvas.command.AddElements
import io.github.leonardosponton.inkflow.domain.canvas.element.CanvasElement
import io.github.leonardosponton.inkflow.domain.canvas.element.StrokePoints
import io.github.leonardosponton.inkflow.domain.canvas.ink.GeometricShapeRecognizer
import io.github.leonardosponton.inkflow.domain.canvas.ink.InkEraser
import io.github.leonardosponton.inkflow.domain.canvas.ink.RecognizedShape
import io.github.leonardosponton.inkflow.domain.canvas.notation.NotationCatalog
import io.github.leonardosponton.inkflow.domain.canvas.scene.Scene
import io.github.leonardosponton.inkflow.domain.canvas.scene.SceneChange
import io.github.leonardosponton.inkflow.domain.canvas.serialization.SceneJson
import io.github.leonardosponton.inkflow.domain.canvas.snap.Snapper
import io.github.leonardosponton.inkflow.domain.canvas.undo.UndoManager
import io.github.leonardosponton.inkflow.domain.diagram.BasicShapes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.cos
import kotlin.math.sin

class MiscDomainTest {
    private val recognizer = GeometricShapeRecognizer()

    private fun circlePoints(cx: Double, cy: Double, rx: Double, ry: Double) =
        (0..64).map {
            val a = it / 64.0 * 2 * Math.PI
            Vec2(cx + rx * cos(a), cy + ry * sin(a))
        }

    private fun polyPoints(vararg corners: Vec2): List<Vec2> {
        val closed = corners.toList() + corners.first()
        return closed.zipWithNext().flatMap { (a, b) -> (0 until 10).map { a.lerp(b, it / 10.0) } } + corners.first()
    }

    @Test
    fun `recognizes basic shapes`() {
        assertInstanceOf(RecognizedShape.Line::class.java, recognizer.recognize((0..20).map { Vec2(it * 10.0, it * 0.5) }))
        assertInstanceOf(RecognizedShape.Ellipse::class.java, recognizer.recognize(circlePoints(100.0, 100.0, 80.0, 50.0)))
        val rect = recognizer.recognize(polyPoints(Vec2(0.0, 0.0), Vec2(200.0, 0.0), Vec2(200.0, 100.0), Vec2(0.0, 100.0)))
        assertEquals(RecognizedShape.Rectangle(Rect(0.0, 0.0, 200.0, 100.0)), rect)
        val diamond = recognizer.recognize(polyPoints(Vec2(100.0, 0.0), Vec2(200.0, 60.0), Vec2(100.0, 120.0), Vec2(0.0, 60.0)))
        assertInstanceOf(RecognizedShape.Diamond::class.java, diamond)
        val tri = recognizer.recognize(polyPoints(Vec2(100.0, 0.0), Vec2(200.0, 150.0), Vec2(0.0, 150.0)))
        assertInstanceOf(RecognizedShape.Triangle::class.java, tri)
        val shaft = (0..20).map { Vec2(it * 10.0, 0.0) }
        val head = listOf(Vec2(185.0, -15.0), Vec2(170.0, -25.0), Vec2(185.0, -12.0), Vec2(200.0, 0.0), Vec2(185.0, 15.0), Vec2(172.0, 24.0))
        assertEquals(RecognizedShape.Arrow(Vec2(0.0, 0.0), Vec2(200.0, 0.0)), recognizer.recognize(shaft + head))
        assertNull(recognizer.recognize(listOf(Vec2.Zero, Vec2(1.0, 1.0))))
        assertNull(recognizer.recognize((0..10).map { Vec2(it.toDouble(), 0.0) }))
        val scribble = (0..40).map { Vec2(it * 5.0, if (it % 2 == 0) 0.0 else 80.0) }
        assertNull(recognizer.recognize(scribble))
    }

    @Test
    fun `eraser splits strokes`() {
        val s = stroke("s", *(0..10).map { it * 10.0 to 0.0 }.toTypedArray())
        var n = 0
        val pieces = InkEraser.erase(s, Vec2(50.0, 0.0), 8.0) { id("p${n++}") }
        assertNotNull(pieces)
        assertEquals(2, pieces!!.size)
        assertTrue(pieces.all { it.points.size >= 2 })
        assertNull(InkEraser.erase(s, Vec2(500.0, 500.0), 8.0) { id("x") })
    }

    @Test
    fun `json round trip of every element and changes`() {
        val catalog = NotationCatalog(emptyList())
        val json = SceneJson(catalog.serializers)
        val s = session()
        s.execute(AddElements(listOf(node("a", 0.0, 0.0), node("b", 300.0, 0.0), edge("e", "a", "b"), stroke("k", 0.0 to 0.0, 5.0 to 5.0))))
        s.scene.elements.values.forEach { el ->
            assertEquals(el, json.decodeElement(json.encodeElement(el)))
        }
        val change = s.undo.history.first()
        assertEquals(change, json.decodeChange(json.encodeChange(change)))
        val unknown = """{"type":"node","id":"z","shape":"future.shape","frame":{"left":0,"top":0,"right":1,"bottom":1},"content":{"type":"future","x":1}}"""
        val decoded: CanvasElement = json.decodeElement(unknown)
        assertEquals("", (decoded as io.github.leonardosponton.inkflow.domain.canvas.element.ShapeNode).content.summary)
        assertEquals(BasicShapes.Rectangle, catalog.shapes.resolve(decoded.shape))
    }

    @Test
    fun `stroke points builder bounds map and equality`() {
        val b = StrokePoints.Builder()
        repeat(100) { b.add(it.toFloat(), -it.toFloat(), it.toFloat(), 1f, 0.1f, 0.2f) }
        val pts = b.build()
        assertEquals(100, pts.size)
        assertEquals(-99.0, pts.localBounds()!!.top)
        assertEquals(99.0, pts.localBounds()!!.right)
        assertEquals(0.1f, pts.tilt(3))
        assertEquals(0.2f, pts.orientation(3))
        assertEquals(1f, pts.pressure(3))
        assertEquals(3f, pts.time(3))
        assertEquals(pts, StrokePoints(pts.data.copyOf()))
        assertEquals(pts.hashCode(), StrokePoints(pts.data.copyOf()).hashCode())
        assertEquals(2f, pts.mapPositions { it * 2.0 }.x(1))
        assertNull(StrokePoints.Empty.localBounds())
        assertTrue(pts.toString().contains("100"))
    }

    @Test
    fun `undo manager bounds and restore`() {
        val um = UndoManager(maxEntries = 3)
        val scene = Scene.Empty
        repeat(5) { um.record(SceneChange.of(scene, listOf(node("n$it", 0.0, 0.0)))) }
        assertEquals(3, um.history.size)
        um.record(SceneChange.Empty)
        assertEquals(3, um.history.size)
        um.restore(um.history.take(1))
        assertTrue(um.canUndo)
        assertTrue(!um.canRedo)
        um.undo()
        assertTrue(um.canRedo)
    }

    @Test
    fun `snapper aligns to neighbours or grid`() {
        val snapper = Snapper(gridSize = 20.0)
        val moving = Rect(0.0, 0.0, 100.0, 50.0)
        val other = Rect(203.0, 300.0, 303.0, 350.0)
        val r = snapper.snapMove(moving, Vec2(200.0, 0.0), listOf(other), threshold = 5.0, grid = true, guides = true)
        assertEquals(203.0, r.delta.x, 1e-9)
        assertEquals(1, r.guides.size)
        val g = snapper.snapMove(moving, Vec2(13.0, 27.0), emptyList(), threshold = 5.0, grid = true, guides = true)
        assertEquals(Vec2(20.0, 20.0), g.delta)
        val none = snapper.snapMove(moving, Vec2(13.0, 27.0), emptyList(), threshold = 5.0, grid = false, guides = false)
        assertEquals(Vec2(13.0, 27.0), none.delta)
        assertEquals(Vec2(40.0, 60.0), snapper.snapPoint(Vec2(41.0, 55.0)))
    }

    @Test
    fun `hit tester picks precise targets`() {
        val s = session()
        val frame = node("f", 0.0, 0.0, 400.0, 300.0, BasicShapes.Frame.key)
        s.execute(AddElements(listOf(frame, node("a", 50.0, 50.0), node("b", 250.0, 50.0), edge("e", "a", "b"), stroke("k", 0.0 to 400.0, 100.0 to 400.0))))
        val hit = s.hitTester()
        assertEquals(id("a"), hit.elementAt(Vec2(60.0, 60.0), 2.0)?.id)
        assertEquals(id("e"), hit.elementAt(Vec2(200.0, 80.0), 3.0)?.id)
        assertEquals(id("k"), hit.elementAt(Vec2(50.0, 401.0), 2.0)?.id)
        assertEquals(id("f"), hit.elementAt(Vec2(200.0, 10.0), 2.0)?.id)
        assertNull(hit.elementAt(Vec2(200.0, 200.0), 2.0))
        assertEquals("e", hit.portAt(Vec2(151.0, 80.0), 10.0)?.port?.id)
        assertEquals(id("b"), hit.nodeAt(Vec2(260.0, 60.0), 1.0)?.id)
        val lasso = listOf(Vec2(-10.0, 380.0), Vec2(120.0, 380.0), Vec2(120.0, 420.0), Vec2(-10.0, 420.0))
        assertEquals(listOf(id("k")), hit.inLasso(lasso).map { it.id })
        assertEquals(setOf(id("a")), hit.inRect(Rect(40.0, 40.0, 160.0, 120.0)).map { it.id }.toSet())
        assertTrue(hit.inLasso(emptyList()).isEmpty())
    }
}
