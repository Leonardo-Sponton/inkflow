package io.github.leonardosponton.inkflow.domain.project

import io.github.leonardosponton.inkflow.core.model.DiagramProfile
import io.github.leonardosponton.inkflow.core.model.ProjectId
import io.github.leonardosponton.inkflow.core.model.TabId
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Default titles: "Untitled project", "Untitled project 2", … unique among existing names. */
object Naming {
    const val DEFAULT_PROJECT = "Untitled project"
    const val DEFAULT_TAB = "Board"

    fun nextName(base: String, existing: Collection<String>): String {
        val taken = existing.map { it.trim().lowercase() }.toSet()
        if (base.lowercase() !in taken) return base
        return generateSequence(2) { it + 1 }.map { "$base $it" }.first { it.lowercase() !in taken }
    }

    /** Trims and caps user-entered names; blank input keeps [fallback]. */
    fun sanitize(input: String, fallback: String): String = input.trim().take(MAX_NAME).ifEmpty { fallback }

    const val MAX_NAME = 80
}

class CreateProject
@Inject
constructor(private val projects: ProjectRepository) {
    suspend operator fun invoke(name: String? = null, profile: DiagramProfile = DiagramProfile.FREEFORM): ProjectId {
        val existing = projects.observeProjects().first().map { it.name }
        val finalName = name?.let { Naming.sanitize(it, Naming.DEFAULT_PROJECT) } ?: Naming.nextName(Naming.DEFAULT_PROJECT, existing)
        return projects.createProject(finalName, Naming.DEFAULT_TAB, profile)
    }
}

class AddTab
@Inject
constructor(private val projects: ProjectRepository, private val tabs: TabRepository) {
    suspend operator fun invoke(projectId: ProjectId, profile: DiagramProfile = DiagramProfile.FREEFORM): TabId {
        val existing = projects.observeProject(projectId).first()?.tabs?.map { it.title }.orEmpty()
        return tabs.addTab(projectId, Naming.nextName(Naming.DEFAULT_TAB, existing), profile)
    }
}

/** Moves the tab at [from] to [to] and persists the full order. */
class MoveTab
@Inject
constructor(private val projects: ProjectRepository, private val tabRepo: TabRepository) {
    suspend operator fun invoke(projectId: ProjectId, from: Int, to: Int) {
        val tabs = projects.observeProject(projectId).first()?.tabs ?: return
        if (from !in tabs.indices || to !in tabs.indices || from == to) return
        val order = tabs.map { it.id }.toMutableList()
        order.add(to, order.removeAt(from))
        tabRepo.reorderTabs(projectId, order)
    }
}
