package io.github.leonardosponton.inkflow.feature.palette

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.domain.diagram.NotationId
import io.github.leonardosponton.inkflow.domain.diagram.ShapeDefinition
import io.github.leonardosponton.inkflow.domain.diagram.ShapeRegistry
import io.github.leonardosponton.inkflow.domain.diagram.ShapeTypeKey
import io.github.leonardosponton.inkflow.feature.canvasrender.ShapePreview

/** Drag callbacks report positions in root coordinates. */
interface PaletteDragListener {
    fun onDrag(key: ShapeTypeKey, root: Offset)

    fun onDrop(key: ShapeTypeKey, root: Offset)

    fun onCancel()
}

/**
 * Searchable shape library grouped by notation, with recently used shapes first. Tap a shape to
 * arm the shape tool, or drag it onto the canvas with a finger or the pen.
 */
@Composable
fun ShapePalette(
    registry: ShapeRegistry,
    notationNames: Map<NotationId, String>,
    preferred: NotationId?,
    recent: List<ShapeTypeKey>,
    selected: ShapeTypeKey?,
    onPick: (ShapeTypeKey) -> Unit,
    drag: PaletteDragListener,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val groups = remember(registry, query, preferred, recent) { PaletteGroups.build(registry, query, preferred, recent) }
    Column(modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            placeholder = { Text(stringResource(R.string.search_shapes)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(8.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(CELL.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            groups.forEach { group ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "h-${group.title}") {
                    val title = if (group.isRecent) stringResource(R.string.recent) else notationNames[group.notation] ?: group.title
                    Text(
                        title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(group.shapes, key = { "${group.title}-${it.key.value}" }) { def ->
                    ShapeCell(def, def.key == selected, onPick, drag)
                }
            }
        }
    }
}

@Composable
private fun ShapeCell(def: ShapeDefinition, selected: Boolean, onPick: (ShapeTypeKey) -> Unit, drag: PaletteDragListener) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    val strokeColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val fillColor = MaterialTheme.colorScheme.surface.toArgb()
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .onGloballyPositioned { origin = it.boundsInRoot().topLeft }
            .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(8.dp))
            .clickable { onPick(def.key) }
            .pointerInput(def.key) {
                detectDragGestures(
                    onDragStart = { pointer = it },
                    onDrag = { change, amount ->
                        change.consume()
                        pointer += amount
                        drag.onDrag(def.key, origin + pointer)
                    },
                    onDragEnd = { drag.onDrop(def.key, origin + pointer) },
                    onDragCancel = { drag.onCancel() },
                )
            }
            .padding(4.dp)
            .semantics { contentDescription = def.displayName },
    ) {
        Box(Modifier.size(ICON.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(ICON.dp)) {
                drawIntoCanvas { c ->
                    ShapePreview.draw(c.nativeCanvas, def, Rect(2.0, 2.0, size.width - 2.0, size.height - 2.0), strokeColor, fillColor)
                }
            }
        }
        Text(
            def.displayName,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

private const val CELL = 76
private const val ICON = 44

/** Pure grouping logic for the palette (unit tested). */
object PaletteGroups {
    data class Group(val title: String, val notation: NotationId?, val shapes: List<ShapeDefinition>, val isRecent: Boolean = false)

    fun build(registry: ShapeRegistry, query: String, preferred: NotationId?, recent: List<ShapeTypeKey>): List<Group> {
        val matches = registry.search(query)
        val recentGroup = recent.mapNotNull { registry[it] }.filter { it in matches }
            .takeIf { it.isNotEmpty() && query.isBlank() }
            ?.let { Group("recent", null, it, isRecent = true) }
        val byNotation = matches.groupBy { it.notation }
        val order = byNotation.keys.sortedWith(
            compareBy<NotationId> {
                it != preferred
            }.thenBy { it != NotationId.Basic }.thenBy { it.value },
        )
        return listOfNotNull(recentGroup) + order.map { Group(it.value, it, byNotation.getValue(it)) }
    }
}
