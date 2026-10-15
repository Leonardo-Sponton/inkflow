package io.github.leonardosponton.inkflow.domain.uml

import io.github.leonardosponton.inkflow.core.model.DashPattern
import io.github.leonardosponton.inkflow.core.model.ElementId
import io.github.leonardosponton.inkflow.core.model.IdGenerator
import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.core.model.ShapeStyle
import io.github.leonardosponton.inkflow.core.model.Vec2
import io.github.leonardosponton.inkflow.domain.canvas.command.AddElements
import io.github.leonardosponton.inkflow.domain.canvas.command.UpdateConnector
import io.github.leonardosponton.inkflow.domain.canvas.element.Connector
import io.github.leonardosponton.inkflow.domain.canvas.element.Endpoint
import io.github.leonardosponton.inkflow.domain.canvas.element.LabelPosition
import io.github.leonardosponton.inkflow.domain.canvas.element.Routing
import io.github.leonardosponton.inkflow.domain.canvas.element.ShapeNode
import io.github.leonardosponton.inkflow.domain.canvas.notation.NotationCatalog
import io.github.leonardosponton.inkflow.domain.canvas.notation.Severity
import io.github.leonardosponton.inkflow.domain.canvas.serialization.SceneJson
import io.github.leonardosponton.inkflow.domain.canvas.session.EditorSession
import io.github.leonardosponton.inkflow.domain.diagram.ApproximateTextMetrics
import io.github.leonardosponton.inkflow.domain.diagram.MarkerKey
import io.github.leonardosponton.inkflow.domain.diagram.ShapeDefinition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UmlTest {
    private var n = 0
    private val catalog = NotationCatalog(listOf(UmlNotation))
    private val session = EditorSession(catalog, IdGenerator { "g${++n}" })

    private fun node(id: String, def: ShapeDefinition, x: Double = 0.0, content: UmlClassContent? = null) =
        ShapeNode(ElementId(id), def.key, Rect.fromOriginSize(Vec2(x, 0.0), def.defaultSize), content ?: def.createContent())

    private fun edge(id: String, from: String, to: String) = Connector(ElementId(id), Endpoint.Attached(ElementId(from)), Endpoint.Attached(ElementId(to)))

    @Test
    fun `member signatures parse and print`() {
        val attr = UmlMember.parse("- balance: Decimal = 0")
        assertEquals(Visibility.PRIVATE, attr.visibility)
        assertEquals("balance", attr.name)
        assertEquals("Decimal", attr.type)
        assertEquals("0", attr.defaultValue)
        assertFalse(attr.isOperation)
        assertEquals("- balance: Decimal = 0", attr.signature)
        val op = UmlMember.parse("# static abstract deposit(amount: Decimal, note: String): Boolean")
        assertEquals(Visibility.PROTECTED, op.visibility)
        assertTrue(op.isStatic && op.isAbstract && op.isOperation)
        assertEquals("amount: Decimal, note: String", op.parameters)
        assertEquals("Boolean", op.type)
        assertEquals("# deposit(amount: Decimal, note: String): Boolean", op.signature)
        val plain = UmlMember.parse("name", keepFlags = UmlMember("x", isStatic = true, visibility = Visibility.PACKAGE))
        assertEquals("~ name", plain.signature)
        assertTrue(plain.isStatic)
        assertEquals(null, Visibility.of('x'))
    }

    @Test
    fun `class layout has compartments and grows with members`() {
        val c = UmlClassContent(
            "Account",
            kind = ClassifierKind.ABSTRACT_CLASS,
            attributes = listOf(UmlMember("id", "Long", Visibility.PRIVATE), UmlMember("count", "Int", isStatic = true)),
            operations = listOf(UmlMember("close", "void", parameters = "", isAbstract = true)),
        )
        val def = UmlShapes.Class
        val size = UmlClassLayouter.preferredSize(c, def, ShapeStyle.Default, ApproximateTextMetrics)
        val layout = UmlClassLayouter.layout(c, def, Rect.fromOriginSize(Vec2.Zero, size), ShapeStyle.Default, ApproximateTextMetrics)
        assertEquals(listOf("Account", "- id: Long", "+ count: Int", "+ close(): void"), layout.texts.map { it.text })
        assertTrue(layout.texts[0].italic && layout.texts[0].bold)
        assertTrue(layout.texts[2].underline)
        assertTrue(layout.texts[3].italic)
        assertEquals(2, layout.separators.size)
        val bigger = UmlClassLayouter.preferredSize(c.copy(attributes = c.attributes + UmlMember("x")), def, ShapeStyle.Default, ApproximateTextMetrics)
        assertTrue(bigger.height > size.height)
        val e = UmlClassContent("Color", kind = ClassifierKind.ENUMERATION, literals = listOf("RED", "GREEN"))
        val el = UmlClassLayouter.layout(e, def, Rect(0.0, 0.0, 200.0, 200.0), ShapeStyle.Default, ApproximateTextMetrics)
        assertEquals(listOf("«enumeration»", "Color", "RED", "GREEN"), el.texts.map { it.text })
        assertEquals("«custom»", UmlClassContent(stereotype = "custom").stereotypeLabel)
        assertTrue(UmlClassLayouter.supports(c))
    }

    @Test
    fun `relations restyle connectors with markers dashes and labels`() {
        val base = edge("e", "a", "b")
        val comp = UmlRelation.applyTo(base, UmlRelation(UmlRelationKind.COMPOSITION, sourceMultiplicity = "1", targetMultiplicity = "0..*", targetRole = "items"))
        assertEquals(MarkerKey.DiamondFilled, comp.startMarker)
        assertEquals("1", comp.label(LabelPosition.START))
        assertEquals("items 0..*", comp.label(LabelPosition.END))
        val real = UmlRelation.applyTo(base, UmlRelation(UmlRelationKind.REALIZATION))
        assertEquals(DashPattern.DASHED, real.style.dash)
        assertEquals(MarkerKey.TriangleHollow, real.endMarker)
        val inc = UmlRelation.applyTo(base, UmlRelation(UmlRelationKind.INCLUDE))
        assertEquals("«include»", inc.label(LabelPosition.MIDDLE))
        val t = UmlRelation(UmlRelationKind.TRANSITION, event = "pay", guard = "amount > 0", action = "ship()")
        assertEquals("pay [amount > 0] / ship()", t.middleLabel)
        val msg = UmlRelation.applyTo(base, UmlRelation(UmlRelationKind.SYNC_MESSAGE, name = "login()"))
        assertEquals(Routing.STRAIGHT, msg.routing)
        assertTrue(UmlRelationKind.RETURN_MESSAGE.isMessage)
    }

    @Test
    fun `new connectors get a fitting relation`() {
        session.execute(
            AddElements(
                listOf(
                    node("c1", UmlShapes.Class),
                    node("c2", UmlShapes.Interface, 400.0),
                    node("l1", UmlShapes.Lifeline, 800.0),
                    node("l2", UmlShapes.Lifeline, 1100.0),
                    node("s1", UmlShapes.State, 1400.0),
                    node("s2", UmlShapes.State, 1700.0),
                    node("u1", UmlShapes.UseCase, 2000.0),
                    node("u2", UmlShapes.UseCase, 2300.0),
                    node("act", UmlShapes.Actor, 2600.0),
                    node("cmp", UmlShapes.Component, 2900.0),
                    node("art", UmlShapes.Artifact, 3200.0),
                ),
            ),
        )

        fun kindOf(a: String, b: String) = (catalog.decorateNewConnector(edge("x", a, b), session.scene).semantics as? UmlRelation)?.kind
        assertEquals(UmlRelationKind.ASSOCIATION, kindOf("c1", "c2"))
        assertEquals(UmlRelationKind.SYNC_MESSAGE, kindOf("l1", "l2"))
        assertEquals(UmlRelationKind.TRANSITION, kindOf("s1", "s2"))
        assertEquals(UmlRelationKind.INCLUDE, kindOf("u1", "u2"))
        assertEquals(UmlRelationKind.ASSOCIATION, kindOf("act", "u1"))
        assertEquals(UmlRelationKind.DEPENDENCY, kindOf("cmp", "art"))
        val free = Connector(ElementId("f"), Endpoint.Free(Vec2.Zero), Endpoint.Free(Vec2(1.0, 1.0)))
        assertNull(catalog.decorateNewConnector(free, session.scene).semantics as? UmlRelation)
        val already = UmlRelation.applyTo(edge("y", "c1", "c2"), UmlRelation(UmlRelationKind.GENERALIZATION))
        assertEquals(already, catalog.decorateNewConnector(already, session.scene))
    }

    @Test
    fun `validators catch misuse duplicates and cycles`() {
        val a = UmlClassContent("A")
        session.execute(AddElements(listOf(node("a", UmlShapes.Class, content = a), node("b", UmlShapes.Class, 300.0, UmlClassContent("B")), node("a2", UmlShapes.Class, 600.0, a), node("s", UmlShapes.State, 900.0))))
        val gen1 = UmlRelation.applyTo(edge("g1", "a", "b"), UmlRelation(UmlRelationKind.GENERALIZATION))
        val gen2 = UmlRelation.applyTo(edge("g2", "b", "a"), UmlRelation(UmlRelationKind.GENERALIZATION))
        val bad = UmlRelation.applyTo(edge("c", "a", "s"), UmlRelation(UmlRelationKind.COMPOSITION))
        val real = UmlRelation.applyTo(edge("r", "a", "b"), UmlRelation(UmlRelationKind.REALIZATION))
        session.execute(AddElements(listOf(gen1, gen2, bad, real)))
        val issues = catalog.validate(session.scene)
        assertTrue(issues.any { it.severity == Severity.ERROR && it.message.contains("cycle") })
        assertTrue(issues.any { it.message.startsWith("Composition") })
        assertTrue(issues.any { it.message.startsWith("Duplicate") })
        assertTrue(issues.any { it.message.startsWith("Realization") })
    }

    @Test
    fun `content and relations serialize and plantuml export`() {
        val json = SceneJson(catalog.serializers)
        val cls = node("a", UmlShapes.Class, content = UmlClassContent("Order", attributes = listOf(UmlMember("total", "Decimal", Visibility.PRIVATE, isStatic = true))))
        assertEquals(cls, json.decodeElement(json.encodeElement(cls)))
        session.execute(AddElements(listOf(cls, node("b", UmlShapes.Enumeration, 400.0), node("i", UmlShapes.Interface, 800.0), node("x", UmlShapes.AbstractClass, 1200.0))))
        val rel = UmlRelation.applyTo(edge("r", "a", "b"), UmlRelation(UmlRelationKind.AGGREGATION, sourceMultiplicity = "1", targetMultiplicity = "*", name = "has"))
        session.execute(AddElements(listOf(rel, UmlRelation.applyTo(edge("r2", "a", "i"), UmlRelation(UmlRelationKind.REALIZATION)))))
        session.execute(UpdateConnector(ElementId("r")) { it })
        assertEquals(rel.semantics, (json.decodeElement(json.encodeElement(session.scene[ElementId("r")]!!)) as Connector).semantics)
        val text = PlantUmlExporter.export(session.scene)
        assertTrue(text.startsWith("@startuml"))
        assertTrue(text.contains("class \"Order\" as Order {"))
        assertTrue(text.contains("{static} - total: Decimal"))
        assertTrue(text.contains("enum \"Enum\" as Enum"))
        assertTrue(text.contains("abstract class"))
        assertTrue(text.contains("Order \"1\" o-- \"*\" Enum : has"))
        assertTrue(text.contains("..|>"))
    }

    @Test
    fun `all shapes have outlines and ports where expected`() {
        UmlShapes.all.forEach { assertTrue(it.outline.paths.isNotEmpty(), it.displayName) }
        assertTrue(UmlShapes.Lifeline.ports.size > 10)
        assertTrue(UmlShapes.Package.isContainer && UmlShapes.Fragment.isContainer && UmlShapes.SystemBoundary.isContainer)
        assertTrue(UmlNotation.isClassifier(node("q", UmlShapes.Interface)))
    }
}
