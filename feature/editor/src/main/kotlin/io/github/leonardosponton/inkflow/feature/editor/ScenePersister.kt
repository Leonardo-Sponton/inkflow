package io.github.leonardosponton.inkflow.feature.editor

import io.github.leonardosponton.inkflow.core.model.TabId
import io.github.leonardosponton.inkflow.domain.canvas.repository.SceneRepository
import io.github.leonardosponton.inkflow.domain.canvas.scene.SceneChange
import io.github.leonardosponton.inkflow.domain.canvas.undo.UndoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Autosave: scene changes are merged per tab and written after a short quiet period, in one
 * transaction, together with the last undo entries. Call [flush] when leaving the editor.
 */
class ScenePersister(
    private val repository: SceneRepository,
    private val scope: CoroutineScope,
    private val history: (TabId) -> List<SceneChange>?,
    private val debounceMs: Long = DEBOUNCE_MS,
) {
    private val pending = LinkedHashMap<TabId, SceneChange>()
    private val writeLock = Mutex()
    private var job: Job? = null

    val hasPending: Boolean get() = pending.isNotEmpty()

    fun record(tab: TabId, change: SceneChange) {
        if (change.isEmpty) return
        pending[tab] = pending[tab]?.then(change) ?: change
        job?.cancel()
        job = scope.launch {
            delay(debounceMs)
            withContext(NonCancellable) { flush() }
        }
    }

    suspend fun flush() {
        val batch = LinkedHashMap(pending)
        pending.clear()
        writeLock.withLock {
            batch.forEach { (tab, change) ->
                if (!change.isEmpty) repository.applyChange(tab, change)
                history(tab)?.let { repository.saveUndoHistory(tab, it.takeLast(UndoManager.PERSISTED_ENTRIES)) }
            }
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 400L
    }
}
