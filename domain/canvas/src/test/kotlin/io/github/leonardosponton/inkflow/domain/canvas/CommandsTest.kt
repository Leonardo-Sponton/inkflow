package io.github.leonardosponton.inkflow.domain.canvas

import io.github.leonardosponton.inkflow.core.geometry.Affine
import io.github.leonardosponton.inkflow.core.model.AssetId
import io.github.leonardosponton.inkflow.core.model.ColorArgb
import io.github.leonardosponton.inkflow.core.model.LayerId
import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.core.model.StylePatch
import io.github.leonardosponton.inkflow.core.model.Vec2
import io.github.leonardosponton.inkflow.domain.canvas.command.AddElements
import io.github.leonardosponton.inkflow.domain.canvas.command.ApplyEdits
import io.github.leonardosponton.inkflow.domain.canvas.command.ChangeZOrder
import io.github.leonardosponton.inkflow.domain.canvas.command.CompositeCommand
import io.github.leonardosponton.inkflow.domain.canvas.command.MoveElements
import io.github.leonardosponton.inkflow.domain.canvas.command.MoveToLayer
import io.github.leonardosponton.inkflow.domain.canvas.command.RemoveElements
import io.github.leonardosponton.inkflow.domain.canvas.command.SetFrame
import io.github.leonardosponton.inkflow.domain.canvas.command.SetGroup
import io.github.leonardosponton.inkflow.domain.canvas.command.SetLocked
import io.github.leonardosponton.inkflow.domain.canvas.command.TransformElements
import io.github.leonardosponton.inkflow.domain.canvas.command.UpdateConnector
import io.github.leonardosponton.inkflow.domain.canvas.command.UpdateNodeContent
import io.github.leonardosponton.inkflow.domain.canvas.command.UpdateStyle
import io.github.leonardosponton.inkflow.domain.canvas.command.UpdateText
import io.github.leonardosponton.inkflow.domain.canvas.command.ZOrderOp
import io.github.leonardosponton.inkflow.domain.canvas.element.Connector
import io.github.leonardosponton.inkflow.domain.canvas.element.Endpoint
import io.github.leonardosponton.inkflow.domain.canvas.element.ImageElement
import io.github.leonardosponton.inkflow.domain.canvas.element.InkStroke
import io.github.leonardosponton.inkflow.domain.canvas.element.LabelPosition
import io.github.leonardosponton.inkflow.domain.canvas.element.Routing
import io.github.leonardosponton.inkflow.domain.canvas.element.ShapeNode
import io.github.leonardosponton.inkflow.domain.canvas.element.TextElement
import io.github.leonardosponton.inkflow.domain.diagram.BasicShapes
import io.github.leonardosponton.inkflow.domain.diagram.PlainLabel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommandsTest {
    @Test
    fun `connector stays attached and reroutes when a node moves`() {
        val s = session()
        s.execute(AddElements(listOf(node("a", 0.0, 0.0), node("b", 300.0, 0.0), edge("e", "a", "b"))))
        val before = s.scene[id("e")] as Connector
        assertEquals(Vec2(100.0, 30.0), before.route.first())
        assertEquals(Vec2(300.0, 30.0), before.route.last())
        s.execute(MoveElements(listOf(id("b")), Vec2(0.0, 200.0)))
        val after = s.scene[id("e")] as Connector
        assertTrue(after.source is Endpoint.Attached)
        assertEquals(s.scene[id("b")]!!.bounds.center.y, 230.0)
        assertTrue(after.route.last().y in 200.0..260.0)
    }

    @Test
    fun `orthogonal and curved routes are computed`() {
        val s = session()
        s.execute(
            AddElements(
                listOf(node("a", 0.0, 0.0), node("b", 300.0, 200.0), edge("o", "a", "b", Routing.ORTHOGONAL, "e")),
            ),
        )
        val o = s.scene[id("o")] as Connector
        o.route.zipWithNext().forEach { (p, q) -> assertTrue(p.x == q.x || p.y == q.y) }
        s.execute(UpdateConnector(id("o")) { it.copy(routing = Routing.CURVED) })
        assertEquals(4, (s.scene[id("o")] as Connector).route.size)
    }

    @Test
    fun `undo and redo restore exact states`() {
        val s = session()
        s.execute(AddElements(listOf(node("a", 0.0, 0.0))))
        s.execute(MoveElements(listOf(id("a")), Vec2(10.0, 10.0)))
        assertEquals(Rect(10.0, 10.0, 110.0, 70.0), s.scene[id("a")]!!.bounds)
        s.undo()
        assertEquals(Rect(0.0, 0.0, 100.0, 60.0), s.scene[id("a")]!!.bounds)
        s.undo()
        assertNull(s.scene[id("a")])
        assertNull(s.undo())
        s.redo()
        s.redo()
        assertEquals(Rect(10.0, 10.0, 110.0, 70.0), s.scene[id("a")]!!.bounds)
        assertNull(s.redo())
        assertEquals(1, s.index.size)
    }

    @Test
    fun `continuous gestures coalesce into one undo step`() {
        val s = session()
        s.execute(AddElements(listOf(node("a", 0.0, 0.0))))
        repeat(5) { s.execute(MoveElements(listOf(id("a")), Vec2(1.0, 0.0)), coalesceKey = "drag") }
        s.undo.seal()
        assertEquals(5.0, s.scene[id("a")]!!.bounds.left)
        s.undo()
        assertEquals(0.0, s.scene[id("a")]!!.bounds.left)
    }

    @Test
    fun `removing a node frees its connectors and removes container children`() {
        val s = session()
        val frame = node("f", 0.0, 0.0, 500.0, 400.0, BasicShapes.Frame.key)
        s.execute(
            AddElements(
                listOf(frame, node("c", 50.0, 50.0, parent = id("f")), node("x", 800.0, 0.0), edge("e", "c", "x")),
            ),
        )
        s.execute(RemoveElements(listOf(id("f"))))
        assertNull(s.scene[id("c")])
        val e = s.scene[id("e")] as Connector
        assertTrue(e.source is Endpoint.Free)
        assertTrue(e.target is Endpoint.Attached)
    }

    @Test
    fun `moving a container moves its children and dropping reparents`() {
        val s = session()
        val frame = node("f", 0.0, 0.0, 500.0, 400.0, BasicShapes.Frame.key)
        s.execute(AddElements(listOf(frame, node("c", 600.0, 50.0))))
        s.execute(MoveElements(listOf(id("c")), Vec2(-500.0, 0.0)))
        assertEquals(id("f"), (s.scene[id("c")] as ShapeNode).parent)
        s.execute(MoveElements(listOf(id("f")), Vec2(0.0, 100.0)))
        assertEquals(150.0, s.scene[id("c")]!!.bounds.top)
        s.execute(MoveElements(listOf(id("c")), Vec2(2000.0, 0.0)))
        assertNull((s.scene[id("c")] as ShapeNode).parent)
    }

    @Test
    fun `locked elements do not move`() {
        val s = session()
        s.execute(AddElements(listOf(node("a", 0.0, 0.0))))
        s.execute(SetLocked(listOf(id("a")), true))
        s.execute(MoveElements(listOf(id("a")), Vec2(10.0, 0.0)))
        assertEquals(0.0, s.scene[id("a")]!!.bounds.left)
    }

    @Test
    fun `z order operations`() {
        val s = session()
        s.execute(AddElements(listOf(node("a", 0.0, 0.0), node("b", 0.0, 0.0), node("c", 0.0, 0.0))))

        fun order() = s.scene.ordered.map { it.id.value }
        assertEquals(listOf("a", "b", "c"), order())
        s.execute(ChangeZOrder(listOf(id("a")), ZOrderOp.TO_FRONT))
        assertEquals(listOf("b", "c", "a"), order())
        s.execute(ChangeZOrder(listOf(id("a")), ZOrderOp.TO_BACK))
        assertEquals(listOf("a", "b", "c"), order())
        s.execute(ChangeZOrder(listOf(id("a")), ZOrderOp.FORWARD))
        assertEquals(listOf("b", "a", "c"), order())
        s.execute(ChangeZOrder(listOf(id("c")), ZOrderOp.BACKWARD))
        assertEquals(listOf("b", "c", "a"), order())
        assertTrue(s.execute(ChangeZOrder(listOf(id("zzz")), ZOrderOp.FORWARD)).isEmpty)
    }

    @Test
    fun `style content text frame layer and group edits`() {
        val s = session()
        val text = TextElement(id("t"), Rect(0.0, 0.0, 50.0, 20.0), "hi")
        val img = ImageElement(id("i"), Rect(0.0, 0.0, 10.0, 10.0), AssetId("a"))
        val ink = stroke("k", 0.0 to 0.0, 10.0 to 10.0)
        s.execute(AddElements(listOf(node("a", 0.0, 0.0), text, img, ink, edge("e", "a", "a"))))
        val red = ColorArgb(0xFFFF0000.toInt())
        s.execute(
            UpdateStyle(
                listOf(id("a"), id("t"), id("k"), id("e"), id("i")),
                StylePatch(stroke = red, strokeWidth = 6.0),
            ),
        )
        assertEquals(red, (s.scene[id("a")] as ShapeNode).style.stroke)
        assertEquals(red, (s.scene[id("k")] as InkStroke).brush.color)
        assertEquals(6f, (s.scene[id("k")] as InkStroke).brush.size)
        s.execute(UpdateNodeContent(id("a"), PlainLabel("renamed"), Rect(0.0, 0.0, 200.0, 100.0)))
        assertEquals("renamed", (s.scene[id("a")] as ShapeNode).content.summary)
        s.execute(UpdateText(id("t"), "hello"))
        assertEquals("hello", (s.scene[id("t")] as TextElement).text)
        s.execute(SetFrame(id("i"), Rect(0.0, 0.0, 40.0, 40.0)))
        s.execute(SetFrame(id("t"), Rect(0.0, 0.0, 80.0, 40.0)))
        s.execute(SetFrame(id("a"), Rect(0.0, 0.0, 30.0, 30.0)))
        assertEquals(40.0, s.scene[id("i")]!!.bounds.width)
        assertTrue(s.execute(SetFrame(id("k"), Rect(0.0, 0.0, 1.0, 1.0))).isEmpty)
        s.execute(SetGroup(listOf(id("a"), id("t")), id("g")))
        assertEquals(2, s.scene.groupMembers(id("g")).size)
        s.execute(MoveToLayer(listOf(id("a")), LayerId("l2")))
        assertEquals(LayerId("l2"), s.scene[id("a")]!!.layerId)
        assertTrue(s.execute(UpdateText(id("a"), "x")).isEmpty)
        assertTrue(s.execute(UpdateNodeContent(id("t"), PlainLabel("x"))).isEmpty)
        assertTrue(s.execute(UpdateConnector(id("a")) { it }).isEmpty)
    }

    @Test
    fun `transform scales ink and frames and composite applies in order`() {
        val s = session()
        s.execute(AddElements(listOf(stroke("k", 0.0 to 0.0, 10.0 to 0.0), node("a", 0.0, 0.0))))
        s.execute(TransformElements(listOf(id("k"), id("a")), Affine.scale(2.0, 2.0)))
        val k = s.scene[id("k")] as InkStroke
        assertEquals(20.0, k.worldPoint(1).x, 1e-6)
        assertEquals(4f, k.brush.size)
        assertEquals(200.0, s.scene[id("a")]!!.bounds.width)
        s.execute(
            CompositeCommand(
                listOf(
                    MoveElements(listOf(id("a")), Vec2(5.0, 0.0)),
                    ApplyEdits(emptyList(), listOf(id("k"))),
                ),
            ),
        )
        assertNull(s.scene[id("k")])
        s.undo()
        assertNotNull(s.scene[id("k")])
        assertEquals(0.0, s.scene[id("a")]!!.bounds.left)
    }

    @Test
    fun `free connectors move with the selection and labels edit`() {
        val s = session()
        val free =
            Connector(
                id("f"),
                Endpoint.Free(Vec2(0.0, 0.0)),
                Endpoint.Free(Vec2(100.0, 0.0)),
                routing = Routing.STRAIGHT,
            )
        s.execute(AddElements(listOf(free)))
        s.execute(MoveElements(listOf(id("f")), Vec2(0.0, 50.0)))
        val moved = s.scene[id("f")] as Connector
        assertEquals(Vec2(0.0, 50.0), moved.route.first())
        val labelled = moved.withLabel(LabelPosition.MIDDLE, "yes").withLabel(LabelPosition.END, "1")
        assertEquals("yes", labelled.label(LabelPosition.MIDDLE))
        assertEquals("", labelled.withLabel(LabelPosition.MIDDLE, " ").label(LabelPosition.MIDDLE))
        assertFalse(labelled.attachedNodes().isNotEmpty())
    }
}
