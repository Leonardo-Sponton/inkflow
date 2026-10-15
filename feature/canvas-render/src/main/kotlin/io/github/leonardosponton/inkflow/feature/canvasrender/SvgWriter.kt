@file:Suppress("MaxLineLength", "ktlint:standard:max-line-length")

package io.github.leonardosponton.inkflow.feature.canvasrender

import android.util.Base64
import androidx.ink.geometry.MutableVec
import io.github.leonardosponton.inkflow.core.model.AssetId
import io.github.leonardosponton.inkflow.core.model.ColorArgb
import io.github.leonardosponton.inkflow.core.model.DashPattern
import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.core.model.ShapeStyle
import io.github.leonardosponton.inkflow.core.model.Size
import io.github.leonardosponton.inkflow.core.model.TextAlign
import io.github.leonardosponton.inkflow.core.model.Vec2
import io.github.leonardosponton.inkflow.domain.canvas.connector.ConnectorRouter
import io.github.leonardosponton.inkflow.domain.canvas.element.BrushKind
import io.github.leonardosponton.inkflow.domain.canvas.element.CanvasElement
import io.github.leonardosponton.inkflow.domain.canvas.element.Connector
import io.github.leonardosponton.inkflow.domain.canvas.element.ImageElement
import io.github.leonardosponton.inkflow.domain.canvas.element.InkStroke
import io.github.leonardosponton.inkflow.domain.canvas.element.Routing
import io.github.leonardosponton.inkflow.domain.canvas.element.ShapeNode
import io.github.leonardosponton.inkflow.domain.canvas.element.TextElement
import io.github.leonardosponton.inkflow.domain.canvas.element.TextKind
import io.github.leonardosponton.inkflow.domain.canvas.notation.NotationCatalog
import io.github.leonardosponton.inkflow.domain.canvas.scene.Scene
import io.github.leonardosponton.inkflow.domain.diagram.PathFill
import io.github.leonardosponton.inkflow.domain.diagram.PathOp
import io.github.leonardosponton.inkflow.domain.diagram.ShapePath
import io.github.leonardosponton.inkflow.domain.diagram.TextBlock
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Vector export. Shapes, connectors and text are written as SVG primitives; ink uses the exact
 * outlines Jetpack Ink computed for the stroke mesh; images are embedded as data URIs.
 */
class SvgWriter(
    private val catalog: NotationCatalog,
    private val metrics: AndroidTextMetrics,
    private val assetBytes: (AssetId) -> Pair<ByteArray, String>?,
) {
    private val palette = CanvasPalette.Light

    fun write(elements: List<CanvasElement>, transparent: Boolean, padding: Double = SceneRasterizer.DEFAULT_PADDING): String {
        val area = Rect.unionAll(elements.map { it.bounds })?.inflate(padding) ?: Rect(0.0, 0.0, 1.0, 1.0)
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append(
            """<svg xmlns="http://www.w3.org/2000/svg" viewBox="${n(area.left)} ${n(area.top)} ${n(area.width)} ${n(area.height)}" """,
        )
        sb.append("""width="${n(area.width)}" height="${n(area.height)}" font-family="sans-serif">""").append('\n')
        if (!transparent) {
            sb.append(
                """<rect x="${n(
                    area.left,
                )}" y="${n(area.top)}" width="${n(area.width)}" height="${n(area.height)}" fill="${color(palette.background)}"/>""",
            ).append('\n')
        }
        elements.sortedWith(Scene.Z_ORDER).forEach { el ->
            when (el) {
                is ShapeNode -> node(sb, el)
                is Connector -> connector(sb, el)
                is InkStroke -> ink(sb, el)
                is TextElement -> text(sb, el)
                is ImageElement -> image(sb, el)
            }
        }
        sb.append("</svg>\n")
        return sb.toString()
    }

    // ---- shapes ---------------------------------------------------------------------------------

    private fun node(sb: StringBuilder, node: ShapeNode) {
        val def = catalog.shapes.resolve(node.shape)
        val s = node.style
        sb.append("""<g opacity="${n(s.opacity)}">""")
        def.outline.paths.forEach { sp -> shapePath(sb, sp, node.frame, s) }
        val layout = catalog.layouts.layouterFor(node.content).layout(node.content, def, node.frame, s, metrics)
        layout.separators.forEach { (a, b) ->
            sb.append(
                """<line x1="${n(
                    a.x,
                )}" y1="${n(
                    a.y,
                )}" x2="${n(b.x)}" y2="${n(b.y)}" stroke="${color(s.stroke.argb)}" stroke-width="${n(s.strokeWidth * SEPARATOR)}"/>""",
            )
        }
        layout.texts.forEach { textBlock(sb, it, s.textColor.argb) }
        sb.append("</g>\n")
    }

    private fun shapePath(sb: StringBuilder, sp: ShapePath, frame: Rect, s: ShapeStyle) {
        val d = pathData(sp.ops, frame)
        val fill = when (sp.fill) {
            PathFill.FILL -> s.fill?.let { color(it.argb) } ?: "none"
            PathFill.STROKE_COLOR -> color(s.stroke.argb)
            PathFill.NONE -> "none"
        }
        val stroke = if (sp.stroked && s.strokeWidth > 0) color(s.stroke.argb) else "none"
        val dash = dashAttr(if (sp.dashed) DashPattern.DASHED else s.dash, s.strokeWidth)
        sb.append("""<path d="$d" fill="$fill" stroke="$stroke" stroke-width="${n(s.strokeWidth)}" stroke-linejoin="round"$dash/>""")
    }

    /** SVG path data for declarative ops resolved against [frame]. */
    fun pathData(ops: List<PathOp>, frame: Rect): String = buildString {
        var current = Vec2.Zero
        ops.forEach { op ->
            when (op) {
                is PathOp.MoveTo -> op.p.resolve(frame).also {
                    current = it
                    append("M${p(it)} ")
                }
                is PathOp.LineTo -> op.p.resolve(frame).also {
                    current = it
                    append("L${p(it)} ")
                }
                is PathOp.QuadTo -> op.p.resolve(frame).also {
                    append("Q${p(op.c.resolve(frame))} ${p(it)} ")
                    current = it
                }
                is PathOp.CubicTo -> op.p.resolve(frame).also {
                    append("C${p(op.c1.resolve(frame))} ${p(op.c2.resolve(frame))} ${p(it)} ")
                    current = it
                }
                is PathOp.Oval -> append(oval(Rect.fromPoints(op.topLeft.resolve(frame), op.bottomRight.resolve(frame))))
                is PathOp.Arc ->
                    current =
                        arc(
                            this,
                            Rect.fromPoints(op.topLeft.resolve(frame), op.bottomRight.resolve(frame)),
                            op.startDeg,
                            op.sweepDeg,
                            current,
                        )
                PathOp.Close -> append("Z ")
            }
        }
    }.trim()

    private fun oval(r: Rect): String {
        val rx = r.width / 2
        val ry = r.height / 2
        val half = "A${n(rx)},${n(ry)} 0 1,0"
        return "M${n(r.left)},${n(r.center.y)} $half ${n(r.right)},${n(r.center.y)} $half ${n(r.left)},${n(r.center.y)} Z "
    }

    private fun arc(sb: StringBuilder, r: Rect, startDeg: Double, sweepDeg: Double, current: Vec2): Vec2 {
        val c = r.center
        val rx = r.width / 2
        val ry = r.height / 2

        fun at(deg: Double) = Vec2(c.x + rx * cos(Math.toRadians(deg)), c.y + ry * sin(Math.toRadians(deg)))
        val start = at(startDeg)
        val end = at(startDeg + sweepDeg)
        sb.append(if (start.distanceTo(current) > EPS) "L${p(start)} " else "")
        val large = if (kotlin.math.abs(sweepDeg) > HALF_TURN) 1 else 0
        val sweep = if (sweepDeg > 0) 1 else 0
        sb.append("A${n(rx)},${n(ry)} 0 $large,$sweep ${p(end)} ")
        return end
    }

    // ---- connectors -----------------------------------------------------------------------------

    private fun connector(sb: StringBuilder, c: Connector) {
        if (c.route.size < 2) return
        val s = c.style
        val d = if (c.routing == Routing.CURVED && c.route.size >= CUBIC) {
            buildString {
                append("M${p(c.route[0])} ")
                var i = 1
                while (i + 2 < c.route.size) {
                    append("C${p(c.route[i])} ${p(c.route[i + 1])} ${p(c.route[i + 2])} ")
                    i += CUBIC - 1
                }
            }
        } else {
            "M" + c.route.joinToString(" L") { p(it) }
        }
        sb.append(
            """<path d="$d" fill="none" stroke="${color(
                s.stroke.argb,
            )}" stroke-width="${n(s.strokeWidth)}"${dashAttr(s.dash, s.strokeWidth)}/>""",
        )
        marker(sb, c, start = false)
        marker(sb, c, start = true)
        connectorLabels(sb, c)
        sb.append('\n')
    }

    private fun connectorLabels(sb: StringBuilder, c: Connector) {
        val s = c.style
        c.labels.forEach { label ->
            val size = s.fontSize * LABEL_SCALE
            val m = metrics.measure(label.text, size, false, null)
            val at = ConnectorLabels.anchor(c, ConnectorRouter.flatten(c), label.position, m, catalog)
            sb.append(
                """<text x="${n(
                    at.x,
                )}" y="${n(
                    at.y + size / 3,
                )}" font-size="${n(
                    size,
                )}" text-anchor="middle" fill="${color(
                    s.textColor.argb,
                )}" paint-order="stroke" stroke="${color(palette.background)}" stroke-width="4">${esc(label.text)}</text>""",
            )
        }
    }

    private fun marker(sb: StringBuilder, c: Connector, start: Boolean) {
        val def = catalog.markers[if (start) c.startMarker else c.endMarker] ?: return
        if (def.outline.paths.isEmpty()) return
        val r = c.route
        val (tip, prev) = if (start) r[0] to r[1] else r.last() to r[r.lastIndex - 1]
        val angle = Math.toDegrees(atan2(tip.y - prev.y, tip.x - prev.x))
        val scale = (c.style.strokeWidth / BASE_STROKE).coerceIn(MIN_MARKER, MAX_MARKER)
        sb.append("""<g transform="translate(${p(tip)}) rotate(${n(angle)}) scale(${n(scale)})">""")
        val style = c.style.copy(fill = ColorArgb(palette.background), strokeWidth = c.style.strokeWidth / scale, dash = DashPattern.SOLID)
        def.outline.paths.forEach { shapePath(sb, it, Rect(0.0, 0.0, 0.0, 0.0), style) }
        sb.append("</g>")
    }

    // ---- text, ink, images ----------------------------------------------------------------------

    private fun text(sb: StringBuilder, t: TextElement) {
        val f = t.frame
        val bg = t.style.fill ?: if (t.kind == TextKind.STICKY) ColorArgb.StickyYellow else null
        bg?.let {
            sb.append("""<rect x="${n(f.left)}" y="${n(f.top)}" width="${n(f.width)}" height="${n(f.height)}" fill="${color(it.argb)}"/>""")
        }
        textBlock(
            sb,
            TextBlock(
                f.inflate(-TEXT_PAD),
                t.text,
                t.style.fontSize,
                t.style.textAlign,
                t.style.bold,
                t.style.italic,
                verticalCenter = false,
            ),
            t.style.textColor.argb,
        )
        sb.append('\n')
    }

    /** Text broken into lines exactly like the screen (StaticLayout), one `<text>` per line. */
    private fun textBlock(sb: StringBuilder, b: TextBlock, argb: Int) {
        if (b.text.isEmpty()) return
        val r = if (b.rotation != 0) Rect.fromCenter(b.rect.center, Size(b.rect.height, b.rect.width)) else b.rect
        val layout = metrics.layout(
            b.text,
            r.width.toInt().coerceAtLeast(1),
            b.fontSize.toFloat(),
            b.bold,
            b.italic,
            b.align,
            argb,
            b.underline,
        )
        val dy = if (b.verticalCenter) ((r.height - layout.height) / 2).coerceAtLeast(0.0) else 0.0
        val (anchor, x) = when (b.align) {
            TextAlign.START -> "start" to r.left
            TextAlign.CENTER -> "middle" to r.center.x
            TextAlign.END -> "end" to r.right
        }
        val rotate = if (b.rotation != 0) """ transform="rotate(${-b.rotation} ${p(b.rect.center)})"""" else ""
        sb.append("<g$rotate>")
        for (line in 0 until layout.lineCount) {
            val content = b.text.substring(layout.getLineStart(line), layout.getLineEnd(line)).trimEnd('\n')
            if (r.top + dy + layout.getLineBottom(line) > r.bottom + 1) break
            val y = r.top + dy + layout.getLineBaseline(line)
            val weight = if (b.bold) """ font-weight="bold"""" else ""
            val style = if (b.italic) """ font-style="italic"""" else ""
            val deco = if (b.underline) """ text-decoration="underline"""" else ""
            sb.append(
                """<text x="${n(
                    x,
                )}" y="${n(
                    y,
                )}" font-size="${n(b.fontSize)}" text-anchor="$anchor" fill="${color(argb)}"$weight$style$deco>${esc(content)}</text>""",
            )
        }
        sb.append("</g>")
    }

    private fun ink(sb: StringBuilder, s: InkStroke) {
        val stroke = InkBrushes.toInkStroke(s, s.brush.color.argb)
        val shape = stroke.shape
        val v = MutableVec()
        val d = StringBuilder()
        for (g in 0 until shape.getRenderGroupCount()) {
            for (o in 0 until shape.getOutlineCount(g)) {
                val count = shape.getOutlineVertexCount(g, o)
                for (i in 0 until count) {
                    shape.populateOutlinePosition(g, o, i, v)
                    d.append(if (i == 0) "M" else "L").append(n(s.origin.x + v.x)).append(',').append(n(s.origin.y + v.y)).append(' ')
                }
                d.append("Z ")
            }
        }
        val opacity = if (s.brush.kind == BrushKind.HIGHLIGHTER) HIGHLIGHTER_OPACITY else 1.0
        sb.append(
            """<path d="${d.toString().trim()}" fill="${color(s.brush.color.argb)}" fill-opacity="${n(opacity)}" fill-rule="nonzero"/>""",
        ).append('\n')
    }

    private fun image(sb: StringBuilder, img: ImageElement) {
        val (bytes, mime) = assetBytes(img.asset) ?: return
        val f = img.frame
        val data = Base64.encodeToString(bytes, Base64.NO_WRAP)
        sb.append(
            """<image x="${n(
                f.left,
            )}" y="${n(
                f.top,
            )}" width="${n(f.width)}" height="${n(f.height)}" preserveAspectRatio="none" href="data:$mime;base64,$data"/>""",
        ).append('\n')
    }

    // ---- formatting -----------------------------------------------------------------------------

    private fun dashAttr(d: DashPattern, w: Double) = when (d) {
        DashPattern.SOLID -> ""
        DashPattern.DASHED -> """ stroke-dasharray="${n(w * DASH_ON)} ${n(w * DASH_OFF)}""""
        DashPattern.DOTTED -> """ stroke-dasharray="0.1 ${n(w * DOT_GAP)}" stroke-linecap="round""""
    }

    private fun p(v: Vec2) = "${n(v.x)},${n(v.y)}"

    private fun n(d: Double) = String.format(Locale.ROOT, "%.2f", d).trimEnd('0').trimEnd('.')

    private fun n(f: Float) = n(f.toDouble())

    private fun color(argb: Int): String {
        val a = (argb ushr ALPHA_SHIFT) and MAX_CHANNEL
        val rgb = String.format(Locale.ROOT, "#%06X", argb and RGB_MASK)
        return if (a == MAX_CHANNEL) rgb else "$rgb${String.format(Locale.ROOT, "%02X", a)}"
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private companion object {
        const val SEPARATOR = 0.75
        const val LABEL_SCALE = 0.85
        const val TEXT_PAD = 8.0
        const val CUBIC = 4
        const val BASE_STROKE = 2.0
        const val MIN_MARKER = 0.8
        const val MAX_MARKER = 3.0
        const val DASH_ON = 4.0
        const val DASH_OFF = 3.0
        const val DOT_GAP = 2.5
        const val HIGHLIGHTER_OPACITY = 0.4
        const val HALF_TURN = 180.0
        const val EPS = 1e-6
        const val ALPHA_SHIFT = 24
        const val MAX_CHANNEL = 0xFF
        const val RGB_MASK = 0xFFFFFF
    }
}
