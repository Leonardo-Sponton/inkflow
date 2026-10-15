package io.github.leonardosponton.inkflow.feature.editor.interaction

import io.github.leonardosponton.inkflow.core.model.Camera
import io.github.leonardosponton.inkflow.core.model.ElementId
import io.github.leonardosponton.inkflow.core.model.LayerId
import io.github.leonardosponton.inkflow.core.model.Size
import io.github.leonardosponton.inkflow.core.model.UserSettings
import io.github.leonardosponton.inkflow.core.model.Vec2
import io.github.leonardosponton.inkflow.domain.canvas.command.Command
import io.github.leonardosponton.inkflow.domain.canvas.notation.FollowUp
import io.github.leonardosponton.inkflow.domain.canvas.scene.SceneChange
import io.github.leonardosponton.inkflow.domain.canvas.session.EditorSession
import io.github.leonardosponton.inkflow.domain.diagram.ShapeTypeKey

/** What the interaction layer may read and do; implemented by the editor view model. */
interface EditorEnvironment {
    val session: EditorSession
    val camera: Camera
    val viewport: Size
    val tools: ToolState
    val settings: UserSettings
    val activeLayer: LayerId
    val hiddenLayers: Set<LayerId>
    val lockedLayers: Set<LayerId>
    var selection: Set<ElementId>

    fun newId(): ElementId

    fun execute(command: Command, coalesceKey: String? = null): SceneChange

    /** Ends the current undo coalescing run (gesture finished). */
    fun seal()

    fun setCamera(camera: Camera)

    fun undo()

    fun redo()

    fun fitToContent()

    fun editText(id: ElementId)

    /** A shape was placed with the shape tool: remember it as recent and return to selection. */
    fun shapePlaced(key: ShapeTypeKey)

    fun openContextMenu(screen: Vec2, target: ElementId?)

    fun offerFollowUp(followUp: FollowUp)

    /** Overlay or other transient visuals changed; redraw. */
    fun invalidate()
}
