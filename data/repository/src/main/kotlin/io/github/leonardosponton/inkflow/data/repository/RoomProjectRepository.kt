package io.github.leonardosponton.inkflow.data.repository

import androidx.room.withTransaction
import io.github.leonardosponton.inkflow.core.common.IoDispatcher
import io.github.leonardosponton.inkflow.core.model.Camera
import io.github.leonardosponton.inkflow.core.model.Clock
import io.github.leonardosponton.inkflow.core.model.DiagramProfile
import io.github.leonardosponton.inkflow.core.model.ElementId
import io.github.leonardosponton.inkflow.core.model.GridStyle
import io.github.leonardosponton.inkflow.core.model.IdGenerator
import io.github.leonardosponton.inkflow.core.model.Layer
import io.github.leonardosponton.inkflow.core.model.ProjectId
import io.github.leonardosponton.inkflow.core.model.TabColor
import io.github.leonardosponton.inkflow.core.model.TabId
import io.github.leonardosponton.inkflow.data.files.FileStore
import io.github.leonardosponton.inkflow.data.local.InkflowDatabase
import io.github.leonardosponton.inkflow.data.local.ProjectEntity
import io.github.leonardosponton.inkflow.data.repository.Mappers.toDomain
import io.github.leonardosponton.inkflow.data.repository.Mappers.toEntity
import io.github.leonardosponton.inkflow.domain.canvas.clipboard.ElementCopier
import io.github.leonardosponton.inkflow.domain.canvas.serialization.SceneJson
import io.github.leonardosponton.inkflow.domain.project.Project
import io.github.leonardosponton.inkflow.domain.project.ProjectRepository
import io.github.leonardosponton.inkflow.domain.project.ProjectSummary
import io.github.leonardosponton.inkflow.domain.project.Tab
import io.github.leonardosponton.inkflow.domain.project.TabRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomProjectRepository
    @Inject
    constructor(
        private val db: InkflowDatabase,
        private val files: FileStore,
        private val json: SceneJson,
        private val clock: Clock,
        private val ids: IdGenerator,
        @param:IoDispatcher private val io: CoroutineDispatcher,
    ) : ProjectRepository,
        TabRepository {
        private val projects = db.projects()
        private val tabs = db.tabs()
        private val elements = db.elements()

        override fun observeProjects(): Flow<List<ProjectSummary>> = projects.observeSummaries().map { rows ->
            rows.map { r ->
                val thumb = files.thumbnailFile(r.id)
                ProjectSummary(
                    id = ProjectId(r.id),
                    name = r.name,
                    createdAt = r.createdAt,
                    updatedAt = r.updatedAt,
                    favorite = r.favorite,
                    tabCount = r.tabCount,
                    thumbnailPath = thumb.absolutePath.takeIf { r.thumbnailVersion > 0 && thumb.exists() },
                    thumbnailVersion = r.thumbnailVersion,
                )
            }
        }.flowOn(io)

        override fun observeProject(id: ProjectId): Flow<Project?> =
            combine(projects.observe(id.value), tabs.observeVisible(id.value)) { p, ts ->
                p?.let { Project(ProjectId(it.id), it.name, it.createdAt, it.updatedAt, it.favorite, ts.map { t -> t.toDomain() }) }
            }.distinctUntilChanged().flowOn(io)

        override suspend fun createProject(name: String, firstTabTitle: String, profile: DiagramProfile): ProjectId = withContext(io) {
            val now = clock.nowMillis()
            val id = ProjectId(ids.next())
            db.withTransaction {
                projects.insert(ProjectEntity(id.value, name, now, now, favorite = false))
                tabs.insert(Tab(TabId(ids.next()), id, firstTabTitle, 0, profile).toEntity())
            }
            id
        }

        override suspend fun renameProject(id: ProjectId, name: String) =
            withContext(io) { projects.rename(id.value, name, clock.nowMillis()) }

        override suspend fun setFavorite(id: ProjectId, favorite: Boolean) = withContext(io) { projects.setFavorite(id.value, favorite) }

        override suspend fun deleteProject(id: ProjectId) = withContext(io) {
            projects.delete(id.value)
            files.deleteProjectFiles(id.value)
        }

        override suspend fun touch(id: ProjectId) = withContext(io) { projects.touch(id.value, clock.nowMillis()) }

        override suspend fun saveThumbnail(projectId: ProjectId, png: ByteArray) = withContext(io) {
            files.writeThumbnail(projectId.value, png)
            projects.setThumbnailVersion(projectId.value, clock.nowMillis())
        }

        override suspend fun addTab(projectId: ProjectId, title: String, profile: DiagramProfile): TabId = withContext(io) {
            val id = TabId(ids.next())
            db.withTransaction {
                val position = tabs.visible(projectId.value).size
                tabs.insert(Tab(id, projectId, title, position, profile).toEntity())
                projects.touch(projectId.value, clock.nowMillis())
            }
            id
        }

        override suspend fun renameTab(id: TabId, title: String) = withContext(io) { tabs.rename(id.value, title) }

        override suspend fun setTabColor(id: TabId, color: TabColor) = withContext(io) { tabs.setColor(id.value, color.name) }

        override suspend fun setTabProfile(id: TabId, profile: DiagramProfile) = withContext(io) { tabs.setProfile(id.value, profile.name) }

        override suspend fun setTabGrid(id: TabId, grid: GridStyle) = withContext(io) { tabs.setGrid(id.value, grid.name) }

        override suspend fun setTabLayers(id: TabId, layers: List<Layer>) =
            withContext(io) { tabs.setLayers(id.value, Mappers.encodeLayers(layers)) }

        override suspend fun saveCamera(id: TabId, camera: Camera) =
            withContext(io) { tabs.setCamera(id.value, camera.center.x, camera.center.y, camera.zoom, camera.rotation) }

        override suspend fun reorderTabs(projectId: ProjectId, ordered: List<TabId>) = withContext(io) {
            tabs.reorder(ordered.map { it.value })
        }

        override suspend fun duplicateTab(id: TabId): TabId = withContext(io) {
            val newId = TabId(ids.next())
            db.withTransaction {
                val original = requireNotNull(tabs.get(id.value)) { "Unknown tab $id" }
                val siblings = tabs.visible(original.projectId).map { it.id }.toMutableList()
                tabs.insert(original.copy(id = newId.value, title = original.title + " copy", deletedAt = null))
                siblings.add(siblings.indexOf(original.id) + 1, newId.value)
                tabs.reorder(siblings)
                val copies = ElementCopier.copy(elements.forTab(id.value).map { json.decodeElement(it.payload) }) { ElementId(ids.next()) }
                elements.upsert(copies.map { ElementMapper.toEntity(newId.value, it, json) })
            }
            newId
        }

        override suspend fun deleteTab(id: TabId) = withContext(io) { tabs.setDeleted(id.value, clock.nowMillis()) }

        override suspend fun restoreTab(id: TabId) = withContext(io) { tabs.setDeleted(id.value, null) }

        override suspend fun purgeDeletedTabs(projectId: ProjectId) = withContext(io) { tabs.purgeDeleted(projectId.value) }
    }
