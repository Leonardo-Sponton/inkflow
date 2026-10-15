package io.github.leonardosponton.inkflow.domain.canvas.command

import io.github.leonardosponton.inkflow.core.model.ElementId
import io.github.leonardosponton.inkflow.core.model.IdGenerator
import io.github.leonardosponton.inkflow.domain.canvas.connector.ConnectorRouter
import io.github.leonardosponton.inkflow.domain.canvas.element.CanvasElement
import io.github.leonardosponton.inkflow.domain.canvas.element.Connector
import io.github.leonardosponton.inkflow.domain.canvas.scene.Scene
import io.github.leonardosponton.inkflow.domain.canvas.scene.SceneChange
import io.github.leonardosponton.inkflow.domain.diagram.ShapeRegistry

/** Everything a command may consult while computing its change. */
class CommandContext(
    val scene: Scene,
    val shapes: ShapeRegistry,
    val router: ConnectorRouter,
    val ids: IdGenerator,
) {
    /**
     * Builds the final change for [updated]/[removed] elements, rerouting every connector attached
     * to a touched node (and every touched connector) against the resulting scene.
     */
    fun change(updated: Collection<CanvasElement>, removed: Collection<ElementId> = emptyList()): SceneChange {
        val primary = SceneChange.of(scene, updated, removed)
        val interim = scene.apply(primary)
        val touchedIds = primary.touched
        val toReroute = LinkedHashMap<ElementId, Connector>()
        touchedIds.forEach { id ->
            (interim[id] as? Connector)?.let { toReroute[id] = it }
            (scene.connectorsOf(id) + interim.connectorsOf(id)).forEach { c ->
                interim[c.id]?.let {
                    toReroute[c.id] =
                        it as Connector
                }
            }
        }
        if (toReroute.isEmpty()) return primary
        val rerouted = toReroute.values.map { router.reroute(it, interim) }
        return primary.then(SceneChange.of(interim, rerouted))
    }
}

/** A user intention that produces an invertible [SceneChange]. */
fun interface Command {
    fun execute(ctx: CommandContext): SceneChange
}
