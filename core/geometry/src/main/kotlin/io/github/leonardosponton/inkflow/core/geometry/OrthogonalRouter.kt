package io.github.leonardosponton.inkflow.core.geometry

import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.core.model.Vec2
import java.util.PriorityQueue
import kotlin.math.abs

/** Direction a connector leaves (or enters) a node port. */
enum class Side(val dx: Double, val dy: Double) {
    NORTH(0.0, -1.0),
    EAST(1.0, 0.0),
    SOUTH(0.0, 1.0),
    WEST(-1.0, 0.0),
    ;

    val isHorizontal: Boolean get() = dy == 0.0

    companion object {
        /** Side of [r] that faces [p] best. */
        fun facing(r: Rect, p: Vec2): Side {
            val c = r.center
            val nx = (p.x - c.x) / (r.width / 2).coerceAtLeast(EPS)
            val ny = (p.y - c.y) / (r.height / 2).coerceAtLeast(EPS)
            return if (abs(nx) >= abs(ny)) {
                if (nx >= 0) EAST else WEST
            } else {
                if (ny >= 0) SOUTH else NORTH
            }
        }
    }
}

data class RouteEnd(val point: Vec2, val side: Side?)

/**
 * Obstacle-avoiding Manhattan router.
 *
 * Builds a sparse grid from obstacle edges (inflated by [margin]) and the two endpoint stubs, then
 * runs A* where the cost is length plus a penalty per bend. Falls back to a simple elbow route when
 * no path exists (e.g. an endpoint sits inside an obstacle).
 */
class OrthogonalRouter(
    private val margin: Double = DEFAULT_MARGIN,
    private val bendPenalty: Double = DEFAULT_BEND_PENALTY,
) {
    fun route(source: RouteEnd, target: RouteEnd, obstacles: List<Rect>): List<Vec2> {
        val start = stub(source)
        val end = stub(target)
        val blocks = obstacles.map { it.inflate(margin - EPS * 10) }
        val path = search(start, end, blocks) ?: elbow(start, end, source.side)
        return removeCollinear(listOf(source.point) + path + listOf(target.point))
    }

    private fun stub(end: RouteEnd): Vec2 {
        val side = end.side ?: return end.point
        return Vec2(end.point.x + side.dx * margin, end.point.y + side.dy * margin)
    }

    private fun elbow(a: Vec2, b: Vec2, side: Side?): List<Vec2> {
        val horizontalFirst = side?.isHorizontal ?: (abs(b.x - a.x) >= abs(b.y - a.y))
        val mid = if (horizontalFirst) Vec2(b.x, a.y) else Vec2(a.x, b.y)
        return listOf(a, mid, b)
    }

    private fun search(start: Vec2, end: Vec2, blocks: List<Rect>): List<Vec2>? {
        val xs = gridLines(blocks, start.x, end.x) { listOf(it.left, it.right) }
        val ys = gridLines(blocks, start.y, end.y) { listOf(it.top, it.bottom) }
        val sx = xs.indexOfFirst { it == start.x }
        val sy = ys.indexOfFirst { it == start.y }
        val ex = xs.indexOfFirst { it == end.x }
        val ey = ys.indexOfFirst { it == end.y }
        val grid = Grid(xs, ys, blocks)
        if (grid.blocked(sx, sy) || grid.blocked(ex, ey)) return null
        return AStar(grid, bendPenalty).run(sx, sy, ex, ey)
    }

    private fun gridLines(blocks: List<Rect>, a: Double, b: Double, edges: (Rect) -> List<Double>): DoubleArray {
        val set = sortedSetOf(a, b, (a + b) / 2)
        blocks.forEach { r -> edges(r).forEach { set.add(it) } }
        val list = set.toList()
        // Midlines between neighbouring edges give the router corridors between close obstacles.
        val mids = list.zipWithNext { p, q -> (p + q) / 2 }
        return (list + mids).distinct().sorted().toDoubleArray()
    }

    private class Grid(val xs: DoubleArray, val ys: DoubleArray, val blocks: List<Rect>) {
        private val blockedCache = HashMap<Long, Boolean>()

        fun point(ix: Int, iy: Int) = Vec2(xs[ix], ys[iy])

        fun blocked(ix: Int, iy: Int): Boolean = blockedCache.getOrPut(key(ix, iy)) {
            val p = point(ix, iy)
            blocks.any { p.x > it.left && p.x < it.right && p.y > it.top && p.y < it.bottom }
        }

        fun edgeFree(ax: Int, ay: Int, bx: Int, by: Int): Boolean {
            val a = point(ax, ay)
            val b = point(bx, by)
            return blocks.none { segmentCrossesRectInterior(a, b, it) }
        }

        fun key(ix: Int, iy: Int): Long = (ix.toLong() shl Int.SIZE_BITS) or iy.toLong()
    }

    private class AStar(val grid: Grid, val bendPenalty: Double) {
        private data class State(val ix: Int, val iy: Int, val dir: Int)

        private class Entry(val state: State, val g: Double, val f: Double)

        fun run(sx: Int, sy: Int, ex: Int, ey: Int): List<Vec2>? {
            val goal = grid.point(ex, ey)
            val best = HashMap<State, Double>()
            val parent = HashMap<State, State>()
            val open = PriorityQueue<Entry>(compareBy { it.f })
            val start = State(sx, sy, NO_DIR)
            best[start] = 0.0
            open.add(Entry(start, 0.0, heuristic(sx, sy, goal)))
            while (open.isNotEmpty()) {
                val cur = open.poll()
                if (cur.g > (best[cur.state] ?: Double.MAX_VALUE)) continue
                if (cur.state.ix == ex && cur.state.iy == ey) return rebuild(cur.state, parent)
                expand(cur, goal, best, parent, open)
            }
            return null
        }

        private fun expand(
            cur: Entry,
            goal: Vec2,
            best: HashMap<State, Double>,
            parent: HashMap<State, State>,
            open: PriorityQueue<Entry>,
        ) {
            val s = cur.state
            for (dir in 0 until DIRS) {
                val nx = s.ix + DX[dir]
                val ny = s.iy + DY[dir]
                if (nx !in grid.xs.indices || ny !in grid.ys.indices) continue
                if (grid.blocked(nx, ny) || !grid.edgeFree(s.ix, s.iy, nx, ny)) continue
                val step = grid.point(s.ix, s.iy).distanceTo(grid.point(nx, ny))
                val bend = if (s.dir != NO_DIR && s.dir != dir) bendPenalty else 0.0
                val g = cur.g + step + bend
                val next = State(nx, ny, dir)
                if (g < (best[next] ?: Double.MAX_VALUE)) {
                    best[next] = g
                    parent[next] = s
                    open.add(Entry(next, g, g + heuristic(nx, ny, goal)))
                }
            }
        }

        private fun heuristic(ix: Int, iy: Int, goal: Vec2): Double {
            val p = grid.point(ix, iy)
            return abs(p.x - goal.x) + abs(p.y - goal.y)
        }

        private fun rebuild(end: State, parent: Map<State, State>): List<Vec2> {
            val out = ArrayList<Vec2>()
            var s: State? = end
            while (s != null) {
                out.add(grid.point(s.ix, s.iy))
                s = parent[s]
            }
            return out.asReversed()
        }

        private companion object {
            const val NO_DIR = -1
            const val DIRS = 4
            val DX = intArrayOf(1, -1, 0, 0)
            val DY = intArrayOf(0, 0, 1, -1)
        }
    }

    companion object {
        const val DEFAULT_MARGIN = 16.0
        const val DEFAULT_BEND_PENALTY = 40.0
    }
}
