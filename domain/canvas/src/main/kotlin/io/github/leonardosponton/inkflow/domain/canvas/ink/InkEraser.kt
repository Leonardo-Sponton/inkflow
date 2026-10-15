package io.github.leonardosponton.inkflow.domain.canvas.ink

import io.github.leonardosponton.inkflow.core.model.ElementId
import io.github.leonardosponton.inkflow.core.model.Vec2
import io.github.leonardosponton.inkflow.domain.canvas.element.InkStroke
import io.github.leonardosponton.inkflow.domain.canvas.element.StrokePoints

/** Partial ("pixel-like") erasing: cuts the samples under the eraser and keeps the rest as new strokes. */
object InkEraser {
    /**
     * Pieces of [stroke] left after erasing a circle at [center] with [radius] (world units),
     * or `null` if the eraser does not touch it. Pieces get ids from [newId].
     */
    fun erase(stroke: InkStroke, center: Vec2, radius: Double, newId: () -> ElementId): List<InkStroke>? {
        val reach = radius + stroke.brush.size / 2
        val n = stroke.points.size
        val keep = BooleanArray(n) { stroke.worldPoint(it).distanceTo(center) > reach }
        if (keep.all { it }) return null
        val pieces = mutableListOf<InkStroke>()
        var i = 0
        while (i < n) {
            if (!keep[i]) {
                i++
                continue
            }
            val start = i
            while (i < n && keep[i]) i++
            if (i - start >= MIN_PIECE_POINTS) pieces += piece(stroke, start, i, newId())
        }
        return pieces
    }

    private fun piece(stroke: InkStroke, from: Int, to: Int, id: ElementId): InkStroke {
        val stride = StrokePoints.STRIDE
        val data = stroke.points.data.copyOfRange(from * stride, to * stride)
        return stroke.copy(id = id, points = StrokePoints(data))
    }

    private const val MIN_PIECE_POINTS = 2
}
