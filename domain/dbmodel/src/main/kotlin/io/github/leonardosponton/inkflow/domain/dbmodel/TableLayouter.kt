package io.github.leonardosponton.inkflow.domain.dbmodel

import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.core.model.ShapeStyle
import io.github.leonardosponton.inkflow.core.model.Size
import io.github.leonardosponton.inkflow.core.model.TextAlign
import io.github.leonardosponton.inkflow.core.model.Vec2
import io.github.leonardosponton.inkflow.domain.diagram.ContentLayouter
import io.github.leonardosponton.inkflow.domain.diagram.NodeContent
import io.github.leonardosponton.inkflow.domain.diagram.NodeLayout
import io.github.leonardosponton.inkflow.domain.diagram.ShapeDefinition
import io.github.leonardosponton.inkflow.domain.diagram.TextBlock
import io.github.leonardosponton.inkflow.domain.diagram.TextMetrics

/** Table header plus one row per column: key badge, name, type and constraint flags. */
object TableLayouter : ContentLayouter {
    private const val PAD_X = 10.0
    private const val PAD_Y = 6.0
    private const val ROW_SCALE = 0.875
    private const val BADGE_W = 48.0
    private const val MIN_WIDTH = 200.0
    private const val GUTTER = 16.0

    override fun supports(content: NodeContent) = content is DbTableContent

    fun badge(c: DbColumn) = listOfNotNull("PK".takeIf { c.primaryKey }, "FK".takeIf { c.isForeignKey }).joinToString(",")

    fun typeText(c: DbColumn) = buildString {
        append(c.type)
        if (c.notNull && !c.primaryKey) append(" NN")
        if (c.unique) append(" UQ")
    }

    override fun layout(
        content: NodeContent,
        definition: ShapeDefinition,
        frame: Rect,
        style: ShapeStyle,
        metrics: TextMetrics,
    ): NodeLayout {
        val t = content as DbTableContent
        val headH = metrics.lineHeight(style.fontSize) + 2 * PAD_Y
        val rowSize = style.fontSize * ROW_SCALE
        val rowH = metrics.lineHeight(rowSize) + PAD_Y
        val header =
            TextBlock(
                Rect(frame.left + PAD_X, frame.top, frame.right - PAD_X, frame.top + headH),
                t.name,
                style.fontSize,
                TextAlign.CENTER,
                bold = true,
            )
        val texts = mutableListOf(header)
        var y = frame.top + headH + PAD_Y / 2
        t.columns.forEach { c ->
            if (y + rowH > frame.bottom + 1) return@forEach
            texts += row(c, Rect(frame.left + PAD_X, y, frame.right - PAD_X, y + rowH), rowSize)
            y += rowH
        }
        return NodeLayout(texts, listOf(Vec2(frame.left, frame.top + headH) to Vec2(frame.right, frame.top + headH)))
    }

    private fun row(c: DbColumn, r: Rect, size: Double): List<TextBlock> {
        val body = Rect(r.left + BADGE_W, r.top, r.right, r.bottom)
        return listOf(
            TextBlock(
                Rect(r.left, r.top, r.left + BADGE_W, r.bottom),
                badge(c),
                size * ROW_SCALE,
                TextAlign.START,
                bold = true,
                wrap = false,
            ),
            TextBlock(body, c.name, size, TextAlign.START, bold = c.primaryKey, underline = c.primaryKey, wrap = false),
            TextBlock(body, typeText(c), size, TextAlign.END, italic = true, wrap = false),
        )
    }

    override fun preferredSize(content: NodeContent, definition: ShapeDefinition, style: ShapeStyle, metrics: TextMetrics): Size {
        val t = content as DbTableContent
        val rowSize = style.fontSize * ROW_SCALE
        val widest = t.columns.maxOfOrNull { metrics.width(it.name, rowSize, it.primaryKey) + metrics.width(typeText(it), rowSize) } ?: 0.0
        val width = maxOf(MIN_WIDTH, metrics.width(t.name, style.fontSize, true) + 2 * PAD_X, widest + BADGE_W + GUTTER + 2 * PAD_X)
        val height = metrics.lineHeight(style.fontSize) + 2 * PAD_Y + t.columns.size * (metrics.lineHeight(rowSize) + PAD_Y) + PAD_Y
        return Size(width, height)
    }
}
