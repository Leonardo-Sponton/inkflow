package io.github.leonardosponton.inkflow.core.geometry

import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.core.model.Vec2
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OrthogonalRouterTest {
    private val router = OrthogonalRouter(margin = 10.0)

    private fun assertOrthogonal(path: List<Vec2>) {
        path.zipWithNext().forEach { (a, b) -> assertTrue(a.x == b.x || a.y == b.y, "diagonal $a→$b") }
    }

    @Test
    fun `straight route when nothing is in the way`() {
        val path = router.route(RouteEnd(Vec2(0.0, 0.0), Side.EAST), RouteEnd(Vec2(100.0, 0.0), Side.WEST), emptyList())
        assertEquals(listOf(Vec2(0.0, 0.0), Vec2(100.0, 0.0)), path)
    }

    @Test
    fun `route avoids an obstacle between the endpoints`() {
        val wall = Rect(40.0, -50.0, 60.0, 50.0)
        val path = router.route(
            RouteEnd(Vec2(0.0, 0.0), Side.EAST),
            RouteEnd(Vec2(100.0, 0.0), Side.WEST),
            listOf(wall),
        )
        assertOrthogonal(path)
        path.zipWithNext().forEach { (a, b) -> assertFalse(segmentCrossesRectInterior(a, b, wall)) }
        assertEquals(Vec2(0.0, 0.0), path.first())
        assertEquals(Vec2(100.0, 0.0), path.last())
    }

    @Test
    fun `route between two nodes leaves and enters through their sides`() {
        val a = Rect(0.0, 0.0, 50.0, 30.0)
        val b = Rect(200.0, 100.0, 250.0, 130.0)
        val path = router.route(
            RouteEnd(Vec2(25.0, 30.0), Side.SOUTH),
            RouteEnd(Vec2(200.0, 115.0), Side.WEST),
            listOf(a, b),
        )
        assertOrthogonal(path)
        assertEquals(Vec2(25.0, 30.0), path.first())
        assertTrue(path[1].y > 30.0)
        assertTrue(path[path.size - 2].x < 200.0)
    }

    @Test
    fun `falls back to an elbow when an endpoint is enclosed`() {
        val box = Rect(-100.0, -100.0, 100.0, 100.0)
        val path = router.route(RouteEnd(Vec2(0.0, 0.0), null), RouteEnd(Vec2(300.0, 50.0), null), listOf(box))
        assertOrthogonal(path)
        assertEquals(3, path.size)
    }
}

class RouteEndTest {
    @Test
    fun `routes leave a diamond through its bounding box`() {
        val diamond = Rect(0.0, 0.0, 100.0, 100.0)
        val wall = Rect(-20.0, -120.0, 120.0, -60.0)
        val start = RouteEnd(Vec2(25.0, 25.0), Side.NORTH, diamond)
        assertEquals(Vec2(25.0, 0.0), start.exit())
        val path = OrthogonalRouter(margin = 10.0).route(start, RouteEnd(Vec2(50.0, -300.0), Side.SOUTH), listOf(diamond, wall))
        path.zipWithNext().forEach { (a, b) -> assertFalse(segmentCrossesRectInterior(a, b, wall)) }
        assertEquals(Vec2(25.0, 25.0), path.first())
        assertEquals(Vec2(25.0, 0.0), RouteEnd(Vec2(25.0, 0.0), null, diamond).exit())
        assertEquals(Vec2(100.0, 40.0), RouteEnd(Vec2(60.0, 40.0), Side.EAST, diamond).exit())
        assertEquals(Vec2(0.0, 40.0), RouteEnd(Vec2(60.0, 40.0), Side.WEST, diamond).exit())
        assertEquals(Vec2(60.0, 100.0), RouteEnd(Vec2(60.0, 40.0), Side.SOUTH, diamond).exit())
    }
}
