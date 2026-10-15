package io.github.leonardosponton.inkflow.domain.canvas.scene

import io.github.leonardosponton.inkflow.core.model.ElementId
import io.github.leonardosponton.inkflow.domain.canvas.element.CanvasElement
import kotlinx.serialization.Serializable

/**
 * Invertible description of a scene mutation: the state of every touched element before and after
 * (`null` = absent). Undo/redo, persistence and the spatial index all consume these.
 */
@Serializable
data class SceneChange(
    val before: Map<ElementId, CanvasElement?> = emptyMap(),
    val after: Map<ElementId, CanvasElement?> = emptyMap(),
) {
    val isEmpty: Boolean get() = after.isEmpty() || after.all { (id, el) -> before[id] == el }

    val touched: Set<ElementId> get() = after.keys

    fun inverted() = SceneChange(before = after, after = before)

    /** This change followed by [next], as one change (used to coalesce continuous gestures). */
    fun then(next: SceneChange): SceneChange {
        val mergedBefore = LinkedHashMap(before)
        next.before.forEach { (id, el) -> if (id !in mergedBefore) mergedBefore[id] = el }
        val mergedAfter = LinkedHashMap(after)
        mergedAfter.putAll(next.after)
        return SceneChange(mergedBefore, mergedAfter).withoutNoOps()
    }

    fun withoutNoOps(): SceneChange {
        val keep = after.keys.filter { before[it] != after[it] }.toSet()
        return SceneChange(before.filterKeys { it in keep }, after.filterKeys { it in keep })
    }

    companion object {
        val Empty = SceneChange()

        /** Change that puts [updated] elements into [scene] (adds or replaces). */
        fun of(
            scene: Scene,
            updated: Collection<CanvasElement>,
            removed: Collection<ElementId> = emptyList(),
        ): SceneChange {
            val before = LinkedHashMap<ElementId, CanvasElement?>()
            val after = LinkedHashMap<ElementId, CanvasElement?>()
            updated.forEach {
                before[it.id] = scene[it.id]
                after[it.id] = it
            }
            removed.forEach {
                before[it] = scene[it]
                after[it] = null
            }
            return SceneChange(before, after).withoutNoOps()
        }
    }
}
