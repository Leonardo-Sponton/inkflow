package io.github.leonardosponton.inkflow.domain.flowchart

import io.github.leonardosponton.inkflow.core.model.ElementId
import io.github.leonardosponton.inkflow.core.model.IdGenerator
import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.domain.canvas.command.AddElements
import io.github.leonardosponton.inkflow.domain.canvas.element.Connector
import io.github.leonardosponton.inkflow.domain.canvas.element.Endpoint
import io.github.leonardosponton.inkflow.domain.canvas.element.LabelPosition
import io.github.leonardosponton.inkflow.domain.canvas.element.ShapeNode
import io.github.leonardosponton.inkflow.domain.canvas.notation.NotationCatalog
import io.github.leonardosponton.inkflow.domain.canvas.session.EditorSession
import io.github.leonardosponton.inkflow.domain.diagram.ApproximateTextMetrics
import io.github.leonardosponton.inkflow.domain.diagram.MarkerKey
import io.github.leonardosponton.inkflow.domain.diagram.ShapeDefinition
import io.github.leonardosponton.inkflow.domain.diagram.ShapeTypeKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FlowchartTest {
    private var n = 0
    private val catalog = NotationCatalog(listOf(FlowchartNotation))
    private val session = EditorSession(catalog, IdGenerator { "g${++n}" })

    private fun node(id: String, def: ShapeDefinition, x: Double = 0.0) =
        ShapeNode(ElementId(id), def.key, Rect.fromOriginSize(io.github.leonardosponton.inkflow.core.model.Vec2(x, 0.0), def.defaultSize), def.createContent())

    private fun edge(id: String, from: String, to: String) =
        Connector(ElementId(id), Endpoint.Attached(ElementId(from)), Endpoint.Attached(ElementId(to)), endMarker = MarkerKey.None)

    @Test
    fun `every iso 5807 symbol is registered with an outline`() {
        val names = FlowchartSymbols.all.map { it.key.value.removePrefix("flowchart.") }.toSet()
        val required = listOf(
            "terminator",
            "process",
            "predefined",
            "decision",
            "data",
            "manual_input",
            "manual_operation",
            "preparation",
            "document",
            "multi_document",
            "stored_data",
            "database",
            "internal_storage",
            "display",
            "delay",
            "merge",
            "extract",
            "sort",
            "collate",
            "or",
            "summing_junction",
            "connector",
            "off_page",
            "annotation",
            "loop_start",
            "loop_end",
            "lane_vertical",
            "lane_horizontal",
        )
        assertTrue(names.containsAll(required), "missing ${required - names}")
        FlowchartSymbols.all.forEach { assertTrue(it.outline.paths.isNotEmpty(), it.displayName) }
        assertEquals(FlowchartSymbols.all.size, catalog.shapes.inNotation(FlowchartSymbols.Id).size)
        assertTrue(FlowchartSymbols.HorizontalLane.headerOnLeft)
        assertTrue(FlowchartSymbols.isLane(FlowchartSymbols.VerticalLane.key))
    }

    @Test
    fun `decision branches get yes then no labels and arrows`() {
        session.execute(AddElements(listOf(node("d", FlowchartSymbols.Decision), node("a", FlowchartSymbols.Process, 300.0), node("b", FlowchartSymbols.Process, 600.0), node("c", FlowchartSymbols.Process, 900.0))))
        val first = catalog.decorateNewConnector(edge("e1", "d", "a"), session.scene)
        assertEquals(FlowchartNotation.YES, first.label(LabelPosition.MIDDLE))
        assertEquals(MarkerKey.Arrow, first.endMarker)
        session.execute(AddElements(listOf(first)))
        val second = catalog.decorateNewConnector(edge("e2", "d", "b"), session.scene)
        assertEquals(FlowchartNotation.NO, second.label(LabelPosition.MIDDLE))
        session.execute(AddElements(listOf(second)))
        assertEquals("", catalog.decorateNewConnector(edge("e3", "d", "c"), session.scene).label(LabelPosition.MIDDLE))
        val fromProcess = edge("e4", "a", "b")
        assertEquals(fromProcess, catalog.decorateNewConnector(fromProcess, session.scene))
        val labelled = edge("e5", "d", "c").withLabel(LabelPosition.MIDDLE, "maybe")
        assertEquals("maybe", catalog.decorateNewConnector(labelled, session.scene).label(LabelPosition.MIDDLE))
        val free = Connector(ElementId("f"), Endpoint.Free(io.github.leonardosponton.inkflow.core.model.Vec2.Zero), Endpoint.Attached(ElementId("a")))
        assertEquals(free, catalog.decorateNewConnector(free, session.scene))
        assertTrue(catalog.validate(session.scene).none { it.message.startsWith("Decision") })
    }

    @Test
    fun `validation flags lonely decisions and missing terminators`() {
        session.execute(AddElements(listOf(node("d", FlowchartSymbols.Decision), node("a", FlowchartSymbols.Process, 300.0), node("b", FlowchartSymbols.Data, 600.0))))
        val issues = catalog.validate(session.scene).map { it.message }
        assertTrue(issues.any { it.startsWith("Decision") })
        assertTrue(issues.any { it.contains("terminator") })
        session.execute(AddElements(listOf(node("t", FlowchartSymbols.Terminator, 900.0))))
        assertTrue(catalog.validate(session.scene).none { it.message.contains("terminator") })
    }

    @Test
    fun `lane header lays out rotated text on the left`() {
        val lane = FlowchartSymbols.HorizontalLane
        val layout = catalog.layouts.layouterFor(lane.createContent()).layout(lane.createContent(), lane, Rect(0.0, 0.0, 700.0, 200.0), lane.defaultStyle, ApproximateTextMetrics)
        val block = layout.texts.single()
        assertEquals(90, block.rotation)
        assertEquals(36.0, block.rect.width)
        assertEquals(ShapeTypeKey("flowchart.decision"), FlowchartSymbols.Decision.key)
    }
}
