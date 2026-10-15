package io.github.leonardosponton.inkflow.domain.canvas.session

import io.github.leonardosponton.inkflow.core.model.IdGenerator
import io.github.leonardosponton.inkflow.core.model.LayerId
import io.github.leonardosponton.inkflow.domain.canvas.command.Command
import io.github.leonardosponton.inkflow.domain.canvas.command.CommandContext
import io.github.leonardosponton.inkflow.domain.canvas.connector.ConnectorRouter
import io.github.leonardosponton.inkflow.domain.canvas.hit.HitTester
import io.github.leonardosponton.inkflow.domain.canvas.notation.NotationCatalog
import io.github.leonardosponton.inkflow.domain.canvas.scene.Scene
import io.github.leonardosponton.inkflow.domain.canvas.scene.SceneChange
import io.github.leonardosponton.inkflow.domain.canvas.scene.SceneIndex
import io.github.leonardosponton.inkflow.domain.canvas.undo.UndoManager
import io.github.leonardosponton.inkflow.domain.diagram.ApproximateTextMetrics
import io.github.leonardosponton.inkflow.domain.diagram.TextMetrics

/**
 * The editing aggregate of one tab: current scene, its spatial index and undo history.
 * Not thread-safe — confine it to one thread (the editor uses the main thread).
 */
class EditorSession(
    val catalog: NotationCatalog,
    private val ids: IdGenerator,
    initial: Scene = Scene.Empty,
    /** Metrics used to size structured content (the renderer passes real font metrics). */
    val textMetrics: TextMetrics = ApproximateTextMetrics,
) {
    val router = ConnectorRouter(catalog.shapes)
    val index = SceneIndex().also { it.rebuild(initial) }
    val undo = UndoManager()

    var scene: Scene = initial
        private set

    fun reset(scene: Scene, history: List<SceneChange> = emptyList()) {
        this.scene = scene
        index.rebuild(scene)
        undo.restore(history)
    }

    /** Runs [command]; returns the applied change (empty when nothing changed). */
    fun execute(command: Command, coalesceKey: String? = null): SceneChange {
        val change = command.execute(CommandContext(scene, catalog.shapes, router, ids))
        if (change.isEmpty) return SceneChange.Empty
        applyInternal(change)
        undo.record(change, coalesceKey)
        return change
    }

    fun undo(): SceneChange? = undo.undo()?.also(::applyInternal)

    fun redo(): SceneChange? = undo.redo()?.also(::applyInternal)

    fun hitTester(hiddenLayers: Set<LayerId> = emptySet()) = HitTester(scene, index, catalog.shapes, hiddenLayers)

    private fun applyInternal(change: SceneChange) {
        scene = scene.apply(change)
        index.apply(change)
    }
}
