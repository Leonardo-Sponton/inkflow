package io.github.leonardosponton.inkflow.feature.editor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.leonardosponton.inkflow.core.model.GridStyle
import io.github.leonardosponton.inkflow.domain.canvas.command.ZOrderOp
import io.github.leonardosponton.inkflow.domain.canvas.notation.FollowUp
import io.github.leonardosponton.inkflow.feature.editor.ContextMenuRequest
import io.github.leonardosponton.inkflow.feature.editor.EditorIntent
import io.github.leonardosponton.inkflow.feature.editor.EditorUiState
import io.github.leonardosponton.inkflow.feature.editor.EditorViewModel
import io.github.leonardosponton.inkflow.feature.editor.R
import io.github.leonardosponton.inkflow.feature.editor.TextEditRequest
import io.github.leonardosponton.inkflow.feature.editor.interaction.ToolKind
import kotlin.math.roundToInt

@Composable
fun EditorTopBar(
    state: EditorUiState,
    onBack: () -> Unit,
    onIntent: (EditorIntent) -> Unit,
    onRenameProject: () -> Unit,
    onExport: () -> Unit,
    onImportDdl: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Surface(tonalElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
            Text(
                state.projectName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).clickable(onClick = onRenameProject).padding(8.dp),
            )
            IconButton(onClick = {
                onIntent(EditorIntent.Undo)
            }, enabled = state.canUndo) { Icon(Icons.AutoMirrored.Outlined.Undo, stringResource(R.string.undo)) }
            IconButton(onClick = {
                onIntent(EditorIntent.Redo)
            }, enabled = state.canRedo) { Icon(Icons.AutoMirrored.Outlined.Redo, stringResource(R.string.redo)) }
            IconButton(onClick = { onIntent(EditorIntent.TogglePalette) }) {
                Icon(Icons.Outlined.Widgets, stringResource(R.string.shapes), tint = tint(state.showPalette))
            }
            IconButton(onClick = { onIntent(EditorIntent.ToggleProperties) }) {
                Icon(Icons.Outlined.Tune, stringResource(R.string.properties), tint = tint(state.showProperties))
            }
            IconButton(onClick = { onIntent(EditorIntent.ToggleIssues) }) {
                BadgedBox(badge = { if (state.issues.isNotEmpty()) Badge { Text("${state.issues.size}") } }) {
                    Icon(Icons.Outlined.WarningAmber, stringResource(R.string.validation), tint = tint(state.showIssues))
                }
            }
            IconButton(onClick = onExport) { Icon(Icons.Outlined.IosShare, stringResource(R.string.export)) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    Text(
                        stringResource(R.string.background),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    GridStyle.entries.forEach { g ->
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Outlined.GridOn, null) },
                            text = {
                                Text(
                                    (if (state.activeTab?.grid == g) "✓ " else "") + g.name.lowercase().replaceFirstChar {
                                        it.uppercase()
                                    },
                                )
                            },
                            onClick = {
                                menu = false
                                onIntent(EditorIntent.SetTabGrid(g))
                            },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text(stringResource(R.string.select_all)) }, onClick = {
                        menu = false
                        onIntent(EditorIntent.SetTool(ToolKind.SELECT))
                        onIntent(EditorIntent.SelectAll)
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.paste)) }, enabled = state.clipboardFull, onClick = {
                        menu = false
                        onIntent(EditorIntent.Paste)
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.import_ddl)) }, onClick = {
                        menu = false
                        onImportDdl()
                    })
                }
            }
        }
    }
}

@Composable
private fun tint(on: Boolean) = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

/** Mini toolbar floating above the selection (pen-friendly 48 dp targets). */
@Composable
fun ContextToolbar(vm: EditorViewModel, state: EditorUiState, modifier: Modifier) {
    val rect by vm.selectionScreen.collectAsStateWithLifecycle()
    val r = rect ?: return
    if (!state.hasSelection || state.editing != null) return
    val density = LocalDensity.current
    val heightPx = with(density) { TOOLBAR_H.dp.toPx() }
    val y = (r.top - heightPx - with(density) { 12.dp.toPx() }).coerceAtLeast(0.0)
    val locked = state.selected.all { it.locked }
    Surface(
        modifier.offset { IntOffset(r.left.roundToInt().coerceAtLeast(0), y.roundToInt()) },
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.onIntent(EditorIntent.Copy) }) { Icon(Icons.Outlined.ContentCopy, stringResource(R.string.copy)) }
            IconButton(onClick = { vm.onIntent(EditorIntent.Cut) }) { Icon(Icons.Outlined.ContentCut, stringResource(R.string.cut)) }
            IconButton(onClick = { vm.onIntent(EditorIntent.Duplicate) }) { Text("⧉", style = MaterialTheme.typography.titleMedium) }
            IconButton(onClick = { vm.onIntent(EditorIntent.Lock(!locked)) }) {
                Icon(
                    if (locked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                    stringResource(if (locked) R.string.unlock else R.string.lock),
                )
            }
            IconButton(onClick = {
                vm.onIntent(EditorIntent.ToggleProperties)
            }) { Icon(Icons.Outlined.Tune, stringResource(R.string.properties)) }
            IconButton(onClick = {
                vm.onIntent(EditorIntent.DeleteSelection)
            }) { Icon(Icons.Outlined.Delete, stringResource(R.string.delete)) }
        }
    }
}

/** Text field placed over the edited element; S Pen handwriting goes through the system keyboard. */
@Composable
fun InlineTextEditor(request: TextEditRequest, onIntent: (EditorIntent) -> Unit) {
    var value by remember(request.id) { mutableStateOf(TextFieldValue(request.text, TextRange(request.text.length))) }
    val focus = remember { FocusRequester() }
    val density = LocalDensity.current
    val r = request.screenRect
    val fontSp = with(density) { request.fontSizePx.toFloat().coerceIn(MIN_EDIT_PX, MAX_EDIT_PX).toSp() }
    Popup(offset = IntOffset(r.left.roundToInt(), r.top.roundToInt()), onDismissRequest = {
        onIntent(EditorIntent.CommitText(request.id, value.text))
    }, properties = PopupProperties(focusable = true)) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (request.sticky) Color(STICKY) else MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
        ) {
            Row(verticalAlignment = Alignment.Top) {
                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    textStyle = TextStyle(
                        fontSize = fontSp,
                        color = if (request.sticky) Color.Black else MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .padding(8.dp)
                        .size(
                            with(density) {
                                r.width.toFloat().coerceAtLeast(MIN_EDIT_W).toDp()
                            },
                            with(density) { r.height.toFloat().coerceAtLeast(MIN_EDIT_H).toDp() },
                        )
                        .focusRequester(focus)
                        .onPreviewKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) {
                                onIntent(EditorIntent.CommitText(request.id, value.text))
                                true
                            } else {
                                false
                            }
                        },
                )
                IconButton(onClick = {
                    onIntent(EditorIntent.CommitText(request.id, value.text))
                }) { Icon(Icons.Outlined.Check, stringResource(R.string.done)) }
            }
        }
    }
    LaunchedEffect(request.id) { focus.requestFocus() }
}

@Composable
fun CanvasContextMenu(request: ContextMenuRequest, state: EditorUiState, onIntent: (EditorIntent) -> Unit) {
    val dismiss = { onIntent(EditorIntent.DismissContextMenu) }
    Box(Modifier.offset { IntOffset(request.screen.x.roundToInt(), request.screen.y.roundToInt()) }) {
        DropdownMenu(expanded = true, onDismissRequest = dismiss) {
            if (request.target != null) {
                MenuItem(R.string.edit_text) { onIntent(EditorIntent.BeginTextEdit(request.target)) }
                MenuItem(R.string.copy) { onIntent(EditorIntent.Copy) }
                MenuItem(R.string.cut) { onIntent(EditorIntent.Cut) }
                MenuItem(R.string.duplicate) { onIntent(EditorIntent.Duplicate) }
                MenuItem(R.string.to_front) { onIntent(EditorIntent.Order(ZOrderOp.TO_FRONT)) }
                MenuItem(R.string.to_back) { onIntent(EditorIntent.Order(ZOrderOp.TO_BACK)) }
                val locked = state.selected.all { it.locked }
                MenuItem(if (locked) R.string.unlock else R.string.lock) { onIntent(EditorIntent.Lock(!locked)) }
                if (state.selected.size > 1) MenuItem(R.string.group) { onIntent(EditorIntent.Group) }
                if (state.selected.any { it.groupId != null }) MenuItem(R.string.ungroup) { onIntent(EditorIntent.Ungroup) }
                MenuItem(R.string.delete) { onIntent(EditorIntent.DeleteSelection) }
            } else {
                MenuItem(R.string.paste, enabled = state.clipboardFull) { onIntent(EditorIntent.Paste) }
                MenuItem(R.string.select_all) { onIntent(EditorIntent.SelectAll) }
                MenuItem(R.string.fit_content) { onIntent(EditorIntent.FitContent) }
            }
        }
    }
}

@Composable
private fun MenuItem(label: Int, enabled: Boolean = true, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(label)) }, enabled = enabled, onClick = onClick)
}

@Composable
fun FollowUpDialog(f: FollowUp, onIntent: (EditorIntent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onIntent(EditorIntent.DismissFollowUp) },
        text = { Text(f.message) },
        confirmButton = { TextButton(onClick = { onIntent(EditorIntent.AcceptFollowUp) }) { Text(f.actionLabel) } },
        dismissButton = { TextButton(onClick = { onIntent(EditorIntent.DismissFollowUp) }) { Text(stringResource(R.string.not_now)) } },
    )
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

/** List of notation warnings; tap one to select the elements involved. */
@Composable
fun ValidationPanel(state: EditorUiState, onIntent: (EditorIntent) -> Unit, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), tonalElevation = 3.dp, shadowElevation = 6.dp) {
        Column(Modifier.padding(12.dp)) {
            Text(stringResource(R.string.validation), style = MaterialTheme.typography.titleSmall)
            if (state.issues.isEmpty()) {
                Text(stringResource(R.string.no_issues), style = MaterialTheme.typography.bodySmall)
            } else {
                LazyColumn(Modifier.padding(top = 4.dp).size(width = 336.dp, height = 280.dp)) {
                    items(state.issues) { issue ->
                        Text(
                            "• ${issue.message}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth().clickable {
                                onIntent(EditorIntent.Select(issue.elements.toSet()))
                            }.padding(vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Book Cover Keyboard shortcuts. Returns `true` when consumed. */
fun keyboardShortcut(e: KeyEvent, state: EditorUiState, onIntent: (EditorIntent) -> Unit): Boolean {
    if (e.type != KeyEventType.KeyDown || state.editing != null) return false
    val intent: EditorIntent? = if (e.isCtrlPressed) {
        when (e.key) {
            Key.Z -> if (e.isShiftPressed) EditorIntent.Redo else EditorIntent.Undo
            Key.Y -> EditorIntent.Redo
            Key.C -> EditorIntent.Copy
            Key.X -> EditorIntent.Cut
            Key.V -> EditorIntent.Paste
            Key.D -> EditorIntent.Duplicate
            Key.A -> EditorIntent.SelectAll
            Key.G -> if (e.isShiftPressed) EditorIntent.Ungroup else EditorIntent.Group
            Key.Equals, Key.Plus, Key.NumPadAdd -> EditorIntent.Zoom(KEY_ZOOM)
            Key.Minus, Key.NumPadSubtract -> EditorIntent.Zoom(1 / KEY_ZOOM)
            Key.Zero -> EditorIntent.FitContent
            Key.One -> EditorIntent.ZoomReset
            else -> null
        }
    } else {
        when (e.key) {
            Key.Delete, Key.Backspace -> EditorIntent.DeleteSelection
            Key.Escape -> EditorIntent.ClearSelection
            Key.V -> EditorIntent.SetTool(ToolKind.SELECT)
            Key.L -> EditorIntent.SetTool(ToolKind.LASSO)
            Key.P -> EditorIntent.SetTool(ToolKind.PEN)
            Key.H -> EditorIntent.SetTool(ToolKind.HIGHLIGHTER)
            Key.E -> EditorIntent.SetTool(ToolKind.ERASER)
            Key.T -> EditorIntent.SetTool(ToolKind.TEXT)
            Key.N -> EditorIntent.SetTool(ToolKind.STICKY)
            Key.R -> EditorIntent.SetTool(ToolKind.SHAPE)
            Key.C -> EditorIntent.SetTool(ToolKind.CONNECTOR)
            else -> null
        }
    }
    intent?.let(onIntent)
    return intent != null
}

private const val TOOLBAR_H = 48
private const val KEY_ZOOM = 1.25
private const val STICKY = 0xFFFFF59D
private const val MIN_EDIT_PX = 14f
private const val MAX_EDIT_PX = 64f
private const val MIN_EDIT_W = 220f
private const val MIN_EDIT_H = 56f
