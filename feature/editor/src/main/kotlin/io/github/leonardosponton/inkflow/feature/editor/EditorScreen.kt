package io.github.leonardosponton.inkflow.feature.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.leonardosponton.inkflow.core.model.Vec2
import io.github.leonardosponton.inkflow.domain.diagram.NotationId
import io.github.leonardosponton.inkflow.domain.diagram.ShapeTypeKey
import io.github.leonardosponton.inkflow.feature.canvasrender.AssetImageCache
import io.github.leonardosponton.inkflow.feature.canvasrender.InkCanvasView
import io.github.leonardosponton.inkflow.feature.canvasrender.ShapePreview
import io.github.leonardosponton.inkflow.feature.editor.ui.CanvasContextMenu
import io.github.leonardosponton.inkflow.feature.editor.ui.ContextToolbar
import io.github.leonardosponton.inkflow.feature.editor.ui.EditorTopBar
import io.github.leonardosponton.inkflow.feature.editor.ui.FollowUpDialog
import io.github.leonardosponton.inkflow.feature.editor.ui.InlineTextEditor
import io.github.leonardosponton.inkflow.feature.editor.ui.Minimap
import io.github.leonardosponton.inkflow.feature.editor.ui.PropertiesPanel
import io.github.leonardosponton.inkflow.feature.editor.ui.RenameDialog
import io.github.leonardosponton.inkflow.feature.editor.ui.TabStrip
import io.github.leonardosponton.inkflow.feature.editor.ui.ToolRail
import io.github.leonardosponton.inkflow.feature.editor.ui.ValidationPanel
import io.github.leonardosponton.inkflow.feature.editor.ui.ZoomControls
import io.github.leonardosponton.inkflow.feature.editor.ui.keyboardShortcut
import io.github.leonardosponton.inkflow.feature.export.ExportSheet
import io.github.leonardosponton.inkflow.feature.export.ExportTarget
import io.github.leonardosponton.inkflow.feature.palette.ShapePalette
import kotlinx.coroutines.launch

@Composable
fun EditorScreen(viewModel: EditorViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onIntent = viewModel::onIntent
    val focus = remember { FocusRequester() }
    var renaming by remember { mutableStateOf<RenameTarget?>(null) }
    var toolbarRight by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val imagePicker = rememberImagePicker(onIntent)
    val ddlPicker = rememberDdlPicker(onIntent)

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding()
                // Bubbling (not preview): focused text fields consume their own keys first.
                .onKeyEvent { keyboardShortcut(it, state, onIntent) }
                .focusRequester(focus)
                .focusable(),
        ) {
            EditorTopBar(
                state = state,
                onBack = onBack,
                onIntent = onIntent,
                onRenameProject = { renaming = RenameTarget.Project(state.projectName) },
                onExport = {
                    scope.launch {
                        viewModel.flushForExport()
                        exporting = true
                    }
                },
                onImportDdl = ddlPicker,
            )
            TabStrip(state.tabs, state.activeTabId, onIntent) { renaming = RenameTarget.Tab(it.id, it.title) }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth >= WIDE_DP.dp
                Row(Modifier.fillMaxSize()) {
                    if (state.showPalette && wide) PaletteSide(viewModel, state, Modifier.width(PALETTE_DP.dp).fillMaxHeight())
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        CanvasArea(viewModel, state, toolbarRight, { toolbarRight = !toolbarRight }, imagePicker) { focus.requestFocus() }
                    }
                    if (wide && state.hasSelection && state.showProperties) {
                        Surface(tonalElevation = 1.dp, modifier = Modifier.width(PANEL_DP.dp).fillMaxHeight()) {
                            PropertiesPanel(state, viewModel.catalog, onIntent, onClose = { onIntent(EditorIntent.ToggleProperties) })
                        }
                    }
                }
                if (!wide) NarrowSheets(viewModel, state, Modifier.align(Alignment.BottomCenter))
            }
        }
        Overlays(viewModel, state)
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val tab = state.activeTabId
    if (exporting && tab != null) {
        ExportSheet(
            target = ExportTarget(viewModel.projectId, tab, state.selected.map { it.id }.toSet(), state.settings.darkCanvas),
            hasSelection = state.hasSelection,
            onDismiss = { exporting = false },
        )
    }
    renaming?.let { target ->
        RenameDialog(target.initial, onDismiss = { renaming = null }) { text ->
            when (target) {
                is RenameTarget.Project -> onIntent(EditorIntent.RenameProject(text))
                is RenameTarget.Tab -> onIntent(EditorIntent.RenameTab(target.id, text))
            }
            renaming = null
        }
    }
}

private sealed interface RenameTarget {
    val initial: String

    data class Project(override val initial: String) : RenameTarget

    data class Tab(val id: io.github.leonardosponton.inkflow.core.model.TabId, override val initial: String) : RenameTarget
}

@Composable
private fun rememberImagePicker(onIntent: (EditorIntent) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } ?: return@launch
            onIntent(EditorIntent.InsertImage(bytes, context.contentResolver.getType(uri) ?: "image/*"))
        }
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

@Composable
private fun rememberDdlPicker(onIntent: (EditorIntent) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val sql = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            } ?: return@launch
            onIntent(EditorIntent.ImportDdl(sql))
        }
    }
    return { launcher.launch(arrayOf("application/sql", "text/*", "application/octet-stream")) }
}

@Composable
private fun CanvasArea(
    vm: EditorViewModel,
    state: EditorUiState,
    toolbarRight: Boolean,
    swapSide: () -> Unit,
    pickImage: () -> Unit,
    focusEditor: () -> Unit,
) {
    var canvasOrigin by remember { mutableStateOf(Offset.Zero) }
    var canvasView by remember { mutableStateOf<InkCanvasView?>(null) }
    Box(Modifier.fillMaxSize().onGloballyPositioned { canvasOrigin = it.boundsInRoot().topLeft }) {
        AndroidView(
            factory = { ctx ->
                lateinit var view: InkCanvasView
                val images = AssetImageCache({ vm.renderers.assetFile(vm.projectId, it) }) { view.invalidate() }
                view = InkCanvasView(ctx, vm.renderers.screenDrawer(images))
                view.host = vm
                view.accessibility = vm
                canvasView = view
                view
            },
            modifier = Modifier.fillMaxSize().clipToBounds().pointerInput(Unit) {
                // Touching the canvas moves focus from text fields (properties panel) back to the
                // editor, so keyboard shortcuts such as Delete act on the selection again.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    focusEditor()
                }
            },
        )
        // Frames go straight into the view, without recomposition.
        LaunchedEffect(canvasView) { canvasView?.let { v -> vm.frame.collect { v.frame = it } } }
        ToolRail(
            tools = state.tools,
            onIntent = vm::onIntent,
            onInsertImage = pickImage,
            onSwapSide = swapSide,
            modifier = Modifier.align(
                if (toolbarRight) Alignment.CenterEnd else Alignment.CenterStart,
            ).padding(12.dp).heightIn(max = 720.dp),
        )
        ContextToolbar(vm, state, Modifier)
        Column(Modifier.align(Alignment.BottomEnd).padding(12.dp), horizontalAlignment = Alignment.End) {
            if (state.settings.showMinimap) {
                val frame by vm.frame.collectAsStateWithLifecycle()
                frame?.let { Minimap(it, vm.viewport, vm::onIntent, Modifier.padding(bottom = 8.dp)) }
            }
            ZoomControls(state.zoom, state.settings.showMinimap, vm::onIntent)
        }
        if (state.showIssues) ValidationPanel(state, vm::onIntent, Modifier.align(Alignment.TopEnd).padding(12.dp).widthIn(max = 360.dp))
        state.editing?.let { InlineTextEditor(it, vm::onIntent) }
        state.contextMenu?.let { CanvasContextMenu(it, state, vm::onIntent) }
        PaletteDropTarget(vm, canvasOrigin)
    }
}

@Composable
private fun PaletteSide(vm: EditorViewModel, state: EditorUiState, modifier: Modifier) {
    Surface(modifier, tonalElevation = 1.dp) {
        ShapePalette(
            registry = vm.catalog.shapes,
            notationNames = vm.catalog.modules.associate { it.id to it.displayName },
            preferred = preferredNotation(state),
            recent = state.tools.recentShapes,
            selected = state.tools.shape.takeIf {
                state.tools.kind ==
                    io.github.leonardosponton.inkflow.feature.editor.interaction.ToolKind.SHAPE
            },
            onPick = { vm.onIntent(EditorIntent.SetShapeTool(it)) },
            drag = vm.paletteDrag,
        )
    }
}

@Composable
private fun NarrowSheets(vm: EditorViewModel, state: EditorUiState, modifier: Modifier) {
    Column(modifier.fillMaxWidth()) {
        if (state.showPalette) {
            Surface(tonalElevation = 2.dp, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                ShapePalette(
                    registry = vm.catalog.shapes,
                    notationNames = vm.catalog.modules.associate { it.id to it.displayName },
                    preferred = preferredNotation(state),
                    recent = state.tools.recentShapes,
                    selected = state.tools.shape,
                    onPick = { vm.onIntent(EditorIntent.SetShapeTool(it)) },
                    drag = vm.paletteDrag,
                )
            }
        }
        if (state.hasSelection && state.showProperties) {
            Surface(tonalElevation = 2.dp, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                PropertiesPanel(state, vm.catalog, vm::onIntent, onClose = { vm.onIntent(EditorIntent.ToggleProperties) })
            }
        }
    }
}

private fun preferredNotation(state: EditorUiState): NotationId? = when (state.activeTab?.profile) {
    io.github.leonardosponton.inkflow.core.model.DiagramProfile.UML -> NotationId("uml")
    io.github.leonardosponton.inkflow.core.model.DiagramProfile.DATABASE -> NotationId("db")
    io.github.leonardosponton.inkflow.core.model.DiagramProfile.FLOWCHART -> NotationId("flowchart")
    else -> null
}

/** Ghost shown while a palette shape is dragged; dropping over the canvas places it. */
@Composable
private fun PaletteDropTarget(vm: EditorViewModel, canvasOrigin: Offset) {
    val drag by vm.paletteDragState.collectAsStateWithLifecycle()
    LaunchedEffect(canvasOrigin) { vm.canvasOriginInRoot = Vec2(canvasOrigin.x.toDouble(), canvasOrigin.y.toDouble()) }
    drag?.let { d -> DragGhost(vm, d.key, d.root - canvasOrigin) }
}

@Composable
private fun DragGhost(vm: EditorViewModel, key: ShapeTypeKey, at: Offset) {
    val def = vm.catalog.shapes.resolve(key)
    val strokeColor = MaterialTheme.colorScheme.primary.toArgb()
    val fillColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = GHOST_ALPHA).toArgb()
    Canvas(
        Modifier.offset { IntOffset((at.x - GHOST_PX / 2).toInt(), (at.y - GHOST_PX / 2).toInt()) }.size(GHOST_DP.dp),
    ) {
        drawIntoCanvas { c ->
            ShapePreview.draw(
                c.nativeCanvas,
                def,
                io.github.leonardosponton.inkflow.core.model.Rect(0.0, 0.0, size.width.toDouble(), size.height.toDouble()),
                strokeColor,
                fillColor,
            )
        }
    }
}

@Composable
private fun Overlays(vm: EditorViewModel, state: EditorUiState) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        val m = state.message ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(m.text, m.actionLabel, duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) m.action?.let(vm::onIntent)
        vm.onIntent(EditorIntent.DismissMessage)
    }
    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.BottomCenter) { SnackbarHost(snackbar) }
    state.followUp?.let { FollowUpDialog(it, vm::onIntent) }
}

private const val WIDE_DP = 840
private const val PANEL_DP = 320
private const val PALETTE_DP = 300
private const val GHOST_DP = 72
private const val GHOST_PX = 180f
private const val GHOST_ALPHA = 0.6f
