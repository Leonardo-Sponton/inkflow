package io.github.leonardosponton.inkflow.feature.canvasrender

import android.util.LruCache
import androidx.ink.brush.Brush
import androidx.ink.brush.BrushFamily
import androidx.ink.brush.InputToolType
import androidx.ink.brush.StockBrushes
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import io.github.leonardosponton.inkflow.core.model.ColorArgb
import io.github.leonardosponton.inkflow.core.model.ElementId
import io.github.leonardosponton.inkflow.core.model.Vec2
import io.github.leonardosponton.inkflow.domain.canvas.element.BrushKind
import io.github.leonardosponton.inkflow.domain.canvas.element.InkBrush
import io.github.leonardosponton.inkflow.domain.canvas.element.InkStroke
import io.github.leonardosponton.inkflow.domain.canvas.element.StrokePoints

/** Bridges domain ink strokes and Jetpack Ink [Stroke]s. */
object InkBrushes {
    private val pen: BrushFamily by lazy { StockBrushes.pressurePen() }
    private val highlighter: BrushFamily by lazy { StockBrushes.highlighter() }
    private val marker: BrushFamily by lazy { StockBrushes.marker() }
    private const val EPSILON = 0.1f
    private const val HIGHLIGHTER_ALPHA = 0x66

    fun family(kind: BrushKind): BrushFamily = when (kind) {
        BrushKind.PEN -> pen
        BrushKind.HIGHLIGHTER -> highlighter
        BrushKind.PENCIL -> marker
    }

    /** Ink [Brush] for [brush], drawing with [displayColor] (palette-adapted). */
    fun toInk(brush: InkBrush, displayColor: Int = brush.color.argb): Brush {
        val color = if (brush.kind == BrushKind.HIGHLIGHTER) ColorArgb(displayColor).withAlpha(HIGHLIGHTER_ALPHA).argb else displayColor
        return Brush.createWithColorIntArgb(family(brush.kind), color, brush.size, EPSILON)
    }

    /** Domain stroke from a finished Ink stroke whose inputs are in world coordinates. */
    fun fromInk(id: ElementId, stroke: Stroke, brush: InkBrush): InkStroke? {
        val inputs = stroke.inputs
        if (inputs.size == 0) return null
        val first = inputs.get(0)
        val origin = Vec2(first.x.toDouble(), first.y.toDouble())
        val b = StrokePoints.Builder()
        val scratch = StrokeInput()
        for (i in 0 until inputs.size) {
            val s = inputs.populate(i, scratch)
            b.add(
                s.x - first.x,
                s.y - first.y,
                s.elapsedTimeMillis.toFloat(),
                s.pressure.takeIf { it >= 0f } ?: 1f,
                s.tiltRadians.takeIf { it >= 0f } ?: 0f,
                s.orientationRadians.takeIf { it >= 0f } ?: 0f,
            )
        }
        return InkStroke(id, origin, brush, b.build())
    }

    /** Ink stroke in the domain stroke's *local* coordinates (relative to its origin). */
    fun toInkStroke(s: InkStroke, displayColor: Int): Stroke {
        val batch = MutableStrokeInputBatch()
        val p = s.points
        val input = StrokeInput()
        var lastTime = -1L
        for (i in 0 until p.size) {
            val t = maxOf(p.time(i).toLong(), lastTime)
            lastTime = t
            input.update(
                x = p.x(i),
                y = p.y(i),
                elapsedTimeMillis = t,
                toolType = InputToolType.STYLUS,
                pressure = p.pressure(i),
                tiltRadians = p.tilt(i).coerceIn(0f, MAX_TILT),
                orientationRadians = p.orientation(i).coerceIn(0f, MAX_ORIENTATION),
            )
            batch.add(input)
        }
        return Stroke(toInk(s.brush, displayColor), batch)
    }

    private const val MAX_TILT = (Math.PI / 2).toFloat()
    private const val MAX_ORIENTATION = (Math.PI * 2).toFloat() - 0.0001f
}

/** Meshes are expensive to build, so finished strokes are cached by element value. */
class InkStrokeCache {
    /** Cheap key: element id, identity of its (immutable) point buffer, brush and display color. */
    private data class Key(val id: ElementId, val points: Int, val brush: InkBrush, val color: Int)

    private val cache = object : LruCache<Key, Stroke>(CACHE_SIZE) {}

    fun get(s: InkStroke, displayColor: Int): Stroke {
        val key = Key(s.id, System.identityHashCode(s.points.data), s.brush, displayColor)
        cache.get(key)?.let { return it }
        return InkBrushes.toInkStroke(s, displayColor).also { cache.put(key, it) }
    }

    private companion object {
        const val CACHE_SIZE = 6000
    }
}
