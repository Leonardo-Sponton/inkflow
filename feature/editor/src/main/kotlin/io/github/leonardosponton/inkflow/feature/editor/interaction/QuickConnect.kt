package io.github.leonardosponton.inkflow.feature.editor.interaction

import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.core.model.Vec2
import io.github.leonardosponton.inkflow.domain.canvas.command.AddElements
import io.github.leonardosponton.inkflow.domain.canvas.element.Connector
import io.github.leonardosponton.inkflow.domain.canvas.element.Endpoint
import io.github.leonardosponton.inkflow.domain.canvas.element.ShapeNode
import io.github.leonardosponton.inkflow.domain.canvas.scene.Scene
import kotlin.math.abs

/** Side of a shape a quick-connect arrow points to. */
enum class QuickDirection(val unit: Vec2) {
    UP(Vec2(0.0, -1.0)),
    RIGHT(Vec2(1.0, 0.0)),
    DOWN(Vec2(0.0, 1.0)),
    LEFT(Vec2(-1.0, 0.0)),
}

/**
 * Quick-connect arrows around a single selected shape: tapping one connects the shape to the
 * nearest shape on that side, or, when there is none, adds a copy of the shape there and connects
 * to it. The new (or reached) shape becomes the selection so flows can be chained tap by tap.
 */
internal class QuickConnect(private val env: EditorEnvironment) {
    private var runs = 0

    /** The shape that shows arrows, if any. */
    fun source(): ShapeNode? {
        val node = env.selection.singleOrNull()?.let { env.session.scene[it] } as? ShapeNode ?: return null
        if (node.locked || node.layerId in env.lockedLayers) return null
        return node
    }

    /** Arrow anchor points in world coordinates. */
    fun arrows(): Map<QuickDirection, Vec2> {
        val node = source() ?: return emptyMap()
        return arrowPositions(node.frame, OFFSET_PX / env.camera.zoom)
    }

    /** Arrow under [world], if any. */
    fun arrowAt(world: Vec2): QuickDirection? {
        val r = HIT_PX / env.camera.zoom
        return arrows().entries.firstOrNull { (_, p) -> p.distanceTo(world) <= r }?.key
    }

    fun connect(direction: QuickDirection) {
        val source = source() ?: return
        val scene = env.session.scene
        val key = "quick-connect-${runs++}"
        val target = nearest(scene, source, direction) ?: newNeighbour(scene, source, direction).also {
            env.execute(AddElements(listOf(it)), key)
        }
        val tools = env.tools
        val base = Connector(
            env.newId(),
            Endpoint.Attached(source.id, null),
            Endpoint.Attached(target.id, null),
            routing = tools.routing,
            startMarker = tools.startMarker,
            endMarker = tools.endMarker,
            layerId = env.activeLayer,
        )
        val catalog = env.session.catalog
        val connector = catalog.decorateNewConnector(base, env.session.scene)
        env.execute(AddElements(listOf(connector)), key)
        env.seal()
        env.selection = setOf(target.id)
        (env.session.scene[connector.id] as? Connector)?.let { catalog.followUpFor(it, env.session.scene) }?.let(env::offerFollowUp)
    }

    /** Closest unconnected shape whose center lies in the 90° cone on [direction]'s side. */
    private fun nearest(scene: Scene, source: ShapeNode, direction: QuickDirection): ShapeNode? {
        val from = source.frame.center
        val connected = scene.elements.values.filterIsInstance<Connector>()
            .mapNotNull { c ->
                val a = (c.source as? Endpoint.Attached)?.node
                val b = (c.target as? Endpoint.Attached)?.node
                when (source.id) {
                    a -> b
                    b -> a
                    else -> null
                }
            }.toSet()
        val reach = REACH_SIZES * maxOf(source.frame.width, source.frame.height) + REACH_PX / env.camera.zoom
        return scene.elements.values.asSequence()
            .filterIsInstance<ShapeNode>()
            .filter { it.id != source.id && it.id !in connected && it.layerId !in env.hiddenLayers }
            .filter { !env.session.catalog.shapes.resolve(it.shape).isContainer }
            .mapNotNull { n ->
                val v = n.frame.center - from
                val along = v.dot(direction.unit)
                val across = abs(v.cross(direction.unit))
                if (along > 0 && across <= along && along <= reach) n to along + across else null
            }
            .minByOrNull { it.second }?.first
    }

    /** A fresh copy of [source]'s shape one gap away on [direction]'s side, skipping occupied spots. */
    private fun newNeighbour(scene: Scene, source: ShapeNode, direction: QuickDirection): ShapeNode {
        val def = env.session.catalog.shapes.resolve(source.shape)
        val f = source.frame
        val step = Vec2(direction.unit.x * (f.width + GAP), direction.unit.y * (f.height + GAP))
        var frame = f.translate(step)
        val others = scene.elements.values.filterIsInstance<ShapeNode>().map { it.frame }
        var tries = 0
        while (others.any { it.intersects(frame) } && tries++ < MAX_TRIES) frame = frame.translate(step)
        return ShapeNode(
            env.newId(),
            source.shape,
            frame,
            def.createContent(),
            source.style,
            parent = source.parent,
            layerId = env.activeLayer,
            zIndex = (scene.elements.values.maxOfOrNull { it.zIndex } ?: 0) + 1,
        )
    }

    companion object {
        /** Distance from the shape edge to the arrow center, in screen pixels. */
        const val OFFSET_PX = 34.0
        const val HIT_PX = 22.0
        private const val GAP = 120.0
        private const val REACH_SIZES = 4.0
        private const val REACH_PX = 200.0
        private const val MAX_TRIES = 20

        fun arrowPositions(r: Rect, offset: Double): Map<QuickDirection, Vec2> {
            val c = r.center
            return mapOf(
                QuickDirection.UP to Vec2(c.x, r.top - offset),
                QuickDirection.RIGHT to Vec2(r.right + offset, c.y),
                QuickDirection.DOWN to Vec2(c.x, r.bottom + offset),
                QuickDirection.LEFT to Vec2(r.left - offset, c.y),
            )
        }
    }
}
