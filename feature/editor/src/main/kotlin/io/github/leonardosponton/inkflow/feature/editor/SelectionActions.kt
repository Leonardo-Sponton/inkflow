package io.github.leonardosponton.inkflow.feature.editor

import android.graphics.BitmapFactory
import androidx.lifecycle.viewModelScope
import io.github.leonardosponton.inkflow.core.model.ElementId
import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.core.model.Size
import io.github.leonardosponton.inkflow.core.model.Vec2
import io.github.leonardosponton.inkflow.domain.canvas.clipboard.ElementCopier
import io.github.leonardosponton.inkflow.domain.canvas.command.AddElements
import io.github.leonardosponton.inkflow.domain.canvas.command.ApplyEdits
import io.github.leonardosponton.inkflow.domain.canvas.command.ChangeZOrder
import io.github.leonardosponton.inkflow.domain.canvas.command.Command
import io.github.leonardosponton.inkflow.domain.canvas.command.CompositeCommand
import io.github.leonardosponton.inkflow.domain.canvas.command.MoveElements
import io.github.leonardosponton.inkflow.domain.canvas.command.RemoveElements
import io.github.leonardosponton.inkflow.domain.canvas.command.SetGroup
import io.github.leonardosponton.inkflow.domain.canvas.command.SetLocked
import io.github.leonardosponton.inkflow.domain.canvas.command.UpdateConnector
import io.github.leonardosponton.inkflow.domain.canvas.command.UpdateNodeContent
import io.github.leonardosponton.inkflow.domain.canvas.command.UpdateStyle
import io.github.leonardosponton.inkflow.domain.canvas.command.UpdateText
import io.github.leonardosponton.inkflow.domain.canvas.element.CanvasElement
import io.github.leonardosponton.inkflow.domain.canvas.element.Connector
import io.github.leonardosponton.inkflow.domain.canvas.element.Endpoint
import io.github.leonardosponton.inkflow.domain.canvas.element.ImageElement
import io.github.leonardosponton.inkflow.domain.canvas.element.LabelPosition
import io.github.leonardosponton.inkflow.domain.canvas.element.ShapeNode
import io.github.leonardosponton.inkflow.domain.canvas.element.TextElement
import io.github.leonardosponton.inkflow.domain.canvas.element.TextKind
import io.github.leonardosponton.inkflow.domain.canvas.scene.Scene
import io.github.leonardosponton.inkflow.domain.dbmodel.DbNotation
import io.github.leonardosponton.inkflow.domain.dbmodel.DbRelationship
import io.github.leonardosponton.inkflow.domain.dbmodel.DbShapes
import io.github.leonardosponton.inkflow.domain.dbmodel.DbTableContent
import io.github.leonardosponton.inkflow.domain.dbmodel.DdlImporter
import io.github.leonardosponton.inkflow.domain.dbmodel.TableLayouter
import io.github.leonardosponton.inkflow.domain.diagram.NodeContent
import io.github.leonardosponton.inkflow.domain.diagram.PlainLabel
import io.github.leonardosponton.inkflow.domain.diagram.ShapeTypeKey
import kotlinx.coroutines.launch

/** Edits applied to the current selection, clipboard, inline text and drops. */
internal class SelectionActions(private val vm: EditorViewModel) {
    private val scene get() = vm.session.scene

    fun handle(intent: EditorIntent): Boolean {
        when (intent) {
            EditorIntent.SelectAll -> vm.selection = scene.elements.values.filter { it.layerId !in vm.lockedLayers }.map { it.id }.toSet()
            EditorIntent.ClearSelection -> vm.selection = emptySet()
            is EditorIntent.Select -> vm.selection = intent.ids
            EditorIntent.DeleteSelection -> run(RemoveElements(vm.selection)).also { vm.selection = emptySet() }
            EditorIntent.Duplicate -> duplicate()
            EditorIntent.Copy -> copy()
            EditorIntent.Cut -> copy().also { run(RemoveElements(vm.selection)) }
            EditorIntent.Paste -> paste()
            is EditorIntent.Style -> run(UpdateStyle(vm.selection, intent.patch))
            is EditorIntent.Order -> run(ChangeZOrder(vm.selection, intent.op))
            is EditorIntent.Lock -> run(SetLocked(vm.selection, intent.locked))
            EditorIntent.Group -> if (vm.selection.size > 1) run(SetGroup(vm.selection, vm.newId()))
            EditorIntent.Ungroup -> run(SetGroup(vm.selection, null))
            is EditorIntent.EditConnector -> vm.execute(UpdateConnector(intent.id, intent.edit), "edit-${intent.id.value}")
            is EditorIntent.EditContent -> editContent(intent.id, intent.content)
            is EditorIntent.ChangeShape -> changeShape(intent.id, intent.shape)
            is EditorIntent.PlaceShape -> placeShape(intent.key, intent.screen)
            is EditorIntent.InsertImage -> insertImage(intent.bytes, intent.mimeType)
            is EditorIntent.ImportDdl -> importDdl(intent.sql)
            is EditorIntent.BeginTextEdit -> beginTextEdit(intent.id)
            is EditorIntent.CommitText -> commitText(intent.id, intent.text)
            EditorIntent.CancelTextEdit -> closeEditor()
            else -> return false
        }
        return true
    }

    private fun run(command: Command) {
        vm.execute(command)
        vm.seal()
    }

    // ---- clipboard ------------------------------------------------------------------------------

    private fun copy() {
        val items = selectionWithChildren()
        if (items.isEmpty()) return
        vm.clipboard.put(vm.projectId, items)
        vm.refreshUi()
    }

    private fun selectionWithChildren(): List<CanvasElement> {
        val ids = vm.selection.flatMap { id -> listOf(id) + scene.descendantsOf(id).map { it.id } }.toSet()
        return ids.mapNotNull { scene[it] }.sortedWith(Scene.Z_ORDER)
    }

    private fun duplicate() {
        val items = selectionWithChildren()
        if (items.isEmpty()) return
        pasteElements(items, Vec2(DUPLICATE_OFFSET, DUPLICATE_OFFSET))
    }

    private fun paste() {
        val content = vm.clipboard.content ?: return
        vm.viewModelScope.launch {
            val elements = if (content.project == vm.projectId) content.elements else relinkAssets(content)
            val bounds = Rect.unionAll(elements.map { it.bounds }) ?: return@launch
            val visible = vm.camera.visibleWorldRect(vm.viewport)
            vm.clipboard.pasteCount++
            val offset = if (visible.intersects(bounds)) {
                Vec2(DUPLICATE_OFFSET, DUPLICATE_OFFSET) * vm.clipboard.pasteCount.toDouble()
            } else {
                visible.center - bounds.center
            }
            pasteElements(elements, offset)
        }
    }

    private suspend fun relinkAssets(content: ElementClipboard.Content): List<CanvasElement> = content.elements.map { el ->
        if (el is ImageElement) {
            vm.assets.copy(content.project, vm.projectId, el.asset)?.let { el.copy(asset = it) } ?: el
        } else {
            el
        }
    }

    private fun pasteElements(items: List<CanvasElement>, offset: Vec2) {
        val copies = ElementCopier.copy(items, offset) { vm.newId() }.map { it.withLayer(vm.activeLayer).withMeta(locked = false) }
        run(AddElements(copies))
        vm.selection = copies.filter { c -> (c as? ShapeNode)?.parent == null || copies.none { it.id == c.parent } }.map { it.id }.toSet()
    }

    // ---- content --------------------------------------------------------------------------------

    private fun editContent(id: ElementId, content: NodeContent) {
        val node = scene[id] as? ShapeNode ?: return
        val def = vm.catalog.shapes.resolve(node.shape)
        val preferred = vm.catalog.layouts.layouterFor(content).preferredSize(content, def, node.style, vm.textMetrics)
        val frame = preferred?.let {
            Rect.fromOriginSize(node.frame.topLeft, Size(maxOf(node.frame.width, it.width), maxOf(node.frame.height, it.height)))
        }
        vm.execute(UpdateNodeContent(id, content, frame), "edit-${id.value}")
    }

    private fun changeShape(id: ElementId, key: ShapeTypeKey) {
        val node = scene[id] as? ShapeNode ?: return
        run(ApplyEdits(listOf(node.copy(shape = key))))
    }

    private fun placeShape(key: ShapeTypeKey, screen: Vec2) {
        val def = vm.catalog.shapes.resolve(key)
        val world = vm.camera.screenToWorld(screen, vm.viewport)
        val content = def.createContent()
        val preferred = vm.catalog.layouts.layouterFor(content).preferredSize(content, def, def.defaultStyle, vm.textMetrics)
        val size = Size(maxOf(def.defaultSize.width, preferred?.width ?: 0.0), maxOf(def.defaultSize.height, preferred?.height ?: 0.0))
        val node = ShapeNode(vm.newId(), key, Rect.fromCenter(world, size), content, def.defaultStyle, layerId = vm.activeLayer)
        run(CompositeCommand(listOf(AddElements(listOf(node)), MoveElements(listOf(node.id), Vec2.Zero, reparent = true))))
        vm.selection = setOf(node.id)
        vm.shapePlaced(key)
    }

    private fun insertImage(bytes: ByteArray, mime: String) {
        vm.viewModelScope.launch {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            if (opts.outWidth <= 0 || opts.outHeight <= 0) return@launch
            val id = vm.assets.save(vm.projectId, bytes, mime)
            val maxWorld = IMAGE_MAX_SCREEN / vm.camera.zoom
            val scale = minOf(1.0, maxWorld / maxOf(opts.outWidth, opts.outHeight))
            val size = Size(opts.outWidth * scale, opts.outHeight * scale)
            val center = vm.camera.screenToWorld(Vec2(vm.viewport.width / 2, vm.viewport.height / 2), vm.viewport)
            val el = ImageElement(vm.newId(), Rect.fromCenter(center, size), id, layerId = vm.activeLayer)
            run(AddElements(listOf(el)))
            vm.selection = setOf(el.id)
        }
    }

    /** Lays out tables parsed from SQL in a grid at the view center and links their foreign keys. */
    private fun importDdl(sql: String) {
        val result = DdlImporter.parse(sql)
        if (result.tables.isEmpty()) {
            vm.updateState { it.copy(message = EditorMessage("No CREATE TABLE statements found")) }
            return
        }
        val nodeIds = result.tables.keys.associateWith { vm.newId() }
        val def = DbShapes.Table
        val origin = vm.camera.screenToWorld(Vec2(vm.viewport.width / 4, vm.viewport.height / 4), vm.viewport)
        val nodes = result.tables.entries.mapIndexed { i, (key, table) ->
            val linked = table.copy(
                columns = table.columns.map { c ->
                    c.copy(references = c.references?.let { r -> r.copy(tableId = nodeIds.getValue(r.tableId).value) })
                },
            )
            val size = TableLayouter.preferredSize(linked, def, def.defaultStyle, vm.textMetrics)
            val at = origin + Vec2((i % DDL_COLUMNS) * DDL_CELL_W, (i / DDL_COLUMNS) * DDL_CELL_H)
            ShapeNode(nodeIds.getValue(key), def.key, Rect.fromOriginSize(at, size), linked, def.defaultStyle, layerId = vm.activeLayer)
        }
        val edges = nodes.flatMap { child ->
            (child.content as DbTableContent).columns.mapNotNull { c ->
                val ref = c.references ?: return@mapNotNull null
                val base =
                    Connector(vm.newId(), Endpoint.Attached(child.id), Endpoint.Attached(ElementId(ref.tableId)), layerId = vm.activeLayer)
                DbNotation.applyRelationship(base, DbRelationship(identifying = c.primaryKey, fkColumnId = c.id))
            }
        }
        run(AddElements(nodes + edges))
        vm.selection = nodes.map { it.id }.toSet()
        val skipped = if (result.skipped > 0) " (${result.skipped} foreign keys skipped)" else ""
        vm.updateState { it.copy(message = EditorMessage("Imported ${nodes.size} tables$skipped")) }
    }

    // ---- inline text ----------------------------------------------------------------------------

    fun beginTextEdit(id: ElementId) {
        val target = editTarget(id) ?: return
        val screen = Rect.bounding(target.rect.corners().map { vm.camera.worldToScreen(it, vm.viewport) }) ?: return
        vm.updateState {
            it.copy(editing = TextEditRequest(id, target.text, screen, target.font * vm.camera.zoom, target.sticky), contextMenu = null)
        }
        vm.publishFrame()
    }

    /** What inline editing shows for [id]; structured content opens the properties panel instead. */
    private fun editTarget(id: ElementId): EditTarget? = when (val el = scene[id]) {
        is TextElement -> EditTarget(el.text, el.frame, el.style.fontSize, el.kind == TextKind.STICKY)
        is Connector -> EditTarget(el.label(LabelPosition.MIDDLE), el.bounds, el.style.fontSize, false)
        is ShapeNode -> {
            val label = el.content as? PlainLabel
            if (label == null) {
                vm.updateState { it.copy(showProperties = true) }
                null
            } else {
                EditTarget(label.text, vm.catalog.shapes.resolve(el.shape).textArea.resolve(el.frame), el.style.fontSize, false)
            }
        }
        else -> null
    }

    private fun commitText(id: ElementId, text: String) {
        when (val el = scene[id]) {
            is TextElement -> if (text.isBlank() && el.kind == TextKind.TEXT) run(RemoveElements(listOf(id))) else run(UpdateText(id, text))
            is ShapeNode -> if (el.content is PlainLabel) run(UpdateNodeContent(id, PlainLabel(text)))
            is Connector -> run(UpdateConnector(id) { it.withLabel(LabelPosition.MIDDLE, text) })
            else -> Unit
        }
        closeEditor()
    }

    private fun closeEditor() {
        vm.updateState { it.copy(editing = null) }
        vm.refreshUi()
    }

    private data class EditTarget(val text: String, val rect: Rect, val font: Double, val sticky: Boolean)

    private companion object {
        const val DUPLICATE_OFFSET = 24.0
        const val IMAGE_MAX_SCREEN = 480.0
        const val DDL_COLUMNS = 3
        const val DDL_CELL_W = 340.0
        const val DDL_CELL_H = 300.0
    }
}
