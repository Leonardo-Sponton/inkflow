package io.github.leonardosponton.inkflow.domain.dbmodel

import io.github.leonardosponton.inkflow.core.model.ElementId
import io.github.leonardosponton.inkflow.domain.canvas.element.ShapeNode
import io.github.leonardosponton.inkflow.domain.canvas.notation.DiagramValidator
import io.github.leonardosponton.inkflow.domain.canvas.notation.Severity
import io.github.leonardosponton.inkflow.domain.canvas.notation.ValidationIssue
import io.github.leonardosponton.inkflow.domain.canvas.scene.Scene

/** Checks on the physical model; they only warn, never block editing. */
object DbValidators {
    fun tables(scene: Scene): List<Pair<ShapeNode, DbTableContent>> =
        scene.ordered.filterIsInstance<ShapeNode>().mapNotNull { n -> (n.content as? DbTableContent)?.let { n to it } }

    private val MissingPrimaryKey = DiagramValidator { scene ->
        tables(scene).filter { it.second.primaryKey.isEmpty() }
            .map { (n, t) -> ValidationIssue(listOf(n.id), Severity.WARNING, "Table “${t.name}” has no primary key") }
    }

    private val DuplicateTables = DiagramValidator { scene ->
        tables(scene).groupBy { it.second.name.trim().lowercase() }
            .filter { (name, list) -> name.isNotEmpty() && list.size > 1 }
            .map { (_, list) ->
                ValidationIssue(list.map { it.first.id }, Severity.WARNING, "Duplicate table name “${list.first().second.name}”")
            }
    }

    private val DuplicateColumns = DiagramValidator { scene ->
        tables(scene).flatMap { (n, t) ->
            t.columns.groupBy { it.name.trim().lowercase() }.filter { it.value.size > 1 }
                .map { (name, _) -> ValidationIssue(listOf(n.id), Severity.ERROR, "Table “${t.name}” has duplicate column “$name”") }
        }
    }

    private val BrokenForeignKeys = DiagramValidator { scene ->
        val byId = tables(scene).associate { it.first.id.value to it }
        tables(scene).flatMap { (n, t) ->
            t.columns.mapNotNull { c ->
                val ref = c.references ?: return@mapNotNull null
                val target = byId[ref.tableId]
                val targetColumn = target?.second?.column(ref.columnId)
                when {
                    target == null || targetColumn == null ->
                        ValidationIssue(listOf(n.id), Severity.ERROR, "Foreign key “${t.name}.${c.name}” points to a missing column")
                    !sameType(c.type, targetColumn.type) -> ValidationIssue(
                        listOf(n.id, ElementId(ref.tableId)),
                        Severity.WARNING,
                        "Type mismatch: “${t.name}.${c.name}” (${c.type}) references " +
                            "“${target.second.name}.${targetColumn.name}” (${targetColumn.type})",
                    )
                    else -> null
                }
            }
        }
    }

    private val EmptyTables = DiagramValidator { scene ->
        tables(scene).filter { it.second.columns.isEmpty() }
            .map { (n, t) -> ValidationIssue(listOf(n.id), Severity.INFO, "Table “${t.name}” has no columns") }
    }

    fun sameType(a: String, b: String): Boolean = normalize(ForeignKeys.fkType(a)) == normalize(ForeignKeys.fkType(b))

    private fun normalize(t: String) = when (val base = t.trim().lowercase().substringBefore('(').trim()) {
        "int", "integer", "int4" -> "integer"
        "bigint", "int8", "long" -> "bigint"
        "varchar", "string", "character varying" -> "varchar"
        "bool", "boolean" -> "boolean"
        else -> base
    }

    val all: List<DiagramValidator> = listOf(MissingPrimaryKey, DuplicateTables, DuplicateColumns, BrokenForeignKeys, EmptyTables)
}
