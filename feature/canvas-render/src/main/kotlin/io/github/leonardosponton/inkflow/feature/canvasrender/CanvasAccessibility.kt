package io.github.leonardosponton.inkflow.feature.canvasrender

import android.os.Bundle
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import io.github.leonardosponton.inkflow.core.model.ElementId
import io.github.leonardosponton.inkflow.core.model.Size
import io.github.leonardosponton.inkflow.core.model.Vec2
import io.github.leonardosponton.inkflow.domain.canvas.element.CanvasElement
import android.graphics.Rect as AndroidRect
import io.github.leonardosponton.inkflow.core.model.Rect as WorldRect

/** Describes elements for TalkBack and selects them when activated. */
interface CanvasAccessibility {
    fun describe(element: CanvasElement): String

    fun select(id: ElementId)
}

/**
 * Exposes the visible elements of the canvas as virtual accessibility nodes (top-most first when
 * touched), so TalkBack users can explore and select diagram elements.
 */
internal class CanvasAccessibilityHelper(private val view: InkCanvasView) : ExploreByTouchHelper(view) {
    private var items: List<CanvasElement> = emptyList()

    fun update(frame: RenderFrame?, viewport: Size) {
        val f = frame ?: return
        val visible = f.camera.visibleWorldRect(viewport)
        items = f.index.visible(f.scene, visible).filter { it.layerId !in f.hiddenLayers }.takeLast(MAX_ITEMS)
        invalidateRoot()
    }

    private fun screenRect(el: CanvasElement): AndroidRect {
        val f = view.frame ?: return AndroidRect()
        val vp = Size(view.width.toDouble(), view.height.toDouble())
        val r = WorldRect.bounding(el.bounds.corners().map { f.camera.worldToScreen(it, vp) }) ?: return AndroidRect()
        return AndroidRect(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt()).apply {
            intersect(0, 0, view.width, view.height)
            if (isEmpty) set(0, 0, 1, 1)
        }
    }

    override fun getVirtualViewAt(x: Float, y: Float): Int {
        val f = view.frame ?: return INVALID_ID
        val world = f.camera.screenToWorld(Vec2(x.toDouble(), y.toDouble()), Size(view.width.toDouble(), view.height.toDouble()))
        val index = items.indexOfLast { it.bounds.contains(world) }
        return if (index >= 0) index else INVALID_ID
    }

    override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
        items.indices.reversed().forEach(virtualViewIds::add)
    }

    override fun onPopulateNodeForVirtualView(virtualViewId: Int, node: AccessibilityNodeInfoCompat) {
        val el = items.getOrNull(virtualViewId)
        node.contentDescription = el?.let { view.accessibility?.describe(it) } ?: ""
        @Suppress("DEPRECATION")
        node.setBoundsInParent(el?.let(::screenRect) ?: AndroidRect(0, 0, 1, 1))
        node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
        node.isClickable = true
    }

    override fun onPerformActionForVirtualView(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
        if (action != AccessibilityNodeInfoCompat.ACTION_CLICK) return false
        val el = items.getOrNull(virtualViewId) ?: return false
        view.accessibility?.select(el.id)
        invalidateVirtualView(virtualViewId)
        return true
    }

    private companion object {
        const val MAX_ITEMS = 300
    }
}
