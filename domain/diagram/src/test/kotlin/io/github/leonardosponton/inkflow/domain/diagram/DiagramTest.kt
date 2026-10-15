package io.github.leonardosponton.inkflow.domain.diagram

import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.core.model.ShapeStyle
import io.github.leonardosponton.inkflow.core.model.Vec2
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiagramTest {
    private val frame = Rect(100.0, 100.0, 300.0, 200.0)

    @Test
    fun `anchors resolve fractions and offsets`() {
        assertEquals(Vec2(200.0, 100.0), at(0.5, 0.0).resolve(frame))
        assertEquals(Vec2(290.0, 210.0), at(1.0, 1.0, -10.0, 10.0).resolve(frame))
        assertEquals(Vec2(300.0, 150.0), Ports.East.position(frame))
        assertEquals(12, Ports.Dense.size)
    }

    @Test
    fun `outline builder produces paths`() {
        val o = outline {
            rect()
            oval(fill = PathFill.NONE)
            line(at(0.0, 0.0), at(1.0, 1.0))
            path {
                move(0.0, 0.0)
                quad(at(0.5, 0.0), at(1.0, 0.0))
                cubic(at(1.0, 0.5), at(1.0, 0.5), at(1.0, 1.0))
                arc(at(0.0, 0.0), at(1.0, 1.0), 0.0, 90.0)
                close()
            }
        }
        assertEquals(4, o.paths.size)
        assertEquals(PathFill.NONE, o.paths[2].fill)
        assertTrue(o.paths[3].ops.last() is PathOp.Close)
        BasicShapes.all.forEach { assertTrue(it.outline.paths.isNotEmpty() || it == BasicShapes.Text) }
        assertEquals(3, BasicShapes.cylinderOutline().paths.size + 1)
    }

    @Test
    fun `registry lookup search and fallback`() {
        val reg = ShapeRegistry(BasicShapes.all)
        assertEquals(BasicShapes.Diamond, reg[BasicShapes.Diamond.key])
        assertEquals(BasicShapes.Rectangle, reg.resolve(ShapeTypeKey("nope")))
        assertNull(reg[ShapeTypeKey("nope")])
        assertTrue(reg.search("circ").contains(BasicShapes.Circle))
        assertTrue(reg.search("storage").contains(BasicShapes.Cylinder))
        assertEquals(BasicShapes.all.size, reg.search("").size)
        assertEquals(listOf(NotationId.Basic), reg.notations)
        assertEquals(BasicShapes.all.size, reg.inNotation(NotationId.Basic).size)
        assertTrue(BasicShapes.Frame.isContainer)
    }

    @Test
    fun `markers registry`() {
        val reg = MarkerRegistry(BasicMarkers.all)
        assertNotNull(reg[MarkerKey.DiamondFilled])
        assertNull(reg[MarkerKey("zzz")])
        assertTrue(reg[MarkerKey.TriangleHollow]!!.lineInset > 0)
        assertEquals(BasicMarkers.all.size, reg.all.size)
        assertEquals(Vec2(-3.0, 4.0), mk(-3.0, 4.0).resolve(Rect(0.0, 0.0, 0.0, 0.0)))
    }

    @Test
    fun `plain label layout and approximate metrics`() {
        val l = PlainLabelLayouter.layout(PlainLabel("Hello"), BasicShapes.Rectangle, frame, ShapeStyle.Default, ApproximateTextMetrics)
        assertEquals("Hello", l.texts.single().text)
        assertTrue(frame.contains(l.texts.single().rect))
        val c = PlainLabelLayouter.layout(PlainLabel("Lane"), BasicShapes.Frame, frame, ShapeStyle.Default, ApproximateTextMetrics)
        assertEquals(32.0, c.texts.single().rect.height)
        assertTrue(c.texts.single().bold)
        assertEquals(NodeLayout.Empty, PlainLabelLayouter.layout(PlainLabel(""), BasicShapes.Rectangle, frame, ShapeStyle.Default, ApproximateTextMetrics))
        assertNull(PlainLabelLayouter.preferredSize(PlainLabel("x"), BasicShapes.Rectangle, ShapeStyle.Default, ApproximateTextMetrics))
        assertTrue(PlainLabelLayouter.supports(PlainLabel()))
        val m = ApproximateTextMetrics.measure("abcdefghij\nxy", 10.0, maxWidth = 22.0)
        assertEquals(22.0, m.width)
        assertEquals(4 * ApproximateTextMetrics.lineHeight(10.0), m.height, 1e-9)
        assertEquals(ApproximateTextMetrics.width("ab", 10.0) * 2, ApproximateTextMetrics.width("abcd", 10.0), 1e-9)
        assertEquals(LayoutRegistry(emptyList()).layouterFor(PlainLabel()), PlainLabelLayouter)
        assertEquals("x", PlainLabel("x").summary)
    }

    @Test
    fun `text areas and matching`() {
        assertEquals(Rect(150.0, 125.0, 250.0, 175.0), TextArea.inset(0.25, 0.25).resolve(frame))
        assertTrue(BasicShapes.Note.matches("comment"))
        assertFalse(BasicShapes.Note.matches("zzz"))
        assertEquals("", (BasicShapes.Rectangle.createContent() as PlainLabel).text)
        assertEquals("Frame", BasicShapes.Frame.createContent().summary)
    }
}
