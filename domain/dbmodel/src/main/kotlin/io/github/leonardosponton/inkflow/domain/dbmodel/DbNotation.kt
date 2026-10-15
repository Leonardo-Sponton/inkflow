package io.github.leonardosponton.inkflow.domain.dbmodel

import io.github.leonardosponton.inkflow.core.model.DashPattern
import io.github.leonardosponton.inkflow.domain.canvas.command.CompositeCommand
import io.github.leonardosponton.inkflow.domain.canvas.command.UpdateConnector
import io.github.leonardosponton.inkflow.domain.canvas.command.UpdateNodeContent
import io.github.leonardosponton.inkflow.domain.canvas.element.Connector
import io.github.leonardosponton.inkflow.domain.canvas.element.Endpoint
import io.github.leonardosponton.inkflow.domain.canvas.element.LabelPosition
import io.github.leonardosponton.inkflow.domain.canvas.element.Routing
import io.github.leonardosponton.inkflow.domain.canvas.element.ShapeNode
import io.github.leonardosponton.inkflow.domain.canvas.notation.FollowUp
import io.github.leonardosponton.inkflow.domain.canvas.notation.NotationModule
import io.github.leonardosponton.inkflow.domain.canvas.scene.Scene
import io.github.leonardosponton.inkflow.domain.diagram.EdgeSemantics
import io.github.leonardosponton.inkflow.domain.diagram.MarkerKey
import io.github.leonardosponton.inkflow.domain.diagram.NodeContent
import io.github.leonardosponton.inkflow.domain.diagram.PlainEdge
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

object DbNotation : NotationModule {
    override val id = DbShapes.Id
    override val displayName = "Database"
    override val shapes = DbShapes.all
    override val markers = CrowsFoot.all
    override val layouters = listOf(TableLayouter)
    override val serializers = SerializersModule {
        polymorphic(NodeContent::class) { subclass(DbTableContent::class) }
        polymorphic(EdgeSemantics::class) {
            subclass(DbRelationship::class)
            subclass(ChenLink::class)
        }
    }
    override val validators by lazy { DbValidators.all }

    fun table(e: Endpoint, scene: Scene): Pair<ShapeNode, DbTableContent>? {
        val node = (e as? Endpoint.Attached)?.let { scene[it.node] as? ShapeNode } ?: return null
        val content = node.content as? DbTableContent ?: return null
        return node to content
    }

    private fun isChen(e: Endpoint, scene: Scene) =
        (e as? Endpoint.Attached)?.let { (scene[it.node] as? ShapeNode)?.shape?.value?.startsWith("db.") } == true

    override fun decorateNewConnector(connector: Connector, scene: Scene): Connector = when {
        connector.semantics !is PlainEdge -> connector
        table(connector.source, scene) != null && table(connector.target, scene) != null -> applyRelationship(connector, DbRelationship())
        isChen(connector.source, scene) && isChen(connector.target, scene) ->
            connector.copy(semantics = ChenLink(), startMarker = MarkerKey.None, endMarker = MarkerKey.None, routing = Routing.STRAIGHT)
        else -> connector
    }

    fun applyRelationship(c: Connector, r: DbRelationship): Connector = c.copy(
        semantics = r,
        startMarker = CrowsFoot.key(r.sourceCardinality),
        endMarker = CrowsFoot.key(r.targetCardinality),
        style = c.style.copy(dash = if (r.identifying) DashPattern.SOLID else DashPattern.DASHED),
        routing = Routing.ORTHOGONAL,
    ).withLabel(LabelPosition.MIDDLE, r.name)

    fun applyChen(c: Connector, link: ChenLink): Connector = c.copy(
        semantics = link,
        style = c.style.copy(strokeWidth = if (link.totalParticipation) TOTAL_WIDTH else PARTIAL_WIDTH),
    ).withLabel(LabelPosition.MIDDLE, link.cardinality)

    /** Offer to create the foreign key column in the child (source) table. */
    override fun followUpFor(connector: Connector, scene: Scene): FollowUp? {
        val rel = connector.semantics as? DbRelationship ?: return null
        if (rel.fkColumnId != null) return null
        val (child, childTable) = table(connector.source, scene) ?: return null
        val (parent, parentTable) = table(connector.target, scene) ?: return null
        val pk = parentTable.primaryKey.firstOrNull() ?: return null
        val existing = childTable.columns.firstOrNull { it.references?.tableId == parent.id.value }
        if (existing != null) return null
        val column = ForeignKeys.newColumn(childTable, parentTable, parent.id.value, pk, "fk-${connector.id.value}")
        val command = CompositeCommand(
            listOf(
                UpdateNodeContent(child.id, childTable.copy(columns = childTable.columns + column)),
                UpdateConnector(connector.id) { applyRelationship(it, rel.copy(fkColumnId = column.id)) },
            ),
        )
        return FollowUp(
            "Add foreign key “${childTable.name}.${column.name}” referencing “${parentTable.name}.${pk.name}”?",
            "Add column",
            command,
        )
    }

    private const val TOTAL_WIDTH = 4.0
    private const val PARTIAL_WIDTH = 2.0
}

object ForeignKeys {
    /** `<parent>_<pk>` (e.g. `customer_id`), made unique within the child table. */
    fun newColumn(child: DbTableContent, parent: DbTableContent, parentId: String, pk: DbColumn, id: String): DbColumn {
        val base = "${singular(parent.name)}_${pk.name}".lowercase()
        var name = base
        var i = 2
        while (child.columnNamed(name) != null) name = "${base}_${i++}"
        return DbColumn(id, name, fkType(pk.type), notNull = true, references = ColumnRef(parentId, pk.id))
    }

    /** Auto-increment types become their plain integer counterpart in the referencing column. */
    fun fkType(type: String): String = when (type.trim().lowercase()) {
        "serial" -> "integer"
        "bigserial" -> "bigint"
        "smallserial" -> "smallint"
        else -> type
    }

    private fun singular(name: String): String = when {
        name.endsWith("ies", true) -> name.dropLast(3) + "y"
        name.endsWith("ses", true) -> name.dropLast(2)
        name.endsWith("s", true) && !name.endsWith("ss", true) -> name.dropLast(1)
        else -> name
    }
}
