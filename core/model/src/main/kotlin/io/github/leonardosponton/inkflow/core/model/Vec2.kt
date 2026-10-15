package io.github.leonardosponton.inkflow.core.model

import kotlinx.serialization.Serializable
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** A point or vector in world space. World coordinates use `Double` so the canvas is effectively unbounded. */
@Serializable
data class Vec2(val x: Double, val y: Double) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)

    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)

    operator fun times(s: Double) = Vec2(x * s, y * s)

    operator fun div(s: Double) = Vec2(x / s, y / s)

    operator fun unaryMinus() = Vec2(-x, -y)

    val length: Double get() = hypot(x, y)

    fun distanceTo(o: Vec2): Double = hypot(x - o.x, y - o.y)

    fun dot(o: Vec2): Double = x * o.x + y * o.y

    fun cross(o: Vec2): Double = x * o.y - y * o.x

    fun normalized(): Vec2 = if (length == 0.0) Zero else this / length

    fun lerp(to: Vec2, t: Double) = Vec2(x + (to.x - x) * t, y + (to.y - y) * t)

    companion object {
        val Zero = Vec2(0.0, 0.0)
    }
}

@Serializable
data class Size(val width: Double, val height: Double) {
    companion object {
        val Zero = Size(0.0, 0.0)
    }
}

/** Axis-aligned rectangle in world space. `left <= right` and `top <= bottom` always hold. */
@Serializable
data class Rect(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    init {
        require(left <= right && top <= bottom) { "Invalid rect $left,$top,$right,$bottom" }
    }

    val width: Double get() = right - left
    val height: Double get() = bottom - top
    val center: Vec2 get() = Vec2((left + right) / 2, (top + bottom) / 2)
    val topLeft: Vec2 get() = Vec2(left, top)
    val size: Size get() = Size(width, height)
    val isEmpty: Boolean get() = width == 0.0 || height == 0.0

    fun contains(p: Vec2): Boolean = p.x in left..right && p.y in top..bottom

    fun contains(r: Rect): Boolean = r.left >= left && r.right <= right && r.top >= top && r.bottom <= bottom

    fun intersects(r: Rect): Boolean = r.left <= right && r.right >= left && r.top <= bottom && r.bottom >= top

    fun union(r: Rect) = Rect(min(left, r.left), min(top, r.top), max(right, r.right), max(bottom, r.bottom))

    fun inflate(d: Double): Rect = if (d >= 0 || (width + 2 * d >= 0 && height + 2 * d >= 0)) {
        Rect(left - d, top - d, right + d, bottom + d)
    } else {
        Rect(center.x, center.y, center.x, center.y)
    }

    fun translate(dx: Double, dy: Double) = Rect(left + dx, top + dy, right + dx, bottom + dy)

    fun translate(d: Vec2) = translate(d.x, d.y)

    fun corners(): List<Vec2> = listOf(Vec2(left, top), Vec2(right, top), Vec2(right, bottom), Vec2(left, bottom))

    companion object {
        fun fromPoints(a: Vec2, b: Vec2) = Rect(min(a.x, b.x), min(a.y, b.y), max(a.x, b.x), max(a.y, b.y))

        fun fromCenter(c: Vec2, size: Size) =
            Rect(c.x - size.width / 2, c.y - size.height / 2, c.x + size.width / 2, c.y + size.height / 2)

        fun fromOriginSize(origin: Vec2, size: Size) =
            Rect(origin.x, origin.y, origin.x + size.width, origin.y + size.height)

        /** Smallest rect containing all [points], or `null` for an empty list. */
        fun bounding(points: Iterable<Vec2>): Rect? {
            val it = points.iterator()
            if (!it.hasNext()) return null
            var first = it.next()
            var l = first.x
            var t = first.y
            var r = first.x
            var b = first.y
            while (it.hasNext()) {
                first = it.next()
                l = min(l, first.x)
                t = min(t, first.y)
                r = max(r, first.x)
                b = max(b, first.y)
            }
            return Rect(l, t, r, b)
        }

        fun unionAll(rects: Iterable<Rect>): Rect? = rects.reduceOrNull { a, b -> a.union(b) }
    }
}
