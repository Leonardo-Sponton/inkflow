package io.github.leonardosponton.inkflow.feature.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.domain.canvas.element.ShapeNode
import io.github.leonardosponton.inkflow.domain.canvas.notation.NotationCatalog
import io.github.leonardosponton.inkflow.domain.canvas.scene.Scene
import io.github.leonardosponton.inkflow.domain.diagram.ShapeDefinition
import io.github.leonardosponton.inkflow.domain.diagram.ShapeTypeKey
import io.github.leonardosponton.inkflow.feature.canvasrender.ShapePreview
import io.github.leonardosponton.inkflow.feature.editor.EditorIntent
import io.github.leonardosponton.inkflow.feature.editor.R

/** Shapes [node] can turn into: same notation and content kind, never containers. */
internal fun shapeAlternatives(node: ShapeNode, catalog: NotationCatalog): List<ShapeDefinition> {
    val def = catalog.shapes.resolve(node.shape)
    return catalog.shapes.inNotation(def.notation).filter { !it.isContainer && it.createContent()::class == node.content::class }
}

/**
 * The [limit] alternatives to offer first: most used on this board, then recently placed, then
 * catalog order. The current shape is left out.
 */
internal fun mostUsedAlternatives(
    node: ShapeNode,
    catalog: NotationCatalog,
    scene: Scene,
    recent: List<ShapeTypeKey>,
    limit: Int = QUICK_SHAPES,
): List<ShapeDefinition> {
    val counts = scene.elements.values.filterIsInstance<ShapeNode>().groupingBy { it.shape }.eachCount()
    val options = shapeAlternatives(node, catalog).filter { it.key != node.shape }
    return options.withIndex().sortedWith(
        compareByDescending<IndexedValue<ShapeDefinition>> { counts[it.value.key] ?: 0 }
            .thenBy { recent.indexOf(it.value.key).let { i -> if (i < 0) Int.MAX_VALUE else i } }
            .thenBy { it.index },
    ).map { it.value }.take(limit)
}

/** Toolbar button showing the shape; opens a small draw.io-like picker of the most used shapes. */
@Composable
internal fun QuickShapeButton(
    node: ShapeNode,
    catalog: NotationCatalog,
    scene: Scene,
    recent: List<ShapeTypeKey>,
    onIntent: (EditorIntent) -> Unit,
    onMore: () -> Unit,
) {
    val options = remember(node.shape, scene, recent) { mostUsedAlternatives(node, catalog, scene, recent) }
    if (options.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.change_shape)
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.semantics { contentDescription = label }) {
            Preview(catalog.shapes.resolve(node.shape), ICON_DP)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(12.dp)) {
            Row(Modifier.padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                options.forEach { def ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(CELL_DP.dp)
                            .clickable {
                                open = false
                                onIntent(EditorIntent.ChangeShape(node.id, def.key))
                            }
                            .padding(4.dp),
                    ) {
                        Preview(def, PREVIEW_DP)
                        Text(
                            def.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            if (shapeAlternatives(node, catalog).size - 1 > options.size) {
                TextButton(onClick = {
                    open = false
                    onMore()
                }, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.more_shapes)) }
            }
        }
    }
}

@Composable
private fun Preview(def: ShapeDefinition, sizeDp: Int) {
    val stroke = MaterialTheme.colorScheme.onSurface.toArgb()
    val fill = MaterialTheme.colorScheme.surface.toArgb()
    Canvas(Modifier.size(sizeDp.dp)) {
        drawIntoCanvas { c ->
            ShapePreview.draw(c.nativeCanvas, def, Rect(2.0, 2.0, size.width - 2.0, size.height - 2.0), stroke, fill)
        }
    }
}

private const val QUICK_SHAPES = 5
private const val ICON_DP = 24
private const val PREVIEW_DP = 40
private const val CELL_DP = 72
