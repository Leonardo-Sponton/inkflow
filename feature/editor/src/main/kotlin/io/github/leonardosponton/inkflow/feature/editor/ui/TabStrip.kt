package io.github.leonardosponton.inkflow.feature.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.leonardosponton.inkflow.core.model.DiagramProfile
import io.github.leonardosponton.inkflow.core.model.TabColor
import io.github.leonardosponton.inkflow.core.model.TabId
import io.github.leonardosponton.inkflow.domain.project.Tab
import io.github.leonardosponton.inkflow.feature.editor.EditorIntent
import io.github.leonardosponton.inkflow.feature.editor.R

/** Horizontal project tabs; long-press and drag (finger or pen) to reorder. */
@Composable
fun TabStrip(tabs: List<Tab>, active: TabId?, onIntent: (EditorIntent) -> Unit, onRename: (Tab) -> Unit) {
    val widths = remember { mutableStateMapOf<TabId, Float>() }
    var dragging by remember { mutableStateOf<TabId?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var addMenu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            tabs.forEachIndexed { index, tab ->
                val isDragged = dragging == tab.id
                TabChip(
                    tab = tab,
                    selected = tab.id == active,
                    canDelete = tabs.size > 1,
                    onIntent = onIntent,
                    onRename = { onRename(tab) },
                    modifier = Modifier
                        .onGloballyPositioned { widths[tab.id] = it.size.width.toFloat() }
                        .graphicsLayer {
                            translationX = if (isDragged) dragOffset else 0f
                            shadowElevation = if (isDragged) DRAG_ELEVATION else 0f
                        }
                        .pointerInput(tab.id, tabs.size) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    dragging = tab.id
                                    dragOffset = 0f
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.x
                                },
                                onDragEnd = {
                                    val target = targetIndex(index, dragOffset, tabs.map { widths[it.id] ?: 0f })
                                    if (target != index) onIntent(EditorIntent.MoveTab(index, target))
                                    dragging = null
                                    dragOffset = 0f
                                },
                                onDragCancel = {
                                    dragging = null
                                    dragOffset = 0f
                                },
                            )
                        },
                )
            }
        }
        Box {
            IconButton(onClick = { addMenu = true }) { Icon(Icons.Outlined.Add, stringResource(R.string.add_tab)) }
            DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                DiagramProfile.entries.forEach { p ->
                    DropdownMenuItem(text = { Text(profileName(p)) }, onClick = {
                        addMenu = false
                        onIntent(EditorIntent.AddTab(p))
                    })
                }
            }
        }
    }
}

/** Index a tab dragged by [offset] px lands on, given every tab's width. */
internal fun targetIndex(from: Int, offset: Float, widths: List<Float>): Int {
    var i = from
    var remaining = offset
    while (remaining > 0 && i < widths.lastIndex && remaining > widths[i + 1] / 2) {
        remaining -= widths[i + 1]
        i++
    }
    while (remaining < 0 && i > 0 && -remaining > widths[i - 1] / 2) {
        remaining += widths[i - 1]
        i--
    }
    return i
}

@Composable
private fun TabChip(
    tab: Tab,
    selected: Boolean,
    canDelete: Boolean,
    onIntent: (EditorIntent) -> Unit,
    onRename: () -> Unit,
    modifier: Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val label = stringResource(R.string.tab_label, tab.title)
    Box(modifier) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = bg,
            tonalElevation = if (selected) 2.dp else 0.dp,
            modifier = Modifier
                .height(40.dp)
                .semantics { contentDescription = label }
                .clickable { if (selected) menu = true else onIntent(EditorIntent.SelectTab(tab.id)) },
        ) {
            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                if (tab.color != TabColor.NONE) {
                    Box(Modifier.size(10.dp).background(tabColor(tab.color), CircleShape))
                    Box(Modifier.size(8.dp))
                }
                Text(tab.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = {
                menu = false
                onRename()
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.duplicate)) }, onClick = {
                menu = false
                onIntent(EditorIntent.DuplicateTab(tab.id))
            })
            HorizontalDivider()
            Text(
                stringResource(R.string.palette_profile),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            DiagramProfile.entries.forEach { p ->
                DropdownMenuItem(
                    text = { Text((if (p == tab.profile) "✓ " else "") + profileName(p)) },
                    onClick = {
                        menu = false
                        onIntent(EditorIntent.SetTabProfile(tab.id, p))
                    },
                )
            }
            HorizontalDivider()
            Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TabColor.entries.forEach { c ->
                    Box(
                        Modifier.size(28.dp).padding(2.dp)
                            .background(if (c == TabColor.NONE) MaterialTheme.colorScheme.outlineVariant else tabColor(c), CircleShape)
                            .clickable {
                                menu = false
                                onIntent(EditorIntent.SetTabColor(tab.id, c))
                            }
                            .semantics { contentDescription = c.name.lowercase() },
                    )
                }
            }
            if (canDelete) {
                HorizontalDivider()
                DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = {
                    menu = false
                    onIntent(EditorIntent.DeleteTab(tab.id))
                })
            }
        }
    }
}

@Composable
fun profileName(p: DiagramProfile) = stringResource(
    when (p) {
        DiagramProfile.FREEFORM -> R.string.profile_freeform
        DiagramProfile.UML -> R.string.profile_uml
        DiagramProfile.DATABASE -> R.string.profile_database
        DiagramProfile.FLOWCHART -> R.string.profile_flowchart
    },
)

fun tabColor(c: TabColor): Color = when (c) {
    TabColor.NONE -> Color.Transparent
    TabColor.RED -> Color(0xFFE53935)
    TabColor.ORANGE -> Color(0xFFFB8C00)
    TabColor.YELLOW -> Color(0xFFFDD835)
    TabColor.GREEN -> Color(0xFF43A047)
    TabColor.BLUE -> Color(0xFF1E88E5)
    TabColor.PURPLE -> Color(0xFF8E24AA)
}

private const val DRAG_ELEVATION = 12f
