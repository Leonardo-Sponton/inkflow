package io.github.leonardosponton.inkflow.feature.canvasrender

import android.graphics.Canvas
import android.graphics.Paint
import io.github.leonardosponton.inkflow.core.model.GridStyle
import io.github.leonardosponton.inkflow.core.model.Rect
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/** Background grids drawn in world space; spacing adapts to zoom so density stays readable. */
class GridDrawer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var points = FloatArray(0)

    fun draw(canvas: Canvas, style: GridStyle, visible: Rect, zoom: Double, palette: CanvasPalette) {
        if (style == GridStyle.PLAIN) return
        val spacing = spacingFor(zoom)
        paint.color = palette.grid
        when (style) {
            GridStyle.DOTS -> dots(canvas, visible, spacing, zoom)
            GridStyle.SQUARES -> squares(canvas, visible, spacing, zoom, palette)
            GridStyle.ISOMETRIC -> isometric(canvas, visible, spacing, zoom)
            GridStyle.PLAIN -> Unit
        }
    }

    /** World spacing ≥ [MIN_PX] screen pixels, stepping by ×5 from [BASE]. */
    fun spacingFor(zoom: Double): Double {
        var s = BASE
        while (s * zoom < MIN_PX) s *= STEP
        while (s * zoom > MIN_PX * STEP * STEP && s > BASE / (STEP * STEP)) s /= STEP
        return s
    }

    private fun dots(canvas: Canvas, v: Rect, s: Double, zoom: Double) {
        val x0 = floor(v.left / s) * s
        val y0 = floor(v.top / s) * s
        val cols = ceil((v.right - x0) / s).toInt() + 1
        val rows = ceil((v.bottom - y0) / s).toInt() + 1
        val needed = cols * rows * 2
        if (needed > MAX_DOTS * 2) return
        if (points.size < needed) points = FloatArray(needed)
        var i = 0
        for (r in 0 until rows) {
            val y = (y0 + r * s).toFloat()
            for (c in 0 until cols) {
                points[i++] = (x0 + c * s).toFloat()
                points[i++] = y
            }
        }
        paint.strokeWidth = (DOT_PX / zoom).toFloat()
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawPoints(points, 0, i, paint)
    }

    private fun squares(canvas: Canvas, v: Rect, s: Double, zoom: Double, palette: CanvasPalette) {
        paint.strokeWidth = (LINE_PX / zoom).toFloat()
        var x = floor(v.left / s) * s
        var n = Math.round(x / s)
        while (x <= v.right) {
            paint.color = if (n % MAJOR_EVERY == 0L) palette.gridMajor else palette.grid
            canvas.drawLine(x.toFloat(), v.top.toFloat(), x.toFloat(), v.bottom.toFloat(), paint)
            x += s
            n++
        }
        var y = floor(v.top / s) * s
        n = Math.round(y / s)
        while (y <= v.bottom) {
            paint.color = if (n % MAJOR_EVERY == 0L) palette.gridMajor else palette.grid
            canvas.drawLine(v.left.toFloat(), y.toFloat(), v.right.toFloat(), y.toFloat(), paint)
            y += s
            n++
        }
    }

    /** Vertical lines plus two families at ±30° from horizontal. */
    private fun isometric(canvas: Canvas, v: Rect, s: Double, zoom: Double) {
        paint.strokeWidth = (LINE_PX / zoom).toFloat()
        val dx = s * sqrt(3.0)
        var x = floor(v.left / dx) * dx
        while (x <= v.right) {
            canvas.drawLine(x.toFloat(), v.top.toFloat(), x.toFloat(), v.bottom.toFloat(), paint)
            x += dx
        }
        val slope = 1 / sqrt(3.0)
        val span = v.width * slope
        listOf(slope, -slope).forEach { k ->
            // Lines y = k·x + b, with b on a lattice of spacing 2s.
            val step = 2 * s
            val bMin = minOf(v.top - k * v.left, v.top - k * v.right) - span
            val bMax = maxOf(v.bottom - k * v.left, v.bottom - k * v.right) + span
            var b = floor(bMin / step) * step
            while (b <= bMax) {
                canvas.drawLine(v.left.toFloat(), (k * v.left + b).toFloat(), v.right.toFloat(), (k * v.right + b).toFloat(), paint)
                b += step
            }
        }
    }

    private companion object {
        const val BASE = 20.0
        const val STEP = 5.0
        const val MIN_PX = 14.0
        const val DOT_PX = 2.2
        const val LINE_PX = 1.0
        const val MAJOR_EVERY = 5L
        const val MAX_DOTS = 60_000
    }
}
