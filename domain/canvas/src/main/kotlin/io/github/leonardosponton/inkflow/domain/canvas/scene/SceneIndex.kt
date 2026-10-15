package io.github.leonardosponton.inkflow.domain.canvas.scene

import io.github.leonardosponton.inkflow.core.geometry.Quadtree
import io.github.leonardosponton.inkflow.core.model.ElementId
import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.domain.canvas.element.CanvasElement

/**
 * Mutable spatial index kept in sync with the current [Scene] by feeding it every [SceneChange].
 * Viewport culling and hit testing query this instead of iterating all elements.
 */
class SceneIndex {
    private val tree = Quadtree<ElementId>()

    val size: Int get() = tree.size

    fun rebuild(scene: Scene) {
        tree.clear()
        scene.elements.values.forEach { tree.put(it.id, it.bounds) }
    }

    fun apply(change: SceneChange) {
        change.after.forEach { (id, el) -> if (el == null) tree.remove(id) else tree.put(id, el.bounds) }
    }

    fun idsIn(area: Rect): List<ElementId> = tree.query(area)

    /** Elements intersecting [area] in back-to-front order. */
    fun visible(scene: Scene, area: Rect): List<CanvasElement> =
        tree.query(area).mapNotNull { scene[it] }.sortedWith(Scene.Z_ORDER)

    fun contentBounds(): Rect? = tree.contentBounds()
}
