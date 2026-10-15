package io.github.leonardosponton.inkflow.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.leonardosponton.inkflow.core.model.UserSettings
import io.github.leonardosponton.inkflow.domain.project.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
@Inject
constructor(private val repository: SettingsRepository) : ViewModel() {
    val settings: StateFlow<UserSettings> =
        repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), UserSettings())

    fun update(transform: (UserSettings) -> UserSettings) {
        viewModelScope.launch { repository.update(transform) }
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}
