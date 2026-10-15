package io.github.leonardosponton.inkflow.domain.uml

import io.github.leonardosponton.inkflow.core.model.ElementId
import io.github.leonardosponton.inkflow.domain.canvas.element.Connector
import io.github.leonardosponton.inkflow.domain.canvas.element.Endpoint
import io.github.leonardosponton.inkflow.domain.canvas.element.ShapeNode
import io.github.leonardosponton.inkflow.domain.canvas.scene.Scene

/** Offline text export of the class diagram on a tab to PlantUML. */
object PlantUmlExporter {
    fun export(scene: Scene): String = buildString {
        appendLine("@startuml")
        val classes = scene.ordered.filterIsInstance<ShapeNode>().filter { it.content is UmlClassContent }
        val names = classes.associate { it.id to alias((it.content as UmlClassContent).name) }
        classes.forEach { appendClass(it.content as UmlClassContent) }
        scene.ordered.filterIsInstance<Connector>().forEach { c -> relationLine(c, names)?.let(::appendLine) }
        appendLine("@enduml")
    }

    private fun StringBuilder.appendClass(c: UmlClassContent) {
        val keyword = when (c.kind) {
            ClassifierKind.CLASS -> "class"
            ClassifierKind.ABSTRACT_CLASS -> "abstract class"
            ClassifierKind.INTERFACE -> "interface"
            ClassifierKind.ENUMERATION -> "enum"
        }
        val stereo = c.stereotype.takeIf { it.isNotBlank() }?.let { " <<$it>>" }.orEmpty()
        appendLine("$keyword \"${c.name}\" as ${alias(c.name)}$stereo {")
        if (c.kind == ClassifierKind.ENUMERATION) c.literals.forEach { appendLine("  $it") }
        (c.attributes + c.operations).forEach { m ->
            val mods = listOfNotNull("{static}".takeIf { m.isStatic }, "{abstract}".takeIf { m.isAbstract }).joinToString(" ")
            appendLine("  " + listOf(mods, m.signature).filter { it.isNotBlank() }.joinToString(" "))
        }
        appendLine("}")
    }

    private fun relationLine(c: Connector, names: Map<ElementId, String>): String? {
        val r = c.semantics as? UmlRelation ?: return null
        val a = names[(c.source as? Endpoint.Attached)?.node] ?: return null
        val b = names[(c.target as? Endpoint.Attached)?.node] ?: return null
        val arrow = when (r.kind) {
            UmlRelationKind.ASSOCIATION -> "--"
            UmlRelationKind.DIRECTED_ASSOCIATION -> "-->"
            UmlRelationKind.AGGREGATION -> "o--"
            UmlRelationKind.COMPOSITION -> "*--"
            UmlRelationKind.GENERALIZATION -> "--|>"
            UmlRelationKind.REALIZATION -> "..|>"
            else -> "..>"
        }
        val sm = r.sourceMultiplicity.takeIf { it.isNotBlank() }?.let { " \"$it\"" }.orEmpty()
        val tm = r.targetMultiplicity.takeIf { it.isNotBlank() }?.let { "\"$it\" " }.orEmpty()
        val label = r.middleLabel.takeIf { it.isNotBlank() }?.let { " : $it" }.orEmpty()
        return "$a$sm $arrow $tm$b$label"
    }

    private fun alias(name: String) = name.replace(Regex("[^A-Za-z0-9_]"), "_").ifEmpty { "Unnamed" }
}
