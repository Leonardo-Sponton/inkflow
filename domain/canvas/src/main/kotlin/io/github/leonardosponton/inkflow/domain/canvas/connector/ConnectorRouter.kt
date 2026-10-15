package io.github.leonardosponton.inkflow.domain.canvas.connector

import io.github.leonardosponton.inkflow.core.geometry.Cubic
import io.github.leonardosponton.inkflow.core.geometry.OrthogonalRouter
import io.github.leonardosponton.inkflow.core.geometry.RouteEnd
import io.github.leonardosponton.inkflow.core.geometry.Side
import io.github.leonardosponton.inkflow.core.geometry.ellipseBoundaryToward
import io.github.leonardosponton.inkflow.core.geometry.rectBoundaryToward
import io.github.leonardosponton.inkflow.core.geometry.removeCollinear
import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.core.model.Vec2
import io.github.leonardosponton.inkflow.domain.canvas.element.Connector
import io.github.leonardosponton.inkflow.domain.canvas.element.Endpoint
import io.github.leonardosponton.inkflow.domain.canvas.element.Routing
import io.github.leonardosponton.inkflow.domain.canvas.element.ShapeNode
import io.github.leonardosponton.inkflow.domain.canvas.scene.Scene
import io.github.leonardosponton.inkflow.domain.diagram.Perimeter
import io.github.leonardosponton.inkflow.domain.diagram.ShapeRegistry
import kotlin.math.abs
import kotlin.math.max

/** Resolves connector endpoints against the scene and computes the route geometry. */
class ConnectorRouter(
    private val shapes: ShapeRegistry,
    private val orthogonal: OrthogonalRouter = OrthogonalRouter(),
) {
    /** [connector] with a fresh [Connector.route] for the current positions in [scene]. */
    fun reroute(connector: Connector, scene: Scene): Connector = connector.copy(route = route(connector, scene))

    fun route(c: Connector, scene: Scene): List<Vec2> {
        selfLoop(c, scene)?.let { return it }
        val srcRef = referencePoint(c.target, scene, c.waypoints.firstOrNull())
        val tgtRef = referencePoint(c.source, scene, c.waypoints.lastOrNull())
        val src = resolve(c.source, scene, srcRef)
        val tgt = resolve(c.target, scene, tgtRef)
        return when (c.routing) {
            Routing.STRAIGHT -> listOf(src.point) + c.waypoints + listOf(tgt.point)
            Routing.ORTHOGONAL -> orthogonalRoute(c, src, tgt, scene)
            Routing.CURVED -> curvedRoute(src, tgt, c.waypoints)
        }
    }

    /** A connector from a node to itself (self message, reflexive association) loops out on the right. */
    private fun selfLoop(c: Connector, scene: Scene): List<Vec2>? {
        val s = c.source as? Endpoint.Attached ?: return null
        val t = c.target as? Endpoint.Attached ?: return null
        if (s.node != t.node || c.waypoints.isNotEmpty()) return null
        val f = (scene[s.node] as? ShapeNode)?.frame ?: return null
        val out = f.right + LOOP_REACH
        val y1 = f.top + f.height * LOOP_START
        val y2 = f.top + f.height * LOOP_END
        return listOf(Vec2(f.right, y1), Vec2(out, y1), Vec2(out, y2), Vec2(f.right, y2))
    }

    /** Where an endpoint sits and which way it faces. [toward] is used for floating ends. */
    fun resolve(end: Endpoint, scene: Scene, toward: Vec2): RouteEnd = when (end) {
        is Endpoint.Free -> {
            RouteEnd(end.point, null)
        }
        is Endpoint.Attached -> {
            val node = scene[end.node] as? ShapeNode
            if (node == null) RouteEnd(toward, null) else resolveOnNode(node, end.port, toward)
        }
    }

    private fun resolveOnNode(node: ShapeNode, portId: String?, toward: Vec2): RouteEnd {
        val def = shapes.resolve(node.shape)
        val port = portId?.let { id -> def.ports.firstOrNull { it.id == id } }
        if (port != null) return RouteEnd(port.position(node.frame), port.side)
        val p = boundaryPoint(node.frame, def.perimeter, toward)
        return RouteEnd(p, Side.facing(node.frame, p))
    }

    private fun boundaryPoint(frame: Rect, perimeter: Perimeter, toward: Vec2): Vec2 = when (perimeter) {
        Perimeter.RECTANGLE -> rectBoundaryToward(frame, toward)
        Perimeter.ELLIPSE -> ellipseBoundaryToward(frame, toward)
        Perimeter.DIAMOND -> diamondBoundaryToward(frame, toward)
    }

    private fun diamondBoundaryToward(r: Rect, toward: Vec2): Vec2 {
        val c = r.center
        val d = toward - c
        val hw = r.width / 2
        val hh = r.height / 2
        val denom = abs(d.x) / hw.coerceAtLeast(EPS) + abs(d.y) / hh.coerceAtLeast(EPS)
        return if (denom < EPS) Vec2(c.x, r.top) else c + d * (1.0 / denom)
    }

    private fun referencePoint(end: Endpoint, scene: Scene, waypoint: Vec2?): Vec2 {
        if (waypoint != null) return waypoint
        return when (end) {
            is Endpoint.Free -> end.point
            is Endpoint.Attached -> scene[end.node]?.bounds?.center ?: Vec2.Zero
        }
    }

    private fun orthogonalRoute(c: Connector, src: RouteEnd, tgt: RouteEnd, scene: Scene): List<Vec2> {
        if (c.waypoints.isNotEmpty()) {
            val pts = listOf(src.point) + c.waypoints + listOf(tgt.point)
            return removeCollinear(pts.zipWithNext { a, b -> listOf(a, Vec2(b.x, a.y)) }.flatten() + tgt.point)
        }
        val area = Rect.fromPoints(src.point, tgt.point).inflate(OBSTACLE_SEARCH)
        val obstacles = scene.elements.values.asSequence()
            .filterIsInstance<ShapeNode>()
            .filter { !shapes.resolve(it.shape).isContainer && it.frame.intersects(area) }
            .map { it.frame }
            .take(MAX_OBSTACLES)
            .toList()
        return orthogonal.route(src, tgt, obstacles)
    }

    private fun curvedRoute(src: RouteEnd, tgt: RouteEnd, waypoints: List<Vec2>): List<Vec2> {
        val pts = listOf(src.point) + waypoints + listOf(tgt.point)
        val srcSide = src.side
        val tgtSide = tgt.side
        val out = mutableListOf(pts.first())
        for (i in 0 until pts.lastIndex) {
            val a = pts[i]
            val b = pts[i + 1]
            val reach = max(MIN_HANDLE, a.distanceTo(b) / 3)
            val c1 = if (i == 0 &&
                srcSide != null
            ) {
                a + Vec2(srcSide.dx, srcSide.dy) * reach
            } else {
                tangentHandle(pts, i, reach, true)
            }
            val c2 = if (i == pts.lastIndex - 1 && tgtSide != null) {
                b + Vec2(tgtSide.dx, tgtSide.dy) * reach
            } else {
                tangentHandle(pts, i + 1, reach, false)
            }
            out += listOf(c1, c2, b)
        }
        return out
    }

    /** Catmull-Rom style handle at point [i] (outgoing if [forward]). */
    private fun tangentHandle(pts: List<Vec2>, i: Int, reach: Double, forward: Boolean): Vec2 {
        val prev = pts[(i - 1).coerceAtLeast(0)]
        val next = pts[(i + 1).coerceAtMost(pts.lastIndex)]
        val dir = (next - prev).normalized()
        return if (forward) pts[i] + dir * reach else pts[i] - dir * reach
    }

    companion object {
        private const val EPS = 1e-9
        private const val OBSTACLE_SEARCH = 240.0
        private const val MAX_OBSTACLES = 60
        private const val MIN_HANDLE = 30.0
        private const val LOOP_REACH = 40.0
        private const val LOOP_START = 0.3
        private const val LOOP_END = 0.7

        /** Polyline approximation of a connector's route (curves flattened) for hit testing and labels. */
        fun flatten(c: Connector): List<Vec2> {
            if (c.routing != Routing.CURVED || c.route.size < 4) return c.route
            val out = mutableListOf<Vec2>()
            var i = 0
            while (i + 3 < c.route.size) {
                val seg = Cubic(c.route[i], c.route[i + 1], c.route[i + 2], c.route[i + 3]).flatten(FLATTEN_STEPS)
                out += if (out.isEmpty()) seg else seg.drop(1)
                i += 3
            }
            return out
        }

        private const val FLATTEN_STEPS = 16
    }
}
