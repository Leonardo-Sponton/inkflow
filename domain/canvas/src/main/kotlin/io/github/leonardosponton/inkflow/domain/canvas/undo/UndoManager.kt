package io.github.leonardosponton.inkflow.domain.canvas.undo

import io.github.leonardosponton.inkflow.domain.canvas.scene.SceneChange

/**
 * Per-tab undo/redo history of [SceneChange]s. Consecutive changes with the same coalesce key
 * (one continuous gesture) merge into one entry. History is bounded by [maxEntries].
 */
class UndoManager(private val maxEntries: Int = DEFAULT_MAX_ENTRIES) {
    private val undoStack = ArrayDeque<SceneChange>()
    private val redoStack = ArrayDeque<SceneChange>()
    private var openKey: String? = null

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** Snapshot of undoable entries, oldest first (for persistence). */
    val history: List<SceneChange> get() = undoStack.toList()

    fun record(change: SceneChange, coalesceKey: String? = null) {
        if (change.isEmpty) return
        redoStack.clear()
        if (coalesceKey != null && coalesceKey == openKey && undoStack.isNotEmpty()) {
            undoStack.addLast(undoStack.removeLast().then(change))
        } else {
            undoStack.addLast(change)
            while (undoStack.size > maxEntries) undoStack.removeFirst()
        }
        openKey = coalesceKey
    }

    /** Ends the current coalescing run so the next change starts a new entry. */
    fun seal() {
        openKey = null
    }

    /** Change to apply to undo the last entry, or `null`. */
    fun undo(): SceneChange? {
        seal()
        val entry = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(entry)
        return entry.inverted()
    }

    fun redo(): SceneChange? {
        seal()
        val entry = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(entry)
        return entry
    }

    fun restore(entries: List<SceneChange>) {
        undoStack.clear()
        redoStack.clear()
        entries.takeLast(maxEntries).forEach(undoStack::addLast)
        openKey = null
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 500

        /** How many entries survive an app restart. */
        const val PERSISTED_ENTRIES = 50
    }
}
