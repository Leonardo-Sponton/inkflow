package io.github.leonardosponton.inkflow.feature.canvasrender

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import io.github.leonardosponton.inkflow.core.model.GridStyle
import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.domain.canvas.element.CanvasElement
import io.github.leonardosponton.inkflow.domain.canvas.scene.Scene
import java.io.ByteArrayOutputStream
import kotlin.math.ceil
import kotlin.math.min

/** Renders a scene region off-screen (thumbnails, PNG export, PDF pages). */
class SceneRasterizer(private val drawer: SceneDrawer) {
    data class Options(
        val scale: Double = 1.0,
        val transparent: Boolean = false,
        val darkCanvas: Boolean = false,
        val grid: GridStyle = GridStyle.PLAIN,
        val padding: Double = DEFAULT_PADDING,
        val maxPixels: Int = MAX_SIDE,
    )

    /** Bounds of [elements] padded, or `null` if empty. */
    fun contentBounds(elements: Collection<CanvasElement>, padding: Double): Rect? =
        Rect.unionAll(elements.map { it.bounds })?.inflate(padding)

    fun render(scene: Scene, elements: List<CanvasElement> = scene.ordered, options: Options = Options()): Bitmap? {
        val area = contentBounds(elements, options.padding) ?: return null
        val scale = min(options.scale, options.maxPixels / maxOf(area.width, area.height))
        val w = ceil(area.width * scale).toInt().coerceAtLeast(1)
        val h = ceil(area.height * scale).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        draw(canvas, area, scale, elements, options)
        return bitmap
    }

    /** Draws [elements] so that world [area] fills the canvas at [scale] device px per world unit. */
    fun draw(canvas: Canvas, area: Rect, scale: Double, elements: List<CanvasElement>, options: Options) {
        val palette = if (options.darkCanvas) CanvasPalette.Dark else CanvasPalette.Light
        if (!options.transparent) canvas.drawColor(palette.background)
        val m = Matrix().apply {
            postTranslate(-area.left.toFloat(), -area.top.toFloat())
            postScale(scale.toFloat(), scale.toFloat())
        }
        canvas.save()
        canvas.concat(m)
        if (!options.transparent) GridDrawer().draw(canvas, options.grid, area, scale, palette)
        drawer.drawAll(canvas, elements.sortedWith(Scene.Z_ORDER), SceneDrawer.Pass(palette, scale, m))
        canvas.restore()
    }

    /** Small PNG preview for the project gallery. */
    fun thumbnailPng(scene: Scene, darkCanvas: Boolean): ByteArray? {
        val area = contentBounds(scene.ordered, DEFAULT_PADDING) ?: return null
        val scale = min(THUMB_W / area.width, THUMB_H / area.height).coerceAtMost(1.0)
        val bmp = render(scene, options = Options(scale = scale, darkCanvas = darkCanvas)) ?: return null
        return ByteArrayOutputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
            bmp.recycle()
            out.toByteArray()
        }
    }

    companion object {
        const val DEFAULT_PADDING = 32.0
        const val MAX_SIDE = 8192
        private const val THUMB_W = 480.0
        private const val THUMB_H = 300.0
        private const val PNG_QUALITY = 100
    }
}
