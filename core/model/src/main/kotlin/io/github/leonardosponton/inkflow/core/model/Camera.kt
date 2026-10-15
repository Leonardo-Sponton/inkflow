package io.github.leonardosponton.inkflow.core.model

import kotlinx.serialization.Serializable
import kotlin.math.min

/**
 * Viewport onto the infinite canvas. [center] is the world point shown at the middle of the
 * viewport; [zoom] is screen pixels per world unit.
 */
@Serializable
data class Camera(val center: Vec2 = Vec2.Zero, val zoom: Double = 1.0) {
    fun worldToScreen(p: Vec2, viewport: Size): Vec2 =
        Vec2((p.x - center.x) * zoom + viewport.width / 2, (p.y - center.y) * zoom + viewport.height / 2)

    fun screenToWorld(p: Vec2, viewport: Size): Vec2 =
        Vec2((p.x - viewport.width / 2) / zoom + center.x, (p.y - viewport.height / 2) / zoom + center.y)

    fun visibleWorldRect(viewport: Size): Rect =
        Rect.fromCenter(center, Size(viewport.width / zoom, viewport.height / zoom))

    /** Moves the view by a screen-space delta (finger pan). */
    fun panByScreen(dx: Double, dy: Double): Camera = copy(center = Vec2(center.x - dx / zoom, center.y - dy / zoom))

    /** Zooms by [factor] keeping the world point under [focus] (screen space) fixed. */
    fun zoomAround(focus: Vec2, factor: Double, viewport: Size): Camera {
        val newZoom = clampZoom(zoom * factor)
        val anchor = screenToWorld(focus, viewport)
        val offset = Vec2(focus.x - viewport.width / 2, focus.y - viewport.height / 2)
        return Camera(center = Vec2(anchor.x - offset.x / newZoom, anchor.y - offset.y / newZoom), zoom = newZoom)
    }

    companion object {
        const val MIN_ZOOM = 0.02
        const val MAX_ZOOM = 40.0

        fun clampZoom(z: Double): Double = z.coerceIn(MIN_ZOOM, MAX_ZOOM)

        /** Camera that shows [content] inside [viewport] with [paddingPx] screen pixels around it. */
        fun fit(content: Rect, viewport: Size, paddingPx: Double = 48.0, maxZoom: Double = 2.0): Camera {
            val w = (viewport.width - 2 * paddingPx).coerceAtLeast(1.0)
            val h = (viewport.height - 2 * paddingPx).coerceAtLeast(1.0)
            val zx = if (content.width > 0) w / content.width else maxZoom
            val zy = if (content.height > 0) h / content.height else maxZoom
            return Camera(content.center, clampZoom(min(min(zx, zy), maxZoom)))
        }
    }
}
