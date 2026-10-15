package io.github.leonardosponton.inkflow.feature.editor

import io.github.leonardosponton.inkflow.domain.canvas.element.CanvasElement
import io.github.leonardosponton.inkflow.domain.canvas.element.Connector
import io.github.leonardosponton.inkflow.domain.canvas.element.Endpoint
import io.github.leonardosponton.inkflow.domain.canvas.element.ImageElement
import io.github.leonardosponton.inkflow.domain.canvas.element.InkStroke
import io.github.leonardosponton.inkflow.domain.canvas.element.LabelPosition
import io.github.leonardosponton.inkflow.domain.canvas.element.ShapeNode
import io.github.leonardosponton.inkflow.domain.canvas.element.TextElement
import io.github.leonardosponton.inkflow.domain.canvas.element.TextKind
import io.github.leonardosponton.inkflow.domain.canvas.notation.NotationCatalog
import io.github.leonardosponton.inkflow.domain.canvas.scene.Scene

/** Short spoken descriptions of canvas elements (TalkBack). */
object ElementDescriber {
    fun describe(el: CanvasElement, scene: Scene, catalog: NotationCatalog): String = when (el) {
        is ShapeNode -> listOf(catalog.shapes.resolve(el.shape).displayName, el.content.summary).filter {
            it.isNotBlank()
        }.joinToString(": ")
        is Connector -> {
            val from = name(el.source, scene, catalog)
            val to = name(el.target, scene, catalog)
            val label = el.label(LabelPosition.MIDDLE).takeIf { it.isNotBlank() }?.let { ", “$it”" }.orEmpty()
            "Connector from $from to $to$label"
        }
        is InkStroke -> "Handwriting stroke"
        is TextElement -> (if (el.kind == TextKind.STICKY) "Sticky note: " else "Text: ") + el.text.ifBlank { "empty" }
        is ImageElement -> "Image"
    }

    private fun name(e: Endpoint, scene: Scene, catalog: NotationCatalog): String = when (e) {
        is Endpoint.Free -> "a point"
        is Endpoint.Attached -> (scene[e.node] as? ShapeNode)?.let { n ->
            n.content.summary.ifBlank { catalog.shapes.resolve(n.shape).displayName }
        } ?: "a removed element"
    }
}
