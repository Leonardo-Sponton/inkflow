package io.github.leonardosponton.inkflow.feature.editor

import io.github.leonardosponton.inkflow.core.model.IdGenerator
import io.github.leonardosponton.inkflow.core.model.TabId
import io.github.leonardosponton.inkflow.domain.canvas.notation.NotationCatalog
import io.github.leonardosponton.inkflow.domain.canvas.repository.SceneRepository
import io.github.leonardosponton.inkflow.domain.canvas.session.EditorSession
import io.github.leonardosponton.inkflow.domain.diagram.TextMetrics

/**
 * Loads tab sessions lazily (only the active tab's elements are read) and keeps the few most
 * recent ones so switching back is instant.
 */
internal class SessionCache(
    private val catalog: NotationCatalog,
    private val ids: IdGenerator,
    private val scenes: SceneRepository,
    private val metrics: TextMetrics,
    private val capacity: Int = CAPACITY,
) {
    private val placeholder = EditorSession(catalog, ids, textMetrics = metrics)
    private val cache = LinkedHashMap<TabId, EditorSession>(capacity, LOAD_FACTOR, true)

    fun empty(): EditorSession = placeholder

    fun peek(tab: TabId): EditorSession? = cache[tab]

    suspend fun get(tab: TabId): EditorSession {
        cache[tab]?.let { return it }
        val session = EditorSession(catalog, ids, textMetrics = metrics)
        session.reset(scenes.load(tab), scenes.loadUndoHistory(tab))
        cache[tab] = session
        while (cache.size > capacity) cache.remove(cache.keys.first())
        return session
    }

    fun evict(tab: TabId) {
        cache.remove(tab)
    }

    private companion object {
        const val CAPACITY = 3
        const val LOAD_FACTOR = 0.75f
    }
}
