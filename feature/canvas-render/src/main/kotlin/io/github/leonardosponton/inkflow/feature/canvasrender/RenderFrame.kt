package io.github.leonardosponton.inkflow.feature.canvasrender

import io.github.leonardosponton.inkflow.core.model.Camera
import io.github.leonardosponton.inkflow.core.model.ElementId
import io.github.leonardosponton.inkflow.core.model.GridStyle
import io.github.leonardosponton.inkflow.core.model.LayerId
import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.core.model.Vec2
import io.github.leonardosponton.inkflow.domain.canvas.element.CanvasElement
import io.github.leonardosponton.inkflow.domain.canvas.scene.Scene
import io.github.leonardosponton.inkflow.domain.canvas.scene.SceneIndex
import io.github.leonardosponton.inkflow.domain.canvas.snap.Guide

/** Resize handle positions around a selection. */
enum class Handle { NW, N, NE, E, SE, S, SW, W }

/** Transient visuals drawn above the scene (world coordinates). */
data class Overlay(
    val selection: Rect? = null,
    val selectedBounds: List<Rect> = emptyList(),
    val handles: Boolean = false,
    val marquee: Rect? = null,
    val lasso: List<Vec2> = emptyList(),
    val guides: List<Guide> = emptyList(),
    val ports: List<Vec2> = emptyList(),
    val activePort: Vec2? = null,
    val connectorPreview: List<Vec2> = emptyList(),
    val eraser: Pair<Vec2, Double>? = null,
    val brushCursor: Pair<Vec2, Double>? = null,
    val dropTarget: Rect? = null,
    val warnings: List<Rect> = emptyList(),
    val waypoints: List<Vec2> = emptyList(),
    /** Quick-connect arrows: center and unit direction. */
    val quickArrows: List<Pair<Vec2, Vec2>> = emptyList(),
) {
    companion object {
        val Empty = Overlay()
    }
}

/** Everything the canvas view needs to draw one frame. */
data class RenderFrame(
    val scene: Scene,
    val index: SceneIndex,
    val camera: Camera,
    val grid: GridStyle,
    val darkCanvas: Boolean,
    val overlay: Overlay = Overlay.Empty,
    val hiddenLayers: Set<LayerId> = emptySet(),
    /** Elements not drawn (e.g. a text box while its text is edited inline). */
    val hidden: Set<ElementId> = emptySet(),
    /** Uncommitted elements drawn on top (shape placement preview, recognized ink). */
    val preview: List<CanvasElement> = emptyList(),
    /** A pinch is in progress: the renderer may scale its cached layer instead of redrawing. */
    val zooming: Boolean = false,
) {
    val palette: CanvasPalette get() = if (darkCanvas) CanvasPalette.Dark else CanvasPalette.Light
}
