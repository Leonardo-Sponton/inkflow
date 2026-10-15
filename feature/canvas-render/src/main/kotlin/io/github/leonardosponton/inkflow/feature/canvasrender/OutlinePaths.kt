package io.github.leonardosponton.inkflow.feature.canvasrender

import android.graphics.Path
import android.graphics.RectF
import android.util.LruCache
import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.core.model.Vec2
import io.github.leonardosponton.inkflow.domain.diagram.PathOp
import io.github.leonardosponton.inkflow.domain.diagram.ShapeOutline
import io.github.leonardosponton.inkflow.domain.diagram.ShapePath
import io.github.leonardosponton.inkflow.domain.diagram.ShapeTypeKey

/** Converts declarative [ShapeOutline]s into android [Path]s, cached per shape type and size. */
class OutlinePaths {
    private data class Key(val shape: String, val w: Float, val h: Float)

    private val cache = object : LruCache<Key, List<Path>>(CACHE_SIZE) {}

    /** Paths in local coordinates (frame origin at 0,0); translate the canvas to the frame's top-left. */
    fun local(shape: ShapeTypeKey, outline: ShapeOutline, width: Double, height: Double): List<Path> {
        val key = Key(shape.value, width.toFloat(), height.toFloat())
        cache.get(key)?.let { return it }
        val frame = Rect(0.0, 0.0, width, height)
        val built = outline.paths.map { build(it, frame) }
        cache.put(key, built)
        return built
    }

    companion object {
        private const val CACHE_SIZE = 512

        fun build(sp: ShapePath, frame: Rect): Path {
            val path = Path()
            sp.ops.forEach { op -> append(path, op, frame) }
            return path
        }

        private fun append(path: Path, op: PathOp, frame: Rect) {
            when (op) {
                is PathOp.MoveTo -> op.p.resolve(frame).let { path.moveTo(it.x.toFloat(), it.y.toFloat()) }
                is PathOp.LineTo -> op.p.resolve(frame).let { path.lineTo(it.x.toFloat(), it.y.toFloat()) }
                is PathOp.QuadTo -> {
                    val c = op.c.resolve(frame)
                    val p = op.p.resolve(frame)
                    path.quadTo(c.x.toFloat(), c.y.toFloat(), p.x.toFloat(), p.y.toFloat())
                }
                is PathOp.CubicTo -> {
                    val a = op.c1.resolve(frame)
                    val b = op.c2.resolve(frame)
                    val p = op.p.resolve(frame)
                    path.cubicTo(a.x.toFloat(), a.y.toFloat(), b.x.toFloat(), b.y.toFloat(), p.x.toFloat(), p.y.toFloat())
                }
                is PathOp.Oval -> path.addOval(rectF(op.topLeft.resolve(frame), op.bottomRight.resolve(frame)), Path.Direction.CW)
                is PathOp.Arc -> path.arcTo(
                    rectF(op.topLeft.resolve(frame), op.bottomRight.resolve(frame)),
                    op.startDeg.toFloat(),
                    op.sweepDeg.toFloat(),
                    false,
                )
                PathOp.Close -> path.close()
            }
        }

        private fun rectF(a: Vec2, b: Vec2) =
            RectF(minOf(a.x, b.x).toFloat(), minOf(a.y, b.y).toFloat(), maxOf(a.x, b.x).toFloat(), maxOf(a.y, b.y).toFloat())
    }
}
