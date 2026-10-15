package io.github.leonardosponton.inkflow.core.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** What holding the S Pen side button does while drawing. */
enum class SideButtonAction { ERASER, LASSO }

data class UserSettings(
    val fingerDrawing: Boolean = false,
    val sideButtonAction: SideButtonAction = SideButtonAction.ERASER,
    val fingerRotation: Boolean = false,
    val snapToGrid: Boolean = true,
    val smartGuides: Boolean = true,
    val showMinimap: Boolean = false,
    val defaultGrid: GridStyle = GridStyle.DOTS,
    val inkToShape: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val darkCanvas: Boolean = false,
)
