package io.github.leonardosponton.inkflow.domain.canvas.ink

import io.github.leonardosponton.inkflow.core.geometry.polylineLength
import io.github.leonardosponton.inkflow.core.geometry.simplifyPolyline
import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.core.model.Vec2
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/** Clean shape a rough stroke was recognized as (world coordinates). */
sealed interface RecognizedShape {
    data class Line(val from: Vec2, val to: Vec2) : RecognizedShape

    data class Arrow(val from: Vec2, val to: Vec2) : RecognizedShape

    data class Rectangle(val frame: Rect) : RecognizedShape

    data class Ellipse(val frame: Rect) : RecognizedShape

    data class Diamond(val frame: Rect) : RecognizedShape

    data class Triangle(val frame: Rect) : RecognizedShape
}

/** Swappable recognizer so an ML model could replace the geometric one later. */
fun interface ShapeRecognizer {
    fun recognize(points: List<Vec2>): RecognizedShape?
}

/**
 * On-device geometric recognizer: straightness for lines/arrows; for closed strokes, radial
 * deviation for ellipses and corner count + corner placement for polygons.
 */
class GeometricShapeRecognizer : ShapeRecognizer {
    override fun recognize(points: List<Vec2>): RecognizedShape? {
        if (points.size < MIN_POINTS) return null
        val box = Rect.bounding(points) ?: return null
        val diag = hypot(box.width, box.height)
        if (diag < MIN_SIZE) return null
        val length = polylineLength(points)
        val closed = points.first().distanceTo(points.last()) < CLOSED_RATIO * diag
        return if (closed) recognizeClosed(points, box, diag) else recognizeOpen(points, length)
    }

    private fun recognizeOpen(points: List<Vec2>, length: Double): RecognizedShape? {
        val start = points.first()
        val end = points.last()
        if (start.distanceTo(end) / length > STRAIGHT_RATIO) return RecognizedShape.Line(start, end)
        // Single-stroke arrow: a straight shaft to the farthest point, then a short head scribble.
        val tipIndex = points.indices.maxBy { points[it].distanceTo(start) }
        val tip = points[tipIndex]
        val shaft = points.subList(0, tipIndex + 1)
        val head = points.subList(tipIndex, points.size)
        val shaftLen = start.distanceTo(tip)
        val shaftStraight = shaftLen / polylineLength(shaft).coerceAtLeast(EPS) > STRAIGHT_RATIO
        val headSmall = head.all { it.distanceTo(tip) < HEAD_RATIO * shaftLen }
        return if (shaftStraight && headSmall && head.size > 2) RecognizedShape.Arrow(start, tip) else null
    }

    private fun recognizeClosed(points: List<Vec2>, box: Rect, diag: Double): RecognizedShape? {
        if (box.width < MIN_SIZE / 2 || box.height < MIN_SIZE / 2) return null
        val corners = corners(points, diag)
        val ellipseError = ellipseDeviation(points, box)
        return when {
            corners.size == 3 -> RecognizedShape.Triangle(box)
            corners.size == 4 && nearBoxCorners(corners, box, diag) -> RecognizedShape.Rectangle(box)
            corners.size == 4 && nearEdgeMidpoints(corners, box, diag) -> RecognizedShape.Diamond(box)
            ellipseError < ELLIPSE_TOLERANCE -> RecognizedShape.Ellipse(box)
            corners.size == 4 -> RecognizedShape.Rectangle(box)
            else -> null
        }
    }

    /** Vertices of the simplified closed polygon (closing duplicate removed). */
    private fun corners(points: List<Vec2>, diag: Double): List<Vec2> {
        val simplified = simplifyPolyline(points, diag * CORNER_TOLERANCE).toMutableList()
        if (simplified.size > 1 && simplified.first().distanceTo(simplified.last()) < CLOSED_RATIO * diag) {
            simplified.removeAt(simplified.lastIndex)
        }
        // Merge vertices that sit on an almost straight continuation (start/end seam).
        return simplified.filterIndexed { i, p ->
            val prev = simplified[(i - 1 + simplified.size) % simplified.size]
            val next = simplified[(i + 1) % simplified.size]
            val a = (p - prev).normalized()
            val b = (next - p).normalized()
            a.dot(b) < STRAIGHT_COS
        }
    }

    private fun ellipseDeviation(points: List<Vec2>, box: Rect): Double {
        val c = box.center
        val a = box.width / 2
        val b = box.height / 2
        return points.map { p ->
            val nx = (p.x - c.x) / a
            val ny = (p.y - c.y) / b
            abs(sqrt(nx * nx + ny * ny) - 1)
        }.average()
    }

    private fun nearBoxCorners(corners: List<Vec2>, box: Rect, diag: Double) =
        box.corners().all { bc -> corners.any { it.distanceTo(bc) < MATCH_RATIO * diag } }

    private fun nearEdgeMidpoints(corners: List<Vec2>, box: Rect, diag: Double): Boolean {
        val c = box.center
        val mids = listOf(Vec2(c.x, box.top), Vec2(box.right, c.y), Vec2(c.x, box.bottom), Vec2(box.left, c.y))
        return mids.all { m -> corners.any { it.distanceTo(m) < MATCH_RATIO * diag } }
    }

    private companion object {
        const val MIN_POINTS = 5
        const val MIN_SIZE = 24.0
        const val CLOSED_RATIO = 0.2
        const val STRAIGHT_RATIO = 0.92
        const val HEAD_RATIO = 0.35
        const val CORNER_TOLERANCE = 0.06
        const val STRAIGHT_COS = 0.85
        const val ELLIPSE_TOLERANCE = 0.12
        const val MATCH_RATIO = 0.2
        const val EPS = 1e-9
    }
}
