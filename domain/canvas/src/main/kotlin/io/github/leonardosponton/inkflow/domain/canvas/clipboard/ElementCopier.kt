package io.github.leonardosponton.inkflow.domain.canvas.clipboard

import io.github.leonardosponton.inkflow.core.geometry.Affine
import io.github.leonardosponton.inkflow.core.model.ElementId
import io.github.leonardosponton.inkflow.core.model.Vec2
import io.github.leonardosponton.inkflow.domain.canvas.element.CanvasElement
import io.github.leonardosponton.inkflow.domain.canvas.element.Connector
import io.github.leonardosponton.inkflow.domain.canvas.element.Endpoint
import io.github.leonardosponton.inkflow.domain.canvas.element.ImageElement
import io.github.leonardosponton.inkflow.domain.canvas.element.InkStroke
import io.github.leonardosponton.inkflow.domain.canvas.element.ShapeNode
import io.github.leonardosponton.inkflow.domain.canvas.element.TextElement

/**
 * Copies elements with fresh ids, remapping references between them (connector ends, container
 * parents, groups). References to elements outside the copied set are dropped: connector ends
 * become free, parents are cleared. Used by copy/paste, duplicate and tab duplication.
 */
object ElementCopier {
    fun copy(elements: List<CanvasElement>, offset: Vec2 = Vec2.Zero, newId: () -> ElementId): List<CanvasElement> {
        val idMap = elements.associate { it.id to newId() }
        val groupMap = elements.mapNotNull { it.groupId }.distinct().associateWith { newId() }
        val t = Affine.translate(offset.x, offset.y)
        return elements.map { el ->
            val remapped = when (el) {
                is Connector -> el.copy(
                    id = idMap.getValue(el.id),
                    source = remap(el.source, idMap, el.route.firstOrNull()),
                    target = remap(el.target, idMap, el.route.lastOrNull()),
                )
                is ShapeNode -> el.copy(id = idMap.getValue(el.id), parent = el.parent?.let(idMap::get))
                else -> reId(el, idMap.getValue(el.id))
            }
            remapped.withMeta(groupId = el.groupId?.let(groupMap::get)).transformed(t)
        }
    }

    private fun remap(end: Endpoint, idMap: Map<ElementId, ElementId>, fallback: Vec2?): Endpoint = when (end) {
        is Endpoint.Free -> end
        is Endpoint.Attached -> idMap[end.node]?.let { end.copy(node = it) } ?: Endpoint.Free(fallback ?: Vec2.Zero)
    }

    private fun reId(el: CanvasElement, id: ElementId): CanvasElement = when (el) {
        is InkStroke -> el.copy(id = id)
        is TextElement -> el.copy(id = id)
        is ImageElement -> el.copy(id = id)
        is ShapeNode -> el.copy(id = id)
        is Connector -> el.copy(id = id)
    }
}
