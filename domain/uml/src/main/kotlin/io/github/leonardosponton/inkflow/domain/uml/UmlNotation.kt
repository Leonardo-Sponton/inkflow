package io.github.leonardosponton.inkflow.domain.uml

import io.github.leonardosponton.inkflow.core.model.ElementId
import io.github.leonardosponton.inkflow.domain.canvas.element.Connector
import io.github.leonardosponton.inkflow.domain.canvas.element.Endpoint
import io.github.leonardosponton.inkflow.domain.canvas.element.ShapeNode
import io.github.leonardosponton.inkflow.domain.canvas.notation.DiagramValidator
import io.github.leonardosponton.inkflow.domain.canvas.notation.NotationModule
import io.github.leonardosponton.inkflow.domain.canvas.notation.Severity
import io.github.leonardosponton.inkflow.domain.canvas.notation.ValidationIssue
import io.github.leonardosponton.inkflow.domain.canvas.scene.Scene
import io.github.leonardosponton.inkflow.domain.diagram.EdgeSemantics
import io.github.leonardosponton.inkflow.domain.diagram.NodeContent
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

object UmlNotation : NotationModule {
    override val id = UmlShapes.Id
    override val displayName = "UML"
    override val shapes = UmlShapes.all
    override val layouters = listOf(UmlClassLayouter)
    override val serializers = SerializersModule {
        polymorphic(NodeContent::class) { subclass(UmlClassContent::class) }
        polymorphic(EdgeSemantics::class) { subclass(UmlRelation::class) }
    }
    override val validators by lazy { listOf(StructuralEnds, RealizationTarget, DuplicateNames, InheritanceCycles) }

    private val lifelines = setOf(UmlShapes.Lifeline.key, UmlShapes.ActorLifeline.key, UmlShapes.Activation.key)
    private val flowNodes = setOf(
        UmlShapes.Initial.key,
        UmlShapes.Final.key,
        UmlShapes.FlowFinal.key,
        UmlShapes.Action.key,
        UmlShapes.ActivityDecision.key,
        UmlShapes.ForkHorizontal.key,
        UmlShapes.ForkVertical.key,
        UmlShapes.ObjectNode.key,
        UmlShapes.SendSignal.key,
        UmlShapes.AcceptEvent.key,
        UmlShapes.State.key,
        UmlShapes.CompositeState.key,
        UmlShapes.InitialState.key,
        UmlShapes.FinalState.key,
        UmlShapes.Choice.key,
        UmlShapes.History.key,
    )

    /** Picks a sensible relation for what was connected: messages, flows, associations. */
    override fun decorateNewConnector(connector: Connector, scene: Scene): Connector {
        if (connector.semantics is UmlRelation) return connector
        val a = node(connector.source, scene)
        val b = node(connector.target, scene)
        val kind = when {
            a == null && b == null -> return connector
            a?.shape in lifelines || b?.shape in lifelines -> UmlRelationKind.SYNC_MESSAGE
            a?.shape in flowNodes || b?.shape in flowNodes -> UmlRelationKind.TRANSITION
            isClassifier(a) && isClassifier(b) -> UmlRelationKind.ASSOCIATION
            a?.shape == UmlShapes.Actor.key || b?.shape == UmlShapes.Actor.key -> UmlRelationKind.ASSOCIATION
            a?.shape == UmlShapes.UseCase.key && b?.shape == UmlShapes.UseCase.key -> UmlRelationKind.INCLUDE
            isUml(a) || isUml(b) -> UmlRelationKind.DEPENDENCY
            else -> return connector
        }
        return UmlRelation.applyTo(connector, UmlRelation(kind))
    }

    private fun node(e: Endpoint, scene: Scene) = (e as? Endpoint.Attached)?.let { scene[it.node] as? ShapeNode }

    private fun isUml(n: ShapeNode?) = n != null && n.shape.value.startsWith("uml.")

    fun isClassifier(n: ShapeNode?) = n?.content is UmlClassContent

    private fun relations(scene: Scene): List<Pair<Connector, UmlRelation>> =
        scene.elements.values.filterIsInstance<Connector>().mapNotNull { c -> (c.semantics as? UmlRelation)?.let { c to it } }

    private val StructuralEnds = DiagramValidator { scene ->
        relations(scene).filter { (c, r) ->
            r.kind.structural &&
                !(isClassifier(node(c.source, scene)) && isClassifier(node(c.target, scene)))
        }
            .map { (c, r) -> ValidationIssue(listOf(c.id), Severity.WARNING, "${r.kind.displayName} should connect two classifiers") }
    }

    private val RealizationTarget = DiagramValidator { scene ->
        relations(scene).filter { (c, r) ->
            r.kind == UmlRelationKind.REALIZATION && (node(c.target, scene)?.content as? UmlClassContent)?.kind != ClassifierKind.INTERFACE
        }.map { (c, _) -> ValidationIssue(listOf(c.id), Severity.INFO, "Realization usually points to an interface") }
    }

    private val DuplicateNames = DiagramValidator { scene ->
        scene.elements.values.filterIsInstance<ShapeNode>()
            .filter { it.content is UmlClassContent }
            .groupBy { (it.content as UmlClassContent).name.trim().lowercase() }
            .filter { (name, nodes) -> name.isNotEmpty() && nodes.size > 1 }
            .map { (_, nodes) ->
                ValidationIssue(nodes.map { it.id }, Severity.WARNING, "Duplicate classifier name “${nodes.first().content.summary}”")
            }
    }

    private val InheritanceCycles = DiagramValidator { scene ->
        val parents = HashMap<ElementId, MutableList<ElementId>>()
        relations(scene).filter { it.second.kind == UmlRelationKind.GENERALIZATION }.forEach { (c, _) ->
            val s = (c.source as? Endpoint.Attached)?.node
            val t = (c.target as? Endpoint.Attached)?.node
            if (s != null && t != null) parents.getOrPut(s) { mutableListOf() }.add(t)
        }
        val cyclic = parents.keys.filter { start -> reaches(start, start, parents) }
        if (cyclic.isEmpty()) {
            emptyList()
        } else {
            listOf(
                ValidationIssue(cyclic, Severity.ERROR, "Inheritance cycle between ${cyclic.size} classes"),
            )
        }
    }

    private fun reaches(from: ElementId, target: ElementId, edges: Map<ElementId, List<ElementId>>): Boolean {
        val seen = HashSet<ElementId>()
        val stack = ArrayDeque(edges[from].orEmpty())
        while (stack.isNotEmpty()) {
            val n = stack.removeLast()
            if (n == target) return true
            if (seen.add(n)) stack.addAll(edges[n].orEmpty())
        }
        return false
    }
}
