package io.github.leonardosponton.inkflow.domain.canvas

import io.github.leonardosponton.inkflow.core.model.ElementId
import io.github.leonardosponton.inkflow.core.model.IdGenerator
import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.core.model.Vec2
import io.github.leonardosponton.inkflow.domain.canvas.element.Connector
import io.github.leonardosponton.inkflow.domain.canvas.element.Endpoint
import io.github.leonardosponton.inkflow.domain.canvas.element.InkBrush
import io.github.leonardosponton.inkflow.domain.canvas.element.InkStroke
import io.github.leonardosponton.inkflow.domain.canvas.element.Routing
import io.github.leonardosponton.inkflow.domain.canvas.element.ShapeNode
import io.github.leonardosponton.inkflow.domain.canvas.element.StrokePoints
import io.github.leonardosponton.inkflow.domain.canvas.notation.NotationCatalog
import io.github.leonardosponton.inkflow.domain.canvas.session.EditorSession
import io.github.leonardosponton.inkflow.domain.diagram.BasicShapes
import io.github.leonardosponton.inkflow.domain.diagram.PlainLabel
import io.github.leonardosponton.inkflow.domain.diagram.ShapeTypeKey

class SeqIds : IdGenerator {
    private var n = 0

    override fun next() = "id${++n}"
}

fun id(v: String) = ElementId(v)

fun node(
    name: String,
    x: Double,
    y: Double,
    w: Double = 100.0,
    h: Double = 60.0,
    shape: ShapeTypeKey = BasicShapes.Rectangle.key,
    parent: ElementId? = null,
) = ShapeNode(id(name), shape, Rect(x, y, x + w, y + h), PlainLabel(name), parent = parent)

fun edge(name: String, from: String, to: String, routing: Routing = Routing.STRAIGHT, port: String? = null) =
    Connector(id(name), Endpoint.Attached(id(from), port), Endpoint.Attached(id(to), port), routing = routing)

fun stroke(name: String, vararg pts: Pair<Double, Double>, origin: Vec2 = Vec2.Zero): InkStroke {
    val b = StrokePoints.Builder()
    pts.forEachIndexed { i, (x, y) -> b.add(x.toFloat(), y.toFloat(), i * 10f, 0.5f) }
    return InkStroke(id(name), origin, InkBrush(size = 2f), b.build())
}

fun session() = EditorSession(NotationCatalog(emptyList()), SeqIds())
