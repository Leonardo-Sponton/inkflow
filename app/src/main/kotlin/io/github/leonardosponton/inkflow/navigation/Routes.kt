package io.github.leonardosponton.inkflow.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute : NavKey

@Serializable
data class EditorRoute(val projectId: String) : NavKey

@Serializable
data object SettingsRoute : NavKey
