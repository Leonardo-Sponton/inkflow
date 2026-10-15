package io.github.leonardosponton.inkflow.domain.dbmodel

import io.github.leonardosponton.inkflow.core.model.DashPattern
import io.github.leonardosponton.inkflow.core.model.ElementId
import io.github.leonardosponton.inkflow.core.model.IdGenerator
import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.core.model.ShapeStyle
import io.github.leonardosponton.inkflow.core.model.Vec2
import io.github.leonardosponton.inkflow.domain.canvas.command.AddElements
import io.github.leonardosponton.inkflow.domain.canvas.element.Connector
import io.github.leonardosponton.inkflow.domain.canvas.element.Endpoint
import io.github.leonardosponton.inkflow.domain.canvas.element.LabelPosition
import io.github.leonardosponton.inkflow.domain.canvas.element.ShapeNode
import io.github.leonardosponton.inkflow.domain.canvas.notation.NotationCatalog
import io.github.leonardosponton.inkflow.domain.canvas.notation.Severity
import io.github.leonardosponton.inkflow.domain.canvas.serialization.SceneJson
import io.github.leonardosponton.inkflow.domain.canvas.session.EditorSession
import io.github.leonardosponton.inkflow.domain.diagram.ApproximateTextMetrics
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DbModelTest {
    private var n = 0
    private val catalog = NotationCatalog(listOf(DbNotation))
    private val session = EditorSession(catalog, IdGenerator { "g${++n}" })

    private val customers = DbTableContent(
        "customers",
        listOf(
            DbColumn("c1", "id", "serial", primaryKey = true),
            DbColumn("c2", "email", "varchar(200)", notNull = true, unique = true, comment = "login e-mail"),
            DbColumn("c3", "active", "boolean", defaultValue = "true"),
        ),
        indexes = listOf(DbIndex("idx_customers_email", listOf("c2"), unique = true)),
        comment = "People who buy",
    )
    private val orders = DbTableContent(
        "orders",
        listOf(
            DbColumn("o1", "id", "bigint", primaryKey = true),
            DbColumn("o2", "total", "decimal(10,2)", notNull = true, defaultValue = "0"),
            DbColumn("o3", "created_at", "timestamp"),
            DbColumn("o4", "ref", "uuid"),
        ),
    )

    private fun table(id: String, t: DbTableContent, x: Double = 0.0) =
        ShapeNode(ElementId(id), DbShapes.Table.key, Rect.fromOriginSize(Vec2(x, 0.0), DbShapes.Table.defaultSize), t)

    private fun edge(id: String, from: String, to: String) = Connector(ElementId(id), Endpoint.Attached(ElementId(from)), Endpoint.Attached(ElementId(to)))

    @Test
    fun `connecting tables becomes a crows foot relationship and offers the fk column`() {
        session.execute(AddElements(listOf(table("cust", customers), table("ord", orders, 400.0))))
        val c = catalog.decorateNewConnector(edge("r", "ord", "cust"), session.scene)
        val rel = c.semantics as DbRelationship
        assertEquals(CrowsFoot.key(Cardinality.ZERO_OR_MANY), c.startMarker)
        assertEquals(CrowsFoot.key(Cardinality.ONE_AND_ONLY_ONE), c.endMarker)
        assertEquals(DashPattern.DASHED, c.style.dash)
        session.execute(AddElements(listOf(c)))
        val follow = catalog.followUpFor(session.scene[ElementId("r")] as Connector, session.scene)
        assertNotNull(follow)
        assertTrue(follow!!.message.contains("orders.customer_id"))
        session.execute(follow.command)
        val updated = (session.scene[ElementId("ord")] as ShapeNode).content as DbTableContent
        val fk = updated.columnNamed("customer_id")!!
        assertEquals("integer", fk.type)
        assertEquals(ColumnRef("cust", "c1"), fk.references)
        assertEquals(fk.id, ((session.scene[ElementId("r")] as Connector).semantics as DbRelationship).fkColumnId)
        assertNull(catalog.followUpFor(session.scene[ElementId("r")] as Connector, session.scene))
        assertTrue(catalog.validate(session.scene).none { it.severity == Severity.ERROR })
        assertFalse(rel.identifying)
    }

    @Test
    fun `chen links and identifying relationships`() {
        val e = ShapeNode(ElementId("e"), DbShapes.Entity.key, Rect(0.0, 0.0, 100.0, 50.0), DbShapes.Entity.createContent())
        val r = ShapeNode(ElementId("r"), DbShapes.Relationship.key, Rect(300.0, 0.0, 400.0, 50.0), DbShapes.Relationship.createContent())
        session.execute(AddElements(listOf(e, r)))
        val link = catalog.decorateNewConnector(edge("l", "e", "r"), session.scene)
        assertTrue(link.semantics is ChenLink)
        val total = DbNotation.applyChen(link, ChenLink("N", totalParticipation = true))
        assertEquals("N", total.label(LabelPosition.MIDDLE))
        assertEquals(4.0, total.style.strokeWidth)
        val ident = DbNotation.applyRelationship(edge("x", "a", "b"), DbRelationship(identifying = true, name = "places"))
        assertEquals(DashPattern.SOLID, ident.style.dash)
        assertEquals("places", ident.label(LabelPosition.MIDDLE))
        assertEquals(6, CrowsFoot.all.size)
        assertTrue(DbShapes.KeyAttribute.labelUnderline)
    }

    @Test
    fun `validators report keys duplicates broken fks and type mismatches`() {
        val noPk = DbTableContent("logs", listOf(DbColumn("l1", "msg", "text"), DbColumn("l2", "msg", "text")))
        val badFk = orders.copy(columns = orders.columns + DbColumn("o9", "customer_id", "varchar", references = ColumnRef("cust", "c1")))
        val dangling = DbTableContent("items", listOf(DbColumn("i1", "id", "int", primaryKey = true), DbColumn("i2", "order_id", "bigint", references = ColumnRef("nope", "x"))))
        session.execute(AddElements(listOf(table("cust", customers), table("ord", badFk, 300.0), table("logs", noPk, 600.0), table("items", dangling, 900.0), table("dup", DbTableContent("orders"), 1200.0))))
        val messages = catalog.validate(session.scene).map { it.message }
        assertTrue(messages.any { it.contains("no primary key") })
        assertTrue(messages.any { it.contains("duplicate column") })
        assertTrue(messages.any { it.startsWith("Type mismatch") })
        assertTrue(messages.any { it.contains("missing column") })
        assertTrue(messages.any { it.startsWith("Duplicate table") })
        assertTrue(messages.any { it.contains("no columns") })
        assertTrue(DbValidators.sameType("int", "serial"))
    }

    @Test
    fun `ddl per dialect`() {
        val linked = orders.copy(columns = orders.columns + DbColumn("o5", "customer_id", "integer", notNull = true, references = ColumnRef("cust", "c1")))
        val tables = mapOf("ord" to linked, "cust" to customers)
        val pg = DdlGenerator(SqlDialect.POSTGRESQL).generate(tables)
        assertTrue(pg.indexOf("CREATE TABLE \"customers\"") < pg.indexOf("CREATE TABLE \"orders\""), "parents first")
        assertTrue(pg.contains("\"id\" SERIAL PRIMARY KEY"))
        assertTrue(pg.contains("\"email\" VARCHAR(200) NOT NULL UNIQUE"))
        assertTrue(pg.contains("\"active\" BOOLEAN DEFAULT true"))
        assertTrue(pg.contains("\"ref\" UUID"))
        assertTrue(pg.contains("FOREIGN KEY (\"customer_id\") REFERENCES \"customers\" (\"id\")"))
        assertTrue(pg.contains("CREATE UNIQUE INDEX \"idx_customers_email\" ON \"customers\" (\"email\");"))
        assertTrue(pg.contains("COMMENT ON COLUMN \"customers\".\"email\" IS 'login e-mail';"))
        val my = DdlGenerator(SqlDialect.MYSQL).generate(tables)
        assertTrue(my.contains("`id` INT AUTO_INCREMENT PRIMARY KEY"))
        assertTrue(my.contains("`active` TINYINT(1) DEFAULT 1"))
        assertTrue(my.contains("COMMENT 'login e-mail'"))
        assertTrue(my.contains(") COMMENT='People who buy';"))
        assertTrue(my.contains("`created_at` DATETIME"))
        val lite = DdlGenerator(SqlDialect.SQLITE).generate(tables)
        assertTrue(lite.startsWith("-- Generated by Inkflow (SQLite)\nPRAGMA foreign_keys = ON;"))
        assertTrue(lite.contains("\"total\" NUMERIC NOT NULL DEFAULT 0"))
        assertTrue(lite.contains("-- customers.email: login e-mail"))
        val composite = DbTableContent("pairs", listOf(DbColumn("a", "a", "int", primaryKey = true), DbColumn("b", "b", "int", primaryKey = true)))
        assertTrue(DdlGenerator(SqlDialect.SQLITE).generate(mapOf("p" to composite)).contains("PRIMARY KEY (\"a\", \"b\")"))
        assertEquals("JSONB", SqlDialect.POSTGRESQL.type("json"))
        assertEquals("BLOB", SqlDialect.MYSQL.type("bytes"))
        assertEquals("GEOMETRY", SqlDialect.SQLITE.type("geometry"))
        assertEquals("`a``b`", SqlDialect.MYSQL.quote("a`b"))
    }

    @Test
    fun `ddl from a scene and mermaid export`() {
        session.execute(AddElements(listOf(table("cust", customers), table("ord", orders, 400.0))))
        val c = DbNotation.applyRelationship(edge("r", "ord", "cust"), DbRelationship(name = "places"))
        session.execute(AddElements(listOf(c)))
        assertTrue(DdlGenerator(SqlDialect.POSTGRESQL).generate(session.scene).contains("CREATE TABLE \"orders\""))
        val mermaid = MermaidExporter.export(session.scene)
        assertTrue(mermaid.startsWith("erDiagram"))
        assertTrue(mermaid.contains("customers ||..o{ orders : \"places\""))
        assertTrue(mermaid.contains("serial id PK"))
    }

    @Test
    fun `ddl import round trips the generator output`() {
        val linked = orders.copy(columns = orders.columns + DbColumn("o5", "customer_id", "integer", notNull = true, references = ColumnRef("cust", "c1")))
        val sql = DdlGenerator(SqlDialect.POSTGRESQL).generate(mapOf("cust" to customers, "ord" to linked))
        val result = DdlImporter.parse(sql)
        assertEquals(0, result.skipped)
        val tables = result.tables.values.associateBy { it.name }
        assertEquals(setOf("customers", "orders"), tables.keys)
        assertTrue(tables.getValue("customers").columnNamed("id")!!.primaryKey)
        assertTrue(tables.getValue("customers").columnNamed("email")!!.unique)
        val fk = tables.getValue("orders").columnNamed("customer_id")!!
        val parentId = result.tables.entries.first { it.value.name == "customers" }.key
        assertEquals(parentId, fk.references!!.tableId)
        val mysql = """
            CREATE TABLE `a` (`x` int NOT NULL, `y` varchar(20) DEFAULT 'q', PRIMARY KEY (`x`)) ENGINE=InnoDB; -- c
            CREATE TABLE b (id int primary key, a_id int REFERENCES a(x), z int, FOREIGN KEY (z) REFERENCES missing(id), UNIQUE (z));
        """.trimIndent()
        val r2 = DdlImporter.parse(mysql)
        assertEquals(1, r2.skipped)
        val a = r2.tables.values.first { it.name == "a" }
        assertTrue(a.columnNamed("x")!!.primaryKey)
        assertEquals("'q'", a.columnNamed("y")!!.defaultValue)
        assertNotNull(r2.tables.values.first { it.name == "b" }.columnNamed("a_id")!!.references)
    }

    @Test
    fun `table layout serialization and naming`() {
        val size = TableLayouter.preferredSize(customers, DbShapes.Table, ShapeStyle.Default, ApproximateTextMetrics)
        val layout = TableLayouter.layout(customers, DbShapes.Table, Rect.fromOriginSize(Vec2.Zero, size), ShapeStyle.Default, ApproximateTextMetrics)
        assertEquals("customers", layout.texts.first().text)
        assertEquals(1 + 3 * customers.columns.size, layout.texts.size)
        assertEquals("PK", TableLayouter.badge(customers.columns[0]))
        assertEquals("varchar(200) NN UQ", TableLayouter.typeText(customers.columns[1]))
        val json = SceneJson(catalog.serializers)
        val node = table("t", customers)
        assertEquals(node, json.decodeElement(json.encodeElement(node)))
        val rel = DbNotation.applyRelationship(edge("r", "a", "b"), DbRelationship(Cardinality.ONE_OR_MANY, Cardinality.ZERO_OR_ONE, true))
        assertEquals(rel, json.decodeElement(json.encodeElement(rel)))
        val categories = DbTableContent("categories", listOf(DbColumn("k", "id", "int", primaryKey = true)))
        assertEquals("category_id", ForeignKeys.newColumn(DbTableContent("x"), categories, "c", categories.columns[0], "f").name)
        val taken = DbTableContent("x", listOf(DbColumn("q", "category_id", "int")))
        assertEquals("category_id_2", ForeignKeys.newColumn(taken, categories, "c", categories.columns[0], "f").name)
        assertEquals("bigint", ForeignKeys.fkType("bigserial"))
    }
}
