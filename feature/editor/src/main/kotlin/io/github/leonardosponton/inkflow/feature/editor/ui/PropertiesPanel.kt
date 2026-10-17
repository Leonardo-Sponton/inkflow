package io.github.leonardosponton.inkflow.feature.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.FlipToFront
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.leonardosponton.inkflow.core.model.ColorArgb
import io.github.leonardosponton.inkflow.core.model.DashPattern
import io.github.leonardosponton.inkflow.core.model.ShapeStyle
import io.github.leonardosponton.inkflow.core.model.StylePatch
import io.github.leonardosponton.inkflow.core.model.TextAlign
import io.github.leonardosponton.inkflow.domain.canvas.command.ZOrderOp
import io.github.leonardosponton.inkflow.domain.canvas.element.CanvasElement
import io.github.leonardosponton.inkflow.domain.canvas.element.Connector
import io.github.leonardosponton.inkflow.domain.canvas.element.ImageElement
import io.github.leonardosponton.inkflow.domain.canvas.element.InkStroke
import io.github.leonardosponton.inkflow.domain.canvas.element.LabelPosition
import io.github.leonardosponton.inkflow.domain.canvas.element.Routing
import io.github.leonardosponton.inkflow.domain.canvas.element.ShapeNode
import io.github.leonardosponton.inkflow.domain.canvas.element.TextElement
import io.github.leonardosponton.inkflow.domain.canvas.notation.NotationCatalog
import io.github.leonardosponton.inkflow.domain.diagram.MarkerKey
import io.github.leonardosponton.inkflow.feature.editor.EditorIntent
import io.github.leonardosponton.inkflow.feature.editor.EditorUiState
import io.github.leonardosponton.inkflow.feature.editor.R

/** Side sheet (or bottom sheet on narrow windows) editing the current selection. */
@Composable
fun PropertiesPanel(
    state: EditorUiState,
    catalog: NotationCatalog,
    onIntent: (EditorIntent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = state.selected
    Column(modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title(selected, catalog), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, stringResource(R.string.close)) }
        }
        val single = state.singleSelected
        if (single is ShapeNode) {
            NodeContentEditor(single, catalog, onIntent)
            ShapeSwitcher(single, catalog, onIntent)
        }
        if (single is Connector) ConnectorEditor(single, catalog, onIntent)
        StyleEditor(selected, onIntent)
        HorizontalDivider()
        ArrangeEditor(selected, onIntent)
    }
}

private fun title(selected: List<CanvasElement>, catalog: NotationCatalog): String = when (val s = selected.singleOrNull()) {
    null -> "${selected.size} items"
    is ShapeNode -> catalog.shapes.resolve(s.shape).displayName
    is Connector -> "Connector"
    is InkStroke -> "Ink"
    is TextElement -> "Text"
    is ImageElement -> "Image"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StyleEditor(selected: List<CanvasElement>, onIntent: (EditorIntent) -> Unit) {
    val style = styleOf(selected.firstOrNull()) ?: return
    val hasFill = selected.any { it is ShapeNode || it is TextElement }
    val hasText = selected.any { it is ShapeNode || it is TextElement || it is Connector }
    Section(stringResource(R.string.stroke))
    ColorRow(style.stroke, allowNone = false) { onIntent(EditorIntent.Style(StylePatch(stroke = it))) }
    LabeledSlider(stringResource(R.string.width), style.strokeWidth.toFloat(), MIN_WIDTH..MAX_WIDTH) {
        onIntent(EditorIntent.Style(StylePatch(strokeWidth = it.toDouble())))
    }
    if (selected.none { it is InkStroke }) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DashPattern.entries.forEach { d ->
                FilterChip(selected = style.dash == d, onClick = {
                    onIntent(EditorIntent.Style(StylePatch(dash = d)))
                }, label = { Text(d.name.lowercase()) })
            }
        }
    }
    if (hasFill) {
        Section(stringResource(R.string.fill))
        ColorRow(style.fill, allowNone = true) { c ->
            onIntent(EditorIntent.Style(if (c == null) StylePatch(clearFill = true) else StylePatch(fill = c)))
        }
    }
    if (hasText) TextStyleEditor(style, onIntent)
    if (hasFill) {
        LabeledSlider(stringResource(R.string.opacity), style.opacity.toFloat(), MIN_OPACITY..1f) {
            onIntent(EditorIntent.Style(StylePatch(opacity = it.toDouble())))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextStyleEditor(style: ShapeStyle, onIntent: (EditorIntent) -> Unit) {
    Section(stringResource(R.string.text))
    ColorRow(style.textColor, allowNone = false) { onIntent(EditorIntent.Style(StylePatch(textColor = it))) }
    LabeledSlider(stringResource(R.string.font_size), style.fontSize.toFloat(), MIN_FONT..MAX_FONT) {
        onIntent(EditorIntent.Style(StylePatch(fontSize = it.toDouble())))
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.Center) {
        IconToggleButton(checked = style.bold, onCheckedChange = { onIntent(EditorIntent.Style(StylePatch(bold = it))) }) {
            Icon(Icons.Outlined.FormatBold, stringResource(R.string.bold))
        }
        IconToggleButton(checked = style.italic, onCheckedChange = { onIntent(EditorIntent.Style(StylePatch(italic = it))) }) {
            Icon(Icons.Outlined.FormatItalic, stringResource(R.string.italic))
        }
        TextAlign.entries.forEach { a ->
            FilterChip(selected = style.textAlign == a, onClick = {
                onIntent(EditorIntent.Style(StylePatch(textAlign = a)))
            }, label = { Text(a.name.lowercase()) })
        }
    }
}

private fun styleOf(el: CanvasElement?): ShapeStyle? = when (el) {
    is ShapeNode -> el.style
    is TextElement -> el.style
    is Connector -> el.style
    is InkStroke -> ShapeStyle(fill = null, stroke = el.brush.color, strokeWidth = el.brush.size.toDouble())
    is ImageElement, null -> null
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConnectorEditor(c: Connector, catalog: NotationCatalog, onIntent: (EditorIntent) -> Unit) {
    Section(stringResource(R.string.routing))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Routing.entries.forEach { r ->
            FilterChip(selected = c.routing == r, onClick = {
                onIntent(EditorIntent.EditConnector(c.id) { it.copy(routing = r, waypoints = emptyList()) })
            }, label = { Text(r.name.lowercase()) })
        }
    }
    if (c.waypoints.isNotEmpty()) {
        TextButton(onClick = {
            onIntent(EditorIntent.EditConnector(c.id) { it.copy(waypoints = emptyList()) })
        }) { Text(stringResource(R.string.clear_waypoints)) }
    } else {
        TextButton(onClick = {
            onIntent(
                EditorIntent.EditConnector(c.id) {
                    it.copy(waypoints = listOf(io.github.leonardosponton.inkflow.core.geometry.pointAlongPolyline(it.route, 0.5)))
                },
            )
        }) { Text(stringResource(R.string.add_waypoint)) }
    }
    MarkerPicker(stringResource(R.string.start_marker), c.startMarker, catalog) { m ->
        onIntent(EditorIntent.EditConnector(c.id) { it.copy(startMarker = m) })
    }
    MarkerPicker(stringResource(R.string.end_marker), c.endMarker, catalog) { m ->
        onIntent(EditorIntent.EditConnector(c.id) { it.copy(endMarker = m) })
    }
    Section(stringResource(R.string.labels))
    LabelPosition.entries.forEach { pos ->
        OutlinedTextField(
            value = c.label(pos),
            onValueChange = { t -> onIntent(EditorIntent.EditConnector(c.id) { it.withLabel(pos, t) }) },
            label = { Text(pos.name.lowercase()) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    ConnectorSemanticsEditor(c, onIntent)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MarkerPicker(label: String, current: MarkerKey, catalog: NotationCatalog, onPick: (MarkerKey) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        catalog.markers.all.forEach { m ->
            FilterChip(selected = m.key == current, onClick = {
                onPick(m.key)
            }, label = { Text(m.displayName, style = MaterialTheme.typography.labelSmall) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShapeSwitcher(node: ShapeNode, catalog: NotationCatalog, onIntent: (EditorIntent) -> Unit) {
    val siblings = shapeAlternatives(node, catalog)
    if (siblings.size < 2) return
    Section(stringResource(R.string.change_shape))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        siblings.forEach { s ->
            FilterChip(selected = s.key == node.shape, onClick = {
                onIntent(EditorIntent.ChangeShape(node.id, s.key))
            }, label = { Text(s.displayName, style = MaterialTheme.typography.labelSmall) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArrangeEditor(selected: List<CanvasElement>, onIntent: (EditorIntent) -> Unit) {
    val locked = selected.all { it.locked }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        IconButton(onClick = {
            onIntent(EditorIntent.Order(ZOrderOp.TO_FRONT))
        }) { Icon(Icons.Outlined.FlipToFront, stringResource(R.string.to_front)) }
        IconButton(onClick = {
            onIntent(EditorIntent.Order(ZOrderOp.TO_BACK))
        }) { Icon(Icons.Outlined.FlipToBack, stringResource(R.string.to_back)) }
        IconButton(onClick = { onIntent(EditorIntent.Lock(!locked)) }) {
            Icon(
                if (locked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                stringResource(if (locked) R.string.unlock else R.string.lock),
            )
        }
        IconButton(onClick = { onIntent(EditorIntent.Duplicate) }) { Icon(Icons.Outlined.ContentCopy, stringResource(R.string.duplicate)) }
        IconButton(onClick = { onIntent(EditorIntent.DeleteSelection) }) { Icon(Icons.Outlined.Delete, stringResource(R.string.delete)) }
    }
    Row {
        TextButton(onClick = { onIntent(EditorIntent.Order(ZOrderOp.FORWARD)) }) { Text(stringResource(R.string.forward)) }
        TextButton(onClick = { onIntent(EditorIntent.Order(ZOrderOp.BACKWARD)) }) { Text(stringResource(R.string.backward)) }
        if (selected.size > 1) TextButton(onClick = { onIntent(EditorIntent.Group) }) { Text(stringResource(R.string.group)) }
        if (selected.any {
                it.groupId != null
            }
        ) {
            TextButton(onClick = { onIntent(EditorIntent.Ungroup) }) { Text(stringResource(R.string.ungroup)) }
        }
    }
}

@Composable
fun Section(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorRow(current: ColorArgb?, allowNone: Boolean, onPick: (ColorArgb?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (allowNone) Swatch(null, current == null) { onPick(null) }
        (ColorArgb.Swatches + PASTELS).forEach { c -> Swatch(c, current?.argb == c.argb) { onPick(c) } }
    }
}

@Composable
private fun Swatch(c: ColorArgb?, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Box(
        Modifier.size(30.dp)
            .border(if (selected) 3.dp else 1.dp, border, CircleShape)
            .padding(3.dp)
            .background(c?.let { Color(it.argb) } ?: Color.Transparent, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = c?.let { "#%08X".format(it.argb) } ?: "none" },
        contentAlignment = Alignment.Center,
    ) {
        if (c == null) Text("∅", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Text("$label: ${"%.1f".format(value)}", style = MaterialTheme.typography.labelMedium)
        Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range)
    }
}

private val PASTELS = listOf(
    ColorArgb(0xFFFFF59D.toInt()),
    ColorArgb(0xFFC8E6C9.toInt()),
    ColorArgb(0xFFBBDEFB.toInt()),
    ColorArgb(0xFFF8BBD0.toInt()),
    ColorArgb(0xFFE1BEE7.toInt()),
    ColorArgb(0xFFFFE0B2.toInt()),
    ColorArgb(0xFFECEFF1.toInt()),
)

private const val MIN_WIDTH = 0.5f
private const val MAX_WIDTH = 24f
private const val MIN_FONT = 8f
private const val MAX_FONT = 72f
private const val MIN_OPACITY = 0.1f
