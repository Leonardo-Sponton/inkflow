package io.github.leonardosponton.inkflow.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.leonardosponton.inkflow.core.model.GridStyle
import io.github.leonardosponton.inkflow.core.model.SideButtonAction
import io.github.leonardosponton.inkflow.core.model.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).widthIn(max = 720.dp)) {
            Section(stringResource(R.string.section_input))
            Toggle(R.string.finger_drawing, R.string.finger_drawing_hint, s.fingerDrawing) { v ->
                viewModel.update { it.copy(fingerDrawing = v) }
            }
            Choice(R.string.side_button, SideButtonAction.entries, s.sideButtonAction, { sideLabel(it) }) { v ->
                viewModel.update { it.copy(sideButtonAction = v) }
            }
            Toggle(R.string.finger_rotation, R.string.finger_rotation_hint, s.fingerRotation) { v ->
                viewModel.update { it.copy(fingerRotation = v) }
            }
            Toggle(R.string.ink_to_shape, R.string.ink_to_shape_hint, s.inkToShape) { v -> viewModel.update { it.copy(inkToShape = v) } }
            Section(stringResource(R.string.section_canvas))
            Toggle(R.string.snap_grid, null, s.snapToGrid) { v -> viewModel.update { it.copy(snapToGrid = v) } }
            Toggle(R.string.smart_guides, null, s.smartGuides) { v -> viewModel.update { it.copy(smartGuides = v) } }
            Toggle(R.string.minimap, null, s.showMinimap) { v -> viewModel.update { it.copy(showMinimap = v) } }
            Toggle(R.string.dark_canvas, null, s.darkCanvas) { v -> viewModel.update { it.copy(darkCanvas = v) } }
            Choice(R.string.default_grid, GridStyle.entries, s.defaultGrid, {
                gridLabel(it)
            }) { v -> viewModel.update { it.copy(defaultGrid = v) } }
            Section(stringResource(R.string.section_appearance))
            Choice(R.string.theme, ThemeMode.entries, s.themeMode, { themeLabel(it) }) { v -> viewModel.update { it.copy(themeMode = v) } }
            Toggle(R.string.dynamic_color, null, s.dynamicColor) { v -> viewModel.update { it.copy(dynamicColor = v) } }
            Section(stringResource(R.string.section_about))
            ListItem(headlineContent = {
                Text(stringResource(R.string.offline_title))
            }, supportingContent = { Text(stringResource(R.string.offline_body)) })
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 4.dp),
    )
}

@Composable
private fun Toggle(title: Int, hint: Int?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = hint?.let { { Text(stringResource(it)) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    )
}

@Composable
private fun <T> Choice(title: Int, options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            options.forEach { o ->
                FilterChip(selected = o == selected, onClick = {
                    onSelect(o)
                }, label = { Text(label(o)) }, modifier = Modifier.padding(end = 8.dp))
            }
        }
    }
}

@Composable
private fun sideLabel(a: SideButtonAction) = stringResource(if (a == SideButtonAction.ERASER) R.string.eraser else R.string.lasso)

@Composable
private fun themeLabel(t: ThemeMode) = stringResource(
    when (t) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    },
)

@Composable
private fun gridLabel(g: GridStyle) = stringResource(
    when (g) {
        GridStyle.PLAIN -> R.string.grid_plain
        GridStyle.DOTS -> R.string.grid_dots
        GridStyle.SQUARES -> R.string.grid_squares
        GridStyle.ISOMETRIC -> R.string.grid_isometric
    },
)
