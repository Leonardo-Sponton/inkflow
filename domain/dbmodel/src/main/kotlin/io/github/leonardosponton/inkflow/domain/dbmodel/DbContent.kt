package io.github.leonardosponton.inkflow.domain.dbmodel

import io.github.leonardosponton.inkflow.domain.diagram.EdgeSemantics
import io.github.leonardosponton.inkflow.domain.diagram.NodeContent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Reference from a foreign-key column to a column of another table node (ids are element/column ids). */
@Serializable
data class ColumnRef(val tableId: String, val columnId: String)

@Serializable
data class DbColumn(
    val id: String,
    val name: String,
    val type: String = "integer",
    val primaryKey: Boolean = false,
    val notNull: Boolean = false,
    val unique: Boolean = false,
    val defaultValue: String = "",
    val comment: String = "",
    val references: ColumnRef? = null,
) {
    val isForeignKey: Boolean get() = references != null
}

@Serializable
data class DbIndex(val name: String, val columnIds: List<String>, val unique: Boolean = false)

/** A physical table: typed columns, keys and indexes. */
@Serializable
@SerialName("db.table")
data class DbTableContent(
    val name: String = "table",
    val columns: List<DbColumn> = emptyList(),
    val indexes: List<DbIndex> = emptyList(),
    val comment: String = "",
) : NodeContent {
    override val summary: String get() = name

    val primaryKey: List<DbColumn> get() = columns.filter { it.primaryKey }

    fun column(id: String): DbColumn? = columns.firstOrNull { it.id == id }

    fun columnNamed(name: String): DbColumn? = columns.firstOrNull { it.name.equals(name, ignoreCase = true) }
}

/** Crow's foot end of a relationship. */
enum class Cardinality(val displayName: String, val mermaid: String) {
    ONE_AND_ONLY_ONE("One and only one", "||"),
    ZERO_OR_ONE("Zero or one", "|o"),
    ONE_OR_MANY("One or many", "}|"),
    ZERO_OR_MANY("Zero or many", "}o"),
    ONE("One", "||"),
    MANY("Many", "}o"),
}

/**
 * Relationship between two tables. By convention the source is the child (holding the foreign
 * key) and the target is the parent. Identifying relationships are drawn solid, others dashed.
 */
@Serializable
@SerialName("db.relationship")
data class DbRelationship(
    val sourceCardinality: Cardinality = Cardinality.ZERO_OR_MANY,
    val targetCardinality: Cardinality = Cardinality.ONE_AND_ONLY_ONE,
    val identifying: Boolean = false,
    val name: String = "",
    val fkColumnId: String? = null,
) : EdgeSemantics

/** Chen notation link label: cardinality ("1", "N", "M") and total participation. */
@Serializable
@SerialName("db.chen")
data class ChenLink(val cardinality: String = "1", val totalParticipation: Boolean = false) : EdgeSemantics
