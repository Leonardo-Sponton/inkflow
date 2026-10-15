package io.github.leonardosponton.inkflow.core.model

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Viewport onto the infinite canvas. [center] is the world point shown at the middle of the
 * viewport, [zoom] is screen pixels per world unit and [rotation] (radians) rotates the view only —
 * world coordinates never change.
 *
 * `screen = R(rotation) · (world − center) · zoom + viewport / 2`
 */
@Serializable
data class Camera(val center: Vec2 = Vec2.Zero, val zoom: Double = 1.0, val rotation: Double = 0.0) {
    fun worldToScreen(p: Vec2, viewport: Size): Vec2 {
        val r = rotate((p - center) * zoom, rotation)
        return Vec2(r.x + viewport.width / 2, r.y + viewport.height / 2)
    }

    fun screenToWorld(p: Vec2, viewport: Size): Vec2 {
        val local = Vec2(p.x - viewport.width / 2, p.y - viewport.height / 2)
        return rotate(local, -rotation) / zoom + center
    }

    /** World-space bounds of everything visible (axis-aligned, so larger than the view when rotated). */
    fun visibleWorldRect(viewport: Size): Rect {
        if (rotation == 0.0) return Rect.fromCenter(center, Size(viewport.width / zoom, viewport.height / zoom))
        val corners = listOf(Vec2.Zero, Vec2(viewport.width, 0.0), Vec2(viewport.width, viewport.height), Vec2(0.0, viewport.height))
        return Rect.bounding(corners.map { screenToWorld(it, viewport) }) ?: Rect.fromCenter(center, Size.Zero)
    }

    /** Moves the view by a screen-space delta (finger pan). */
    fun panByScreen(dx: Double, dy: Double): Camera = copy(center = center - rotate(Vec2(dx, dy), -rotation) / zoom)

    /** Zooms by [factor] keeping the world point under [focus] (screen space) fixed. */
    fun zoomAround(focus: Vec2, factor: Double, viewport: Size): Camera {
        val anchor = screenToWorld(focus, viewport)
        val next = copy(zoom = clampZoom(zoom * factor))
        return next.keeping(anchor, focus, viewport)
    }

    /** Rotates the view by [delta] radians around the screen point [focus]. */
    fun rotateAround(focus: Vec2, delta: Double, viewport: Size): Camera {
        val anchor = screenToWorld(focus, viewport)
        return copy(rotation = normalizeAngle(rotation + delta)).keeping(anchor, focus, viewport)
    }

    /** Same zoom/rotation, re-centered so [world] appears at [screen]. */
    private fun keeping(world: Vec2, screen: Vec2, viewport: Size): Camera {
        val local = Vec2(screen.x - viewport.width / 2, screen.y - viewport.height / 2)
        return copy(center = world - rotate(local, -rotation) / zoom)
    }

    /** Snaps rotations close to a multiple of 90° onto it. */
    fun snappedRotation(): Camera {
        val quarter = PI / 2
        val nearest = Math.round(rotation / quarter) * quarter
        return if (abs(rotation - nearest) < ROTATION_SNAP) copy(rotation = normalizeAngle(nearest)) else this
    }

    companion object {
        const val MIN_ZOOM = 0.02
        const val MAX_ZOOM = 40.0
        private const val ROTATION_SNAP = 0.08

        fun clampZoom(z: Double): Double = z.coerceIn(MIN_ZOOM, MAX_ZOOM)

        /** Camera that shows [content] inside [viewport] with [paddingPx] screen pixels around it. */
        fun fit(content: Rect, viewport: Size, paddingPx: Double = 48.0, maxZoom: Double = 2.0): Camera {
            val w = (viewport.width - 2 * paddingPx).coerceAtLeast(1.0)
            val h = (viewport.height - 2 * paddingPx).coerceAtLeast(1.0)
            val zx = if (content.width > 0) w / content.width else maxZoom
            val zy = if (content.height > 0) h / content.height else maxZoom
            return Camera(content.center, clampZoom(min(min(zx, zy), maxZoom)))
        }

        fun rotate(v: Vec2, radians: Double): Vec2 {
            if (radians == 0.0) return v
            val c = cos(radians)
            val s = sin(radians)
            return Vec2(v.x * c - v.y * s, v.x * s + v.y * c)
        }

        private fun normalizeAngle(a: Double): Double {
            val twoPi = 2 * PI
            var r = a % twoPi
            if (r > PI) r -= twoPi
            if (r < -PI) r += twoPi
            return r
        }
    }
}
