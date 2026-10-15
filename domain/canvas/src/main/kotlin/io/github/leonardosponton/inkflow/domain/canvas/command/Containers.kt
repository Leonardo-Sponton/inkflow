package io.github.leonardosponton.inkflow.domain.canvas.command

import io.github.leonardosponton.inkflow.core.model.ElementId
import io.github.leonardosponton.inkflow.domain.canvas.element.CanvasElement
import io.github.leonardosponton.inkflow.domain.canvas.element.ShapeNode
import io.github.leonardosponton.inkflow.domain.canvas.scene.Scene
import io.github.leonardosponton.inkflow.domain.canvas.scene.SceneChange

/** Container membership rules: a node belongs to the top-most container under its center. */
internal object Containers {
    fun reparent(
        moved: List<CanvasElement>,
        roots: Set<ElementId>,
        scene: Scene,
        ctx: CommandContext,
    ): List<CanvasElement> {
        val after = scene.apply(SceneChange.of(scene, moved))
        return moved.map { el ->
            if (el !is ShapeNode || el.id !in roots) return@map el
            val newParent = containerAt(el, after, ctx)
            if (newParent == el.parent) el else el.copy(parent = newParent)
        }
    }

    /** Top-most container (other than [node] and its descendants) containing [node]'s center. */
    fun containerAt(node: ShapeNode, scene: Scene, ctx: CommandContext): ElementId? {
        val excluded = scene.descendantsOf(node.id).map { it.id }.toSet() + node.id
        return scene.ordered.asReversed().asSequence()
            .filterIsInstance<ShapeNode>()
            .filter { it.id !in excluded && ctx.shapes.resolve(it.shape).isContainer }
            .firstOrNull { it.frame.contains(node.frame.center) }
            ?.id
    }
}
