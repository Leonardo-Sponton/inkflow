package io.github.leonardosponton.inkflow.feature.canvasrender

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.RenderNode
import io.github.leonardosponton.inkflow.core.model.ElementId
import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.core.model.Size
import io.github.leonardosponton.inkflow.core.model.Vec2
import io.github.leonardosponton.inkflow.domain.canvas.element.CanvasElement
import io.github.leonardosponton.inkflow.domain.canvas.scene.Scene
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Grid + scene cached as square tiles, each a [RenderNode] rasterized into its own compositing
 * layer. Tiles live in *tile space* — world scaled and rotated by the camera around a fixed
 * [anchor], with no translation — so a pan only moves them (snapped to whole pixels): tiles that
 * enter the view are recorded, the rest are composited as they are. A scene change re-records only
 * the tiles under the elements that changed. Elements added above everything else (committed
 * strokes, pasted shapes) skip even that: they are drawn by a small [overlay] node on top, and
 * folded into the tiles once [MAX_APPENDED] have piled up. A change of zoom, rotation, palette,
 * grid or visibility drops every tile; during a pinch the tiles
 * are scaled instead and re-recorded when it ends.
 */
internal class SceneCache(private val view: InkCanvasView) {
    /** Everything except the scene and the camera position that shapes the recorded pixels. */
    private data class Style(
        val zoom: Double,
        val rotation: Double,
        val grid: Any,
        val dark: Boolean,
        val hidden: Any,
        val hiddenLayers: Any,
    )

    private val tiles = HashMap<Long, RenderNode>()
    private val pool = ArrayDeque<RenderNode>()
    private var style: Style? = null
    private var scene: Scene? = null
    private var anchor = Vec2.Zero
    private val tileMatrix = Matrix()
    private val inverse = Matrix()
    private val toScreen = Matrix()

    /** Elements appended since the tiles were recorded, drawn by [overlay] and hidden from new tiles. */
    private var appended: List<CanvasElement> = emptyList()
    private var appendedIds: Set<ElementId> = emptySet()
    private val overlay = RenderNode("inkflow-appended").apply { clipToBounds = false }

    /** Highest z-index drawn so far; an append must go above it. */
    private var topZ = Long.MIN_VALUE

    /** [worldToScreen] is the current camera's matrix. */
    fun draw(canvas: Canvas, f: RenderFrame, viewport: Size, worldToScreen: Matrix) {
        val c = f.camera
        val st = Style(c.zoom, c.rotation, f.grid, f.darkCanvas, f.hidden, f.hiddenLayers)
        // HWUI frees the display lists of the whole view tree while the window is hidden (e.g. a
        // file picker on top); tiles would then draw nothing, so record them again.
        if (tiles.values.any { !it.hasDisplayList() } || (appended.isNotEmpty() && !overlay.hasDisplayList())) style = null
        val current = style
        if (f.zooming && current != null && current.copy(zoom = c.zoom) == st && current.zoom != c.zoom) {
            drawScaled(canvas, current, worldToScreen)
            return
        }
        sync(st, f, viewport)
        val origin = c.worldToScreen(anchor, viewport)
        val ox = origin.x.roundToInt()
        val oy = origin.y.roundToInt()
        canvas.save()
        canvas.translate(ox.toFloat(), oy.toFloat())
        val tileFrame = if (appendedIds.isEmpty()) f else f.copy(hidden = f.hidden + appendedIds)
        val complete = drawTiles(
            canvas,
            tileFrame,
            tileIndex(-ox)..tileIndex(viewport.width.toInt() - ox),
            tileIndex(-oy)..tileIndex(viewport.height.toInt() - oy),
        )
        if (appended.isNotEmpty()) canvas.drawRenderNode(overlay)
        canvas.restore()
        if (!complete) view.postInvalidateOnAnimation()
    }

    /** Brings the tiles in line with [st] and the frame's scene. */
    private fun sync(st: Style, f: RenderFrame, viewport: Size) {
        val o = f.camera.worldToScreen(anchor, viewport)
        if (st != style || abs(o.x) > MAX_OFFSET || abs(o.y) > MAX_OFFSET) {
            reset(st, f.scene, f.camera.center)
        } else if (f.scene !== scene) {
            if (!append(scene, f)) invalidate(scene, f.scene, st.zoom)
            scene = f.scene
        }
    }

    /**
     * Draws the on-screen tiles [cols] × [rows] (recording any missing) and the ring around them,
     * recording at most [PREFETCH_PER_FRAME] of the ring. Returns false if some were left for later.
     */
    private fun drawTiles(canvas: Canvas, f: RenderFrame, cols: IntRange, rows: IntRange): Boolean {
        val ringCols = cols.first - PREFETCH..cols.last + PREFETCH
        val ringRows = rows.first - PREFETCH..rows.last + PREFETCH
        evict(ringCols, ringRows)
        var budget = PREFETCH_PER_FRAME
        var complete = true
        for (j in ringRows) {
            for (i in ringCols) {
                val node = tiles[key(i, j)] ?: when {
                    i in cols && j in rows -> record(i, j, f)
                    budget > 0 -> record(i, j, f).also { budget-- }
                    else -> null.also { complete = false }
                }
                node?.let(canvas::drawRenderNode)
            }
        }
        return complete
    }

    /** Pinch in progress: draw the tiles recorded at [recorded]'s zoom, scaled to the current one. */
    private fun drawScaled(canvas: Canvas, recorded: Style, worldToScreen: Matrix) {
        baseMatrix(recorded.zoom, recorded.rotation, tileMatrix)
        tileMatrix.invert(inverse)
        toScreen.set(inverse)
        toScreen.postConcat(worldToScreen)
        canvas.save()
        canvas.concat(toScreen)
        tiles.values.forEach(canvas::drawRenderNode)
        if (appended.isNotEmpty()) canvas.drawRenderNode(overlay)
        canvas.restore()
    }

    private fun reset(st: Style, s: Scene, center: Vec2) {
        tiles.values.forEach(::recycle)
        tiles.clear()
        style = st
        scene = s
        anchor = center
        clearAppended(s)
    }

    /** The scene only gained elements above everything drawn: add them to the [overlay]. */
    private fun append(old: Scene?, f: RenderFrame): Boolean {
        val new = f.scene
        val added = new.size - (old?.size ?: return false)
        if (added <= 0 || appended.size + added > MAX_APPENDED) return false
        if (old.elements.any { (id, el) -> new[id] !== el }) return false
        val fresh = new.elements.values.filter { it.id !in old }
        if (fresh.any { it.zIndex <= topZ || it.layerId in f.hiddenLayers }) return false
        appended = (appended + fresh).sortedWith(Scene.Z_ORDER)
        appendedIds = appendedIds + fresh.map { it.id }
        topZ = appended.last().zIndex
        recordOverlay(f)
        return true
    }

    private fun recordOverlay(f: RenderFrame) {
        baseMatrix(f.camera.zoom, f.camera.rotation, tileMatrix)
        // The node needs real bounds: HWUI culls a render node by its position rect, so a 0×0
        // node (never positioned) is skipped and new elements stayed invisible until a re-tile.
        val world = Rect.unionAll(appended.map { it.bounds }) ?: return
        val area = RectF(world.left.toFloat(), world.top.toFloat(), world.right.toFloat(), world.bottom.toFloat())
        tileMatrix.mapRect(area)
        val pad = InkCanvasView.CULL_MARGIN.toFloat()
        val left = floor(area.left - pad).toInt()
        val top = floor(area.top - pad).toInt()
        overlay.setPosition(left, top, ceil(area.right + pad).toInt(), ceil(area.bottom + pad).toInt())
        tileMatrix.postTranslate(-left.toFloat(), -top.toFloat())
        val c = overlay.beginRecording()
        try {
            view.drawElements(c, f, appended, tileMatrix)
        } finally {
            overlay.endRecording()
        }
    }

    private fun clearAppended(s: Scene) {
        appended = emptyList()
        appendedIds = emptySet()
        overlay.discardDisplayList()
        topZ = s.elements.values.maxOfOrNull { it.zIndex } ?: Long.MIN_VALUE
    }

    /**
     * Drops the tiles under every element that differs between [old] and [new], and folds the
     * appended elements back into the tiles.
     */
    private fun invalidate(old: Scene?, new: Scene, zoom: Double) {
        val dirty = ArrayList<Rect>()
        appended.mapTo(dirty) { it.bounds }
        clearAppended(new)
        if (old == null) return dropAll()
        for ((id, el) in new.elements) {
            val before = old[id]
            if (before === el) continue
            dirty += el.bounds
            before?.let { dirty += it.bounds }
            if (dirty.size > MAX_DIRTY) return dropAll()
        }
        for ((id, el) in old.elements) {
            if (id in new) continue
            dirty += el.bounds
            if (dirty.size > MAX_DIRTY) return dropAll()
        }
        if (dirty.isEmpty()) return
        val margin = InkCanvasView.CULL_MARGIN / zoom
        val areas = dirty.map { it.inflate(margin) }
        val it = tiles.entries.iterator()
        while (it.hasNext()) {
            val (k, node) = it.next()
            val area = worldArea(tileI(k), tileJ(k))
            if (areas.any(area::intersects)) {
                recycle(node)
                it.remove()
            }
        }
    }

    private fun dropAll() {
        tiles.values.forEach(::recycle)
        tiles.clear()
    }

    private fun evict(cols: IntRange, rows: IntRange) {
        val it = tiles.entries.iterator()
        while (it.hasNext()) {
            val (k, node) = it.next()
            if (tileI(k) !in cols || tileJ(k) !in rows) {
                recycle(node)
                it.remove()
            }
        }
    }

    private fun record(i: Int, j: Int, f: RenderFrame): RenderNode {
        val node = pool.removeLastOrNull() ?: RenderNode("inkflow-tile").apply { setUseCompositingLayer(true, null) }
        node.setPosition(i * TILE, j * TILE, (i + 1) * TILE, (j + 1) * TILE)
        val c = node.beginRecording()
        try {
            val m = tileMatrix
            baseMatrix(f.camera.zoom, f.camera.rotation, m)
            m.postTranslate((-i * TILE).toFloat(), (-j * TILE).toFloat())
            view.drawScene(c, f, worldArea(i, j), m)
        } finally {
            node.endRecording()
        }
        tiles[key(i, j)] = node
        return node
    }

    private fun recycle(node: RenderNode) {
        if (pool.size < POOL) {
            pool.addLast(node)
        } else {
            node.discardDisplayList()
        }
    }

    /** World → tile space: around [anchor], scaled and rotated like the camera. */
    private fun baseMatrix(zoom: Double, rotation: Double, m: Matrix) {
        m.reset()
        m.postTranslate(-anchor.x.toFloat(), -anchor.y.toFloat())
        m.postScale(zoom.toFloat(), zoom.toFloat())
        m.postRotate(Math.toDegrees(rotation).toFloat())
    }

    /** World bounds of tile ([i], [j]) (axis-aligned, so larger than the tile when rotated). */
    private fun worldArea(i: Int, j: Int): Rect {
        val st = style ?: return Rect(0.0, 0.0, 0.0, 0.0)
        val cos = cos(-st.rotation)
        val sin = sin(-st.rotation)
        val x0 = i * TILE_D
        val y0 = j * TILE_D
        val corners = listOf(Vec2(x0, y0), Vec2(x0 + TILE_D, y0), Vec2(x0 + TILE_D, y0 + TILE_D), Vec2(x0, y0 + TILE_D))
            .map { p -> Vec2(p.x * cos - p.y * sin, p.x * sin + p.y * cos) / st.zoom + anchor }
        return Rect.bounding(corners) ?: Rect(0.0, 0.0, 0.0, 0.0)
    }

    private fun tileIndex(px: Int): Int = floor(px.toDouble() / TILE).toInt()

    private fun key(i: Int, j: Int): Long = (i.toLong() shl Int.SIZE_BITS) or (j.toLong() and INT_MASK)

    private fun tileI(k: Long): Int = (k shr Int.SIZE_BITS).toInt()

    private fun tileJ(k: Long): Int = k.toInt()

    private companion object {
        /** Tile edge in screen pixels: small enough that a pan rasterizes a thin strip at a time. */
        const val TILE = 512
        const val TILE_D = TILE.toDouble()

        /** Ring of tiles kept and recorded ahead around the view. */
        const val PREFETCH = 1

        /** Off-screen tiles recorded per frame; the rest come on the following frames. */
        const val PREFETCH_PER_FRAME = 2

        /** Recycled nodes kept (their layers are reused). */
        const val POOL = 16

        /** Appended elements kept in the overlay before they are folded into the tiles. */
        const val MAX_APPENDED = 64

        /** More changed elements than this drop every tile instead of testing each one. */
        const val MAX_DIRTY = 256

        /** Re-anchor before tile-space coordinates grow large enough to lose float precision. */
        const val MAX_OFFSET = 1_000_000.0
        const val INT_MASK = 0xFFFF_FFFFL
    }
}
