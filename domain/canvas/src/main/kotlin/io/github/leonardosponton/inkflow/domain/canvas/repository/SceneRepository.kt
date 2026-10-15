package io.github.leonardosponton.inkflow.domain.canvas.repository

import io.github.leonardosponton.inkflow.core.model.TabId
import io.github.leonardosponton.inkflow.domain.canvas.scene.Scene
import io.github.leonardosponton.inkflow.domain.canvas.scene.SceneChange

/** Persistence of a tab's elements and its recent undo history. */
interface SceneRepository {
    suspend fun load(tab: TabId): Scene

    /** Persists only what [change] touched (upserts and deletes) in one transaction. */
    suspend fun applyChange(tab: TabId, change: SceneChange)

    suspend fun loadUndoHistory(tab: TabId): List<SceneChange>

    suspend fun saveUndoHistory(tab: TabId, entries: List<SceneChange>)
}
