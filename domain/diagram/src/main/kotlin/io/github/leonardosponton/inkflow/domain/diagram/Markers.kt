package io.github.leonardosponton.inkflow.domain.diagram

import kotlinx.serialization.Serializable

/** End decoration of a connector (arrowheads, UML diamonds, crow's feet). */
@Serializable
@JvmInline
value class MarkerKey(val value: String) {
    companion object {
        val None = MarkerKey("none")
        val Arrow = MarkerKey("arrow")
        val ArrowFilled = MarkerKey("arrow.filled")
        val TriangleHollow = MarkerKey("triangle.hollow")
        val DiamondFilled = MarkerKey("diamond.filled")
        val DiamondHollow = MarkerKey("diamond.hollow")
        val CircleFilled = MarkerKey("circle.filled")
        val CircleHollow = MarkerKey("circle.hollow")
        val Bar = MarkerKey("bar")
    }
}

/**
 * Marker drawn in its own space: the connector end is at the origin and the line arrives from
 * negative x, so the marker "points" towards +x. Anchors use only offsets (fractions are 0).
 * [lineInset] is how far the connector line stops before the end, so hollow markers stay hollow.
 */
data class MarkerDefinition(
    val key: MarkerKey,
    val displayName: String,
    val outline: ShapeOutline,
    val lineInset: Double = 0.0,
)

/** Offset-only anchor for marker space. */
fun mk(x: Double, y: Double) = Anchor(0.0, 0.0, x, y)

class MarkerRegistry(definitions: Iterable<MarkerDefinition>) {
    private val byKey = definitions.associateBy { it.key }

    val all: List<MarkerDefinition> = byKey.values.toList()

    operator fun get(key: MarkerKey): MarkerDefinition? = byKey[key]
}

/** Generic markers available to every notation. */
object BasicMarkers {
    private const val L = 14.0
    private const val W = 6.0

    val all: List<MarkerDefinition> = listOf(
        MarkerDefinition(MarkerKey.None, "None", ShapeOutline.Empty),
        MarkerDefinition(
            MarkerKey.Arrow,
            "Arrow",
            outline {
                path(PathFill.NONE) {
                    move(mk(-L, -W))
                    line(mk(0.0, 0.0))
                    line(mk(-L, W))
                }
            },
        ),
        MarkerDefinition(MarkerKey.ArrowFilled, "Filled arrow", triangle(PathFill.STROKE_COLOR), lineInset = L - 1),
        MarkerDefinition(MarkerKey.TriangleHollow, "Hollow triangle", triangle(PathFill.FILL), lineInset = L + 2),
        MarkerDefinition(MarkerKey.DiamondFilled, "Filled diamond", diamond(PathFill.STROKE_COLOR), lineInset = 2 * L),
        MarkerDefinition(MarkerKey.DiamondHollow, "Hollow diamond", diamond(PathFill.FILL), lineInset = 2 * L),
        MarkerDefinition(MarkerKey.CircleFilled, "Filled circle", circle(PathFill.STROKE_COLOR), lineInset = 2 * W),
        MarkerDefinition(MarkerKey.CircleHollow, "Hollow circle", circle(PathFill.FILL), lineInset = 2 * W),
        MarkerDefinition(MarkerKey.Bar, "Bar", outline { line(mk(-W, -W * 1.5), mk(-W, W * 1.5)) }),
    )

    private fun triangle(fill: PathFill) = outline {
        path(fill) {
            move(mk(0.0, 0.0))
            line(mk(-L - 2, -W - 1))
            line(mk(-L - 2, W + 1))
            close()
        }
    }

    private fun diamond(fill: PathFill) = outline {
        path(fill) {
            move(mk(0.0, 0.0))
            line(mk(-L, -W))
            line(mk(-2 * L, 0.0))
            line(mk(-L, W))
            close()
        }
    }

    private fun circle(fill: PathFill) = outline { oval(mk(-2 * W, -W), mk(0.0, W), fill) }
}
