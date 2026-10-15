package io.github.leonardosponton.inkflow.feature.canvasrender

import io.github.leonardosponton.inkflow.core.geometry.pointAlongPolyline
import io.github.leonardosponton.inkflow.core.model.Size
import io.github.leonardosponton.inkflow.core.model.Vec2
import io.github.leonardosponton.inkflow.domain.canvas.element.Connector
import io.github.leonardosponton.inkflow.domain.canvas.element.LabelPosition
import io.github.leonardosponton.inkflow.domain.canvas.notation.NotationCatalog

/** Where connector labels go, shared by the screen renderer and the SVG writer. */
object ConnectorLabels {
    private const val GAP = 8.0

    /**
     * Middle labels sit on the line; start/end labels (multiplicities, roles) sit beside the line,
     * just past the end marker, so they never cover it. [line] is the flattened route.
     */
    fun anchor(c: Connector, line: List<Vec2>, pos: LabelPosition, textSize: Size, catalog: NotationCatalog): Vec2 {
        if (pos == LabelPosition.MIDDLE || line.size < 2) return pointAlongPolyline(line, pos.fraction)
        val fromStart = pos == LabelPosition.START
        val pts = if (fromStart) line else line.asReversed()
        val marker = catalog.markers[if (fromStart) c.startMarker else c.endMarker]
        val along = (marker?.lineInset ?: 0.0) + GAP + textSize.width / 2
        val base = pointAtDistance(pts, along)
        val dir = (pts[1] - pts[0]).normalized()
        return base + Vec2(-dir.y, dir.x) * (textSize.height / 2 + GAP / 2)
    }

    private fun pointAtDistance(pts: List<Vec2>, distance: Double): Vec2 {
        var remaining = distance
        for (i in 0 until pts.lastIndex) {
            val seg = pts[i].distanceTo(pts[i + 1])
            if (remaining <= seg) return pts[i].lerp(pts[i + 1], if (seg == 0.0) 0.0 else remaining / seg)
            remaining -= seg
        }
        return pts.last()
    }
}
