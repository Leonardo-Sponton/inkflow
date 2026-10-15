package io.github.leonardosponton.inkflow.domain.canvas.element

import io.github.leonardosponton.inkflow.core.geometry.Affine
import io.github.leonardosponton.inkflow.core.model.AssetId
import io.github.leonardosponton.inkflow.core.model.ColorArgb
import io.github.leonardosponton.inkflow.core.model.ElementId
import io.github.leonardosponton.inkflow.core.model.LayerId
import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.core.model.ShapeStyle
import io.github.leonardosponton.inkflow.core.model.Vec2
import io.github.leonardosponton.inkflow.domain.diagram.NodeContent
import io.github.leonardosponton.inkflow.domain.diagram.ShapeTypeKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Everything that can live on a tab. Elements are immutable values; changes go through commands.
 *
 * Logical groups are expressed with [groupId] (selecting one member selects the group); containers
 * (frames, swimlanes, packages) are [ShapeNode]s whose children point at them with [ShapeNode.parent].
 */
@Serializable
sealed interface CanvasElement {
    val id: ElementId
    val layerId: LayerId
    val zIndex: Long
    val locked: Boolean
    val groupId: ElementId?

    /** World-space axis-aligned bounds used for culling and hit testing. */
    val bounds: Rect

    fun withMeta(zIndex: Long = this.zIndex, locked: Boolean = this.locked, groupId: ElementId? = this.groupId): CanvasElement

    fun withLayer(layerId: LayerId): CanvasElement

    /** Applies a geometric transform (move, scale, rotate) where the element supports it. */
    fun transformed(t: Affine): CanvasElement
}

enum class BrushKind { PEN, HIGHLIGHTER, PENCIL }

@Serializable
data class InkBrush(val kind: BrushKind = BrushKind.PEN, val color: ColorArgb = ColorArgb.Black, val size: Float = 4f)

@Serializable
@SerialName("ink")
data class InkStroke(
    override val id: ElementId,
    val origin: Vec2,
    val brush: InkBrush,
    val points: StrokePoints,
    override val layerId: LayerId = LayerId.Default,
    override val zIndex: Long = 0,
    override val locked: Boolean = false,
    override val groupId: ElementId? = null,
) : CanvasElement {
    @Transient
    override val bounds: Rect = (points.localBounds() ?: Rect(0.0, 0.0, 0.0, 0.0))
        .translate(origin)
        .inflate(brush.size.toDouble())

    fun worldPoint(i: Int) = origin + points.localPoint(i)

    override fun withMeta(zIndex: Long, locked: Boolean, groupId: ElementId?) =
        copy(zIndex = zIndex, locked = locked, groupId = groupId)

    override fun withLayer(layerId: LayerId) = copy(layerId = layerId)

    override fun transformed(t: Affine): InkStroke {
        if (t.b == 0.0 && t.c == 0.0 && t.a == 1.0 && t.d == 1.0) return copy(origin = Vec2(origin.x + t.e, origin.y + t.f))
        val newOrigin = t.apply(origin)
        return copy(
            origin = newOrigin,
            points = points.mapPositions { t.apply(origin + it) - newOrigin },
            brush = brush.copy(size = (brush.size * t.meanScale).toFloat()),
        )
    }
}

enum class TextKind { TEXT, STICKY }

@Serializable
@SerialName("text")
data class TextElement(
    override val id: ElementId,
    val frame: Rect,
    val text: String,
    val kind: TextKind = TextKind.TEXT,
    val style: ShapeStyle = ShapeStyle(fill = null),
    override val layerId: LayerId = LayerId.Default,
    override val zIndex: Long = 0,
    override val locked: Boolean = false,
    override val groupId: ElementId? = null,
) : CanvasElement {
    override val bounds: Rect get() = frame

    override fun withMeta(zIndex: Long, locked: Boolean, groupId: ElementId?) =
        copy(zIndex = zIndex, locked = locked, groupId = groupId)

    override fun withLayer(layerId: LayerId) = copy(layerId = layerId)

    override fun transformed(t: Affine) = copy(frame = t.mapRect(frame))
}

@Serializable
@SerialName("image")
data class ImageElement(
    override val id: ElementId,
    val frame: Rect,
    val asset: AssetId,
    override val layerId: LayerId = LayerId.Default,
    override val zIndex: Long = 0,
    override val locked: Boolean = false,
    override val groupId: ElementId? = null,
) : CanvasElement {
    override val bounds: Rect get() = frame

    override fun withMeta(zIndex: Long, locked: Boolean, groupId: ElementId?) =
        copy(zIndex = zIndex, locked = locked, groupId = groupId)

    override fun withLayer(layerId: LayerId) = copy(layerId = layerId)

    override fun transformed(t: Affine) = copy(frame = t.mapRect(frame))
}

/** A diagram node: geometry from a [ShapeTypeKey] definition plus structured [content]. */
@Serializable
@SerialName("node")
data class ShapeNode(
    override val id: ElementId,
    val shape: ShapeTypeKey,
    val frame: Rect,
    val content: NodeContent,
    val style: ShapeStyle = ShapeStyle.Default,
    val parent: ElementId? = null,
    override val layerId: LayerId = LayerId.Default,
    override val zIndex: Long = 0,
    override val locked: Boolean = false,
    override val groupId: ElementId? = null,
) : CanvasElement {
    override val bounds: Rect get() = frame

    override fun withMeta(zIndex: Long, locked: Boolean, groupId: ElementId?) =
        copy(zIndex = zIndex, locked = locked, groupId = groupId)

    override fun withLayer(layerId: LayerId) = copy(layerId = layerId)

    override fun transformed(t: Affine) = copy(frame = t.mapRect(frame))
}
