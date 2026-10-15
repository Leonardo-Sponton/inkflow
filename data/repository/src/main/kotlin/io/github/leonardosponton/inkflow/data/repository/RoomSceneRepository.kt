package io.github.leonardosponton.inkflow.data.repository

import androidx.room.withTransaction
import io.github.leonardosponton.inkflow.core.common.IoDispatcher
import io.github.leonardosponton.inkflow.core.common.Logger
import io.github.leonardosponton.inkflow.core.model.TabId
import io.github.leonardosponton.inkflow.data.local.ElementEntity
import io.github.leonardosponton.inkflow.data.local.InkflowDatabase
import io.github.leonardosponton.inkflow.data.local.UndoEntryEntity
import io.github.leonardosponton.inkflow.domain.canvas.element.CanvasElement
import io.github.leonardosponton.inkflow.domain.canvas.repository.SceneRepository
import io.github.leonardosponton.inkflow.domain.canvas.scene.Scene
import io.github.leonardosponton.inkflow.domain.canvas.scene.SceneChange
import io.github.leonardosponton.inkflow.domain.canvas.serialization.SceneJson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import javax.inject.Inject
import javax.inject.Singleton

internal object ElementMapper {
    fun toEntity(tabId: String, el: CanvasElement, json: SceneJson): ElementEntity {
        val b = el.bounds
        return ElementEntity(
            el.id.value,
            tabId,
            el::class.simpleName.orEmpty(),
            el.zIndex,
            b.left,
            b.top,
            b.right,
            b.bottom,
            json.encodeElement(el),
        )
    }
}

@Singleton
class RoomSceneRepository
    @Inject
    constructor(
        private val db: InkflowDatabase,
        private val json: SceneJson,
        private val logger: Logger,
        @param:IoDispatcher private val io: CoroutineDispatcher,
    ) : SceneRepository {
        override suspend fun load(tab: TabId): Scene = withContext(io) {
            val decoded = db.elements().forTab(tab.value).mapNotNull { row ->
                try {
                    json.decodeElement(row.payload)
                } catch (e: SerializationException) {
                    logger.warn(TAG, "Skipping unreadable element ${row.id}", e)
                    null
                }
            }
            Scene.of(decoded)
        }

        override suspend fun applyChange(tab: TabId, change: SceneChange) = withContext(io) {
            val upserts = change.after.values.filterNotNull().map { ElementMapper.toEntity(tab.value, it, json) }
            val deletes = change.after.filterValues { it == null }.keys.map { it.value }
            db.withTransaction { db.elements().apply(upserts, deletes) }
        }

        override suspend fun loadUndoHistory(tab: TabId): List<SceneChange> = withContext(io) {
            db.undo().forTab(tab.value).mapNotNull { runCatching { json.decodeChange(it.payload) }.getOrNull() }
        }

        override suspend fun saveUndoHistory(tab: TabId, entries: List<SceneChange>) = withContext(io) {
            db.undo().replace(tab.value, entries.mapIndexed { i, c -> UndoEntryEntity(tab.value, i, json.encodeChange(c)) })
        }

        private companion object {
            const val TAG = "SceneRepository"
        }
    }
