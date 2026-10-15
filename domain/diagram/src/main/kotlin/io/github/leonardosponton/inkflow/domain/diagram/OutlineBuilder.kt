package io.github.leonardosponton.inkflow.domain.diagram

/** Small DSL so notation modules can describe outlines declaratively. */
class OutlineBuilder {
    private val paths = mutableListOf<ShapePath>()

    fun path(fill: PathFill = PathFill.FILL, stroked: Boolean = true, block: PathBuilder.() -> Unit) {
        paths += ShapePath(PathBuilder().apply(block).ops, fill, stroked)
    }

    /** Closed polygon through fractional points `(fx, fy)`. */
    fun polygon(vararg points: Pair<Double, Double>, fill: PathFill = PathFill.FILL) = path(fill) {
        points.forEachIndexed { i, (x, y) -> if (i == 0) move(x, y) else line(x, y) }
        close()
    }

    /** Open polyline (decoration) through fractional points. */
    fun polyline(vararg points: Anchor) = path(PathFill.NONE) {
        points.forEachIndexed { i, a -> if (i == 0) move(a) else line(a) }
    }

    fun line(from: Anchor, to: Anchor) = polyline(from, to)

    fun rect(fill: PathFill = PathFill.FILL) = polygon(0.0 to 0.0, 1.0 to 0.0, 1.0 to 1.0, 0.0 to 1.0, fill = fill)

    fun oval(topLeft: Anchor = at(0.0, 0.0), bottomRight: Anchor = at(1.0, 1.0), fill: PathFill = PathFill.FILL) =
        path(fill) { ops += PathOp.Oval(topLeft, bottomRight) }

    fun build() = ShapeOutline(paths.toList())
}

class PathBuilder {
    val ops = mutableListOf<PathOp>()

    fun move(fx: Double, fy: Double, dx: Double = 0.0, dy: Double = 0.0) = move(at(fx, fy, dx, dy))

    fun move(a: Anchor) {
        ops += PathOp.MoveTo(a)
    }

    fun line(fx: Double, fy: Double, dx: Double = 0.0, dy: Double = 0.0) = line(at(fx, fy, dx, dy))

    fun line(a: Anchor) {
        ops += PathOp.LineTo(a)
    }

    fun quad(c: Anchor, p: Anchor) {
        ops += PathOp.QuadTo(c, p)
    }

    fun cubic(c1: Anchor, c2: Anchor, p: Anchor) {
        ops += PathOp.CubicTo(c1, c2, p)
    }

    fun arc(topLeft: Anchor, bottomRight: Anchor, startDeg: Double, sweepDeg: Double) {
        ops += PathOp.Arc(topLeft, bottomRight, startDeg, sweepDeg)
    }

    fun close() {
        ops += PathOp.Close
    }
}

fun outline(block: OutlineBuilder.() -> Unit): ShapeOutline = OutlineBuilder().apply(block).build()
