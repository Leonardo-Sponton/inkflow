package io.github.leonardosponton.inkflow.feature.canvasrender

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.core.model.Vec2

/** Selection, handles, guides, ports and tool cursors. Sizes are constant in screen pixels. */
class OverlayDrawer {
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    fun draw(canvas: Canvas, o: Overlay, zoom: Double, palette: CanvasPalette) {
        val px = (1 / zoom).toFloat()
        o.warnings.forEach { r -> rect(canvas, r.inflate(WARN_PAD * px.toDouble()), palette.warning, 2 * px, dashed = true, px) }
        o.dropTarget?.let { rect(canvas, it, palette.selection, 2 * px, dashed = true, px) }
        o.selectedBounds.forEach { rect(canvas, it, palette.selection, px, dashed = false, px) }
        o.selection?.let { sel ->
            fill.color = palette.selectionFill
            canvas.drawRect(sel.left.toFloat(), sel.top.toFloat(), sel.right.toFloat(), sel.bottom.toFloat(), fill)
            rect(canvas, sel, palette.selection, SELECTION_PX * px, dashed = false, px)
            if (o.handles) handlePositions(sel).values.forEach { handle(canvas, it, px, palette) }
        }
        o.marquee?.let { m ->
            fill.color = palette.selectionFill
            canvas.drawRect(m.left.toFloat(), m.top.toFloat(), m.right.toFloat(), m.bottom.toFloat(), fill)
            rect(canvas, m, palette.selection, px, dashed = true, px)
        }
        if (o.lasso.size > 1) polyline(canvas, o.lasso, palette.selection, SELECTION_PX * px, dashed = true, px, closed = true)
        drawGuides(canvas, o, px, palette)
        drawMarkers(canvas, o, px, palette)
    }

    private fun drawGuides(canvas: Canvas, o: Overlay, px: Float, palette: CanvasPalette) {
        o.guides.forEach { g ->
            line.color = palette.guide
            line.strokeWidth = px
            line.pathEffect = null
            if (g.vertical) {
                canvas.drawLine(g.position.toFloat(), g.from.toFloat(), g.position.toFloat(), g.to.toFloat(), line)
            } else {
                canvas.drawLine(g.from.toFloat(), g.position.toFloat(), g.to.toFloat(), g.position.toFloat(), line)
            }
        }
    }

    private fun drawMarkers(canvas: Canvas, o: Overlay, px: Float, palette: CanvasPalette) {
        if (o.connectorPreview.size > 1) polyline(canvas, o.connectorPreview, palette.selection, 2 * px, dashed = true, px, closed = false)
        o.waypoints.forEach { dot(canvas, it, WAYPOINT_R * px, palette.handleFill, palette.selection, px) }
        o.ports.forEach { dot(canvas, it, PORT_R * px, palette.handleFill, palette.port, px) }
        o.activePort?.let { dot(canvas, it, ACTIVE_PORT_R * px, palette.port, palette.port, px) }
        o.eraser?.let { (c, r) -> ring(canvas, c, r.toFloat(), palette, px) }
        o.brushCursor?.let { (c, r) -> ring(canvas, c, maxOf(r.toFloat(), 2 * px), palette, px) }
    }

    private fun rect(canvas: Canvas, r: Rect, color: Int, width: Float, dashed: Boolean, px: Float) {
        line.color = color
        line.strokeWidth = width
        line.pathEffect = if (dashed) DashPathEffect(floatArrayOf(DASH * px, DASH * px), 0f) else null
        canvas.drawRect(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat(), line)
    }

    private fun polyline(canvas: Canvas, pts: List<Vec2>, color: Int, width: Float, dashed: Boolean, px: Float, closed: Boolean) {
        val path = Path()
        path.moveTo(pts[0].x.toFloat(), pts[0].y.toFloat())
        for (i in 1 until pts.size) path.lineTo(pts[i].x.toFloat(), pts[i].y.toFloat())
        if (closed) path.close()
        line.color = color
        line.strokeWidth = width
        line.pathEffect = if (dashed) DashPathEffect(floatArrayOf(DASH * px, DASH * px), 0f) else null
        canvas.drawPath(path, line)
    }

    private fun handle(canvas: Canvas, p: Vec2, px: Float, palette: CanvasPalette) {
        val h = HANDLE_PX * px / 2
        fill.color = palette.handleFill
        canvas.drawRect(p.x.toFloat() - h, p.y.toFloat() - h, p.x.toFloat() + h, p.y.toFloat() + h, fill)
        line.color = palette.selection
        line.strokeWidth = SELECTION_PX * px
        line.pathEffect = null
        canvas.drawRect(p.x.toFloat() - h, p.y.toFloat() - h, p.x.toFloat() + h, p.y.toFloat() + h, line)
    }

    private fun dot(canvas: Canvas, p: Vec2, r: Float, fillColor: Int, strokeColor: Int, px: Float) {
        fill.color = fillColor
        canvas.drawCircle(p.x.toFloat(), p.y.toFloat(), r, fill)
        line.color = strokeColor
        line.strokeWidth = SELECTION_PX * px
        line.pathEffect = null
        canvas.drawCircle(p.x.toFloat(), p.y.toFloat(), r, line)
    }

    private fun ring(canvas: Canvas, c: Vec2, r: Float, palette: CanvasPalette, px: Float) {
        line.color = palette.selection
        line.strokeWidth = SELECTION_PX * px
        line.pathEffect = null
        canvas.drawCircle(c.x.toFloat(), c.y.toFloat(), r, line)
    }

    companion object {
        const val HANDLE_PX = 14f
        private const val SELECTION_PX = 1.5f
        private const val PORT_R = 6f
        private const val ACTIVE_PORT_R = 8f
        private const val WAYPOINT_R = 5f
        private const val DASH = 6f
        private const val WARN_PAD = 4.0

        fun handlePositions(r: Rect): Map<Handle, Vec2> {
            val c = r.center
            return mapOf(
                Handle.NW to Vec2(r.left, r.top),
                Handle.N to Vec2(c.x, r.top),
                Handle.NE to Vec2(r.right, r.top),
                Handle.E to Vec2(r.right, c.y),
                Handle.SE to Vec2(r.right, r.bottom),
                Handle.S to Vec2(c.x, r.bottom),
                Handle.SW to Vec2(r.left, r.bottom),
                Handle.W to Vec2(r.left, c.y),
            )
        }
    }
}
