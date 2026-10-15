package io.github.leonardosponton.inkflow.feature.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.core.model.Vec2
import io.github.leonardosponton.inkflow.feature.canvasrender.RenderFrame
import io.github.leonardosponton.inkflow.feature.editor.EditorIntent
import io.github.leonardosponton.inkflow.feature.editor.R
import kotlin.math.roundToInt
import io.github.leonardosponton.inkflow.core.model.Size as WorldSize

@Composable
fun ZoomControls(zoom: Double, minimapOn: Boolean, onIntent: (EditorIntent) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), tonalElevation = 3.dp, shadowElevation = 4.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                onIntent(EditorIntent.Zoom(1 / ZOOM_STEP))
            }) { Icon(Icons.Outlined.Remove, stringResource(R.string.zoom_out)) }
            TextButton(onClick = { onIntent(EditorIntent.ZoomReset) }) { Text("${(zoom * PERCENT).roundToInt()}%") }
            IconButton(onClick = { onIntent(EditorIntent.Zoom(ZOOM_STEP)) }) { Icon(Icons.Outlined.Add, stringResource(R.string.zoom_in)) }
            IconButton(onClick = {
                onIntent(EditorIntent.FitContent)
            }) { Icon(Icons.Outlined.CenterFocusStrong, stringResource(R.string.fit_content)) }
            IconButton(onClick = { onIntent(EditorIntent.ToggleMinimap) }) {
                Icon(
                    Icons.Outlined.Map,
                    stringResource(R.string.minimap),
                    tint = if (minimapOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Overview of all content with the visible area; tap or drag to jump there. */
@Composable
fun Minimap(frame: RenderFrame, viewport: WorldSize, onIntent: (EditorIntent) -> Unit, modifier: Modifier = Modifier) {
    val visible = frame.camera.visibleWorldRect(viewport)
    val content = frame.index.contentBounds()
    val world = (content?.union(visible) ?: visible).inflate(MARGIN)
    val elementColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val viewColor = MaterialTheme.colorScheme.primary
    Surface(modifier.size(220.dp, 150.dp), shape = RoundedCornerShape(12.dp), tonalElevation = 3.dp, shadowElevation = 4.dp) {
        Canvas(
            Modifier.padding(6.dp)
                .pointerInput(world) {
                    detectTapGestures { onIntent(EditorIntent.JumpTo(toWorld(it, world, size.width.toFloat(), size.height.toFloat()))) }
                }
                .pointerInput(world) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        onIntent(EditorIntent.JumpTo(toWorld(change.position, world, size.width.toFloat(), size.height.toFloat())))
                    }
                },
        ) {
            val s = minOf(size.width / world.width, size.height / world.height).toFloat()

            fun map(r: Rect) = Offset(((r.left - world.left) * s).toFloat(), ((r.top - world.top) * s).toFloat()) to
                Size((r.width * s).toFloat().coerceAtLeast(1f), (r.height * s).toFloat().coerceAtLeast(1f))
            frame.index.visible(frame.scene, world).take(MAX_ITEMS).forEach { el ->
                val (o, sz) = map(el.bounds)
                drawRect(elementColor, o, sz)
            }
            val (o, sz) = map(visible)
            drawRect(viewColor, o, sz, style = Stroke(2f))
        }
    }
}

private fun toWorld(p: Offset, world: Rect, w: Float, h: Float): Vec2 {
    val s = minOf(w / world.width, h / world.height)
    return Vec2(world.left + p.x / s, world.top + p.y / s)
}

private const val ZOOM_STEP = 1.25
private const val PERCENT = 100
private const val MARGIN = 200.0
private const val MAX_ITEMS = 3000
