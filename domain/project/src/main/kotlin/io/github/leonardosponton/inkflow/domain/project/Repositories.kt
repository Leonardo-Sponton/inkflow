package io.github.leonardosponton.inkflow.domain.project

import io.github.leonardosponton.inkflow.core.model.AssetId
import io.github.leonardosponton.inkflow.core.model.Camera
import io.github.leonardosponton.inkflow.core.model.DiagramProfile
import io.github.leonardosponton.inkflow.core.model.GridStyle
import io.github.leonardosponton.inkflow.core.model.Layer
import io.github.leonardosponton.inkflow.core.model.ProjectId
import io.github.leonardosponton.inkflow.core.model.TabColor
import io.github.leonardosponton.inkflow.core.model.TabId
import io.github.leonardosponton.inkflow.core.model.UserSettings
import kotlinx.coroutines.flow.Flow

interface ProjectRepository {
    fun observeProjects(): Flow<List<ProjectSummary>>

    fun observeProject(id: ProjectId): Flow<Project?>

    suspend fun createProject(name: String, firstTabTitle: String, profile: DiagramProfile): ProjectId

    suspend fun renameProject(id: ProjectId, name: String)

    suspend fun setFavorite(id: ProjectId, favorite: Boolean)

    suspend fun deleteProject(id: ProjectId)

    /** Marks the project as edited now (gallery "recent" order). */
    suspend fun touch(id: ProjectId)

    suspend fun saveThumbnail(projectId: ProjectId, png: ByteArray)
}

/** Tabs of a project; [ProjectRepository.observeProject] emits the visible ones in order. */
interface TabRepository {
    suspend fun addTab(projectId: ProjectId, title: String, profile: DiagramProfile): TabId

    suspend fun renameTab(id: TabId, title: String)

    suspend fun setTabColor(id: TabId, color: TabColor)

    suspend fun setTabProfile(id: TabId, profile: DiagramProfile)

    suspend fun setTabGrid(id: TabId, grid: GridStyle)

    suspend fun setTabLayers(id: TabId, layers: List<Layer>)

    suspend fun saveCamera(id: TabId, camera: Camera)

    /** Persists a new tab order; [ordered] lists every tab of the project. */
    suspend fun reorderTabs(projectId: ProjectId, ordered: List<TabId>)

    /** Copies the tab with all its elements (fresh ids) right after the original. */
    suspend fun duplicateTab(id: TabId): TabId

    /** Hides the tab; [restoreTab] brings it back (undo), [purgeDeletedTabs] removes it for good. */
    suspend fun deleteTab(id: TabId)

    suspend fun restoreTab(id: TabId)

    suspend fun purgeDeletedTabs(projectId: ProjectId)
}

interface SettingsRepository {
    val settings: Flow<UserSettings>

    suspend fun update(transform: (UserSettings) -> UserSettings)
}

/** Binary assets (images) owned by a project, stored in app-private storage. */
interface AssetStore {
    /** Absolute path of the asset file, or `null` if missing. */
    fun pathFor(project: ProjectId, asset: AssetId): String?

    suspend fun save(project: ProjectId, bytes: ByteArray, mimeType: String): AssetId

    /** Makes [asset] of [from] available in [to] (cross-project paste); returns the id in [to]. */
    suspend fun copy(from: ProjectId, to: ProjectId, asset: AssetId): AssetId?

    suspend fun list(project: ProjectId): List<AssetInfo>

    suspend fun read(project: ProjectId, asset: AssetId): ByteArray?
}

data class AssetInfo(val id: AssetId, val mimeType: String)
