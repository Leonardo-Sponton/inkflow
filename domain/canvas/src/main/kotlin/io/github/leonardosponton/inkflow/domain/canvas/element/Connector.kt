package io.github.leonardosponton.inkflow.domain.canvas.element

import io.github.leonardosponton.inkflow.core.geometry.Affine
import io.github.leonardosponton.inkflow.core.model.ElementId
import io.github.leonardosponton.inkflow.core.model.LayerId
import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.core.model.ShapeStyle
import io.github.leonardosponton.inkflow.core.model.Vec2
import io.github.leonardosponton.inkflow.domain.diagram.EdgeSemantics
import io.github.leonardosponton.inkflow.domain.diagram.MarkerKey
import io.github.leonardosponton.inkflow.domain.diagram.PlainEdge
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface Endpoint {
    /** Loose end at a world point. */
    @Serializable
    @SerialName("free")
    data class Free(val point: Vec2) : Endpoint

    /** Glued to a node; `port == null` means "floating": the nearest point of the node outline. */
    @Serializable
    @SerialName("attached")
    data class Attached(val node: ElementId, val port: String? = null) : Endpoint
}

enum class Routing { STRAIGHT, ORTHOGONAL, CURVED }

enum class LabelPosition(val fraction: Double) { START(0.12), MIDDLE(0.5), END(0.88) }

@Serializable
data class EdgeLabel(val position: LabelPosition, val text: String)

/**
 * Edge between two endpoints. [route] is the resolved geometry (polyline, or for curved routing
 * the cubic control polygon `p0 c1 c2 p3`), recomputed by commands whenever an attached node moves,
 * so rendering and hit testing never need the router.
 */
@Serializable
@SerialName("connector")
data class Connector(
    override val id: ElementId,
    val source: Endpoint,
    val target: Endpoint,
    val routing: Routing = Routing.ORTHOGONAL,
    val waypoints: List<Vec2> = emptyList(),
    val startMarker: MarkerKey = MarkerKey.None,
    val endMarker: MarkerKey = MarkerKey.Arrow,
    val labels: List<EdgeLabel> = emptyList(),
    val style: ShapeStyle = ShapeStyle.Line,
    val semantics: EdgeSemantics = PlainEdge,
    val route: List<Vec2> = emptyList(),
    override val layerId: LayerId = LayerId.Default,
    override val zIndex: Long = 0,
    override val locked: Boolean = false,
    override val groupId: ElementId? = null,
) : CanvasElement {
    override val bounds: Rect
        get() = (Rect.bounding(route + waypoints) ?: Rect(0.0, 0.0, 0.0, 0.0)).inflate(LABEL_SLACK)

    fun attachedNodes(): List<ElementId> = listOfNotNull(
        (source as? Endpoint.Attached)?.node,
        (target as? Endpoint.Attached)?.node,
    )

    fun label(position: LabelPosition): String = labels.firstOrNull { it.position == position }?.text.orEmpty()

    fun withLabel(position: LabelPosition, text: String): Connector {
        val rest = labels.filterNot { it.position == position }
        return copy(labels = if (text.isBlank()) rest else rest + EdgeLabel(position, text))
    }

    override fun withMeta(zIndex: Long, locked: Boolean, groupId: ElementId?) =
        copy(zIndex = zIndex, locked = locked, groupId = groupId)

    override fun withLayer(layerId: LayerId) = copy(layerId = layerId)

    override fun transformed(t: Affine) = copy(
        source = (source as? Endpoint.Free)?.let { Endpoint.Free(t.apply(it.point)) } ?: source,
        target = (target as? Endpoint.Free)?.let { Endpoint.Free(t.apply(it.point)) } ?: target,
        waypoints = waypoints.map(t::apply),
        route = route.map(t::apply),
    )

    private companion object {
        const val LABEL_SLACK = 24.0
    }
}
