package io.github.leonardosponton.inkflow.feature.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.leonardosponton.inkflow.core.model.ColorArgb
import io.github.leonardosponton.inkflow.feature.editor.EditorIntent
import io.github.leonardosponton.inkflow.feature.editor.R
import io.github.leonardosponton.inkflow.feature.editor.interaction.EraserMode
import io.github.leonardosponton.inkflow.feature.editor.interaction.ToolKind
import io.github.leonardosponton.inkflow.feature.editor.interaction.ToolState

private data class ToolButton(val kind: ToolKind, val icon: ImageVector, val label: Int)

private val TOOLS = listOf(
    ToolButton(ToolKind.SELECT, Icons.Outlined.NearMe, R.string.tool_select),
    ToolButton(ToolKind.LASSO, Icons.Outlined.Gesture, R.string.tool_lasso),
    ToolButton(ToolKind.PEN, Icons.Outlined.Draw, R.string.tool_pen),
    ToolButton(ToolKind.HIGHLIGHTER, Icons.Outlined.BorderColor, R.string.tool_highlighter),
    ToolButton(ToolKind.ERASER, EraserIcon, R.string.tool_eraser),
    ToolButton(ToolKind.TEXT, Icons.Outlined.TextFields, R.string.tool_text),
    ToolButton(ToolKind.STICKY, Icons.AutoMirrored.Outlined.StickyNote2, R.string.tool_sticky),
    ToolButton(ToolKind.SHAPE, Icons.Outlined.Category, R.string.tool_shapes),
    ToolButton(ToolKind.CONNECTOR, Icons.Outlined.Timeline, R.string.tool_connector),
)

/** Floating vertical toolbar; can be docked on either side. */
@Composable
fun ToolRail(
    tools: ToolState,
    onIntent: (EditorIntent) -> Unit,
    onInsertImage: () -> Unit,
    onSwapSide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier, shape = RoundedCornerShape(20.dp), tonalElevation = 3.dp, shadowElevation = 6.dp) {
        Column(
            Modifier.padding(6.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            TOOLS.forEach { t ->
                val label = stringResource(t.label)
                NameTip(label) {
                    FilledIconToggleButton(
                        checked = tools.kind == t.kind,
                        onCheckedChange = {
                            if (t.kind == ToolKind.SHAPE) onIntent(EditorIntent.TogglePalette)
                            onIntent(EditorIntent.SetTool(t.kind))
                        },
                        modifier = Modifier.size(48.dp).semantics { contentDescription = label },
                    ) { Icon(t.icon, null) }
                }
            }
            val image = stringResource(R.string.tool_image)
            NameTip(image) {
                IconButton(onClick = onInsertImage, modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.Image, image) }
            }
            HorizontalDivider(Modifier.size(32.dp, 1.dp))
            ToolOptions(tools, onIntent)
            val swap = stringResource(R.string.move_toolbar)
            NameTip(swap) { IconButton(onClick = onSwapSide) { Icon(Icons.Outlined.SwapHoriz, swap) } }
        }
    }
}

/** Long-pressing (or hovering with the pen over) [content] shows [name] in a small tooltip above it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NameTip(name: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(name) } },
        state = rememberTooltipState(),
        content = content,
    )
}

@Composable
private fun ToolOptions(tools: ToolState, onIntent: (EditorIntent) -> Unit) {
    when (tools.kind) {
        ToolKind.PEN, ToolKind.HIGHLIGHTER -> BrushOptions(tools, onIntent)
        ToolKind.ERASER -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(EraserMode.PARTIAL, EraserMode.STROKE).forEach { m ->
                val partial = m == EraserMode.PARTIAL
                val hint = stringResource(if (partial) R.string.eraser_partial_hint else R.string.eraser_stroke_hint)
                val on = tools.eraserMode == m
                Text(
                    stringResource(if (partial) R.string.eraser_partial else R.string.eraser_stroke),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .background(if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, RoundedCornerShape(8.dp))
                        .clickable { onIntent(EditorIntent.SetEraserMode(m)) }
                        .semantics { contentDescription = hint }
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                )
            }
        }
        ToolKind.CONNECTOR -> Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> Unit
    }
}

@Composable
private fun BrushOptions(tools: ToolState, onIntent: (EditorIntent) -> Unit) {
    val brush = tools.activeBrush ?: return
    val sizes = if (tools.kind == ToolKind.HIGHLIGHTER) ToolState.HighlighterSizes else ToolState.PenSizes
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ColorArgb.Swatches.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { c ->
                    val selected = c.argb or ALPHA == brush.color.argb or ALPHA
                    Box(
                        Modifier.size(20.dp)
                            .background(Color(c.argb), CircleShape)
                            .border(
                                if (selected) 3.dp else 1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = if (selected) 1f else 0.3f),
                                CircleShape,
                            )
                            .clickable { onIntent(EditorIntent.SetBrush(brush.copy(color = c))) }
                            .semantics { contentDescription = "color" },
                    )
                }
            }
        }
        sizes.forEach { s ->
            val selected = s == brush.size
            Box(
                Modifier.size(32.dp).clickable { onIntent(EditorIntent.SetBrush(brush.copy(size = s))) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size((s.coerceIn(2f, 22f)).dp)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            CircleShape,
                        ),
                )
            }
        }
    }
}

private const val ALPHA = 0xFF000000.toInt()
