package io.github.leonardosponton.inkflow.core.geometry

import io.github.leonardosponton.inkflow.core.model.Rect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class QuadtreeTest {
    private fun box(x: Double, y: Double, s: Double = 10.0) = Rect(x, y, x + s, y + s)

    @Test
    fun `query matches brute force on random data`() {
        val rnd = Random(42)
        val tree = Quadtree<Int>(nodeCapacity = 4)
        val all = (0 until 2000).associateWith {
            box(rnd.nextDouble(-50_000.0, 50_000.0), rnd.nextDouble(-50_000.0, 50_000.0), rnd.nextDouble(1.0, 900.0))
        }
        all.forEach { (k, r) -> tree.put(k, r) }
        repeat(50) {
            val area = box(rnd.nextDouble(-50_000.0, 50_000.0), rnd.nextDouble(-50_000.0, 50_000.0), 5_000.0)
            val expected = all.filterValues { it.intersects(area) }.keys
            assertEquals(expected, tree.query(area).toSet())
        }
        assertEquals(2000, tree.size)
    }

    @Test
    fun `put moves remove deletes and root grows`() {
        val tree = Quadtree<String>(initialBounds = Rect(0.0, 0.0, 100.0, 100.0))
        tree.put("a", box(1.0, 1.0))
        tree.put("a", box(1_000_000.0, -2_000_000.0))
        assertEquals(1, tree.size)
        assertTrue("a" in tree)
        assertEquals(box(1_000_000.0, -2_000_000.0), tree.boundsOf("a"))
        assertTrue(tree.query(box(0.0, 0.0)).isEmpty())
        assertEquals(listOf("a"), tree.query(box(1_000_000.0, -2_000_000.0, 1.0)))
        assertTrue(tree.remove("a"))
        assertFalse(tree.remove("a"))
        assertNull(tree.contentBounds())
    }

    @Test
    fun `content bounds and clear`() {
        val tree = Quadtree<Int>()
        tree.put(1, box(0.0, 0.0))
        tree.put(2, box(100.0, 50.0))
        assertEquals(Rect(0.0, 0.0, 110.0, 60.0), tree.contentBounds())
        tree.clear()
        assertEquals(0, tree.size)
    }
}
