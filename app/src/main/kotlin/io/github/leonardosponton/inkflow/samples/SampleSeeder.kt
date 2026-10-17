@file:Suppress("MaxLineLength", "ktlint:standard:max-line-length", "LongMethod", "MagicNumber")

package io.github.leonardosponton.inkflow.samples

import io.github.leonardosponton.inkflow.core.model.DiagramProfile
import io.github.leonardosponton.inkflow.core.model.ElementId
import io.github.leonardosponton.inkflow.core.model.IdGenerator
import io.github.leonardosponton.inkflow.core.model.ProjectId
import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.core.model.Size
import io.github.leonardosponton.inkflow.core.model.TabId
import io.github.leonardosponton.inkflow.core.model.Vec2
import io.github.leonardosponton.inkflow.domain.canvas.command.AddElements
import io.github.leonardosponton.inkflow.domain.canvas.element.CanvasElement
import io.github.leonardosponton.inkflow.domain.canvas.element.Connector
import io.github.leonardosponton.inkflow.domain.canvas.element.Endpoint
import io.github.leonardosponton.inkflow.domain.canvas.element.InkBrush
import io.github.leonardosponton.inkflow.domain.canvas.element.InkStroke
import io.github.leonardosponton.inkflow.domain.canvas.element.ShapeNode
import io.github.leonardosponton.inkflow.domain.canvas.element.StrokePoints
import io.github.leonardosponton.inkflow.domain.canvas.notation.NotationCatalog
import io.github.leonardosponton.inkflow.domain.canvas.repository.SceneRepository
import io.github.leonardosponton.inkflow.domain.canvas.scene.Scene
import io.github.leonardosponton.inkflow.domain.canvas.scene.SceneChange
import io.github.leonardosponton.inkflow.domain.canvas.session.EditorSession
import io.github.leonardosponton.inkflow.domain.dbmodel.ChenLink
import io.github.leonardosponton.inkflow.domain.dbmodel.ColumnRef
import io.github.leonardosponton.inkflow.domain.dbmodel.DbColumn
import io.github.leonardosponton.inkflow.domain.dbmodel.DbIndex
import io.github.leonardosponton.inkflow.domain.dbmodel.DbNotation
import io.github.leonardosponton.inkflow.domain.dbmodel.DbRelationship
import io.github.leonardosponton.inkflow.domain.dbmodel.DbShapes
import io.github.leonardosponton.inkflow.domain.dbmodel.DbTableContent
import io.github.leonardosponton.inkflow.domain.diagram.BasicShapes
import io.github.leonardosponton.inkflow.domain.diagram.NodeContent
import io.github.leonardosponton.inkflow.domain.diagram.PlainLabel
import io.github.leonardosponton.inkflow.domain.diagram.ShapeDefinition
import io.github.leonardosponton.inkflow.domain.flowchart.FlowchartSymbols
import io.github.leonardosponton.inkflow.domain.project.ProjectRepository
import io.github.leonardosponton.inkflow.domain.project.TabRepository
import io.github.leonardosponton.inkflow.domain.uml.ClassifierKind
import io.github.leonardosponton.inkflow.domain.uml.UmlClassContent
import io.github.leonardosponton.inkflow.domain.uml.UmlMember
import io.github.leonardosponton.inkflow.domain.uml.UmlRelation
import io.github.leonardosponton.inkflow.domain.uml.UmlRelationKind
import io.github.leonardosponton.inkflow.domain.uml.UmlShapes
import io.github.leonardosponton.inkflow.domain.uml.Visibility
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import kotlin.math.sin

/**
 * Creates demo projects (debug and benchmark builds only) for manual checks, screenshots and
 * the large-scene benchmark: `adb shell am start -n <pkg>/.MainActivity --es seed all`.
 */
class SampleSeeder
@Inject
constructor(
    private val projects: ProjectRepository,
    private val tabs: TabRepository,
    private val scenes: SceneRepository,
    private val catalog: NotationCatalog,
    private val ids: IdGenerator,
) {
    suspend fun seed(kind: String) {
        if (kind == "all" || kind == "flowchart") flowchart()
        if (kind == "all" || kind == "uml") uml()
        if (kind == "all" || kind == "db") database()
        if (kind == "all" || kind == "library") library()
        if (kind == "all" || kind == "shapes") allShapes()
        if (kind == "all" || kind == "stress") stress(STRESS_COUNT)
    }

    // ---- helpers --------------------------------------------------------------------------------

    private inner class Builder {
        val session = EditorSession(catalog, ids)
        private var n = 0

        fun id(prefix: String = "e") = ElementId("$prefix-${ids.next()}-${n++}")

        fun node(def: ShapeDefinition, x: Double, y: Double, content: NodeContent? = null, size: Size? = null): ShapeNode {
            val c = content ?: def.createContent()
            val preferred = catalog.layouts.layouterFor(c).preferredSize(c, def, def.defaultStyle, session.textMetrics)
            val s =
                size
                    ?: Size(
                        maxOf(def.defaultSize.width, preferred?.width ?: 0.0),
                        maxOf(
                            def.defaultSize.height,
                            preferred?.height ?: 0.0,
                        ),
                    )
            val node = ShapeNode(id(), def.key, Rect.fromOriginSize(Vec2(x, y), s), c, def.defaultStyle)
            session.execute(AddElements(listOf(node)))
            return node
        }

        fun label(def: ShapeDefinition, x: Double, y: Double, text: String) = node(def, x, y, PlainLabel(text))

        fun connect(a: ShapeNode, b: ShapeNode, edit: (Connector) -> Connector = { it }): Connector {
            val base = Connector(id(), Endpoint.Attached(a.id), Endpoint.Attached(b.id))
            val c = edit(catalog.decorateNewConnector(base, session.scene))
            session.execute(AddElements(listOf(c)))
            return c
        }

        val elements: List<CanvasElement> get() = session.scene.ordered
    }

    private suspend fun save(tab: TabId, elements: List<CanvasElement>) =
        scenes.applyChange(tab, SceneChange.of(Scene.Empty, elements))

    private suspend fun newProject(name: String, profile: DiagramProfile, firstTab: String): Pair<ProjectId, TabId> {
        val p = projects.createProject(name, firstTab, profile)
        val first = requireNotNull(projectTabs(p).firstOrNull())
        return p to first
    }

    private suspend fun projectTabs(p: ProjectId): List<TabId> = projects.observeProject(p).first()?.tabs?.map { it.id }.orEmpty()

    // ---- samples --------------------------------------------------------------------------------

    private suspend fun flowchart() {
        val (_, tab) = newProject("Sample · Login flowchart", DiagramProfile.FLOWCHART, "Login")
        val b = Builder()
        val f = FlowchartSymbols
        val start = b.label(f.Terminator, 300.0, 0.0, "Start")
        val input = b.label(f.Data, 300.0, 140.0, "Read e-mail\nand password")
        val check = b.label(f.Process, 300.0, 280.0, "Validate credentials")
        val ok = b.label(f.Decision, 300.0, 420.0, "Valid?")
        val grant = b.label(f.Process, 300.0, 600.0, "Create session")
        val error = b.label(f.Display, 640.0, 430.0, "Show error")
        val tries = b.label(f.Decision, 640.0, 600.0, "3 failed\nattempts?")
        val lock = b.label(f.PredefinedProcess, 980.0, 610.0, "Lock account")
        val end = b.label(f.Terminator, 300.0, 760.0, "End")
        val db = b.label(f.Database, 0.0, 250.0, "Users")
        b.connect(start, input)
        b.connect(input, check)
        b.connect(check, ok)
        b.connect(ok, grant)
        b.connect(ok, error)
        b.connect(error, tries)
        b.connect(tries, lock)
        b.connect(tries, input)
        b.connect(grant, end)
        b.connect(lock, end)
        b.connect(db, check)
        b.label(f.Annotation, 1000.0, 300.0, "Passwords are checked\nagainst a salted hash")
        save(tab, b.elements)
    }

    private suspend fun uml() {
        val (p, tab) = newProject("Sample · Banking (UML)", DiagramProfile.UML, "Classes")
        val b = Builder()
        val u = UmlShapes
        val auditable = b.node(
            u.Interface,
            40.0,
            0.0,
            UmlClassContent(
                "Auditable",
                ClassifierKind.INTERFACE,
                operations = listOf(UmlMember("audit", "Report", parameters = "", isAbstract = true)),
            ),
        )
        val account = b.node(
            u.AbstractClass,
            400.0,
            0.0,
            UmlClassContent(
                "Account",
                ClassifierKind.ABSTRACT_CLASS,
                attributes = listOf(
                    UmlMember("number", "String", Visibility.PRIVATE),
                    UmlMember("balance", "Decimal", Visibility.PROTECTED),
                    UmlMember("count", "Int", isStatic = true),
                ),
                operations = listOf(
                    UmlMember("deposit", "void", parameters = "amount: Decimal"),
                    UmlMember("withdraw", "Boolean", parameters = "amount: Decimal", isAbstract = true),
                ),
            ),
        )
        val savings = b.node(
            u.Class,
            280.0,
            340.0,
            UmlClassContent(
                "SavingsAccount",
                attributes = listOf(UmlMember("rate", "Double", Visibility.PRIVATE)),
                operations = listOf(UmlMember("withdraw", "Boolean", parameters = "amount: Decimal")),
            ),
        )
        val checking = b.node(
            u.Class,
            620.0,
            340.0,
            UmlClassContent("CheckingAccount", attributes = listOf(UmlMember("overdraft", "Decimal", Visibility.PRIVATE))),
        )
        val customer = b.node(
            u.Class,
            900.0,
            0.0,
            UmlClassContent(
                "Customer",
                attributes = listOf(UmlMember("name", "String", Visibility.PRIVATE), UmlMember("email", "String", Visibility.PRIVATE)),
            ),
        )
        val tx = b.node(
            u.Class,
            400.0,
            600.0,
            UmlClassContent(
                "Transaction",
                attributes = listOf(UmlMember("amount", "Decimal", Visibility.PRIVATE), UmlMember("at", "Instant", Visibility.PRIVATE)),
            ),
        )
        val type = b.node(
            u.Enumeration,
            800.0,
            600.0,
            UmlClassContent("TxType", ClassifierKind.ENUMERATION, literals = listOf("DEPOSIT", "WITHDRAWAL", "TRANSFER")),
        )
        val rel = {
                k: UmlRelationKind,
                sm: String,
                tm: String,
            ->
            { c: Connector -> UmlRelation.applyTo(c, UmlRelation(k, sourceMultiplicity = sm, targetMultiplicity = tm)) }
        }
        b.connect(savings, account, rel(UmlRelationKind.GENERALIZATION, "", ""))
        b.connect(checking, account, rel(UmlRelationKind.GENERALIZATION, "", ""))
        b.connect(account, auditable, rel(UmlRelationKind.REALIZATION, "", ""))
        b.connect(customer, account, rel(UmlRelationKind.AGGREGATION, "1", "1..*"))
        b.connect(account, tx, rel(UmlRelationKind.COMPOSITION, "1", "0..*"))
        b.connect(tx, type, rel(UmlRelationKind.DIRECTED_ASSOCIATION, "", "1"))
        save(tab, b.elements)

        val seqTab = tabs.addTab(p, "Sequence", DiagramProfile.UML)
        val s = Builder()
        val actor = s.node(u.ActorLifeline, 0.0, 0.0, PlainLabel("Client"))
        val app = s.node(u.Lifeline, 260.0, 0.0, PlainLabel(":App"))
        val bank = s.node(u.Lifeline, 520.0, 0.0, PlainLabel(":Bank"))
        s.session.execute(
            AddElements(
                listOf(
                    Connector(s.id(), Endpoint.Attached(actor.id, "le3"), Endpoint.Attached(app.id, "lw3")).let {
                        UmlRelation.applyTo(it, UmlRelation(UmlRelationKind.SYNC_MESSAGE, name = "transfer(100)"))
                    },
                ),
            ),
        )
        s.session.execute(
            AddElements(
                listOf(
                    Connector(s.id(), Endpoint.Attached(app.id, "le5"), Endpoint.Attached(bank.id, "lw5")).let {
                        UmlRelation.applyTo(it, UmlRelation(UmlRelationKind.SYNC_MESSAGE, name = "debit()"))
                    },
                ),
            ),
        )
        s.session.execute(
            AddElements(
                listOf(
                    Connector(s.id(), Endpoint.Attached(bank.id, "lw7"), Endpoint.Attached(app.id, "le7")).let {
                        UmlRelation.applyTo(it, UmlRelation(UmlRelationKind.RETURN_MESSAGE, name = "ok"))
                    },
                ),
            ),
        )
        s.node(u.Fragment, 200.0, 280.0, PlainLabel("alt [insufficient funds]"), Size(420.0, 120.0))
        save(seqTab, s.elements)

        val stateTab = tabs.addTab(p, "States", DiagramProfile.UML)
        val st = Builder()
        val init = st.node(u.InitialState, 0.0, 60.0)
        val open = st.node(u.State, 120.0, 40.0, PlainLabel("Open"))
        val frozen = st.node(u.State, 420.0, 40.0, PlainLabel("Frozen"))
        val closed = st.node(u.State, 270.0, 240.0, PlainLabel("Closed"))
        val fin = st.node(u.FinalState, 300.0, 420.0)
        st.connect(init, open)
        st.connect(open, frozen) { UmlRelation.applyTo(it, UmlRelation(UmlRelationKind.TRANSITION, event = "freeze", guard = "fraud")) }
        st.connect(frozen, open) { UmlRelation.applyTo(it, UmlRelation(UmlRelationKind.TRANSITION, event = "unfreeze")) }
        st.connect(open, closed) {
            UmlRelation.applyTo(it, UmlRelation(UmlRelationKind.TRANSITION, event = "close", action = "archive()"))
        }
        st.connect(closed, fin)
        save(stateTab, st.elements)
    }

    private suspend fun database() {
        val (p, tab) = newProject("Sample · Shop database", DiagramProfile.DATABASE, "Physical")
        val b = Builder()
        val customers = b.node(
            DbShapes.Table,
            0.0,
            0.0,
            DbTableContent(
                "customers",
                listOf(
                    DbColumn("cid", "id", "serial", primaryKey = true),
                    DbColumn("cemail", "email", "varchar(200)", notNull = true, unique = true),
                    DbColumn("cname", "name", "varchar(120)"),
                ),
            ),
        )
        val products = b.node(
            DbShapes.Table,
            700.0,
            0.0,
            DbTableContent(
                "products",
                listOf(
                    DbColumn("pid", "id", "serial", primaryKey = true),
                    DbColumn("pname", "name", "varchar(120)", notNull = true),
                    DbColumn("pprice", "price", "decimal(10,2)", notNull = true, defaultValue = "0"),
                ),
            ),
        )
        val orders = b.node(
            DbShapes.Table,
            0.0,
            320.0,
            DbTableContent(
                "orders",
                listOf(
                    DbColumn("oid", "id", "serial", primaryKey = true),
                    DbColumn("ocust", "customer_id", "integer", notNull = true, references = ColumnRef(customers.id.value, "cid")),
                    DbColumn("odate", "created_at", "timestamp", notNull = true),
                ),
            ),
        )
        val items = b.node(
            DbShapes.Table,
            700.0,
            320.0,
            DbTableContent(
                "order_items",
                listOf(
                    DbColumn("iord", "order_id", "integer", primaryKey = true, references = ColumnRef(orders.id.value, "oid")),
                    DbColumn("iprod", "product_id", "integer", primaryKey = true, references = ColumnRef(products.id.value, "pid")),
                    DbColumn("iqty", "quantity", "integer", notNull = true, defaultValue = "1"),
                ),
            ),
        )
        b.connect(orders, customers) { DbNotation.applyRelationship(it, DbRelationship(name = "places", fkColumnId = "ocust")) }
        b.connect(items, orders) { DbNotation.applyRelationship(it, DbRelationship(identifying = true, fkColumnId = "iord")) }
        b.connect(items, products) { DbNotation.applyRelationship(it, DbRelationship(fkColumnId = "iprod")) }
        save(tab, b.elements)

        val chenTab = tabs.addTab(p, "Conceptual", DiagramProfile.DATABASE)
        val c = Builder()
        val cust = c.label(DbShapes.Entity, 0.0, 200.0, "Customer")
        val order = c.label(DbShapes.Entity, 520.0, 200.0, "Order")
        val places = c.label(DbShapes.Relationship, 250.0, 190.0, "places")
        val key = c.label(DbShapes.KeyAttribute, -40.0, 40.0, "id")
        val phones = c.label(DbShapes.MultivaluedAttribute, 140.0, 40.0, "phones")
        val total = c.label(DbShapes.DerivedAttribute, 560.0, 40.0, "total")
        val line = c.label(DbShapes.WeakEntity, 520.0, 400.0, "OrderLine")
        val has = c.label(DbShapes.IdentifyingRelationship, 520.0, 300.0, "has")
        c.connect(cust, places) { DbNotation.applyChen(it, ChenLink("1")) }
        c.connect(places, order) { DbNotation.applyChen(it, ChenLink("N", totalParticipation = true)) }
        c.connect(key, cust)
        c.connect(phones, cust)
        c.connect(total, order)
        c.connect(order, has)
        c.connect(has, line)
        save(chenTab, c.elements)
    }

    /** Small library ER model: Chen conceptual tab plus the Crow's Foot tables it maps to. */
    private suspend fun library() {
        val (p, tab) = newProject("Example · Library ER model", DiagramProfile.DATABASE, "Conceptual")
        val c = Builder()
        val author = c.label(DbShapes.Entity, 0.0, 258.0, "Author")
        val writes = c.label(DbShapes.Relationship, 260.0, 250.0, "writes")
        val book = c.label(DbShapes.Entity, 520.0, 258.0, "Book")
        val borrows = c.label(DbShapes.Relationship, 780.0, 250.0, "borrows")
        val member = c.label(DbShapes.Entity, 1040.0, 258.0, "Member")
        c.connect(author, writes) { DbNotation.applyChen(it, ChenLink("M")) }
        c.connect(writes, book) { DbNotation.applyChen(it, ChenLink("N", totalParticipation = true)) }
        c.connect(book, borrows) { DbNotation.applyChen(it, ChenLink("N")) }
        c.connect(borrows, member) { DbNotation.applyChen(it, ChenLink("M")) }
        listOf(
            Triple(DbShapes.KeyAttribute, -80.0, "id") to author,
            Triple(DbShapes.Attribute, 100.0, "name") to author,
            Triple(DbShapes.KeyAttribute, 440.0, "isbn") to book,
            Triple(DbShapes.Attribute, 600.0, "title") to book,
            Triple(DbShapes.KeyAttribute, 960.0, "id") to member,
            Triple(DbShapes.Attribute, 1120.0, "email") to member,
        ).forEach { (a, owner) -> c.connect(c.label(a.first, a.second, 120.0, a.third), owner) }
        c.connect(c.label(DbShapes.Attribute, 790.0, 420.0, "due_date"), borrows)
        save(tab, c.elements)

        val tablesTab = tabs.addTab(p, "Tables", DiagramProfile.DATABASE)
        val t = Builder()
        val authors = t.node(
            DbShapes.Table,
            0.0,
            0.0,
            DbTableContent(
                "authors",
                listOf(
                    DbColumn("aid", "id", "serial", primaryKey = true),
                    DbColumn("aname", "name", "varchar(120)", notNull = true),
                ),
            ),
        )
        val books = t.node(
            DbShapes.Table,
            1120.0,
            0.0,
            DbTableContent(
                "books",
                listOf(
                    DbColumn("bid", "id", "serial", primaryKey = true),
                    DbColumn("bisbn", "isbn", "char(13)", notNull = true, unique = true),
                    DbColumn("btitle", "title", "varchar(200)", notNull = true),
                    DbColumn("byear", "published_year", "integer"),
                ),
            ),
        )
        val bookAuthors = t.node(
            DbShapes.Table,
            560.0,
            0.0,
            DbTableContent(
                "book_authors",
                listOf(
                    DbColumn("babook", "book_id", "integer", primaryKey = true, references = ColumnRef(books.id.value, "bid")),
                    DbColumn("baauthor", "author_id", "integer", primaryKey = true, references = ColumnRef(authors.id.value, "aid")),
                ),
            ),
        )
        val members = t.node(
            DbShapes.Table,
            560.0,
            420.0,
            DbTableContent(
                "members",
                listOf(
                    DbColumn("mid", "id", "serial", primaryKey = true),
                    DbColumn("mname", "name", "varchar(120)", notNull = true),
                    DbColumn("memail", "email", "varchar(200)", notNull = true, unique = true),
                ),
            ),
        )
        val loans = t.node(
            DbShapes.Table,
            1120.0,
            420.0,
            DbTableContent(
                "loans",
                listOf(
                    DbColumn("lid", "id", "serial", primaryKey = true),
                    DbColumn("lbook", "book_id", "integer", notNull = true, references = ColumnRef(books.id.value, "bid")),
                    DbColumn("lmember", "member_id", "integer", notNull = true, references = ColumnRef(members.id.value, "mid")),
                    DbColumn("lout", "loaned_at", "timestamp", notNull = true, defaultValue = "CURRENT_TIMESTAMP"),
                    DbColumn("ldue", "due_date", "date", notNull = true),
                    DbColumn("lback", "returned_at", "timestamp"),
                ),
                indexes = listOf(DbIndex("loans_member_idx", listOf("lmember"))),
            ),
        )
        t.connect(bookAuthors, authors) { DbNotation.applyRelationship(it, DbRelationship(identifying = true, fkColumnId = "baauthor")) }
        t.connect(bookAuthors, books) { DbNotation.applyRelationship(it, DbRelationship(identifying = true, fkColumnId = "babook")) }
        t.connect(loans, books) { DbNotation.applyRelationship(it, DbRelationship(fkColumnId = "lbook")) }
        t.connect(loans, members) { DbNotation.applyRelationship(it, DbRelationship(name = "borrows", fkColumnId = "lmember")) }
        save(tablesTab, t.elements)
    }

    private suspend fun allShapes() {
        val (_, tab) = newProject("Sample · All shapes", DiagramProfile.FREEFORM, "Catalog")
        val b = Builder()
        var y = 0.0
        catalog.modules.forEach { module ->
            b.label(BasicShapes.Text, 0.0, y, module.displayName)
            y += 60.0
            var x = 0.0
            var rowH = 0.0
            module.shapes.forEach { def ->
                val size = Size(minOf(def.defaultSize.width, MAX_CELL), minOf(def.defaultSize.height, MAX_CELL))
                if (x + size.width > ROW_WIDTH) {
                    x = 0.0
                    y += rowH + GAP
                    rowH = 0.0
                }
                val content =
                    (def.createContent() as? PlainLabel)?.let { PlainLabel(it.text.ifBlank { def.displayName }) } ?: def.createContent()
                b.node(def, x, y, content, size.takeIf { def.createContent() is PlainLabel })
                x += size.width + GAP
                rowH = maxOf(rowH, size.height)
            }
            y += rowH + GAP * 3
        }
        save(tab, b.elements)
    }

    /** [count] labelled shapes plus pen strokes for the 5 000-element performance target. */
    private suspend fun stress(count: Int) {
        val (_, tab) = newProject("Stress · $count elements", DiagramProfile.FREEFORM, "Big board")
        val elements = ArrayList<CanvasElement>(count)
        val cols = COLUMNS
        val defs = listOf(BasicShapes.Rectangle, BasicShapes.Ellipse, BasicShapes.Diamond, BasicShapes.RoundedRectangle)
        repeat(count) { i ->
            val x = (i % cols) * CELL
            val y = (i / cols) * CELL
            if (i % INK_EVERY == 0) {
                val pts = StrokePoints.Builder()
                for (k in 0 until INK_POINTS) pts.add(k * 4f, (sin(k / 4.0) * 20).toFloat(), k * 8f, 0.6f)
                elements += InkStroke(ElementId("s$i"), Vec2(x, y + CELL / 2), InkBrush(size = 3f), pts.build(), zIndex = i.toLong())
            } else {
                val def = defs[i % defs.size]
                elements +=
                    ShapeNode(
                        ElementId("s$i"),
                        def.key,
                        Rect(x, y, x + CELL - GAP, y + CELL - GAP * 2),
                        PlainLabel("#$i"),
                        zIndex = i.toLong(),
                    )
            }
        }
        save(tab, elements)
    }

    private companion object {
        const val STRESS_COUNT = 5000
        const val COLUMNS = 80
        const val CELL = 140.0
        const val GAP = 30.0
        const val INK_EVERY = 5
        const val INK_POINTS = 30
        const val MAX_CELL = 220.0
        const val ROW_WIDTH = 1800.0
    }
}
