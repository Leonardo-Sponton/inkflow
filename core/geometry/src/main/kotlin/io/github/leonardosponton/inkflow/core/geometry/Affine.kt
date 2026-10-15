package io.github.leonardosponton.inkflow.core.geometry

import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.core.model.Vec2
import kotlin.math.cos
import kotlin.math.sin

/**
 * 2D affine transform `[a c e; b d f; 0 0 1]` applied as `x' = a·x + c·y + e`, `y' = b·x + d·y + f`.
 */
data class Affine(
    val a: Double = 1.0,
    val b: Double = 0.0,
    val c: Double = 0.0,
    val d: Double = 1.0,
    val e: Double = 0.0,
    val f: Double = 0.0,
) {
    fun apply(p: Vec2) = Vec2(a * p.x + c * p.y + e, b * p.x + d * p.y + f)

    /** `this` after [other]: `(this * other)(p) = this(other(p))`. */
    operator fun times(o: Affine) = Affine(
        a = a * o.a + c * o.b,
        b = b * o.a + d * o.b,
        c = a * o.c + c * o.d,
        d = b * o.c + d * o.d,
        e = a * o.e + c * o.f + e,
        f = b * o.e + d * o.f + f,
    )

    /** Uniform-ish scale factor, useful for scaling stroke widths. */
    val meanScale: Double get() = kotlin.math.sqrt(kotlin.math.abs(a * d - b * c))

    val isIdentity: Boolean get() = this == Identity

    fun mapRect(r: Rect): Rect = Rect.bounding(r.corners().map(::apply)) ?: r

    companion object {
        val Identity = Affine()

        fun translate(dx: Double, dy: Double) = Affine(e = dx, f = dy)

        fun scale(sx: Double, sy: Double, pivot: Vec2 = Vec2.Zero) =
            translate(pivot.x, pivot.y) * Affine(a = sx, d = sy) * translate(-pivot.x, -pivot.y)

        fun rotate(radians: Double, pivot: Vec2 = Vec2.Zero): Affine {
            val cs = cos(radians)
            val sn = sin(radians)
            return translate(pivot.x, pivot.y) * Affine(a = cs, b = sn, c = -sn, d = cs) * translate(-pivot.x, -pivot.y)
        }
    }
}
