package io.github.leonardosponton.inkflow.domain.dbmodel

import io.github.leonardosponton.inkflow.domain.canvas.element.Connector
import io.github.leonardosponton.inkflow.domain.canvas.scene.Scene

/** Offline text export of the physical model as a Mermaid `erDiagram`. */
object MermaidExporter {
    fun export(scene: Scene): String = buildString {
        appendLine("erDiagram")
        val tables = DbValidators.tables(scene)
        tables.forEach { (_, t) ->
            appendLine("    ${ident(t.name)} {")
            t.columns.forEach { c ->
                val keys = listOfNotNull(
                    "PK".takeIf {
                        c.primaryKey
                    },
                    "FK".takeIf { c.isForeignKey },
                    "UK".takeIf { c.unique },
                ).joinToString(",")
                appendLine("        ${ident(c.type.substringBefore('('))} ${ident(c.name)}${if (keys.isEmpty()) "" else " $keys"}")
            }
            appendLine("    }")
        }
        scene.ordered.filterIsInstance<Connector>().forEach { c ->
            val rel = c.semantics as? DbRelationship ?: return@forEach
            val child = DbNotation.table(c.source, scene)?.second ?: return@forEach
            val parent = DbNotation.table(c.target, scene)?.second ?: return@forEach
            val line = if (rel.identifying) "--" else ".."
            val left = rel.targetCardinality.mermaid
            val right = mirror(rel.sourceCardinality.mermaid)
            appendLine("    ${ident(parent.name)} $left$line$right ${ident(child.name)} : \"${rel.name.ifBlank { "has" }}\"")
        }
    }

    /** Mermaid writes the right-hand side mirrored (`o{` instead of `}o`). */
    private fun mirror(s: String) = s.reversed().replace('}', '{')

    private fun ident(s: String) = s.replace(Regex("[^A-Za-z0-9_]"), "_").ifEmpty { "x" }
}
