package io.github.leonardosponton.inkflow.feature.canvasrender

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import io.github.leonardosponton.inkflow.core.model.GridStyle
import io.github.leonardosponton.inkflow.core.model.Rect
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Background grids drawn in world space with one tiled shader per frame (instead of thousands of
 * points or lines). Spacing adapts to zoom so the density stays readable.
 */
class GridDrawer {
    private data class TileKey(val style: GridStyle, val tilePx: Int, val color: Int, val major: Int)

    private var tileKey: TileKey? = null
    private var tile: Bitmap? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shaderMatrix = Matrix()

    fun draw(canvas: Canvas, style: GridStyle, visible: Rect, zoom: Double, palette: CanvasPalette) {
        if (style == GridStyle.PLAIN) return
        val spacing = spacingFor(zoom)
        val tilePx = (spacing * zoom).roundToInt().coerceIn(MIN_PX.toInt(), MAX_TILE)
        val bitmap = tileFor(TileKey(style, tilePx, palette.grid, palette.gridMajor))
        val shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        // Bitmap pixels → world units; one tile spans `periodX × periodY` world units, anchored at 0,0.
        val (periodX, periodY) = period(style, spacing)
        shaderMatrix.reset()
        shaderMatrix.setScale((periodX / bitmap.width).toFloat(), (periodY / bitmap.height).toFloat())
        shader.setLocalMatrix(shaderMatrix)
        paint.shader = shader
        canvas.drawRect(visible.left.toFloat(), visible.top.toFloat(), visible.right.toFloat(), visible.bottom.toFloat(), paint)
        paint.shader = null
    }

    /** World spacing ≥ [MIN_PX] screen pixels, stepping by ×5 from [BASE]. */
    fun spacingFor(zoom: Double): Double {
        var s = BASE
        while (s * zoom < MIN_PX) s *= STEP
        while (s * zoom > MIN_PX * STEP * STEP && s > BASE / (STEP * STEP)) s /= STEP
        return s
    }

    private fun period(style: GridStyle, spacing: Double): Pair<Double, Double> = when (style) {
        GridStyle.SQUARES -> spacing * MAJOR_EVERY to spacing * MAJOR_EVERY
        GridStyle.ISOMETRIC -> spacing * sqrt(3.0) to spacing
        else -> spacing to spacing
    }

    private fun tileFor(key: TileKey): Bitmap {
        if (key == tileKey) tile?.let { return it }
        tile?.recycle()
        val bmp = when (key.style) {
            GridStyle.DOTS -> dotTile(key)
            GridStyle.SQUARES -> squareTile(key)
            GridStyle.ISOMETRIC -> isoTile(key)
            GridStyle.PLAIN -> Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
        tile = bmp
        tileKey = key
        return bmp
    }

    private fun dotTile(k: TileKey): Bitmap {
        val bmp = Bitmap.createBitmap(k.tilePx, k.tilePx, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = k.color }
        Canvas(bmp).drawCircle(0f, 0f, DOT_PX, p)
        Canvas(bmp).apply {
            drawCircle(k.tilePx.toFloat(), 0f, DOT_PX, p)
            drawCircle(0f, k.tilePx.toFloat(), DOT_PX, p)
            drawCircle(k.tilePx.toFloat(), k.tilePx.toFloat(), DOT_PX, p)
        }
        return bmp
    }

    /** Five minor cells per tile, with the major line on the tile edge. */
    private fun squareTile(k: TileKey): Bitmap {
        val size = (k.tilePx * MAJOR_EVERY).coerceAtMost(MAX_TILE * MAJOR_EVERY)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint().apply { strokeWidth = LINE_PX }
        for (i in 0 until MAJOR_EVERY) {
            p.color = if (i == 0) k.major else k.color
            val at = i * size / MAJOR_EVERY.toFloat()
            c.drawLine(at, 0f, at, size.toFloat(), p)
            c.drawLine(0f, at, size.toFloat(), at, p)
        }
        return bmp
    }

    /** Vertical lines plus the two 30° families; tile = √3·s × s. */
    private fun isoTile(k: TileKey): Bitmap {
        val h = k.tilePx
        val w = (h * sqrt(3.0)).roundToInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = k.color
            strokeWidth = LINE_PX
        }
        c.drawLine(0f, 0f, 0f, h.toFloat(), p)
        c.drawLine(0f, 0f, w.toFloat(), h.toFloat(), p)
        c.drawLine(0f, h.toFloat(), w.toFloat(), 0f, p)
        c.drawLine(w.toFloat(), 0f, w.toFloat(), h.toFloat(), p)
        return bmp
    }

    private companion object {
        const val BASE = 20.0
        const val STEP = 5.0
        const val MIN_PX = 14.0
        const val MAX_TILE = 400
        const val DOT_PX = 1.4f
        const val LINE_PX = 1.5f
        const val MAJOR_EVERY = 5
    }
}
