package io.github.leonardosponton.inkflow.domain.uml

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

/**
 * Compartments of a classifier: (stereotype + name), attributes, operations — or literals for an
 * enumeration. Static members are underlined, abstract ones italic.
 */
object UmlClassLayouter : ContentLayouter {
    private const val PAD_X = 10.0
    private const val PAD_Y = 6.0
    private const val MIN_WIDTH = 140.0
    private const val MEMBER_SCALE = 0.875

    override fun supports(content: NodeContent) = content is UmlClassContent

    private data class Line(val text: String, val bold: Boolean = false, val italic: Boolean = false, val underline: Boolean = false)

    private fun compartments(c: UmlClassContent): List<List<Line>> {
        val header = listOfNotNull(c.stereotypeLabel?.let { Line(it) }, Line(c.name, bold = true, italic = c.nameItalic))
        val members = if (c.kind == ClassifierKind.ENUMERATION) {
            listOf(c.literals.map { Line(it) })
        } else {
            listOf(c.attributes.map(::member), c.operations.map(::member))
        }
        return listOf(header) + members
    }

    private fun member(m: UmlMember) = Line(m.signature, italic = m.isAbstract, underline = m.isStatic)

    override fun layout(
        content: NodeContent,
        definition: ShapeDefinition,
        frame: Rect,
        style: ShapeStyle,
        metrics: TextMetrics,
    ): NodeLayout {
        val c = content as UmlClassContent
        val texts = mutableListOf<TextBlock>()
        val separators = mutableListOf<Pair<Vec2, Vec2>>()
        var y = frame.top + PAD_Y
        compartments(c).forEachIndexed { index, lines ->
            if (index > 0) {
                separators += Vec2(frame.left, y) to Vec2(frame.right, y)
                y += PAD_Y
            }
            val size = if (index == 0) style.fontSize else style.fontSize * MEMBER_SCALE
            val lh = metrics.lineHeight(size)
            lines.forEach { l ->
                val align = if (index == 0) TextAlign.CENTER else TextAlign.START
                val rect = Rect(frame.left + PAD_X, y, frame.right - PAD_X, (y + lh).coerceAtMost(frame.bottom).coerceAtLeast(y))
                texts += TextBlock(rect, l.text, size, align, l.bold, l.italic, l.underline, verticalCenter = false, wrap = false)
                y += lh
            }
            if (lines.isEmpty()) y += lh / 2
            y += PAD_Y
        }
        return NodeLayout(texts, separators.filter { it.first.y < frame.bottom })
    }

    override fun preferredSize(content: NodeContent, definition: ShapeDefinition, style: ShapeStyle, metrics: TextMetrics): Size {
        val c = content as UmlClassContent
        var width = MIN_WIDTH
        var height = PAD_Y
        compartments(c).forEachIndexed { index, lines ->
            val size = if (index == 0) style.fontSize else style.fontSize * MEMBER_SCALE
            val lh = metrics.lineHeight(size)
            lines.forEach { width = maxOf(width, metrics.width(it.text, size, it.bold) + 2 * PAD_X) }
            height += (if (lines.isEmpty()) lh / 2 else lh * lines.size) + 2 * PAD_Y
        }
        return Size(width, height)
    }
}
