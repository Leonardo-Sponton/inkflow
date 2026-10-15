package io.github.leonardosponton.inkflow.feature.canvasrender

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import io.github.leonardosponton.inkflow.core.geometry.pointAlongPolyline
import io.github.leonardosponton.inkflow.core.model.ColorArgb
import io.github.leonardosponton.inkflow.core.model.DashPattern
import io.github.leonardosponton.inkflow.core.model.ElementId
import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.core.model.ShapeStyle
import io.github.leonardosponton.inkflow.core.model.Size
import io.github.leonardosponton.inkflow.core.model.TextAlign
import io.github.leonardosponton.inkflow.core.model.Vec2
import io.github.leonardosponton.inkflow.domain.canvas.connector.ConnectorRouter
import io.github.leonardosponton.inkflow.domain.canvas.element.CanvasElement
import io.github.leonardosponton.inkflow.domain.canvas.element.Connector
import io.github.leonardosponton.inkflow.domain.canvas.element.ImageElement
import io.github.leonardosponton.inkflow.domain.canvas.element.InkStroke
import io.github.leonardosponton.inkflow.domain.canvas.element.Routing
import io.github.leonardosponton.inkflow.domain.canvas.element.ShapeNode
import io.github.leonardosponton.inkflow.domain.canvas.element.TextElement
import io.github.leonardosponton.inkflow.domain.canvas.element.TextKind
import io.github.leonardosponton.inkflow.domain.canvas.notation.NotationCatalog
import io.github.leonardosponton.inkflow.domain.diagram.MarkerKey
import io.github.leonardosponton.inkflow.domain.diagram.PathFill
import io.github.leonardosponton.inkflow.domain.diagram.TextBlock
import kotlin.math.atan2

/**
 * Draws scene elements into any android [Canvas] whose matrix maps world → device. Used for the
 * screen, PNG/PDF export and thumbnails, so all look identical.
 *
 * [zoom] is device pixels per world unit; it drives level of detail.
 */
class SceneDrawer(
    private val catalog: NotationCatalog,
    val metrics: AndroidTextMetrics,
    private val images: ImageSource,
) {
    private val outlines = OutlinePaths()
    private val inkCache = InkStrokeCache()
    private val inkRenderer: CanvasStrokeRenderer by lazy { CanvasStrokeRenderer.create() }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val scratchMatrix = Matrix()
    private val scratchRect = RectF()

    data class Pass(val palette: CanvasPalette, val zoom: Double, val worldToDevice: Matrix, val hidden: Set<ElementId> = emptySet())

    fun drawAll(canvas: Canvas, elements: List<CanvasElement>, pass: Pass) {
        elements.forEach { if (it.id !in pass.hidden) draw(canvas, it, pass) }
    }

    fun draw(canvas: Canvas, el: CanvasElement, pass: Pass) {
        when (el) {
            is ShapeNode -> drawNode(canvas, el, pass)
            is Connector -> drawConnector(canvas, el, pass)
            is InkStroke -> drawInk(canvas, el, pass)
            is TextElement -> drawText(canvas, el, pass)
            is ImageElement -> drawImage(canvas, el, pass)
        }
    }

    // ---- nodes ----------------------------------------------------------------------------------

    private fun drawNode(canvas: Canvas, node: ShapeNode, pass: Pass) {
        val def = catalog.shapes.resolve(node.shape)
        val f = node.frame
        val style = node.style
        val alpha = (style.opacity.coerceIn(0.0, 1.0) * MAX_ALPHA).toInt()
        if (pass.zoom < LOD_SIMPLE_ZOOM && def.outline.paths.isNotEmpty()) {
            fill.color = pass.palette.adapt(style.fill?.argb ?: style.stroke.withAlpha(LOD_ALPHA).argb)
            canvas.drawRect(f.left.toFloat(), f.top.toFloat(), f.right.toFloat(), f.bottom.toFloat(), fill)
            return
        }
        canvas.save()
        canvas.translate(f.left.toFloat(), f.top.toFloat())
        val paths = outlines.local(node.shape, def.outline, f.width, f.height)
        def.outline.paths.forEachIndexed { i, sp ->
            val p = paths[i]
            when (sp.fill) {
                PathFill.FILL -> style.fill?.let { fillPath(canvas, p, pass.palette.adapt(it), alpha) }
                PathFill.STROKE_COLOR -> fillPath(canvas, p, pass.palette.adapt(style.stroke), alpha)
                PathFill.NONE -> Unit
            }
            if (sp.stroked) strokePath(canvas, p, style, pass, alpha)
        }
        canvas.restore()
        val layout = catalog.layouts.layouterFor(node.content).layout(node.content, def, f, style, metrics)
        stroke.color = pass.palette.adapt(style.stroke)
        stroke.strokeWidth = (style.strokeWidth * SEPARATOR_RATIO).toFloat()
        stroke.pathEffect = null
        layout.separators.forEach { (a, b) -> canvas.drawLine(a.x.toFloat(), a.y.toFloat(), b.x.toFloat(), b.y.toFloat(), stroke) }
        layout.texts.forEach { drawTextBlock(canvas, it, pass.palette.adapt(style.textColor), pass) }
    }

    private fun fillPath(canvas: Canvas, p: Path, color: Int, alpha: Int) {
        fill.color = color
        fill.alpha = (fill.alpha * alpha) / MAX_ALPHA
        canvas.drawPath(p, fill)
    }

    private fun strokePath(canvas: Canvas, p: Path, style: ShapeStyle, pass: Pass, alpha: Int) {
        if (style.strokeWidth <= 0) return
        configureStroke(style, pass)
        stroke.alpha = (stroke.alpha * alpha) / MAX_ALPHA
        canvas.drawPath(p, stroke)
    }

    private fun configureStroke(style: ShapeStyle, pass: Pass) {
        stroke.color = pass.palette.adapt(style.stroke)
        stroke.strokeWidth = maxOf(style.strokeWidth, HAIRLINE_PX / pass.zoom).toFloat()
        stroke.pathEffect = dash(style.dash, style.strokeWidth)
    }

    private fun dash(d: DashPattern, width: Double): DashPathEffect? = when (d) {
        DashPattern.SOLID -> null
        DashPattern.DASHED -> DashPathEffect(floatArrayOf((width * DASH_ON).toFloat(), (width * DASH_OFF).toFloat()), 0f)
        DashPattern.DOTTED -> DashPathEffect(floatArrayOf(DOT_ON, (width * DOT_GAP).toFloat()), 0f)
    }

    fun drawTextBlock(canvas: Canvas, block: TextBlock, color: Int, pass: Pass) {
        if (block.fontSize * pass.zoom < MIN_TEXT_PX || block.text.isEmpty()) return
        val r = block.rect
        val layout = metrics.layout(
            block.text,
            r.width.toInt().coerceAtLeast(1),
            block.fontSize.toFloat(),
            block.bold,
            block.italic,
            block.align,
            color,
            block.underline,
        )
        canvas.save()
        canvas.clipRect(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat())
        val dy = if (block.verticalCenter) ((r.height - layout.height) / 2).coerceAtLeast(0.0) else 0.0
        canvas.translate(r.left.toFloat(), (r.top + dy).toFloat())
        layout.draw(canvas)
        canvas.restore()
    }

    // ---- connectors -----------------------------------------------------------------------------

    private fun drawConnector(canvas: Canvas, c: Connector, pass: Pass) {
        if (c.route.size < 2) return
        configureStroke(c.style, pass)
        val startDef = catalog.markers[c.startMarker]
        val endDef = catalog.markers[c.endMarker]
        val path = connectorPath(c, startDef?.lineInset ?: 0.0, endDef?.lineInset ?: 0.0)
        canvas.drawPath(path, stroke)
        val (endPoint, endDir) = endDirection(c, atStart = false)
        val (startPoint, startDir) = endDirection(c, atStart = true)
        drawMarker(canvas, c.endMarker, endPoint, endDir, c.style, pass)
        drawMarker(canvas, c.startMarker, startPoint, startDir, c.style, pass)
        drawLabels(canvas, c, pass)
    }

    private fun connectorPath(c: Connector, startInset: Double, endInset: Double): Path {
        val pts = c.route.toMutableList()
        val path = Path()
        if (c.routing == Routing.CURVED && pts.size >= CUBIC_POINTS) {
            pts[0] = shorten(pts[0], pts[1], startInset)
            pts[pts.lastIndex] = shorten(pts.last(), pts[pts.lastIndex - 1], endInset)
            path.moveTo(pts[0].x.toFloat(), pts[0].y.toFloat())
            var i = 1
            while (i + 2 < pts.size) {
                path.cubicTo(
                    pts[i].x.toFloat(),
                    pts[i].y.toFloat(),
                    pts[i + 1].x.toFloat(),
                    pts[i + 1].y.toFloat(),
                    pts[i + 2].x.toFloat(),
                    pts[i + 2].y.toFloat(),
                )
                i += 3
            }
            return path
        }
        pts[0] = shorten(pts[0], pts[1], startInset)
        pts[pts.lastIndex] = shorten(pts.last(), pts[pts.lastIndex - 1], endInset)
        path.moveTo(pts[0].x.toFloat(), pts[0].y.toFloat())
        for (i in 1 until pts.size) path.lineTo(pts[i].x.toFloat(), pts[i].y.toFloat())
        return path
    }

    private fun shorten(end: Vec2, toward: Vec2, by: Double): Vec2 {
        if (by <= 0) return end
        val d = toward - end
        val len = d.length
        return if (len <= by) end else end + d * (by / len)
    }

    /** End point and arrival direction (unit vector pointing out of the line) at one end. */
    private fun endDirection(c: Connector, atStart: Boolean): Pair<Vec2, Vec2> {
        val r = c.route
        val (tip, prev) = if (atStart) r[0] to r[1] else r.last() to r[r.lastIndex - 1]
        val dir = (tip - prev).normalized()
        return tip to if (dir == Vec2.Zero) Vec2(1.0, 0.0) else dir
    }

    private fun drawMarker(canvas: Canvas, key: MarkerKey, tip: Vec2, dir: Vec2, style: ShapeStyle, pass: Pass) {
        val def = catalog.markers[key] ?: return
        if (def.outline.paths.isEmpty()) return
        canvas.save()
        canvas.translate(tip.x.toFloat(), tip.y.toFloat())
        canvas.rotate(Math.toDegrees(atan2(dir.y, dir.x)).toFloat())
        val scale = (style.strokeWidth / BASE_STROKE).coerceIn(MIN_MARKER_SCALE, MAX_MARKER_SCALE).toFloat()
        canvas.scale(scale, scale)
        val zero = Rect(0.0, 0.0, 0.0, 0.0)
        def.outline.paths.forEach { sp ->
            val p = OutlinePaths.build(sp, zero)
            when (sp.fill) {
                PathFill.FILL -> fillPath(canvas, p, pass.palette.background, MAX_ALPHA)
                PathFill.STROKE_COLOR -> fillPath(canvas, p, pass.palette.adapt(style.stroke), MAX_ALPHA)
                PathFill.NONE -> Unit
            }
            if (sp.stroked) {
                stroke.color = pass.palette.adapt(style.stroke)
                stroke.strokeWidth = (style.strokeWidth / scale).toFloat()
                stroke.pathEffect = null
                canvas.drawPath(p, stroke)
            }
        }
        canvas.restore()
    }

    private fun drawLabels(canvas: Canvas, c: Connector, pass: Pass) {
        if (c.labels.isEmpty()) return
        val line = ConnectorRouter.flatten(c)
        val size = c.style.fontSize * LABEL_SCALE
        if (size * pass.zoom < MIN_TEXT_PX) return
        c.labels.forEach { label ->
            val at = pointAlongPolyline(line, label.position.fraction)
            val m = metrics.measure(label.text, size, false, null)
            val box = Rect.fromCenter(at, Size(m.width + LABEL_PAD * 2, m.height + LABEL_PAD))
            fill.color = pass.palette.labelBackground
            scratchRect.set(box.left.toFloat(), box.top.toFloat(), box.right.toFloat(), box.bottom.toFloat())
            canvas.drawRoundRect(scratchRect, LABEL_PAD.toFloat(), LABEL_PAD.toFloat(), fill)
            drawTextBlock(
                canvas,
                TextBlock(box, label.text, size, TextAlign.CENTER, wrap = false),
                pass.palette.adapt(c.style.textColor),
                pass,
            )
        }
    }

    // ---- ink / text / images ------------------------------------------------------------------

    private fun drawInk(canvas: Canvas, s: InkStroke, pass: Pass) {
        if (s.points.size == 0) return
        val color = pass.palette.adapt(s.brush.color)
        canvas.save()
        canvas.translate(s.origin.x.toFloat(), s.origin.y.toFloat())
        scratchMatrix.set(pass.worldToDevice)
        scratchMatrix.preTranslate(s.origin.x.toFloat(), s.origin.y.toFloat())
        inkRenderer.draw(canvas, inkCache.get(s, color), scratchMatrix)
        canvas.restore()
    }

    private fun drawText(canvas: Canvas, t: TextElement, pass: Pass) {
        val f = t.frame
        scratchRect.set(f.left.toFloat(), f.top.toFloat(), f.right.toFloat(), f.bottom.toFloat())
        val bg = t.style.fill ?: if (t.kind == TextKind.STICKY) ColorArgb.StickyYellow else null
        if (bg != null) {
            if (t.kind == TextKind.STICKY) {
                fill.color = SHADOW
                canvas.save()
                canvas.translate(SHADOW_OFFSET, SHADOW_OFFSET)
                canvas.drawRect(scratchRect, fill)
                canvas.restore()
            }
            fill.color = if (t.kind == TextKind.STICKY) bg.argb else pass.palette.adapt(bg)
            canvas.drawRect(scratchRect, fill)
        }
        val textColor = if (t.kind == TextKind.STICKY) t.style.textColor.argb else pass.palette.adapt(t.style.textColor)
        val inner = f.inflate(-TEXT_PAD)
        val block = TextBlock(inner, t.text, t.style.fontSize, t.style.textAlign, t.style.bold, t.style.italic, verticalCenter = false)
        drawTextBlock(canvas, block, textColor, pass)
    }

    private fun drawImage(canvas: Canvas, img: ImageElement, pass: Pass) {
        val f = img.frame
        scratchRect.set(f.left.toFloat(), f.top.toFloat(), f.right.toFloat(), f.bottom.toFloat())
        val px = (maxOf(f.width, f.height) * pass.zoom).toInt()
        val bmp = images.bitmap(img.asset, px)
        if (bmp == null) {
            fill.color = pass.palette.grid
            canvas.drawRect(scratchRect, fill)
            return
        }
        canvas.drawBitmap(bmp, null, scratchRect, fill.apply { color = BLACK })
    }

    private companion object {
        const val MAX_ALPHA = 255
        const val LOD_ALPHA = 0x40
        const val LOD_SIMPLE_ZOOM = 0.12
        const val MIN_TEXT_PX = 3.5
        const val HAIRLINE_PX = 1.0
        const val SEPARATOR_RATIO = 0.75
        const val DASH_ON = 4.0
        const val DASH_OFF = 3.0
        const val DOT_GAP = 2.5
        const val DOT_ON = 0.1f
        const val CUBIC_POINTS = 4
        const val BASE_STROKE = 2.0
        const val MIN_MARKER_SCALE = 0.8
        const val MAX_MARKER_SCALE = 3.0
        const val LABEL_SCALE = 0.85
        const val LABEL_PAD = 4.0
        const val TEXT_PAD = 8.0
        const val SHADOW = 0x22000000
        const val SHADOW_OFFSET = 3f
        const val BLACK = 0xFF000000.toInt()
    }
}
