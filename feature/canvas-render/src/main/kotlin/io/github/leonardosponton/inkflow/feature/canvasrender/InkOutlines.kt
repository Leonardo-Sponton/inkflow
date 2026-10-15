package io.github.leonardosponton.inkflow.feature.canvasrender

import android.graphics.Path
import androidx.ink.geometry.MutableVec
import androidx.ink.strokes.Stroke

/**
 * Exact vector outline of an Ink stroke mesh, for canvases that cannot draw meshes (PDF) and
 * for SVG. Coordinates are in the stroke's own space.
 */
object InkOutlines {
    fun path(stroke: Stroke): Path {
        val shape = stroke.shape
        val v = MutableVec()
        val path = Path().apply { fillType = Path.FillType.WINDING }
        for (g in 0 until shape.getRenderGroupCount()) {
            for (o in 0 until shape.getOutlineCount(g)) outline(stroke, g, o, v, path)
        }
        return path
    }

    private fun outline(stroke: Stroke, group: Int, index: Int, v: MutableVec, path: Path) {
        val shape = stroke.shape
        for (i in 0 until shape.getOutlineVertexCount(group, index)) {
            shape.populateOutlinePosition(group, index, i, v)
            if (i == 0) path.moveTo(v.x, v.y) else path.lineTo(v.x, v.y)
        }
        path.close()
    }
}
