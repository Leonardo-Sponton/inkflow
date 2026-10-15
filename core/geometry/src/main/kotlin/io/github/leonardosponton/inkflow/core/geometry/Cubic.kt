package io.github.leonardosponton.inkflow.core.geometry

import io.github.leonardosponton.inkflow.core.model.Vec2

/** Cubic Bézier segment. */
data class Cubic(val p0: Vec2, val c1: Vec2, val c2: Vec2, val p3: Vec2) {
    fun pointAt(t: Double): Vec2 {
        val u = 1 - t
        return p0 * (u * u * u) + c1 * (3 * u * u * t) + c2 * (3 * u * t * t) + p3 * (t * t * t)
    }

    /** Tangent direction at [t] (not normalized). */
    fun tangentAt(t: Double): Vec2 {
        val u = 1 - t
        return (c1 - p0) * (3 * u * u) + (c2 - c1) * (6 * u * t) + (p3 - c2) * (3 * t * t)
    }

    fun flatten(segments: Int = DEFAULT_SEGMENTS): List<Vec2> = (0..segments).map { pointAt(it.toDouble() / segments) }

    companion object {
        const val DEFAULT_SEGMENTS = 24
    }
}
