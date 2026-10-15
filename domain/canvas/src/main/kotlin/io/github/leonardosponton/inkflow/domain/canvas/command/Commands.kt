package io.github.leonardosponton.inkflow.domain.canvas.command

import io.github.leonardosponton.inkflow.core.geometry.Affine
import io.github.leonardosponton.inkflow.core.model.ElementId
import io.github.leonardosponton.inkflow.core.model.LayerId
import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.core.model.StylePatch
import io.github.leonardosponton.inkflow.core.model.Vec2
import io.github.leonardosponton.inkflow.domain.canvas.element.CanvasElement
import io.github.leonardosponton.inkflow.domain.canvas.element.Connector
import io.github.leonardosponton.inkflow.domain.canvas.element.Endpoint
import io.github.leonardosponton.inkflow.domain.canvas.element.ImageElement
import io.github.leonardosponton.inkflow.domain.canvas.element.InkStroke
import io.github.leonardosponton.inkflow.domain.canvas.element.ShapeNode
import io.github.leonardosponton.inkflow.domain.canvas.element.TextElement
import io.github.leonardosponton.inkflow.domain.canvas.scene.Scene
import io.github.leonardosponton.inkflow.domain.canvas.scene.SceneChange
import io.github.leonardosponton.inkflow.domain.diagram.NodeContent

/** Adds elements on top of everything else (z-order is reassigned unless [keepZ]). */
class AddElements(private val elements: List<CanvasElement>, private val keepZ: Boolean = false) : Command {
    override fun execute(ctx: CommandContext): SceneChange {
        var z = ctx.scene.maxZ
        val placed = if (keepZ) elements else elements.map { it.withMeta(zIndex = ++z) }
        return ctx.change(placed)
    }
}

/**
 * Removes elements; containers take their children with them. Connectors glued to a removed node
 * stay, turned into free ends at their last position, so no drawing is lost.
 */
class RemoveElements(private val ids: Collection<ElementId>) : Command {
    override fun execute(ctx: CommandContext): SceneChange {
        val scene = ctx.scene
        val doomed = ids.flatMap { id ->
            listOf(id) + scene.descendantsOf(id).map { it.id }
        }.filter { it in scene }.toSet()
        val detached = doomed.flatMap { scene.connectorsOf(it) }
            .filter { it.id !in doomed }
            .distinctBy { it.id }
            .map { detach(it, doomed) }
        return ctx.change(detached, doomed)
    }

    private fun detach(c: Connector, removed: Set<ElementId>): Connector {
        fun fix(end: Endpoint, point: Vec2?): Endpoint =
            if (end is Endpoint.Attached && end.node in removed && point != null) Endpoint.Free(point) else end
        return c.copy(source = fix(c.source, c.route.firstOrNull()), target = fix(c.target, c.route.lastOrNull()))
    }
}

/**
 * Moves elements (and container children) by [delta]. With [reparent], moved nodes are dropped
 * into the top-most container under their center, or released from one they left.
 */
class MoveElements(
    private val ids: Collection<ElementId>,
    private val delta: Vec2,
    private val reparent: Boolean = true,
) : Command {
    override fun execute(ctx: CommandContext): SceneChange {
        val scene = ctx.scene
        val all = ids.flatMap { id -> listOf(id) + scene.descendantsOf(id).map { it.id } }.toSet()
        val t = Affine.translate(delta.x, delta.y)
        val moved = all.mapNotNull { scene[it] }.filterNot { it.locked }.map { el -> moveOne(el, t, all) }
        val result = if (reparent) Containers.reparent(moved, ids.toSet(), scene, ctx) else moved
        return ctx.change(result)
    }

    private fun moveOne(el: CanvasElement, t: Affine, moving: Set<ElementId>): CanvasElement {
        if (el !is Connector) return el.transformed(t)
        // A connector glued at both ends to moving nodes moves rigidly (keeps its waypoints);
        // otherwise only the reroute after the move updates it.
        return if (el.attachedNodes().all { it in moving }) el.transformed(t) else el
    }
}

/** Applies an arbitrary affine transform (lasso scale/rotate). */
class TransformElements(private val ids: Collection<ElementId>, private val transform: Affine) : Command {
    override fun execute(ctx: CommandContext): SceneChange {
        val scene = ctx.scene
        val all = ids.flatMap { id -> listOf(id) + scene.descendantsOf(id).map { it.id } }.toSet()
        return ctx.change(all.mapNotNull { scene[it] }.filterNot { it.locked }.map { it.transformed(transform) })
    }
}

/** Sets the frame of a node, text box or image (resize handles). */
class SetFrame(private val id: ElementId, private val frame: Rect) : Command {
    override fun execute(ctx: CommandContext): SceneChange {
        val updated = when (val el = ctx.scene[id]) {
            is ShapeNode -> el.copy(frame = frame)
            is TextElement -> el.copy(frame = frame)
            is ImageElement -> el.copy(frame = frame)
            else -> return SceneChange.Empty
        }
        return ctx.change(listOf(updated))
    }
}

class UpdateStyle(private val ids: Collection<ElementId>, private val patch: StylePatch) : Command {
    override fun execute(ctx: CommandContext): SceneChange = ctx.change(
        ids.mapNotNull { ctx.scene[it] }.mapNotNull { el ->
            when (el) {
                is ShapeNode -> el.copy(style = patch.applyTo(el.style))
                is TextElement -> el.copy(style = patch.applyTo(el.style))
                is Connector -> el.copy(style = patch.applyTo(el.style))
                is InkStroke -> el.copy(
                    brush = el.brush.copy(
                        color = patch.stroke ?: el.brush.color,
                        size = patch.strokeWidth?.toFloat() ?: el.brush.size,
                    ),
                )
                is ImageElement -> null
            }
        },
    )
}

/** Replaces a node's structured content; grows the frame if the content needs more room. */
class UpdateNodeContent(private val id: ElementId, private val content: NodeContent, private val frame: Rect? = null) :
    Command {
    override fun execute(ctx: CommandContext): SceneChange {
        val node = ctx.scene[id] as? ShapeNode ?: return SceneChange.Empty
        return ctx.change(listOf(node.copy(content = content, frame = frame ?: node.frame)))
    }
}

class UpdateText(private val id: ElementId, private val text: String) : Command {
    override fun execute(ctx: CommandContext): SceneChange {
        val el = ctx.scene[id] as? TextElement ?: return SceneChange.Empty
        return ctx.change(listOf(el.copy(text = text)))
    }
}

/** Generic connector edit (markers, routing, labels, semantics, endpoints, waypoints). */
class UpdateConnector(private val id: ElementId, private val edit: (Connector) -> Connector) : Command {
    override fun execute(ctx: CommandContext): SceneChange {
        val c = ctx.scene[id] as? Connector ?: return SceneChange.Empty
        return ctx.change(listOf(edit(c)))
    }
}

enum class ZOrderOp { TO_FRONT, TO_BACK, FORWARD, BACKWARD }

class ChangeZOrder(private val ids: Collection<ElementId>, private val op: ZOrderOp) : Command {
    override fun execute(ctx: CommandContext): SceneChange {
        val scene = ctx.scene
        val chosen = ids.mapNotNull { scene[it] }.sortedWith(Scene.Z_ORDER)
        if (chosen.isEmpty()) return SceneChange.Empty
        val chosenIds = chosen.map { it.id }.toSet()
        val others = scene.ordered.filterNot { it.id in chosenIds }
        val order = when (op) {
            ZOrderOp.TO_FRONT -> others + chosen
            ZOrderOp.TO_BACK -> chosen + others
            ZOrderOp.FORWARD -> shift(scene.ordered, chosenIds, forward = true)
            ZOrderOp.BACKWARD -> shift(scene.ordered, chosenIds, forward = false)
        }
        return ctx.change(order.mapIndexed { i, el -> el.withMeta(zIndex = i.toLong() + 1) })
    }

    private fun shift(order: List<CanvasElement>, chosen: Set<ElementId>, forward: Boolean): List<CanvasElement> {
        val list = order.toMutableList()
        val indices = list.indices.filter { list[it].id in chosen }.let { if (forward) it.reversed() else it }
        indices.forEach { i ->
            val j = if (forward) i + 1 else i - 1
            if (j in list.indices && list[j].id !in chosen) list[i] = list[j].also { list[j] = list[i] }
        }
        return list
    }
}

class SetLocked(private val ids: Collection<ElementId>, private val locked: Boolean) : Command {
    override fun execute(ctx: CommandContext) = ctx.change(ids.mapNotNull { ctx.scene[it]?.withMeta(locked = locked) })
}

/** Groups elements under [groupId], or ungroups them when it is `null`. */
class SetGroup(private val ids: Collection<ElementId>, private val groupId: ElementId?) : Command {
    override fun execute(
        ctx: CommandContext,
    ) = ctx.change(ids.mapNotNull { ctx.scene[it]?.withMeta(groupId = groupId) })
}

class MoveToLayer(private val ids: Collection<ElementId>, private val layer: LayerId) : Command {
    override fun execute(ctx: CommandContext) = ctx.change(ids.mapNotNull { ctx.scene[it]?.withLayer(layer) })
}

/** Raw replace/remove, for edits computed elsewhere (partial erase, paste, ink-to-shape). */
class ApplyEdits(
    private val updated: List<CanvasElement>,
    private val removed: Collection<ElementId> = emptyList(),
) : Command {
    override fun execute(ctx: CommandContext) = ctx.change(updated, removed)
}

/** Several commands as one undo step. */
class CompositeCommand(private val parts: List<Command>) : Command {
    override fun execute(ctx: CommandContext): SceneChange {
        var scene = ctx.scene
        var total = SceneChange.Empty
        parts.forEach { part ->
            val change = part.execute(CommandContext(scene, ctx.shapes, ctx.router, ctx.ids))
            scene = scene.apply(change)
            total = total.then(change)
        }
        return total
    }
}
