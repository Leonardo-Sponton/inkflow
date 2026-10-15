package io.github.leonardosponton.inkflow.domain.flowchart

import io.github.leonardosponton.inkflow.domain.canvas.element.Connector
import io.github.leonardosponton.inkflow.domain.canvas.element.Endpoint
import io.github.leonardosponton.inkflow.domain.canvas.element.LabelPosition
import io.github.leonardosponton.inkflow.domain.canvas.element.ShapeNode
import io.github.leonardosponton.inkflow.domain.canvas.notation.DiagramValidator
import io.github.leonardosponton.inkflow.domain.canvas.notation.NotationModule
import io.github.leonardosponton.inkflow.domain.canvas.notation.Severity
import io.github.leonardosponton.inkflow.domain.canvas.notation.ValidationIssue
import io.github.leonardosponton.inkflow.domain.canvas.scene.Scene
import io.github.leonardosponton.inkflow.domain.diagram.MarkerKey

/**
 * Flowchart notation: symbols, "Yes"/"No" labels on decision branches and a few sanity checks.
 */
object FlowchartNotation : NotationModule {
    override val id = FlowchartSymbols.Id
    override val displayName = "Flowchart"
    override val shapes = FlowchartSymbols.all
    override val validators by lazy { listOf(DecisionBranches, MissingTerminator) }

    const val YES = "Yes"
    const val NO = "No"

    /** The first two branches drawn out of a decision are labelled Yes and No; flow lines get arrows. */
    override fun decorateNewConnector(connector: Connector, scene: Scene): Connector {
        val source = sourceNode(connector, scene) ?: return connector
        if (source.shape != FlowchartSymbols.Decision.key) return connector
        if (connector.label(LabelPosition.MIDDLE).isNotEmpty()) return connector
        val existing = outgoing(source, scene).map { it.label(LabelPosition.MIDDLE) }
        val label = when {
            YES !in existing -> YES
            NO !in existing -> NO
            else -> return connector
        }
        val withArrow = if (connector.endMarker == MarkerKey.None) connector.copy(endMarker = MarkerKey.Arrow) else connector
        return withArrow.withLabel(LabelPosition.MIDDLE, label)
    }

    internal fun sourceNode(c: Connector, scene: Scene): ShapeNode? =
        (c.source as? Endpoint.Attached)?.let { scene[it.node] as? ShapeNode }

    internal fun outgoing(node: ShapeNode, scene: Scene): List<Connector> =
        scene.connectorsOf(node.id).filter { (it.source as? Endpoint.Attached)?.node == node.id }

    private fun isFlowchart(node: ShapeNode) = node.shape.value.startsWith("flowchart.")

    /** A decision should branch at least twice. */
    private val DecisionBranches = DiagramValidator { scene ->
        scene.elements.values.filterIsInstance<ShapeNode>()
            .filter { it.shape == FlowchartSymbols.Decision.key }
            .filter { outgoing(it, scene).size < 2 }
            .map {
                ValidationIssue(
                    listOf(it.id),
                    Severity.WARNING,
                    "Decision “${it.content.summary.ifBlank {
                        "?"
                    }}” has fewer than two outgoing branches",
                )
            }
    }

    /** A flowchart (≥ 3 symbols) should have a start/end terminator. */
    private val MissingTerminator = DiagramValidator { scene ->
        val nodes = scene.elements.values.filterIsInstance<ShapeNode>().filter(::isFlowchart).filterNot {
            FlowchartSymbols.isLane(it.shape)
        }
        if (nodes.size >= MIN_FLOW && nodes.none { it.shape == FlowchartSymbols.Terminator.key }) {
            listOf(ValidationIssue(nodes.map { it.id }, Severity.INFO, "Flowchart has no Start/End terminator"))
        } else {
            emptyList()
        }
    }

    private const val MIN_FLOW = 3
}
