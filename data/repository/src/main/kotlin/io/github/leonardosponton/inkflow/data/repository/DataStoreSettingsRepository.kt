package io.github.leonardosponton.inkflow.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.leonardosponton.inkflow.core.model.UserSettings
import io.github.leonardosponton.inkflow.data.repository.Mappers.enumOr
import io.github.leonardosponton.inkflow.domain.project.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreSettingsRepository
    @Inject
    constructor(private val store: DataStore<Preferences>) : SettingsRepository {
        override val settings: Flow<UserSettings> = store.data.map { it.toSettings() }.distinctUntilChanged()

        override suspend fun update(transform: (UserSettings) -> UserSettings) {
            store.edit { prefs ->
                val s = transform(prefs.toSettings())
                prefs[FINGER_DRAWING] = s.fingerDrawing
                prefs[SIDE_BUTTON] = s.sideButtonAction.name
                prefs[FINGER_ROTATION] = s.fingerRotation
                prefs[SNAP_GRID] = s.snapToGrid
                prefs[GUIDES] = s.smartGuides
                prefs[MINIMAP] = s.showMinimap
                prefs[GRID] = s.defaultGrid.name
                prefs[INK_TO_SHAPE] = s.inkToShape
                prefs[THEME] = s.themeMode.name
                prefs[DYNAMIC] = s.dynamicColor
                prefs[DARK_CANVAS] = s.darkCanvas
            }
        }

        private fun Preferences.toSettings(): UserSettings {
            val d = UserSettings()
            return UserSettings(
                fingerDrawing = this[FINGER_DRAWING] ?: d.fingerDrawing,
                sideButtonAction = enumOr(this[SIDE_BUTTON].orEmpty(), d.sideButtonAction),
                fingerRotation = this[FINGER_ROTATION] ?: d.fingerRotation,
                snapToGrid = this[SNAP_GRID] ?: d.snapToGrid,
                smartGuides = this[GUIDES] ?: d.smartGuides,
                showMinimap = this[MINIMAP] ?: d.showMinimap,
                defaultGrid = enumOr(this[GRID].orEmpty(), d.defaultGrid),
                inkToShape = this[INK_TO_SHAPE] ?: d.inkToShape,
                themeMode = enumOr(this[THEME].orEmpty(), d.themeMode),
                dynamicColor = this[DYNAMIC] ?: d.dynamicColor,
                darkCanvas = this[DARK_CANVAS] ?: d.darkCanvas,
            )
        }

        private companion object {
            val FINGER_DRAWING = booleanPreferencesKey("finger_drawing")
            val SIDE_BUTTON = stringPreferencesKey("side_button")
            val FINGER_ROTATION = booleanPreferencesKey("finger_rotation")
            val SNAP_GRID = booleanPreferencesKey("snap_grid")
            val GUIDES = booleanPreferencesKey("smart_guides")
            val MINIMAP = booleanPreferencesKey("minimap")
            val GRID = stringPreferencesKey("grid")
            val INK_TO_SHAPE = booleanPreferencesKey("ink_to_shape")
            val THEME = stringPreferencesKey("theme")
            val DYNAMIC = booleanPreferencesKey("dynamic_color")
            val DARK_CANVAS = booleanPreferencesKey("dark_canvas")
        }
    }
