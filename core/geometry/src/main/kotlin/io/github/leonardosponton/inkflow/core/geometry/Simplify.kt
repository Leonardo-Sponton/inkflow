package io.github.leonardosponton.inkflow.core.geometry

import io.github.leonardosponton.inkflow.core.model.Vec2

/** Ramer–Douglas–Peucker polyline simplification. Keeps the first and last point. */
fun simplifyPolyline(points: List<Vec2>, tolerance: Double): List<Vec2> {
    if (points.size < 3) return points
    val keep = BooleanArray(points.size)
    keep[0] = true
    keep[points.lastIndex] = true
    val stack = ArrayDeque<Pair<Int, Int>>()
    stack.addLast(0 to points.lastIndex)
    while (stack.isNotEmpty()) {
        val (start, end) = stack.removeLast()
        var maxD = 0.0
        var index = -1
        for (i in start + 1 until end) {
            val d = distanceToSegment(points[i], points[start], points[end])
            if (d > maxD) {
                maxD = d
                index = i
            }
        }
        if (index != -1 && maxD > tolerance) {
            keep[index] = true
            stack.addLast(start to index)
            stack.addLast(index to end)
        }
    }
    return points.filterIndexed { i, _ -> keep[i] }
}

/** Removes consecutive duplicates and middle points of collinear axis-aligned runs. */
fun removeCollinear(points: List<Vec2>): List<Vec2> {
    val dedup = points.fold(mutableListOf<Vec2>()) { acc, p ->
        if (acc.isEmpty() || acc.last().distanceTo(p) > EPS) acc.add(p)
        acc
    }
    if (dedup.size < 3) return dedup
    val out = mutableListOf(dedup.first())
    for (i in 1 until dedup.lastIndex) {
        val a = out.last()
        val b = dedup[i]
        val c = dedup[i + 1]
        if (kotlin.math.abs((b - a).cross(c - b)) > 1e-6) out.add(b)
    }
    out.add(dedup.last())
    return out
}
