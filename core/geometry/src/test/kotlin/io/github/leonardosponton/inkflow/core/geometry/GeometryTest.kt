package io.github.leonardosponton.inkflow.core.geometry

import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.core.model.Vec2
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeometryTest {
    @Test
    fun `distance to segment uses the closest point`() {
        assertEquals(5.0, distanceToSegment(Vec2(5.0, 5.0), Vec2(0.0, 0.0), Vec2(10.0, 0.0)), 1e-9)
        assertEquals(5.0, distanceToSegment(Vec2(-3.0, 4.0), Vec2(0.0, 0.0), Vec2(10.0, 0.0)), 1e-9)
        assertEquals(5.0, distanceToSegment(Vec2(3.0, 4.0), Vec2(0.0, 0.0), Vec2(0.0, 0.0)), 1e-9)
    }

    @Test
    fun `polyline distance length and interpolation`() {
        val line = listOf(Vec2(0.0, 0.0), Vec2(10.0, 0.0), Vec2(10.0, 10.0))
        assertEquals(20.0, polylineLength(line), 1e-9)
        assertEquals(Vec2(10.0, 0.0), pointAlongPolyline(line, 0.5))
        assertEquals(Vec2(10.0, 10.0), pointAlongPolyline(line, 1.0))
        assertEquals(2.0, distanceToPolyline(Vec2(12.0, 5.0), line), 1e-9)
        assertEquals(Double.MAX_VALUE, distanceToPolyline(Vec2.Zero, emptyList()))
        assertEquals(1.0, distanceToPolyline(Vec2(1.0, 0.0), listOf(Vec2.Zero)), 1e-9)
        assertEquals(Vec2.Zero, pointAlongPolyline(listOf(Vec2.Zero, Vec2.Zero), 0.7))
    }

    @Test
    fun `segment intersection including collinear touch`() {
        assertTrue(segmentsIntersect(Vec2(0.0, 0.0), Vec2(10.0, 10.0), Vec2(0.0, 10.0), Vec2(10.0, 0.0)))
        assertFalse(segmentsIntersect(Vec2(0.0, 0.0), Vec2(1.0, 1.0), Vec2(2.0, 2.0), Vec2(3.0, 5.0)))
        assertTrue(segmentsIntersect(Vec2(0.0, 0.0), Vec2(5.0, 0.0), Vec2(5.0, 0.0), Vec2(9.0, 0.0)))
    }

    @Test
    fun `segment crossing rect interior ignores border contact`() {
        val r = Rect(0.0, 0.0, 10.0, 10.0)
        assertTrue(segmentCrossesRectInterior(Vec2(-5.0, 5.0), Vec2(15.0, 5.0), r))
        assertFalse(segmentCrossesRectInterior(Vec2(-5.0, 0.0), Vec2(15.0, 0.0), r))
        assertFalse(segmentCrossesRectInterior(Vec2(-5.0, -5.0), Vec2(-1.0, -1.0), r))
        assertTrue(segmentCrossesRectInterior(Vec2(5.0, 5.0), Vec2(50.0, 50.0), r))
    }

    @Test
    fun `point in polygon`() {
        val tri = listOf(Vec2(0.0, 0.0), Vec2(10.0, 0.0), Vec2(5.0, 10.0))
        assertTrue(polygonContains(tri, Vec2(5.0, 3.0)))
        assertFalse(polygonContains(tri, Vec2(9.0, 9.0)))
        assertFalse(polygonContains(tri.take(2), Vec2(5.0, 0.0)))
    }

    @Test
    fun `boundary projections`() {
        val r = Rect(0.0, 0.0, 20.0, 10.0)
        assertEquals(Vec2(20.0, 5.0), rectBoundaryToward(r, Vec2(100.0, 5.0)))
        assertEquals(Vec2(10.0, 0.0), rectBoundaryToward(r, Vec2(10.0, 5.0)))
        val e = ellipseBoundaryToward(r, Vec2(10.0, 100.0))
        assertEquals(10.0, e.x, 1e-9)
        assertEquals(10.0, e.y, 1e-9)
        assertEquals(Vec2(10.0, 0.0), ellipseBoundaryToward(r, Vec2(10.0, 5.0)))
    }

    @Test
    fun `simplify keeps endpoints and drops near collinear points`() {
        val pts = (0..10).map { Vec2(it.toDouble(), if (it == 5) 0.01 else 0.0) }
        assertEquals(listOf(pts.first(), pts.last()), simplifyPolyline(pts, 0.1))
        val corner = listOf(Vec2(0.0, 0.0), Vec2(5.0, 0.0), Vec2(5.0, 5.0))
        assertEquals(corner, simplifyPolyline(corner, 0.1))
        assertEquals(listOf(Vec2.Zero), simplifyPolyline(listOf(Vec2.Zero), 1.0))
    }

    @Test
    fun `remove collinear merges straight runs`() {
        val pts = listOf(Vec2(0.0, 0.0), Vec2(0.0, 0.0), Vec2(5.0, 0.0), Vec2(10.0, 0.0), Vec2(10.0, 5.0))
        assertEquals(listOf(Vec2(0.0, 0.0), Vec2(10.0, 0.0), Vec2(10.0, 5.0)), removeCollinear(pts))
    }

    @Test
    fun `cubic endpoints and tangent`() {
        val c = Cubic(Vec2(0.0, 0.0), Vec2(10.0, 0.0), Vec2(20.0, 10.0), Vec2(30.0, 10.0))
        assertEquals(Vec2(0.0, 0.0), c.pointAt(0.0))
        assertEquals(Vec2(30.0, 10.0), c.pointAt(1.0))
        assertEquals(25, c.flatten().size)
        assertTrue(c.tangentAt(0.0).x > 0)
    }

    @Test
    fun `affine compose rotate scale and map rect`() {
        val rot = Affine.rotate(Math.PI / 2)
        val p = rot.apply(Vec2(1.0, 0.0))
        assertEquals(0.0, p.x, 1e-9)
        assertEquals(1.0, p.y, 1e-9)
        val s = Affine.scale(2.0, 2.0, pivot = Vec2(10.0, 10.0))
        assertEquals(Vec2(10.0, 10.0), s.apply(Vec2(10.0, 10.0)))
        assertEquals(Vec2(14.0, 10.0), s.apply(Vec2(12.0, 10.0)))
        assertEquals(2.0, s.meanScale, 1e-9)
        assertEquals(
            Rect(-1.0, -1.0, 3.0, 3.0),
            Affine.scale(2.0, 2.0, Vec2(1.0, 1.0)).mapRect(Rect(0.0, 0.0, 2.0, 2.0)),
        )
        assertTrue(Affine.Identity.isIdentity)
        assertEquals(Vec2(3.0, 4.0), (Affine.translate(1.0, 1.0) * Affine.translate(2.0, 3.0)).apply(Vec2.Zero))
    }

    @Test
    fun `side facing picks dominant axis`() {
        val r = Rect(0.0, 0.0, 10.0, 10.0)
        assertEquals(Side.EAST, Side.facing(r, Vec2(30.0, 6.0)))
        assertEquals(Side.WEST, Side.facing(r, Vec2(-30.0, 6.0)))
        assertEquals(Side.NORTH, Side.facing(r, Vec2(5.0, -30.0)))
        assertEquals(Side.SOUTH, Side.facing(r, Vec2(5.0, 30.0)))
    }
}
