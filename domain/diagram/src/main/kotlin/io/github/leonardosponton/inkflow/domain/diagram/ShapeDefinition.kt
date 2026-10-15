package io.github.leonardosponton.inkflow.domain.diagram

import io.github.leonardosponton.inkflow.core.geometry.Side
import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.core.model.ShapeStyle
import io.github.leonardosponton.inkflow.core.model.Size
import io.github.leonardosponton.inkflow.core.model.Vec2

/** A connection point on a shape. */
data class PortDefinition(val id: String, val anchor: Anchor, val side: Side) {
    fun position(frame: Rect): Vec2 = anchor.resolve(frame)
}

object Ports {
    val North = PortDefinition("n", at(0.5, 0.0), Side.NORTH)
    val East = PortDefinition("e", at(1.0, 0.5), Side.EAST)
    val South = PortDefinition("s", at(0.5, 1.0), Side.SOUTH)
    val West = PortDefinition("w", at(0.0, 0.5), Side.WEST)
    val Cardinal = listOf(North, East, South, West)

    /** Cardinal ports plus two extra per side, for wide shapes such as tables and classes. */
    val Dense = Cardinal + listOf(
        PortDefinition("n1", at(0.25, 0.0), Side.NORTH),
        PortDefinition("n3", at(0.75, 0.0), Side.NORTH),
        PortDefinition("s1", at(0.25, 1.0), Side.SOUTH),
        PortDefinition("s3", at(0.75, 1.0), Side.SOUTH),
        PortDefinition("e1", at(1.0, 0.25), Side.EAST),
        PortDefinition("e3", at(1.0, 0.75), Side.EAST),
        PortDefinition("w1", at(0.0, 0.25), Side.WEST),
        PortDefinition("w3", at(0.0, 0.75), Side.WEST),
    )
}

/** Where a shape's label goes, as two anchors. */
data class TextArea(val topLeft: Anchor, val bottomRight: Anchor) {
    fun resolve(frame: Rect): Rect = Rect.fromPoints(topLeft.resolve(frame), bottomRight.resolve(frame))

    companion object {
        private const val PAD_X = 6.0
        private const val PAD_Y = 4.0

        val Full = TextArea(at(0.0, 0.0, PAD_X, PAD_Y), at(1.0, 1.0, -PAD_X, -PAD_Y))

        fun inset(fx: Double, fy: Double) = TextArea(at(fx, fy), at(1 - fx, 1 - fy))
    }
}

/**
 * Data-driven description of a node type. Notations register these; the renderer only interprets
 * [outline], [textArea] and the content layout, so new notations never touch rendering code.
 */
data class ShapeDefinition(
    val key: ShapeTypeKey,
    val notation: NotationId,
    val displayName: String,
    val defaultSize: Size,
    val outline: ShapeOutline,
    val ports: List<PortDefinition> = Ports.Cardinal,
    val perimeter: Perimeter = Perimeter.RECTANGLE,
    val textArea: TextArea = TextArea.Full,
    val defaultLabel: String = "",
    val defaultStyle: ShapeStyle = ShapeStyle.Default,
    /** Children dropped inside move with it (frames, swimlanes, packages, boundaries). */
    val isContainer: Boolean = false,
    /** Container header height in world units; the label sits there. */
    val headerHeight: Double = 0.0,
    /** Header is a strip on the left (horizontal swimlanes) instead of on top; [headerHeight] is its width. */
    val headerOnLeft: Boolean = false,
    val keepAspectRatio: Boolean = false,
    /** Underline the label (key attributes in Chen notation). */
    val labelUnderline: Boolean = false,
    val minSize: Size = Size(MIN_SIDE, MIN_SIDE),
    val keywords: List<String> = emptyList(),
    /** Creates the initial content for a new node of this type. */
    val createContent: () -> NodeContent = { PlainLabel(defaultLabel) },
) {
    fun matches(query: String): Boolean {
        val q = query.trim().lowercase()
        return q.isEmpty() || displayName.lowercase().contains(q) || keywords.any { it.contains(q) } ||
            key.value.lowercase().contains(q)
    }
}

private const val MIN_SIDE = 16.0
