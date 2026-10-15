package io.github.leonardosponton.inkflow.feature.editor.interaction

import io.github.leonardosponton.inkflow.core.model.ElementId
import io.github.leonardosponton.inkflow.core.model.LayerId
import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.core.model.ShapeStyle
import io.github.leonardosponton.inkflow.core.model.Vec2
import io.github.leonardosponton.inkflow.domain.canvas.element.CanvasElement
import io.github.leonardosponton.inkflow.domain.canvas.element.Connector
import io.github.leonardosponton.inkflow.domain.canvas.element.Endpoint
import io.github.leonardosponton.inkflow.domain.canvas.element.InkBrush
import io.github.leonardosponton.inkflow.domain.canvas.element.Routing
import io.github.leonardosponton.inkflow.domain.canvas.element.ShapeNode
import io.github.leonardosponton.inkflow.domain.canvas.ink.RecognizedShape
import io.github.leonardosponton.inkflow.domain.diagram.BasicShapes
import io.github.leonardosponton.inkflow.domain.diagram.MarkerKey
import io.github.leonardosponton.inkflow.domain.diagram.PlainLabel
import io.github.leonardosponton.inkflow.domain.diagram.ShapeTypeKey

/** Maps recognizer output to clean elements drawn with the pen's color and width. */
object RecognizedShapes {
    fun toElement(shape: RecognizedShape, brush: InkBrush, id: ElementId, layer: LayerId): CanvasElement {
        val style = ShapeStyle(fill = null, stroke = brush.color, strokeWidth = brush.size.toDouble().coerceAtLeast(1.0))
        return when (shape) {
            is RecognizedShape.Line -> line(id, shape.from, shape.to, style, MarkerKey.None, layer)
            is RecognizedShape.Arrow -> line(id, shape.from, shape.to, style, MarkerKey.Arrow, layer)
            is RecognizedShape.Rectangle -> node(id, BasicShapes.Rectangle.key, shape.frame, style, layer)
            is RecognizedShape.Ellipse -> node(id, BasicShapes.Ellipse.key, shape.frame, style, layer)
            is RecognizedShape.Diamond -> node(id, BasicShapes.Diamond.key, shape.frame, style, layer)
            is RecognizedShape.Triangle -> node(id, BasicShapes.Triangle.key, shape.frame, style, layer)
        }
    }

    private fun node(id: ElementId, key: ShapeTypeKey, frame: Rect, style: ShapeStyle, layer: LayerId) =
        ShapeNode(id, key, frame, PlainLabel(), style, layerId = layer)

    private fun line(
        id: ElementId,
        a: Vec2,
        b: Vec2,
        style: ShapeStyle,
        end: MarkerKey,
        layer: LayerId,
    ) = Connector(
        id,
        Endpoint.Free(a),
        Endpoint.Free(b),
        routing = Routing.STRAIGHT,
        endMarker = end,
        style = style,
        route = listOf(a, b),
        layerId = layer,
    )
}
