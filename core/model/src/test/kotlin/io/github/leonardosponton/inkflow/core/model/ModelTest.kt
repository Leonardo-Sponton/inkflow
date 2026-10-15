package io.github.leonardosponton.inkflow.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModelTest {
    private val vp = Size(1000.0, 800.0)

    private fun assertNear(a: Vec2, b: Vec2) {
        assertEquals(a.x, b.x, 1e-6)
        assertEquals(a.y, b.y, 1e-6)
    }

    @Test
    fun `camera round trips with zoom and rotation`() {
        val cams = listOf(Camera(Vec2(10.0, -20.0), 2.5), Camera(Vec2(5.0, 5.0), 0.3, 0.7))
        for (c in cams) {
            val w = Vec2(123.0, 456.0)
            assertNear(w, c.screenToWorld(c.worldToScreen(w, vp), vp))
            assertNear(Vec2(500.0, 400.0), c.worldToScreen(c.center, vp))
        }
    }

    @Test
    fun `zoom and rotate keep the focus point fixed`() {
        val c = Camera(Vec2(0.0, 0.0), 1.0)
        val focus = Vec2(200.0, 100.0)
        val anchor = c.screenToWorld(focus, vp)
        assertNear(anchor, c.zoomAround(focus, 3.0, vp).screenToWorld(focus, vp))
        assertNear(anchor, c.rotateAround(focus, 0.5, vp).screenToWorld(focus, vp))
        assertEquals(Camera.MAX_ZOOM, c.zoomAround(focus, 1e9, vp).zoom)
        assertEquals(0.0, Camera(rotation = 0.05).snappedRotation().rotation)
        assertEquals(0.3, Camera(rotation = 0.3).snappedRotation().rotation)
    }

    @Test
    fun `pan visible rect and fit`() {
        val c = Camera(Vec2.Zero, 2.0)
        assertNear(Vec2(-50.0, 0.0), c.panByScreen(100.0, 0.0).center)
        assertEquals(Rect(-250.0, -200.0, 250.0, 200.0), c.visibleWorldRect(vp))
        val rotated = Camera(Vec2.Zero, 1.0, Math.PI / 4).visibleWorldRect(vp)
        assertTrue(rotated.width > 1000.0)
        val fit = Camera.fit(Rect(0.0, 0.0, 2000.0, 1000.0), vp, paddingPx = 0.0)
        assertEquals(0.5, fit.zoom, 1e-9)
        assertEquals(Vec2(1000.0, 500.0), fit.center)
        assertEquals(2.0, Camera.fit(Rect(0.0, 0.0, 0.0, 0.0), vp).zoom)
    }

    @Test
    fun `rect operations`() {
        val r = Rect(0.0, 0.0, 10.0, 20.0)
        assertEquals(Vec2(5.0, 10.0), r.center)
        assertTrue(r.contains(Vec2(10.0, 20.0)))
        assertTrue(r.intersects(Rect(10.0, 20.0, 30.0, 30.0)))
        assertFalse(r.intersects(Rect(11.0, 0.0, 30.0, 30.0)))
        assertEquals(Rect(-1.0, -1.0, 11.0, 21.0), r.inflate(1.0))
        assertEquals(Rect(5.0, 10.0, 5.0, 10.0), r.inflate(-50.0))
        assertEquals(Rect(1.0, 2.0, 11.0, 22.0), r.translate(Vec2(1.0, 2.0)))
        assertEquals(Rect(0.0, 0.0, 15.0, 20.0), r.union(Rect(5.0, 5.0, 15.0, 6.0)))
        assertNull(Rect.bounding(emptyList()))
        assertNull(Rect.unionAll(emptyList()))
        assertEquals(Rect(1.0, 1.0, 3.0, 4.0), Rect.fromOriginSize(Vec2(1.0, 1.0), Size(2.0, 3.0)))
        assertThrows(IllegalArgumentException::class.java) { Rect(1.0, 0.0, 0.0, 0.0) }
        assertTrue(Rect(0.0, 0.0, 0.0, 5.0).isEmpty)
        assertEquals(Size(10.0, 20.0), r.size)
        assertEquals(Vec2.Zero, r.topLeft)
    }

    @Test
    fun `vectors colors and styles`() {
        val v = Vec2(3.0, 4.0)
        assertEquals(5.0, v.length)
        assertEquals(Vec2(0.6, 0.8), v.normalized())
        assertEquals(Vec2.Zero, Vec2.Zero.normalized())
        assertEquals(Vec2(-3.0, -4.0), -v)
        assertEquals(11.0, v.dot(Vec2(1.0, 2.0)))
        assertEquals(2.0, v.cross(Vec2(1.0, 2.0)))
        assertEquals(Vec2(1.5, 2.0), v / 2.0)
        val c = ColorArgb(0xFF102030.toInt()).withAlpha(0x80)
        assertEquals(0x80, c.alpha)
        val patched = StylePatch(clearFill = true, strokeWidth = 4.0, bold = true).applyTo(ShapeStyle.Default)
        assertNull(patched.fill)
        assertEquals(4.0, patched.strokeWidth)
        assertTrue(patched.bold)
        assertEquals(ShapeStyle.Default, StylePatch().applyTo(ShapeStyle.Default))
        assertEquals(36, UuidIdGenerator.next().length)
        assertTrue(Clock.System.nowMillis() > 0)
    }
}
