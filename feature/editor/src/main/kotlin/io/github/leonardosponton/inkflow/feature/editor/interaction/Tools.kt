package io.github.leonardosponton.inkflow.feature.editor.interaction

import io.github.leonardosponton.inkflow.core.model.ColorArgb
import io.github.leonardosponton.inkflow.domain.canvas.element.BrushKind
import io.github.leonardosponton.inkflow.domain.canvas.element.InkBrush
import io.github.leonardosponton.inkflow.domain.canvas.element.Routing
import io.github.leonardosponton.inkflow.domain.diagram.BasicShapes
import io.github.leonardosponton.inkflow.domain.diagram.MarkerKey
import io.github.leonardosponton.inkflow.domain.diagram.ShapeTypeKey

enum class ToolKind { SELECT, LASSO, PEN, HIGHLIGHTER, ERASER, TEXT, STICKY, SHAPE, CONNECTOR }

enum class EraserMode { STROKE, PARTIAL }

data class ToolState(
    val kind: ToolKind = ToolKind.PEN,
    val pen: InkBrush = InkBrush(BrushKind.PEN, ColorArgb.Black, DEFAULT_PEN),
    val highlighter: InkBrush = InkBrush(BrushKind.HIGHLIGHTER, ColorArgb(0xFFFFEB3B.toInt()), DEFAULT_HIGHLIGHTER),
    val eraserMode: EraserMode = EraserMode.STROKE,
    /** Eraser radius in screen pixels. */
    val eraserSize: Double = DEFAULT_ERASER,
    val shape: ShapeTypeKey = BasicShapes.Rectangle.key,
    val routing: Routing = Routing.ORTHOGONAL,
    val startMarker: MarkerKey = MarkerKey.None,
    val endMarker: MarkerKey = MarkerKey.Arrow,
    val recentShapes: List<ShapeTypeKey> = emptyList(),
) {
    fun withRecent(key: ShapeTypeKey) = copy(recentShapes = (listOf(key) + recentShapes.filterNot { it == key }).take(MAX_RECENT))

    val activeBrush: InkBrush? get() = when (kind) {
        ToolKind.PEN -> pen
        ToolKind.HIGHLIGHTER -> highlighter
        else -> null
    }

    companion object {
        const val DEFAULT_PEN = 3f
        const val DEFAULT_HIGHLIGHTER = 18f
        const val DEFAULT_ERASER = 16.0
        const val MAX_RECENT = 8
        val PenSizes = listOf(1.5f, 3f, 5f, 8f, 13f)
        val HighlighterSizes = listOf(10f, 18f, 28f)
    }
}
