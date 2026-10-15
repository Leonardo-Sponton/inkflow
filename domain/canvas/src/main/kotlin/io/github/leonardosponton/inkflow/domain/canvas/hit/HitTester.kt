package io.github.leonardosponton.inkflow.domain.canvas.hit

import io.github.leonardosponton.inkflow.core.geometry.distanceToPolyline
import io.github.leonardosponton.inkflow.core.geometry.distanceToSegment
import io.github.leonardosponton.inkflow.core.geometry.polygonContains
import io.github.leonardosponton.inkflow.core.model.ElementId
import io.github.leonardosponton.inkflow.core.model.LayerId
import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.core.model.Size
import io.github.leonardosponton.inkflow.core.model.Vec2
import io.github.leonardosponton.inkflow.domain.canvas.connector.ConnectorRouter
import io.github.leonardosponton.inkflow.domain.canvas.element.CanvasElement
import io.github.leonardosponton.inkflow.domain.canvas.element.Connector
import io.github.leonardosponton.inkflow.domain.canvas.element.ImageElement
import io.github.leonardosponton.inkflow.domain.canvas.element.InkStroke
import io.github.leonardosponton.inkflow.domain.canvas.element.ShapeNode
import io.github.leonardosponton.inkflow.domain.canvas.element.TextElement
import io.github.leonardosponton.inkflow.domain.canvas.scene.Scene
import io.github.leonardosponton.inkflow.domain.canvas.scene.SceneIndex
import io.github.leonardosponton.inkflow.domain.diagram.PortDefinition
import io.github.leonardosponton.inkflow.domain.diagram.ShapeRegistry

data class PortHit(val node: ShapeNode, val port: PortDefinition, val position: Vec2)

/** Precise picking on top of the spatial index. Tolerances are in world units. */
class HitTester(
    private val scene: Scene,
    private val index: SceneIndex,
    private val shapes: ShapeRegistry,
    private val hiddenLayers: Set<LayerId> = emptySet(),
) {
    /** Top-most element under [p]. */
    fun elementAt(p: Vec2, tolerance: Double, filter: (CanvasElement) -> Boolean = { true }): CanvasElement? =
        candidates(Rect.fromCenter(p, Size(2 * tolerance, 2 * tolerance)))
            .asReversed()
            .firstOrNull { filter(it) && hits(it, p, tolerance) }

    fun hits(el: CanvasElement, p: Vec2, tol: Double): Boolean = when (el) {
        is InkStroke -> inkDistance(el, p) <= el.brush.size / 2 + tol
        is Connector -> distanceToPolyline(p, ConnectorRouter.flatten(el)) <= el.style.strokeWidth / 2 + tol
        is ShapeNode -> nodeHit(el, p, tol)
        is TextElement, is ImageElement -> el.bounds.inflate(tol).contains(p)
    }

    private fun nodeHit(node: ShapeNode, p: Vec2, tol: Double): Boolean {
        val def = shapes.resolve(node.shape)
        if (!def.isContainer) return node.frame.inflate(tol).contains(p)
        // Containers are picked by their header or border so their children stay reachable.
        val f = node.frame
        val inHeader = Rect(f.left, f.top, f.right, f.top + maxOf(def.headerHeight, tol)).inflate(tol).contains(p)
        val onBorder = f.inflate(tol).contains(p) && !f.inflate(-tol).contains(p)
        return inHeader || onBorder
    }

    private fun inkDistance(s: InkStroke, p: Vec2): Double {
        if (s.points.size == 1) return p.distanceTo(s.worldPoint(0))
        var best = Double.MAX_VALUE
        for (i in 0 until s.points.size - 1) {
            best =
                minOf(best, distanceToSegment(p, s.worldPoint(i), s.worldPoint(i + 1)))
        }
        return best
    }

    /** Elements fully inside the lasso [polygon]; strokes count when most of their points are inside. */
    fun inLasso(polygon: List<Vec2>): List<CanvasElement> {
        val area = Rect.bounding(polygon) ?: return emptyList()
        return candidates(area).filter { el ->
            when (el) {
                is InkStroke -> fractionInside(el, polygon) >= LASSO_STROKE_FRACTION
                else -> el.bounds.corners().all { polygonContains(polygon, it) }
            }
        }
    }

    /** Elements fully inside [rect] (marquee selection). */
    fun inRect(rect: Rect): List<CanvasElement> = candidates(rect).filter { rect.contains(it.bounds) }

    /** Nearest port of any node within [tolerance] of [p]. */
    fun portAt(p: Vec2, tolerance: Double, exclude: ElementId? = null): PortHit? {
        val area = Rect.fromCenter(p, Size(2 * tolerance, 2 * tolerance))
        return candidates(area).asSequence()
            .filterIsInstance<ShapeNode>()
            .filter { it.id != exclude }
            .flatMap { node -> shapes.resolve(node.shape).ports.map { PortHit(node, it, it.position(node.frame)) } }
            .filter { it.position.distanceTo(p) <= tolerance }
            .minByOrNull { it.position.distanceTo(p) }
    }

    /** Top-most node under [p] (for floating connector targets and drops). */
    fun nodeAt(p: Vec2, tolerance: Double, exclude: ElementId? = null): ShapeNode? =
        elementAt(p, tolerance) { it is ShapeNode && it.id != exclude } as? ShapeNode

    private fun candidates(area: Rect): List<CanvasElement> =
        index.visible(scene, area).filter { it.layerId !in hiddenLayers }

    private fun fractionInside(s: InkStroke, polygon: List<Vec2>): Double {
        val n = s.points.size
        if (n == 0) return 0.0
        return (0 until n).count { polygonContains(polygon, s.worldPoint(it)) }.toDouble() / n
    }

    private companion object {
        const val LASSO_STROKE_FRACTION = 0.8
    }
}
