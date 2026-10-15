package io.github.leonardosponton.inkflow.feature.editor

import io.github.leonardosponton.inkflow.core.model.ElementId
import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.core.model.Vec2
import io.github.leonardosponton.inkflow.domain.canvas.element.Connector
import io.github.leonardosponton.inkflow.domain.canvas.element.Endpoint
import io.github.leonardosponton.inkflow.domain.canvas.element.ShapeNode
import io.github.leonardosponton.inkflow.domain.canvas.element.TextElement
import io.github.leonardosponton.inkflow.domain.canvas.element.TextKind
import io.github.leonardosponton.inkflow.domain.canvas.notation.NotationCatalog
import io.github.leonardosponton.inkflow.domain.canvas.scene.Scene
import io.github.leonardosponton.inkflow.domain.diagram.BasicShapes
import io.github.leonardosponton.inkflow.domain.diagram.PlainLabel
import io.github.leonardosponton.inkflow.feature.editor.ui.targetIndex
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorHelpersTest {
    @Test
    fun tabDragTargetIndex() {
        val widths = listOf(100f, 100f, 100f, 100f)
        assertEquals(0, targetIndex(0, 30f, widths))
        assertEquals(1, targetIndex(0, 60f, widths))
        assertEquals(3, targetIndex(0, 290f, widths))
        assertEquals(3, targetIndex(0, 5000f, widths))
        assertEquals(1, targetIndex(3, -220f, widths))
        assertEquals(0, targetIndex(2, -5000f, widths))
    }

    @Test
    fun elementDescriptions() {
        val catalog = NotationCatalog(emptyList())
        val a = ShapeNode(ElementId("a"), BasicShapes.Ellipse.key, Rect(0.0, 0.0, 10.0, 10.0), PlainLabel("Login"))
        val c = Connector(ElementId("c"), Endpoint.Attached(a.id), Endpoint.Free(Vec2.Zero)).withLabel(io.github.leonardosponton.inkflow.domain.canvas.element.LabelPosition.MIDDLE, "go")
        val scene = Scene.of(listOf(a, c))
        assertEquals("Ellipse: Login", ElementDescriber.describe(a, scene, catalog))
        assertEquals("Connector from Login to a point, “go”", ElementDescriber.describe(c, scene, catalog))
        assertEquals("Sticky note: empty", ElementDescriber.describe(TextElement(ElementId("t"), Rect(0.0, 0.0, 1.0, 1.0), "", TextKind.STICKY), scene, catalog))
    }
}
