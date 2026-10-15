package io.github.leonardosponton.inkflow.feature.home

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.leonardosponton.inkflow.core.model.DiagramProfile
import io.github.leonardosponton.inkflow.core.model.ProjectId
import io.github.leonardosponton.inkflow.domain.project.ProjectSort
import io.github.leonardosponton.inkflow.domain.project.ProjectSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenProject: (ProjectId) -> Unit, onOpenSettings: () -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.opened.collect(onOpenProject) }
    var renaming by remember { mutableStateOf<ProjectSummary?>(null) }
    var deleting by remember { mutableStateOf<ProjectSummary?>(null) }
    var createMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.onIntent(HomeIntent.Import { context.contentResolver.openInputStream(it) }) }
    }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.onIntent(HomeIntent.DismissMessage)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    IconButton(onClick = { importer.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }) {
                        Icon(Icons.Outlined.FileOpen, stringResource(R.string.import_project))
                    }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, stringResource(R.string.settings)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            Box {
                ExtendedFloatingActionButton(
                    onClick = { createMenu = true },
                    icon = { Icon(Icons.Outlined.Add, null) },
                    text = { Text(stringResource(R.string.new_project)) },
                )
                DropdownMenu(expanded = createMenu, onDismissRequest = { createMenu = false }) {
                    DiagramProfile.entries.forEach { profile ->
                        DropdownMenuItem(
                            text = { Text(profileLabel(profile)) },
                            onClick = {
                                createMenu = false
                                viewModel.onIntent(HomeIntent.Create(profile))
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Filters(state, viewModel::onIntent)
            when {
                state.loading -> Unit
                state.totalCount == 0 -> EmptyState()
                state.projects.isEmpty() -> CenterText(stringResource(R.string.no_matches))
                else -> ProjectGrid(
                    projects = state.projects,
                    onOpen = { onOpenProject(it.id) },
                    onRename = { renaming = it },
                    onDelete = { deleting = it },
                    onFavorite = { viewModel.onIntent(HomeIntent.ToggleFavorite(it)) },
                )
            }
        }
    }
    renaming?.let { p ->
        RenameDialog(p.name, onDismiss = { renaming = null }) {
            viewModel.onIntent(HomeIntent.Rename(p.id, it))
            renaming = null
        }
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete_project_title)) },
            text = { Text(stringResource(R.string.delete_project_message, p.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onIntent(HomeIntent.Delete(p.id))
                    deleting = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun profileLabel(profile: DiagramProfile) = stringResource(
    when (profile) {
        DiagramProfile.FREEFORM -> R.string.profile_freeform
        DiagramProfile.UML -> R.string.profile_uml
        DiagramProfile.DATABASE -> R.string.profile_database
        DiagramProfile.FLOWCHART -> R.string.profile_flowchart
    },
)

@Composable
private fun Filters(state: HomeState, onIntent: (HomeIntent) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = state.query.search,
            onValueChange = { onIntent(HomeIntent.Search(it)) },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            placeholder = { Text(stringResource(R.string.search_projects)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        FilterChip(
            selected = state.query.sort == ProjectSort.RECENT,
            onClick = { onIntent(HomeIntent.Sort(ProjectSort.RECENT)) },
            label = { Text(stringResource(R.string.sort_recent)) },
        )
        FilterChip(
            selected = state.query.sort == ProjectSort.NAME,
            onClick = { onIntent(HomeIntent.Sort(ProjectSort.NAME)) },
            label = { Text(stringResource(R.string.sort_name)) },
        )
        FilterChip(
            selected = state.query.favoritesOnly,
            onClick = { onIntent(HomeIntent.FavoritesOnly(!state.query.favoritesOnly)) },
            label = { Text(stringResource(R.string.favorites)) },
            leadingIcon = { Icon(Icons.Filled.Star, null) },
        )
    }
}

@Composable
private fun ProjectGrid(
    projects: List<ProjectSummary>,
    onOpen: (ProjectSummary) -> Unit,
    onRename: (ProjectSummary) -> Unit,
    onDelete: (ProjectSummary) -> Unit,
    onFavorite: (ProjectSummary) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 240.dp),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(projects, key = { it.id.value }) { p ->
            ProjectCard(p, { onOpen(p) }, { onRename(p) }, { onDelete(p) }, { onFavorite(p) })
        }
    }
}

@Composable
private fun ProjectCard(p: ProjectSummary, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit, onFavorite: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val opened = stringResource(R.string.open_project, p.name)
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen).semantics { contentDescription = opened }) {
        Box(Modifier.fillMaxWidth().aspectRatio(THUMB_RATIO).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Thumbnail(p.thumbnailPath, p.thumbnailVersion)
        }
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    pluralTabs(p.tabCount) + " · " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(p.updatedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onFavorite) {
                if (p.favorite) {
                    Icon(Icons.Filled.Star, stringResource(R.string.unfavorite), tint = MaterialTheme.colorScheme.primary)
                } else {
                    Icon(Icons.Outlined.StarOutline, stringResource(R.string.favorite))
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more_options)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rename)) },
                        leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null) },
                        onClick = {
                            menu = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                        onClick = {
                            menu = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun pluralTabs(n: Int) = if (n == 1) stringResource(R.string.one_tab) else stringResource(R.string.n_tabs, n)

@Composable
private fun Thumbnail(path: String?, version: Long) {
    val bitmap by produceState<ImageBitmap?>(null, path, version) {
        value = path?.let { withContext(Dispatchers.IO) { BitmapFactory.decodeFile(it)?.asImageBitmap() } }
    }
    bitmap?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
}

@Composable
private fun EmptyState() {
    CenterText(stringResource(R.string.empty_home))
}

@Composable
private fun CenterText(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun RenameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename)) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private const val THUMB_RATIO = 16f / 10f
