package io.github.leonardosponton.inkflow.domain.project

import io.github.leonardosponton.inkflow.core.model.DiagramProfile
import io.github.leonardosponton.inkflow.core.model.ProjectId
import io.github.leonardosponton.inkflow.core.testing.FakeProjectRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProjectDomainTest {
    private fun summary(name: String, updated: Long, fav: Boolean = false) =
        ProjectSummary(ProjectId(name), name, 0, updated, fav, 1, null)

    @Test
    fun `query filters and sorts`() {
        val list = listOf(summary("Beta", 1), summary("alpha", 3, fav = true), summary("Gamma", 2))
        assertEquals(listOf("alpha", "Gamma", "Beta"), list.query(ProjectQuery()).map { it.name })
        assertEquals(listOf("alpha", "Beta", "Gamma"), list.query(ProjectQuery(sort = ProjectSort.NAME)).map { it.name })
        assertEquals(listOf("alpha"), list.query(ProjectQuery(favoritesOnly = true)).map { it.name })
        assertEquals(listOf("Gamma"), list.query(ProjectQuery(search = " gam ")).map { it.name })
    }

    @Test
    fun `naming picks the next free name and sanitizes`() {
        assertEquals("Board", Naming.nextName("Board", listOf("Other")))
        assertEquals("Board 3", Naming.nextName("Board", listOf("board", "Board 2")))
        assertEquals("x", Naming.sanitize("  x  ", "fallback"))
        assertEquals("fallback", Naming.sanitize("   ", "fallback"))
        assertEquals(Naming.MAX_NAME, Naming.sanitize("a".repeat(200), "f").length)
    }

    @Test
    fun `use cases create projects tabs and reorder`() = runTest {
        val repo = FakeProjectRepository()
        val p1 = CreateProject(repo)()
        val p2 = CreateProject(repo)()
        assertEquals(listOf("Untitled project", "Untitled project 2"), repo.observeProjects().first().map { it.name }.sorted())
        CreateProject(repo)("  Named  ", DiagramProfile.UML)
        val addTab = AddTab(repo, repo)
        addTab(p1)
        addTab(p1, DiagramProfile.FLOWCHART)
        val titles = repo.observeProject(p1).first()!!.tabs.map { it.title }
        assertEquals(listOf("Board", "Board 2", "Board 3"), titles)
        MoveTab(repo, repo)(p1, 0, 2)
        assertEquals(listOf("Board 2", "Board 3", "Board"), repo.observeProject(p1).first()!!.tabs.map { it.title })
        MoveTab(repo, repo)(p1, 0, 9)
        MoveTab(repo, repo)(p2, 0, 0)
        assertEquals(3, repo.observeProject(p1).first()!!.tabs.size)
    }
}
