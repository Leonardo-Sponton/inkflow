package io.github.leonardosponton.inkflow.domain.project

import io.github.leonardosponton.inkflow.core.model.Camera
import io.github.leonardosponton.inkflow.core.model.DiagramProfile
import io.github.leonardosponton.inkflow.core.model.GridStyle
import io.github.leonardosponton.inkflow.core.model.Layer
import io.github.leonardosponton.inkflow.core.model.ProjectId
import io.github.leonardosponton.inkflow.core.model.TabColor
import io.github.leonardosponton.inkflow.core.model.TabId

/** Row of the home gallery. */
data class ProjectSummary(
    val id: ProjectId,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val favorite: Boolean,
    val tabCount: Int,
    /** Absolute path of the PNG thumbnail of the first tab, if one was rendered. */
    val thumbnailPath: String?,
    val thumbnailVersion: Long = 0,
)

/** One board of a project: an independent infinite canvas. */
data class Tab(
    val id: TabId,
    val projectId: ProjectId,
    val title: String,
    val position: Int,
    val profile: DiagramProfile = DiagramProfile.FREEFORM,
    val color: TabColor = TabColor.NONE,
    val camera: Camera = Camera(),
    val layers: List<Layer> = listOf(Layer.Default),
    val grid: GridStyle = GridStyle.DOTS,
)

data class Project(
    val id: ProjectId,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val favorite: Boolean,
    val tabs: List<Tab>,
)

enum class ProjectSort { RECENT, NAME }

data class ProjectQuery(val search: String = "", val sort: ProjectSort = ProjectSort.RECENT, val favoritesOnly: Boolean = false)

/** Filters and orders the gallery. Favorites first only matter for the recent sort's ties. */
fun List<ProjectSummary>.query(q: ProjectQuery): List<ProjectSummary> {
    val needle = q.search.trim().lowercase()
    val filtered = filter { (!q.favoritesOnly || it.favorite) && (needle.isEmpty() || it.name.lowercase().contains(needle)) }
    return when (q.sort) {
        ProjectSort.RECENT -> filtered.sortedWith(compareByDescending<ProjectSummary> { it.updatedAt }.thenBy { it.name.lowercase() })
        ProjectSort.NAME -> filtered.sortedWith(compareBy<ProjectSummary> { it.name.lowercase() }.thenByDescending { it.updatedAt })
    }
}
