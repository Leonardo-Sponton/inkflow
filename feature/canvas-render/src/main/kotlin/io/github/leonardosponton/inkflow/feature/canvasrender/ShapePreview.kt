package io.github.leonardosponton.inkflow.feature.canvasrender

import android.graphics.Canvas
import android.graphics.Paint
import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.domain.diagram.PathFill
import io.github.leonardosponton.inkflow.domain.diagram.ShapeDefinition

/** Draws a shape definition's outline into a small box (palette icons). */
object ShapePreview {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }

    fun draw(canvas: Canvas, def: ShapeDefinition, box: Rect, strokeColor: Int, fillColor: Int) {
        val ratio = def.defaultSize.width / def.defaultSize.height
        val w = if (ratio >= box.width / box.height) box.width else box.height * ratio
        val h = w / ratio
        val scale = (w / def.defaultSize.width).toFloat()
        canvas.save()
        canvas.translate((box.center.x - w / 2).toFloat(), (box.center.y - h / 2).toFloat())
        canvas.scale(scale, scale)
        val frame = Rect(0.0, 0.0, def.defaultSize.width, def.defaultSize.height)
        stroke.strokeWidth = STROKE_PX / scale
        stroke.color = strokeColor
        def.outline.paths.forEach { sp ->
            val p = OutlinePaths.build(sp, frame)
            when (sp.fill) {
                PathFill.FILL -> canvas.drawPath(p, fill.apply { color = fillColor })
                PathFill.STROKE_COLOR -> canvas.drawPath(p, fill.apply { color = strokeColor })
                PathFill.NONE -> Unit
            }
            if (sp.stroked) canvas.drawPath(p, stroke)
        }
        if (def.outline.paths.isEmpty()) {
            stroke.textSize = frame.height.toFloat() * TEXT_RATIO
            canvas.drawText(
                "T",
                frame.center.x.toFloat(),
                (frame.center.y + frame.height * TEXT_RATIO / 2).toFloat(),
                fill.apply {
                    color = strokeColor
                    textSize = frame.height.toFloat() * TEXT_RATIO
                    textAlign = Paint.Align.CENTER
                },
            )
        }
        canvas.restore()
    }

    private const val STROKE_PX = 1.5f
    private const val TEXT_RATIO = 0.8f
}
