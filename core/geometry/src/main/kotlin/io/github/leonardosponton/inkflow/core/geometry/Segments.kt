package io.github.leonardosponton.inkflow.core.geometry

import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.core.model.Vec2
import kotlin.math.abs

/** Distance from [p] to the segment [a]–[b]. */
fun distanceToSegment(p: Vec2, a: Vec2, b: Vec2): Double = p.distanceTo(closestPointOnSegment(p, a, b))

fun closestPointOnSegment(p: Vec2, a: Vec2, b: Vec2): Vec2 {
    val ab = b - a
    val len2 = ab.dot(ab)
    if (len2 == 0.0) return a
    val t = ((p - a).dot(ab) / len2).coerceIn(0.0, 1.0)
    return a + ab * t
}

/** Distance from [p] to an open polyline. `Double.MAX_VALUE` for an empty list. */
fun distanceToPolyline(p: Vec2, points: List<Vec2>): Double {
    if (points.isEmpty()) return Double.MAX_VALUE
    if (points.size == 1) return p.distanceTo(points[0])
    var best = Double.MAX_VALUE
    for (i in 0 until points.lastIndex) {
        val d = distanceToSegment(p, points[i], points[i + 1])
        if (d < best) best = d
    }
    return best
}

fun polylineLength(points: List<Vec2>): Double = points.zipWithNext { a, b -> a.distanceTo(b) }.sum()

/** Point at [fraction] (0..1) of the polyline's length. */
fun pointAlongPolyline(points: List<Vec2>, fraction: Double): Vec2 {
    require(points.isNotEmpty())
    val total = polylineLength(points)
    if (total == 0.0) return points.first()
    var remaining = total * fraction.coerceIn(0.0, 1.0)
    for (i in 0 until points.lastIndex) {
        val seg = points[i].distanceTo(points[i + 1])
        if (remaining <= seg) return points[i].lerp(points[i + 1], if (seg == 0.0) 0.0 else remaining / seg)
        remaining -= seg
    }
    return points.last()
}

private fun orientation(a: Vec2, b: Vec2, c: Vec2): Double = (b - a).cross(c - a)

private fun onSegment(a: Vec2, b: Vec2, p: Vec2): Boolean =
    p.x in minOf(a.x, b.x)..maxOf(a.x, b.x) && p.y in minOf(a.y, b.y)..maxOf(a.y, b.y)

/** True if segments [a1]–[a2] and [b1]–[b2] touch or cross. */
fun segmentsIntersect(a1: Vec2, a2: Vec2, b1: Vec2, b2: Vec2): Boolean {
    val d1 = orientation(b1, b2, a1)
    val d2 = orientation(b1, b2, a2)
    val d3 = orientation(a1, a2, b1)
    val d4 = orientation(a1, a2, b2)
    if (opposite(d1, d2) && opposite(d3, d4)) return true
    return touches(d1, b1, b2, a1) || touches(d2, b1, b2, a2) || touches(d3, a1, a2, b1) || touches(d4, a1, a2, b2)
}

private fun opposite(p: Double, q: Double) = (p > 0 && q < 0) || (p < 0 && q > 0)

private fun touches(d: Double, a: Vec2, b: Vec2, p: Vec2) = d == 0.0 && onSegment(a, b, p)

/** True if the segment crosses the *interior* of [r] (touching the border does not count). */
fun segmentCrossesRectInterior(a: Vec2, b: Vec2, r: Rect): Boolean {
    if (r.isEmpty) return false
    val inner = r.inflate(-EPS)
    if (strictlyInside(a, inner) || strictlyInside(b, inner)) return true
    if (strictlyInside(a.lerp(b, 0.5), inner)) return true
    val c = inner.corners()
    return (0 until 4).any { segmentsIntersect(a, b, c[it], c[(it + 1) % 4]) }
}

private fun strictlyInside(p: Vec2, r: Rect) = p.x > r.left && p.x < r.right && p.y > r.top && p.y < r.bottom

/** Ray-casting point-in-polygon test (polygon implicitly closed). */
fun polygonContains(polygon: List<Vec2>, p: Vec2): Boolean {
    if (polygon.size < 3) return false
    var inside = false
    var j = polygon.lastIndex
    for (i in polygon.indices) {
        val pi = polygon[i]
        val pj = polygon[j]
        if ((pi.y > p.y) != (pj.y > p.y) && p.x < (pj.x - pi.x) * (p.y - pi.y) / (pj.y - pi.y) + pi.x) {
            inside = !inside
        }
        j = i
    }
    return inside
}

/** Point where the ray from the center of [r] towards [toward] exits the rect. */
fun rectBoundaryToward(r: Rect, toward: Vec2): Vec2 {
    val c = r.center
    val d = toward - c
    if (abs(d.x) < EPS && abs(d.y) < EPS) return Vec2(c.x, r.top)
    val sx = if (abs(d.x) < EPS) Double.MAX_VALUE else (r.width / 2) / abs(d.x)
    val sy = if (abs(d.y) < EPS) Double.MAX_VALUE else (r.height / 2) / abs(d.y)
    return c + d * minOf(sx, sy)
}

/** Point where the ray from the center of the ellipse inscribed in [r] towards [toward] exits it. */
fun ellipseBoundaryToward(r: Rect, toward: Vec2): Vec2 {
    val c = r.center
    val d = toward - c
    val a = r.width / 2
    val b = r.height / 2
    if (d.length < EPS || a < EPS || b < EPS) return Vec2(c.x, r.top)
    val t = 1.0 / kotlin.math.sqrt((d.x * d.x) / (a * a) + (d.y * d.y) / (b * b))
    return c + d * t
}

internal const val EPS = 1e-9
