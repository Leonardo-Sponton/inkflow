package io.github.leonardosponton.inkflow.feature.editor

import io.github.leonardosponton.inkflow.core.model.DiagramProfile
import io.github.leonardosponton.inkflow.core.model.ElementId
import io.github.leonardosponton.inkflow.core.model.GridStyle
import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.core.model.StylePatch
import io.github.leonardosponton.inkflow.core.model.TabColor
import io.github.leonardosponton.inkflow.core.model.TabId
import io.github.leonardosponton.inkflow.core.model.UserSettings
import io.github.leonardosponton.inkflow.core.model.Vec2
import io.github.leonardosponton.inkflow.domain.canvas.command.ZOrderOp
import io.github.leonardosponton.inkflow.domain.canvas.element.CanvasElement
import io.github.leonardosponton.inkflow.domain.canvas.element.Connector
import io.github.leonardosponton.inkflow.domain.canvas.element.InkBrush
import io.github.leonardosponton.inkflow.domain.canvas.notation.FollowUp
import io.github.leonardosponton.inkflow.domain.canvas.notation.ValidationIssue
import io.github.leonardosponton.inkflow.domain.diagram.NodeContent
import io.github.leonardosponton.inkflow.domain.diagram.ShapeTypeKey
import io.github.leonardosponton.inkflow.domain.project.Tab
import io.github.leonardosponton.inkflow.feature.editor.interaction.EraserMode
import io.github.leonardosponton.inkflow.feature.editor.interaction.ToolKind
import io.github.leonardosponton.inkflow.feature.editor.interaction.ToolState

/** Inline text editing request: the screen rectangle and current text of the edited element. */
data class TextEditRequest(val id: ElementId, val text: String, val screenRect: Rect, val fontSizePx: Double, val sticky: Boolean)

data class ContextMenuRequest(val screen: Vec2, val target: ElementId?)

/** Short message with an optional action (snackbar). */
data class EditorMessage(val text: String, val actionLabel: String? = null, val action: EditorIntent? = null)

/** Immutable snapshot of the editor UI (the canvas frame is published separately for speed). */
data class EditorUiState(
    val loading: Boolean = true,
    val projectName: String = "",
    val tabs: List<Tab> = emptyList(),
    val activeTabId: TabId? = null,
    val tools: ToolState = ToolState(),
    val selected: List<CanvasElement> = emptyList(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val zoom: Double = 1.0,
    val settings: UserSettings = UserSettings(),
    val issues: List<ValidationIssue> = emptyList(),
    val showIssues: Boolean = false,
    val editing: TextEditRequest? = null,
    val contextMenu: ContextMenuRequest? = null,
    val followUp: FollowUp? = null,
    val message: EditorMessage? = null,
    val showPalette: Boolean = false,
    val showProperties: Boolean = true,
    val clipboardFull: Boolean = false,
    val elementCount: Int = 0,
) {
    val activeTab: Tab? get() = tabs.firstOrNull { it.id == activeTabId }
    val singleSelected: CanvasElement? get() = selected.singleOrNull()
    val hasSelection: Boolean get() = selected.isNotEmpty()
    val selectedConnector: Connector? get() = singleSelected as? Connector
}

/** Everything the editor UI can ask for. */
sealed interface EditorIntent {
    // tabs & project
    data class SelectTab(val id: TabId) : EditorIntent

    data class AddTab(val profile: DiagramProfile) : EditorIntent

    data class RenameTab(val id: TabId, val title: String) : EditorIntent

    data class DuplicateTab(val id: TabId) : EditorIntent

    data class DeleteTab(val id: TabId) : EditorIntent

    data class RestoreTab(val id: TabId) : EditorIntent

    data class SetTabColor(val id: TabId, val color: TabColor) : EditorIntent

    data class SetTabProfile(val id: TabId, val profile: DiagramProfile) : EditorIntent

    data class SetTabGrid(val grid: GridStyle) : EditorIntent

    data class MoveTab(val from: Int, val to: Int) : EditorIntent

    data class RenameProject(val name: String) : EditorIntent

    // tools
    data class SetTool(val kind: ToolKind) : EditorIntent

    data class SetBrush(val brush: InkBrush) : EditorIntent

    data class SetEraserMode(val mode: EraserMode) : EditorIntent

    data class SetShapeTool(val key: ShapeTypeKey) : EditorIntent

    data class UpdateTools(val tools: ToolState) : EditorIntent

    data object TogglePalette : EditorIntent

    data object ToggleProperties : EditorIntent

    data object ToggleIssues : EditorIntent

    // view
    data object Undo : EditorIntent

    data object Redo : EditorIntent

    data class Zoom(val factor: Double) : EditorIntent

    data object ZoomReset : EditorIntent

    data object FitContent : EditorIntent

    data object ToggleMinimap : EditorIntent

    data object ToggleDarkCanvas : EditorIntent

    data class JumpTo(val world: Vec2) : EditorIntent

    // selection editing
    data object SelectAll : EditorIntent

    data object ClearSelection : EditorIntent

    data class Select(val ids: Set<ElementId>) : EditorIntent

    data object DeleteSelection : EditorIntent

    data object Duplicate : EditorIntent

    data object Copy : EditorIntent

    data object Cut : EditorIntent

    data object Paste : EditorIntent

    data class Style(val patch: StylePatch) : EditorIntent

    data class Order(val op: ZOrderOp) : EditorIntent

    data class Lock(val locked: Boolean) : EditorIntent

    data object Group : EditorIntent

    data object Ungroup : EditorIntent

    data class EditConnector(val id: ElementId, val edit: (Connector) -> Connector) : EditorIntent

    data class EditContent(val id: ElementId, val content: NodeContent) : EditorIntent

    data class ChangeShape(val id: ElementId, val shape: ShapeTypeKey) : EditorIntent

    data class PlaceShape(val key: ShapeTypeKey, val screen: Vec2) : EditorIntent

    data class InsertImage(val bytes: ByteArray, val mimeType: String) : EditorIntent

    data class ImportDdl(val sql: String) : EditorIntent

    // inline text and prompts
    data class BeginTextEdit(val id: ElementId) : EditorIntent

    data class CommitText(val id: ElementId, val text: String) : EditorIntent

    data object CancelTextEdit : EditorIntent

    data object DismissContextMenu : EditorIntent

    data object AcceptFollowUp : EditorIntent

    data object DismissFollowUp : EditorIntent

    data object DismissMessage : EditorIntent
}
