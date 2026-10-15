package io.github.leonardosponton.inkflow.domain.uml

import io.github.leonardosponton.inkflow.core.model.DashPattern
import io.github.leonardosponton.inkflow.domain.canvas.element.Connector
import io.github.leonardosponton.inkflow.domain.canvas.element.LabelPosition
import io.github.leonardosponton.inkflow.domain.canvas.element.Routing
import io.github.leonardosponton.inkflow.domain.diagram.EdgeSemantics
import io.github.leonardosponton.inkflow.domain.diagram.MarkerKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Every UML relationship and message kind the editor knows, with its standard look. */
enum class UmlRelationKind(
    val displayName: String,
    val start: MarkerKey,
    val end: MarkerKey,
    val dashed: Boolean = false,
    val keyword: String? = null,
) {
    ASSOCIATION("Association", MarkerKey.None, MarkerKey.None),
    DIRECTED_ASSOCIATION("Directed association", MarkerKey.None, MarkerKey.Arrow),
    AGGREGATION("Aggregation", MarkerKey.DiamondHollow, MarkerKey.None),
    COMPOSITION("Composition", MarkerKey.DiamondFilled, MarkerKey.None),
    GENERALIZATION("Generalization", MarkerKey.None, MarkerKey.TriangleHollow),
    REALIZATION("Realization", MarkerKey.None, MarkerKey.TriangleHollow, dashed = true),
    DEPENDENCY("Dependency", MarkerKey.None, MarkerKey.Arrow, dashed = true),
    INCLUDE("Include", MarkerKey.None, MarkerKey.Arrow, dashed = true, keyword = "«include»"),
    EXTEND("Extend", MarkerKey.None, MarkerKey.Arrow, dashed = true, keyword = "«extend»"),
    SYNC_MESSAGE("Synchronous message", MarkerKey.None, MarkerKey.ArrowFilled),
    ASYNC_MESSAGE("Asynchronous message", MarkerKey.None, MarkerKey.Arrow),
    RETURN_MESSAGE("Return message", MarkerKey.None, MarkerKey.Arrow, dashed = true),
    CREATE_MESSAGE("Create message", MarkerKey.None, MarkerKey.Arrow, dashed = true, keyword = "«create»"),
    TRANSITION("Transition / control flow", MarkerKey.None, MarkerKey.Arrow),
    ;

    val isMessage: Boolean get() = this in setOf(SYNC_MESSAGE, ASYNC_MESSAGE, RETURN_MESSAGE, CREATE_MESSAGE)

    /** Kinds that only make sense between classifiers (classes, interfaces, enums). */
    val structural: Boolean get() = this in setOf(AGGREGATION, COMPOSITION, GENERALIZATION, REALIZATION)
}

/**
 * Semantics of a UML connector. Multiplicities and roles show as end labels; a transition shows
 * `event [guard] / action` in the middle.
 */
@Serializable
@SerialName("uml.relation")
data class UmlRelation(
    val kind: UmlRelationKind = UmlRelationKind.ASSOCIATION,
    val name: String = "",
    val sourceMultiplicity: String = "",
    val targetMultiplicity: String = "",
    val sourceRole: String = "",
    val targetRole: String = "",
    val event: String = "",
    val guard: String = "",
    val action: String = "",
) : EdgeSemantics {
    /** Text for the middle label. */
    val middleLabel: String
        get() = when (kind) {
            UmlRelationKind.TRANSITION -> buildString {
                append(event)
                if (guard.isNotBlank()) append(" [").append(guard).append(']')
                if (action.isNotBlank()) append(" / ").append(action)
            }.trim()
            else -> listOfNotNull(kind.keyword, name.takeIf { it.isNotBlank() }).joinToString(" ")
        }

    companion object {
        /** [c] restyled and relabelled to show [r]. */
        fun applyTo(c: Connector, r: UmlRelation): Connector {
            val k = r.kind
            val styled = c.copy(
                semantics = r,
                startMarker = k.start,
                endMarker = k.end,
                style = c.style.copy(dash = if (k.dashed) DashPattern.DASHED else DashPattern.SOLID),
                routing = if (k.isMessage) Routing.STRAIGHT else c.routing,
            )
            return styled
                .withLabel(LabelPosition.START, endLabel(r.sourceMultiplicity, r.sourceRole))
                .withLabel(LabelPosition.MIDDLE, r.middleLabel)
                .withLabel(LabelPosition.END, endLabel(r.targetMultiplicity, r.targetRole))
        }

        private fun endLabel(multiplicity: String, role: String) = listOf(role, multiplicity).filter { it.isNotBlank() }.joinToString(" ")
    }
}
