package io.github.leonardosponton.inkflow.domain.diagram

import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.core.model.ShapeStyle
import io.github.leonardosponton.inkflow.core.model.Size
import io.github.leonardosponton.inkflow.core.model.TextAlign
import io.github.leonardosponton.inkflow.core.model.Vec2
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Structured data shown inside a node (a label, a UML class, a database table…). Open for
 * extension: notation modules add subclasses and register them for polymorphic serialization.
 */
interface NodeContent {
    /** Plain text used for search and accessibility. */
    val summary: String
}

@Serializable
@SerialName("label")
data class PlainLabel(val text: String = "") : NodeContent {
    override val summary: String get() = text
}

/** Meaning of a connector beyond its look (UML relation, DB relationship…). Open for extension. */
interface EdgeSemantics

@Serializable
@SerialName("plain")
data object PlainEdge : EdgeSemantics

/** Measures text so layouts can be computed in pure Kotlin; the renderer provides the real one. */
interface TextMetrics {
    fun lineHeight(fontSize: Double): Double

    fun width(text: String, fontSize: Double, bold: Boolean = false): Double

    /** Size of [text] wrapped to [maxWidth] (no wrapping when `null`). */
    fun measure(text: String, fontSize: Double, bold: Boolean = false, maxWidth: Double? = null): Size
}

/** Approximate metrics (monospace-ish) for tests and headless use. */
object ApproximateTextMetrics : TextMetrics {
    private const val CHAR_WIDTH = 0.55
    private const val LINE_HEIGHT = 1.3

    override fun lineHeight(fontSize: Double) = fontSize * LINE_HEIGHT

    override fun width(text: String, fontSize: Double, bold: Boolean) = text.length * fontSize * CHAR_WIDTH

    override fun measure(text: String, fontSize: Double, bold: Boolean, maxWidth: Double?): Size {
        val lines = text.split('\n')
        val charsPerLine = maxWidth?.let { (it / (fontSize * CHAR_WIDTH)).toInt().coerceAtLeast(1) }
        val rows = lines.sumOf { line -> if (charsPerLine == null) 1 else 1 + (line.length - 1).coerceAtLeast(0) / charsPerLine }
        val widest = lines.maxOf { width(it, fontSize, bold) }
        return Size(maxWidth?.let { minOf(it, widest) } ?: widest, rows * lineHeight(fontSize))
    }
}

/** A run of text to draw at [rect]. */
data class TextBlock(
    val rect: Rect,
    val text: String,
    val fontSize: Double,
    val align: TextAlign = TextAlign.CENTER,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val verticalCenter: Boolean = true,
    val wrap: Boolean = true,
)

/** Everything inside a node except its outline: text and separator lines. */
data class NodeLayout(
    val texts: List<TextBlock> = emptyList(),
    val separators: List<Pair<Vec2, Vec2>> = emptyList(),
) {
    companion object {
        val Empty = NodeLayout()
    }
}

/** Lays out one kind of [NodeContent]. */
interface ContentLayouter {
    fun supports(content: NodeContent): Boolean

    fun layout(content: NodeContent, definition: ShapeDefinition, frame: Rect, style: ShapeStyle, metrics: TextMetrics): NodeLayout

    /** Size the node needs to show all content, or `null` when it can be any size. */
    fun preferredSize(content: NodeContent, definition: ShapeDefinition, style: ShapeStyle, metrics: TextMetrics): Size? =
        null
}

/** Centered label in the shape's text area (or header, for containers). */
object PlainLabelLayouter : ContentLayouter {
    override fun supports(content: NodeContent) = content is PlainLabel

    override fun layout(
        content: NodeContent,
        definition: ShapeDefinition,
        frame: Rect,
        style: ShapeStyle,
        metrics: TextMetrics,
    ): NodeLayout {
        val text = content.summary
        if (text.isEmpty()) return NodeLayout.Empty
        val area = if (definition.isContainer && definition.headerHeight > 0) {
            Rect(frame.left + PAD, frame.top, frame.right - PAD, frame.top + definition.headerHeight)
        } else {
            definition.textArea.resolve(frame)
        }
        return NodeLayout(
            texts = listOf(
                TextBlock(area, text, style.fontSize, style.textAlign, bold = style.bold || definition.isContainer, italic = style.italic),
            ),
        )
    }

    private const val PAD = 8.0
}
