package io.github.leonardosponton.inkflow.core.testing

import io.github.leonardosponton.inkflow.core.model.Camera
import io.github.leonardosponton.inkflow.core.model.DiagramProfile
import io.github.leonardosponton.inkflow.core.model.GridStyle
import io.github.leonardosponton.inkflow.core.model.Layer
import io.github.leonardosponton.inkflow.core.model.ProjectId
import io.github.leonardosponton.inkflow.core.model.TabColor
import io.github.leonardosponton.inkflow.core.model.TabId
import io.github.leonardosponton.inkflow.domain.project.Project
import io.github.leonardosponton.inkflow.domain.project.ProjectRepository
import io.github.leonardosponton.inkflow.domain.project.ProjectSummary
import io.github.leonardosponton.inkflow.domain.project.Tab
import io.github.leonardosponton.inkflow.domain.project.TabRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory [ProjectRepository] for unit tests. */
class FakeProjectRepository :
    ProjectRepository,
    TabRepository {
    private data class Row(val project: Project, val deleted: Set<TabId> = emptySet())

    private val state = MutableStateFlow<Map<ProjectId, Row>>(emptyMap())
    private var seq = 0
    private var clock = 0L
    val thumbnails = mutableMapOf<ProjectId, ByteArray>()

    private fun nextId() = "f${++seq}"

    override fun observeProjects(): Flow<List<ProjectSummary>> = state.map { rows ->
        rows.values.map { r ->
            val p = r.project
            ProjectSummary(p.id, p.name, p.createdAt, p.updatedAt, p.favorite, visible(r).size, null)
        }
    }

    override fun observeProject(id: ProjectId): Flow<Project?> =
        state.map { rows -> rows[id]?.let { it.project.copy(tabs = visible(it)) } }

    private fun visible(r: Row) = r.project.tabs.filter { it.id !in r.deleted }.sortedBy { it.position }

    private fun edit(id: ProjectId, f: (Row) -> Row) = state.update { m -> m[id]?.let { m + (id to f(it)) } ?: m }

    private fun editTab(id: TabId, f: (Tab) -> Tab) = state.update { m ->
        m.mapValues { (_, r) -> r.copy(project = r.project.copy(tabs = r.project.tabs.map { if (it.id == id) f(it) else it })) }
    }

    override suspend fun createProject(name: String, firstTabTitle: String, profile: DiagramProfile): ProjectId {
        val id = ProjectId(nextId())
        val tab = Tab(TabId(nextId()), id, firstTabTitle, 0, profile)
        state.update { it + (id to Row(Project(id, name, ++clock, clock, false, listOf(tab)))) }
        return id
    }

    override suspend fun renameProject(id: ProjectId, name: String) = edit(id) { it.copy(project = it.project.copy(name = name)) }

    override suspend fun setFavorite(id: ProjectId, favorite: Boolean) = edit(id) {
        it.copy(project = it.project.copy(favorite = favorite))
    }

    override suspend fun deleteProject(id: ProjectId) = state.update { it - id }

    override suspend fun touch(id: ProjectId) = edit(id) { it.copy(project = it.project.copy(updatedAt = ++clock)) }

    override suspend fun addTab(projectId: ProjectId, title: String, profile: DiagramProfile): TabId {
        val id = TabId(nextId())
        edit(projectId) { r ->
            val pos = (r.project.tabs.maxOfOrNull { it.position } ?: -1) + 1
            r.copy(project = r.project.copy(tabs = r.project.tabs + Tab(id, projectId, title, pos, profile)))
        }
        return id
    }

    override suspend fun renameTab(id: TabId, title: String) = editTab(id) { it.copy(title = title) }

    override suspend fun setTabColor(id: TabId, color: TabColor) = editTab(id) { it.copy(color = color) }

    override suspend fun setTabProfile(id: TabId, profile: DiagramProfile) = editTab(id) { it.copy(profile = profile) }

    override suspend fun setTabGrid(id: TabId, grid: GridStyle) = editTab(id) { it.copy(grid = grid) }

    override suspend fun setTabLayers(id: TabId, layers: List<Layer>) = editTab(id) { it.copy(layers = layers) }

    override suspend fun saveCamera(id: TabId, camera: Camera) = editTab(id) { it.copy(camera = camera) }

    override suspend fun reorderTabs(projectId: ProjectId, ordered: List<TabId>) = edit(projectId) { r ->
        r.copy(project = r.project.copy(tabs = r.project.tabs.map { t -> t.copy(position = ordered.indexOf(t.id)) }))
    }

    override suspend fun duplicateTab(id: TabId): TabId {
        val original = state.value.values.flatMap { it.project.tabs }.first { it.id == id }
        return addTab(original.projectId, original.title + " copy", original.profile)
    }

    override suspend fun deleteTab(id: TabId) = state.update { m ->
        m.mapValues { (_, r) -> if (r.project.tabs.any { it.id == id }) r.copy(deleted = r.deleted + id) else r }
    }

    override suspend fun restoreTab(id: TabId) = state.update { m -> m.mapValues { (_, r) -> r.copy(deleted = r.deleted - id) } }

    override suspend fun purgeDeletedTabs(projectId: ProjectId) = edit(projectId) { r ->
        Row(r.project.copy(tabs = r.project.tabs.filter { it.id !in r.deleted }))
    }

    override suspend fun saveThumbnail(projectId: ProjectId, png: ByteArray) {
        thumbnails[projectId] = png
    }
}
