package io.github.leonardosponton.inkflow.feature.editor

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.leonardosponton.inkflow.core.common.ApplicationScope
import io.github.leonardosponton.inkflow.core.model.Camera
import io.github.leonardosponton.inkflow.core.model.ElementId
import io.github.leonardosponton.inkflow.core.model.IdGenerator
import io.github.leonardosponton.inkflow.core.model.LayerId
import io.github.leonardosponton.inkflow.core.model.ProjectId
import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.core.model.Size
import io.github.leonardosponton.inkflow.core.model.TabId
import io.github.leonardosponton.inkflow.core.model.UserSettings
import io.github.leonardosponton.inkflow.core.model.Vec2
import io.github.leonardosponton.inkflow.domain.canvas.command.Command
import io.github.leonardosponton.inkflow.domain.canvas.element.InkBrush
import io.github.leonardosponton.inkflow.domain.canvas.element.InkStroke
import io.github.leonardosponton.inkflow.domain.canvas.element.TextElement
import io.github.leonardosponton.inkflow.domain.canvas.input.GestureEvent
import io.github.leonardosponton.inkflow.domain.canvas.notation.FollowUp
import io.github.leonardosponton.inkflow.domain.canvas.notation.NotationCatalog
import io.github.leonardosponton.inkflow.domain.canvas.notation.ValidationIssue
import io.github.leonardosponton.inkflow.domain.canvas.repository.SceneRepository
import io.github.leonardosponton.inkflow.domain.canvas.scene.SceneChange
import io.github.leonardosponton.inkflow.domain.canvas.session.EditorSession
import io.github.leonardosponton.inkflow.domain.diagram.ShapeTypeKey
import io.github.leonardosponton.inkflow.domain.diagram.TextMetrics
import io.github.leonardosponton.inkflow.domain.project.AssetStore
import io.github.leonardosponton.inkflow.domain.project.ProjectRepository
import io.github.leonardosponton.inkflow.domain.project.SettingsRepository
import io.github.leonardosponton.inkflow.domain.project.TabRepository
import io.github.leonardosponton.inkflow.feature.canvasrender.CanvasHost
import io.github.leonardosponton.inkflow.feature.canvasrender.RenderFrame
import io.github.leonardosponton.inkflow.feature.editor.interaction.EditorEnvironment
import io.github.leonardosponton.inkflow.feature.editor.interaction.InteractionController
import io.github.leonardosponton.inkflow.feature.editor.interaction.ToolKind
import io.github.leonardosponton.inkflow.feature.editor.interaction.ToolState
import io.github.leonardosponton.inkflow.feature.palette.PaletteDragListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder of the editor screen. Owns the [EditorSession] of the active tab, publishes the
 * canvas [frame] and the [state] snapshot, and routes [EditorIntent]s to small action classes.
 */
@HiltViewModel(assistedFactory = EditorViewModel.Factory::class)
class EditorViewModel
    @AssistedInject
    constructor(
        @Assisted projectIdValue: String,
        internal val projects: ProjectRepository,
        internal val tabRepository: TabRepository,
        private val scenes: SceneRepository,
        private val settingsRepository: SettingsRepository,
        internal val catalog: NotationCatalog,
        private val ids: IdGenerator,
        internal val assets: AssetStore,
        internal val clipboard: ElementClipboard,
        internal val renderers: EditorRenderers,
        @param:ApplicationScope private val appScope: CoroutineScope,
    ) : ViewModel(),
        EditorEnvironment,
        CanvasHost {
        @AssistedFactory
        interface Factory {
            fun create(projectId: String): EditorViewModel
        }

        val projectId = ProjectId(projectIdValue)

        private val _state = MutableStateFlow(EditorUiState())
        val state: StateFlow<EditorUiState> = _state.asStateFlow()

        private val _frame = MutableStateFlow<RenderFrame?>(null)

        /** Canvas frame; collected directly by the canvas view (no recomposition). */
        val frame: StateFlow<RenderFrame?> = _frame.asStateFlow()

        private val _selectionScreen = MutableStateFlow<Rect?>(null)

        /** Screen-space bounds of the selection, for the floating context toolbar. */
        val selectionScreen: StateFlow<Rect?> = _selectionScreen.asStateFlow()

        private val sessions = SessionCache(catalog, ids, scenes, renderers.metrics)
        private val persister = ScenePersister(scenes, viewModelScope, { tab -> sessions.peek(tab)?.undo?.history })
        internal val controller = InteractionController(this)
        private val tabsActions = TabActions(this)
        private val editActions = SelectionActions(this)
        private val background = BackgroundTasks(this)

        override var session: EditorSession = sessions.empty()
            private set
        override var camera: Camera = Camera()
            private set
        override var viewport: Size = Size(DEFAULT_W, DEFAULT_H)
            private set
        override val tools: ToolState get() = _state.value.tools
        override val settings: UserSettings get() = _state.value.settings
        override val activeLayer: LayerId get() = LayerId.Default
        override val hiddenLayers: Set<LayerId> get() = activeTab()?.layers?.filterNot { it.visible }?.map { it.id }?.toSet().orEmpty()
        override val lockedLayers: Set<LayerId> get() = activeTab()?.layers?.filter { it.locked }?.map { it.id }?.toSet().orEmpty()

        override var selection: Set<ElementId> = emptySet()
            set(value) {
                val next = value.filter { it in session.scene }.toSet()
                if (next != field) session.undo.seal()
                field = next
                refreshUi()
            }

        /** A shape being dragged from the palette (root coordinates), for the drag ghost. */
        data class PaletteDrag(val key: ShapeTypeKey, val root: Offset)

        private val _paletteDrag = MutableStateFlow<PaletteDrag?>(null)
        val paletteDragState: StateFlow<PaletteDrag?> = _paletteDrag.asStateFlow()

        /** Top-left of the canvas in root coordinates, so palette drops can be mapped to the canvas. */
        var canvasOriginInRoot: Vec2 = Vec2.Zero

        val paletteDrag = object : PaletteDragListener {
            override fun onDrag(key: ShapeTypeKey, root: Offset) {
                _paletteDrag.value = PaletteDrag(key, root)
            }

            override fun onDrop(key: ShapeTypeKey, root: Offset) {
                _paletteDrag.value = null
                val local = Vec2(root.x - canvasOriginInRoot.x, root.y - canvasOriginInRoot.y)
                if (local.x >= 0 && local.y >= 0 && local.x <= viewport.width && local.y <= viewport.height) {
                    onIntent(EditorIntent.PlaceShape(key, local))
                }
            }

            override fun onCancel() {
                _paletteDrag.value = null
            }
        }

        private var loadJob: Job? = null
        private var cameraSaveJob: Job? = null

        init {
            viewModelScope.launch {
                settingsRepository.settings.collect { s ->
                    _state.update { it.copy(settings = s) }.also { publishFrame() }
                }
            }
            viewModelScope.launch {
                projects.observeProject(projectId).collect { project ->
                    if (project == null) return@collect
                    _state.update { it.copy(projectName = project.name, tabs = project.tabs) }
                    val active = _state.value.activeTabId
                    if (active == null || project.tabs.none { it.id == active }) project.tabs.firstOrNull()?.let { openTab(it.id) }
                    publishFrame()
                }
            }
        }

        fun activeTab() = _state.value.activeTab

        internal fun updateState(f: (EditorUiState) -> EditorUiState) = _state.update(f)

        // ---- intents --------------------------------------------------------------------------------

        fun onIntent(intent: EditorIntent) {
            if (tabsActions.handle(intent)) return
            if (editActions.handle(intent)) return
            handleViewIntent(intent)
        }

        private fun handleViewIntent(intent: EditorIntent) {
            when (intent) {
                is EditorIntent.SetTool -> setTools(tools.copy(kind = intent.kind))
                is EditorIntent.SetBrush -> setTools(
                    if (intent.brush.kind ==
                        tools.highlighter.kind
                    ) {
                        tools.copy(highlighter = intent.brush)
                    } else {
                        tools.copy(pen = intent.brush)
                    },
                )
                is EditorIntent.SetEraserMode -> setTools(tools.copy(eraserMode = intent.mode, kind = ToolKind.ERASER))
                is EditorIntent.SetShapeTool -> setTools(tools.copy(kind = ToolKind.SHAPE, shape = intent.key))
                is EditorIntent.UpdateTools -> setTools(intent.tools)
                EditorIntent.TogglePalette -> _state.update { it.copy(showPalette = !it.showPalette) }
                EditorIntent.ToggleProperties -> _state.update { it.copy(showProperties = !it.showProperties) }
                EditorIntent.ToggleIssues -> _state.update { it.copy(showIssues = !it.showIssues) }.also { publishFrame() }
                EditorIntent.Undo -> undo()
                EditorIntent.Redo -> redo()
                is EditorIntent.Zoom -> setCamera(camera.zoomAround(Vec2(viewport.width / 2, viewport.height / 2), intent.factor, viewport))
                EditorIntent.ZoomReset -> setCamera(camera.copy(zoom = 1.0, rotation = 0.0))
                EditorIntent.FitContent -> fitToContent()
                EditorIntent.ToggleMinimap -> viewModelScope.launch { settingsRepository.update { it.copy(showMinimap = !it.showMinimap) } }
                is EditorIntent.JumpTo -> setCamera(camera.copy(center = intent.world))
                EditorIntent.DismissContextMenu -> _state.update { it.copy(contextMenu = null) }
                EditorIntent.AcceptFollowUp -> acceptFollowUp()
                EditorIntent.DismissFollowUp -> _state.update { it.copy(followUp = null) }
                EditorIntent.DismissMessage -> _state.update { it.copy(message = null) }
                else -> Unit
            }
        }

        private fun setTools(t: ToolState) {
            _state.update { it.copy(tools = t) }
            if (t.kind !in setOf(ToolKind.SELECT, ToolKind.LASSO)) selection = emptySet()
            publishFrame()
        }

        private fun acceptFollowUp() {
            val f = _state.value.followUp ?: return
            _state.update { it.copy(followUp = null) }
            execute(f.command)
            seal()
        }

        // ---- tabs -----------------------------------------------------------------------------------

        internal fun openTab(id: TabId) {
            val previous = _state.value.activeTabId
            if (previous == id && !session.isEmptyPlaceholder()) return
            previous?.let { saveCameraNow(it) }
            _state.update { it.copy(activeTabId = id, loading = true, editing = null, contextMenu = null) }
            loadJob?.cancel()
            loadJob = viewModelScope.launch {
                persister.flush()
                val s = sessions.get(id)
                session = s
                selection = emptySet()
                camera = _state.value.tabs.firstOrNull { it.id == id }?.camera ?: Camera()
                _state.update { it.copy(loading = false) }
                onSceneChanged(null)
            }
        }

        private fun EditorSession.isEmptyPlaceholder() = this === sessions.empty()

        // ---- EditorEnvironment ----------------------------------------------------------------------

        override fun newId(): ElementId = ElementId(ids.next())

        override fun newElementId(): ElementId = newId()

        override fun execute(command: Command, coalesceKey: String?): SceneChange {
            val tab = _state.value.activeTabId ?: return SceneChange.Empty
            val change = session.execute(command, coalesceKey)
            if (!change.isEmpty) {
                persister.record(tab, change)
                onSceneChanged(change)
            }
            return change
        }

        override fun seal() = session.undo.seal()

        override fun undo() {
            val tab = _state.value.activeTabId ?: return
            session.undo()?.let {
                persister.record(tab, it)
                onSceneChanged(it)
            }
        }

        override fun redo() {
            val tab = _state.value.activeTabId ?: return
            session.redo()?.let {
                persister.record(tab, it)
                onSceneChanged(it)
            }
        }

        override fun setCamera(camera: Camera) {
            this.camera = camera
            _state.update { it.copy(zoom = camera.zoom) }
            publishFrame()
            scheduleCameraSave()
        }

        override fun fitToContent() {
            val bounds = session.index.contentBounds() ?: Rect(-FIT_EMPTY, -FIT_EMPTY, FIT_EMPTY, FIT_EMPTY)
            setCamera(Camera.fit(bounds, viewport))
        }

        override fun editText(id: ElementId) = editActions.beginTextEdit(id)

        override fun shapePlaced(key: ShapeTypeKey) = setTools(tools.withRecent(key).copy(kind = ToolKind.SELECT))

        override fun openContextMenu(screen: Vec2, target: ElementId?) = _state.update {
            it.copy(contextMenu = ContextMenuRequest(screen, target))
        }

        override fun offerFollowUp(followUp: FollowUp) = _state.update { it.copy(followUp = followUp) }

        override fun invalidate() = publishFrame()

        // ---- CanvasHost -----------------------------------------------------------------------------

        override fun fingerActsAsPen(screen: Vec2) = controller.fingerActsAsPen(screen)

        override fun inkBrushFor(start: GestureEvent.StrokeStart): InkBrush? = controller.inkBrushFor(start)

        override fun onGesture(event: GestureEvent) = controller.onGesture(event)

        override fun onInkHold(worldPoints: List<Vec2>): Boolean = controller.onInkHold(worldPoints)

        override fun onInkFinished(stroke: InkStroke) = controller.onInkFinished(stroke)

        override fun onViewportChanged(size: Size) {
            val first = viewport.width == DEFAULT_W && viewport.height == DEFAULT_H
            viewport = size
            if (first && camera == Camera() && session.scene.size > 0) fitToContent() else publishFrame()
        }

        // ---- publishing -----------------------------------------------------------------------------

        internal fun onSceneChanged(change: SceneChange?) {
            if (change != null && selection.any { it !in session.scene }) selection = selection.filter { it in session.scene }.toSet()
            refreshUi()
            background.afterChange(change != null)
        }

        internal fun refreshUi() {
            val scene = session.scene
            _state.update {
                it.copy(
                    selected = selection.mapNotNull { id -> scene[id] },
                    canUndo = session.undo.canUndo,
                    canRedo = session.undo.canRedo,
                    elementCount = scene.size,
                    clipboardFull = clipboard.content != null,
                )
            }
            publishFrame()
        }

        internal fun publishFrame() {
            val tab = activeTab()
            val s = _state.value
            val issueRects = if (s.showIssues) s.issues.flatMap { it.elements }.mapNotNull { session.scene[it]?.bounds } else emptyList()
            _frame.value = RenderFrame(
                scene = session.scene,
                index = session.index,
                camera = camera,
                grid = tab?.grid ?: s.settings.defaultGrid,
                darkCanvas = s.settings.darkCanvas,
                overlay = controller.overlay().copy(warnings = issueRects),
                hiddenLayers = hiddenLayers,
                hidden = setOfNotNull(s.editing?.id?.takeIf { session.scene[it] is TextElement }),
            )
            _selectionScreen.value = controller.selectionBounds()?.let { b ->
                Rect.bounding(b.corners().map { camera.worldToScreen(it, viewport) })
            }
        }

        internal fun issuesChanged(issues: List<ValidationIssue>) {
            _state.update { it.copy(issues = issues) }
            if (_state.value.showIssues) publishFrame()
        }

        // ---- persistence ----------------------------------------------------------------------------

        private fun scheduleCameraSave() {
            val tab = _state.value.activeTabId ?: return
            cameraSaveJob?.cancel()
            cameraSaveJob = viewModelScope.launch {
                delay(CAMERA_SAVE_MS)
                tabRepository.saveCamera(tab, camera)
            }
        }

        private fun saveCameraNow(tab: TabId) {
            cameraSaveJob?.cancel()
            val cam = camera
            appScope.launch { tabRepository.saveCamera(tab, cam) }
        }

        internal suspend fun flushPersistence() = persister.flush()

        /** Exports read the database, so pending edits are written first. */
        suspend fun flushForExport() = persister.flush()

        internal val textMetrics: TextMetrics get() = renderers.metrics

        override fun onCleared() {
            val tab = _state.value.activeTabId
            val cam = camera
            appScope.launch(Dispatchers.Main.immediate) {
                persister.flush()
                tab?.let { tabRepository.saveCamera(it, cam) }
                background.finalThumbnail()
                tabRepository.purgeDeletedTabs(projectId)
            }
            super.onCleared()
        }

        private companion object {
            const val DEFAULT_W = 1600.0
            const val DEFAULT_H = 1000.0
            const val FIT_EMPTY = 400.0
            const val CAMERA_SAVE_MS = 700L
        }
    }
