package io.github.leonardosponton.inkflow.domain.canvas.snap

import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.core.model.Vec2
import kotlin.math.abs
import kotlin.math.roundToLong

/** A guide line to draw while dragging: vertical (`x`) or horizontal (`y`) at [position]. */
data class Guide(val vertical: Boolean, val position: Double, val from: Double, val to: Double)

data class SnapResult(val delta: Vec2, val guides: List<Guide>)

/**
 * Grid snapping and smart alignment guides for structured shapes. [threshold] is in world units
 * (callers divide a screen tolerance by the zoom).
 */
class Snapper(private val gridSize: Double = DEFAULT_GRID) {
    fun snapToGrid(v: Double): Double = (v / gridSize).roundToLong() * gridSize

    fun snapPoint(p: Vec2) = Vec2(snapToGrid(p.x), snapToGrid(p.y))

    /**
     * Adjusts the drag [delta] of a box starting at [moving] so its edges or center align with one
     * of [others]; falls back to grid snapping of the top-left corner when [grid] is on.
     */
    fun snapMove(
        moving: Rect,
        delta: Vec2,
        others: List<Rect>,
        threshold: Double,
        grid: Boolean,
        guides: Boolean,
    ): SnapResult {
        val moved = moving.translate(delta)
        val x = if (guides) bestAlignment(xs(moved), others.map(::xs), threshold) else null
        val y = if (guides) bestAlignment(ys(moved), others.map(::ys), threshold) else null
        val dx = x?.first ?: if (grid) snapToGrid(moved.left) - moved.left else 0.0
        val dy = y?.first ?: if (grid) snapToGrid(moved.top) - moved.top else 0.0
        val final = moved.translate(dx, dy)
        val lines = buildList {
            x?.let { (_, pos) -> add(guideFor(true, pos, final, others)) }
            y?.let { (_, pos) -> add(guideFor(false, pos, final, others)) }
        }
        return SnapResult(Vec2(delta.x + dx, delta.y + dy), lines)
    }

    private fun xs(r: Rect) = listOf(r.left, r.center.x, r.right)

    private fun ys(r: Rect) = listOf(r.top, r.center.y, r.bottom)

    /** Smallest correction (and the aligned coordinate) within [threshold], if any. */
    private fun bestAlignment(
        mine: List<Double>,
        theirs: List<List<Double>>,
        threshold: Double,
    ): Pair<Double, Double>? {
        var best: Pair<Double, Double>? = null
        for (candidate in theirs.flatten()) {
            for (m in mine) {
                val d = candidate - m
                if (abs(d) <= threshold && (best == null || abs(d) < abs(best.first))) best = d to candidate
            }
        }
        return best
    }

    private fun guideFor(vertical: Boolean, pos: Double, moving: Rect, others: List<Rect>): Guide {
        val aligned = others.filter { r -> (if (vertical) xs(r) else ys(r)).any { abs(it - pos) < EPS } } + moving
        val from = aligned.minOf { if (vertical) it.top else it.left }
        val to = aligned.maxOf { if (vertical) it.bottom else it.right }
        return Guide(vertical, pos, from, to)
    }

    companion object {
        const val DEFAULT_GRID = 20.0
        private const val EPS = 1e-6
    }
}
