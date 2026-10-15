package io.github.leonardosponton.inkflow.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.leonardosponton.inkflow.core.model.DiagramProfile
import io.github.leonardosponton.inkflow.core.model.ProjectId
import io.github.leonardosponton.inkflow.domain.project.CreateProject
import io.github.leonardosponton.inkflow.domain.project.Naming
import io.github.leonardosponton.inkflow.domain.project.ProjectQuery
import io.github.leonardosponton.inkflow.domain.project.ProjectRepository
import io.github.leonardosponton.inkflow.domain.project.ProjectSort
import io.github.leonardosponton.inkflow.domain.project.ProjectSummary
import io.github.leonardosponton.inkflow.domain.project.query
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeState(
    val loading: Boolean = true,
    val projects: List<ProjectSummary> = emptyList(),
    val query: ProjectQuery = ProjectQuery(),
    val totalCount: Int = 0,
)

sealed interface HomeIntent {
    data class Search(val text: String) : HomeIntent

    data class Sort(val sort: ProjectSort) : HomeIntent

    data class FavoritesOnly(val enabled: Boolean) : HomeIntent

    data class Create(val profile: DiagramProfile) : HomeIntent

    data class Rename(val id: ProjectId, val name: String) : HomeIntent

    data class ToggleFavorite(val project: ProjectSummary) : HomeIntent

    data class Delete(val id: ProjectId) : HomeIntent
}

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val projects: ProjectRepository,
        private val createProject: CreateProject,
    ) : ViewModel() {
        private val query = MutableStateFlow(ProjectQuery())
        private val openEvents = Channel<ProjectId>(Channel.BUFFERED)

        /** Emits the project to open right after it was created. */
        val opened = openEvents.receiveAsFlow()

        val state: StateFlow<HomeState> = combine(projects.observeProjects(), query) { all, q ->
            HomeState(loading = false, projects = all.query(q), query = q, totalCount = all.size)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), HomeState())

        fun onIntent(intent: HomeIntent) {
            when (intent) {
                is HomeIntent.Search -> query.update { it.copy(search = intent.text) }
                is HomeIntent.Sort -> query.update { it.copy(sort = intent.sort) }
                is HomeIntent.FavoritesOnly -> query.update { it.copy(favoritesOnly = intent.enabled) }
                is HomeIntent.Create -> viewModelScope.launch { openEvents.send(createProject(profile = intent.profile)) }
                is HomeIntent.Rename -> viewModelScope.launch {
                    projects.renameProject(intent.id, Naming.sanitize(intent.name, Naming.DEFAULT_PROJECT))
                }
                is HomeIntent.ToggleFavorite -> viewModelScope.launch { projects.setFavorite(intent.project.id, !intent.project.favorite) }
                is HomeIntent.Delete -> viewModelScope.launch { projects.deleteProject(intent.id) }
            }
        }

        private companion object {
            const val STOP_TIMEOUT = 5_000L
        }
    }
