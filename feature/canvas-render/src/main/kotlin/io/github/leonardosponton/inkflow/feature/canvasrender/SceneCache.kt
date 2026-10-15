package io.github.leonardosponton.inkflow.feature.canvasrender

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RenderNode
import io.github.leonardosponton.inkflow.core.model.Camera
import io.github.leonardosponton.inkflow.core.model.Size
import io.github.leonardosponton.inkflow.core.model.Vec2

/**
 * Records grid + scene into a [RenderNode] covering the viewport plus a margin, rasterized once
 * into a compositing layer. While only the camera position changes (pans) that layer is drawn with
 * an offset, so panning a board with thousands of elements costs neither re-recording nor
 * re-rasterizing. Any change of scene, zoom, rotation, palette
 * or visibility re-records once.
 */
internal class SceneCache(private val view: InkCanvasView) {
    private data class Key(
        val scene: Any,
        val zoom: Double,
        val rotation: Double,
        val grid: Any,
        val dark: Boolean,
        val hidden: Any,
        val hiddenLayers: Any,
        val width: Double,
        val height: Double,
    )

    private val node = RenderNode("inkflow-scene").apply { setUseCompositingLayer(true, null) }
    private var key: Key? = null
    private var recordedCamera: Camera? = null
    private val recordMatrix = Matrix()
    private val inverse = Matrix()
    private val toScreen = Matrix()

    /** [worldToScreen] is the current camera's matrix. */
    fun draw(canvas: Canvas, f: RenderFrame, viewport: Size, worldToScreen: Matrix) {
        val c = f.camera
        val k = Key(f.scene, c.zoom, c.rotation, f.grid, f.darkCanvas, f.hidden, f.hiddenLayers, viewport.width, viewport.height)
        val cam = recordedCamera
        val panOnly = k == key && cam != null && covers(cam, c, viewport)
        val pinching = f.zooming && cam != null && key?.copy(zoom = c.zoom) == k
        if (!panOnly && !pinching) record(f, viewport, k)
        // Node space → world (inverse of the recording matrix) → current screen.
        recordMatrix.invert(inverse)
        toScreen.set(inverse)
        toScreen.postConcat(worldToScreen)
        canvas.save()
        canvas.concat(toScreen)
        canvas.drawRenderNode(node)
        canvas.restore()
    }

    /** The recorded area (viewport ± margin) still contains the current view. */
    private fun covers(recorded: Camera, now: Camera, viewport: Size): Boolean {
        val shift = now.worldToScreen(Vec2.Zero, viewport) - recorded.worldToScreen(Vec2.Zero, viewport)
        return kotlin.math.abs(shift.x) <= viewport.width * MARGIN && kotlin.math.abs(shift.y) <= viewport.height * MARGIN
    }

    private fun record(f: RenderFrame, viewport: Size, k: Key) {
        val w = viewport.width * (1 + 2 * MARGIN)
        val h = viewport.height * (1 + 2 * MARGIN)
        node.setPosition(0, 0, w.toInt(), h.toInt())
        val c = node.beginRecording()
        try {
            val m = recordMatrix
            m.reset()
            m.postTranslate(-f.camera.center.x.toFloat(), -f.camera.center.y.toFloat())
            m.postScale(f.camera.zoom.toFloat(), f.camera.zoom.toFloat())
            m.postRotate(Math.toDegrees(f.camera.rotation).toFloat())
            m.postTranslate((w / 2).toFloat(), (h / 2).toFloat())
            val area = Camera(f.camera.center, f.camera.zoom, f.camera.rotation).visibleWorldRect(Size(w, h))
            view.drawScene(c, f, area, m)
        } finally {
            node.endRecording()
        }
        key = k
        recordedCamera = f.camera
    }

    private companion object {
        /**
         * Extra recorded area on each side, as a fraction of the viewport. The node is rasterized
         * into a GPU layer (pans only composite it), so this also bounds that texture's memory.
         */
        const val MARGIN = 0.25
    }
}
