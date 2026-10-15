package io.github.leonardosponton.inkflow.domain.diagram

import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.core.model.Vec2
import kotlinx.serialization.Serializable

/** Identifies a shape definition, e.g. `flowchart.decision`. Unknown keys render as a rectangle. */
@Serializable
@JvmInline
value class ShapeTypeKey(val value: String)

/** Identifies a notation (group of shapes), e.g. `uml`. */
@JvmInline
value class NotationId(val value: String) {
    companion object {
        val Basic = NotationId("basic")
    }
}

/**
 * A point relative to a frame: `left + fx·width + dx`, `top + fy·height + dy`.
 * Fractions scale with the shape; offsets keep fixed-size details (folded corners, bars) crisp.
 */
data class Anchor(val fx: Double, val fy: Double, val dx: Double = 0.0, val dy: Double = 0.0) {
    fun resolve(frame: Rect) = Vec2(frame.left + fx * frame.width + dx, frame.top + fy * frame.height + dy)
}

fun at(fx: Double, fy: Double, dx: Double = 0.0, dy: Double = 0.0) = Anchor(fx, fy, dx, dy)

sealed interface PathOp {
    data class MoveTo(val p: Anchor) : PathOp

    data class LineTo(val p: Anchor) : PathOp

    data class QuadTo(val c: Anchor, val p: Anchor) : PathOp

    data class CubicTo(val c1: Anchor, val c2: Anchor, val p: Anchor) : PathOp

    /** Ellipse inscribed in the rectangle spanned by two anchors (a closed sub-path). */
    data class Oval(val topLeft: Anchor, val bottomRight: Anchor) : PathOp

    /** Elliptical arc of the oval spanned by the anchors, angles in degrees (0 = east, clockwise). */
    data class Arc(val topLeft: Anchor, val bottomRight: Anchor, val startDeg: Double, val sweepDeg: Double) : PathOp

    data object Close : PathOp
}

enum class PathFill {
    /** Filled with the style's fill color. */
    FILL,

    /** Filled with the stroke color (solid dots, bars, filled markers). */
    STROKE_COLOR,

    /** Outline only. */
    NONE,
}

data class ShapePath(val ops: List<PathOp>, val fill: PathFill = PathFill.FILL, val stroked: Boolean = true, val dashed: Boolean = false)

/** Outline the renderer draws for a shape; several sub-paths allow decorations (bars, folds, waves). */
data class ShapeOutline(val paths: List<ShapePath>) {
    companion object {
        val Empty = ShapeOutline(emptyList())
    }
}

/** How floating connector ends meet the shape boundary. */
enum class Perimeter { RECTANGLE, ELLIPSE, DIAMOND }
