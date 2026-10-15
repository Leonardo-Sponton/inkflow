package io.github.leonardosponton.inkflow.feature.editor.interaction

import io.github.leonardosponton.inkflow.core.geometry.Affine
import io.github.leonardosponton.inkflow.core.model.ColorArgb
import io.github.leonardosponton.inkflow.core.model.ElementId
import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.core.model.ShapeStyle
import io.github.leonardosponton.inkflow.core.model.Size
import io.github.leonardosponton.inkflow.core.model.TextAlign
import io.github.leonardosponton.inkflow.core.model.Vec2
import io.github.leonardosponton.inkflow.domain.canvas.command.AddElements
import io.github.leonardosponton.inkflow.domain.canvas.command.ApplyEdits
import io.github.leonardosponton.inkflow.domain.canvas.command.CompositeCommand
import io.github.leonardosponton.inkflow.domain.canvas.command.MoveElements
import io.github.leonardosponton.inkflow.domain.canvas.command.SetFrame
import io.github.leonardosponton.inkflow.domain.canvas.command.TransformElements
import io.github.leonardosponton.inkflow.domain.canvas.command.UpdateConnector
import io.github.leonardosponton.inkflow.domain.canvas.element.CanvasElement
import io.github.leonardosponton.inkflow.domain.canvas.element.Connector
import io.github.leonardosponton.inkflow.domain.canvas.element.Endpoint
import io.github.leonardosponton.inkflow.domain.canvas.element.ImageElement
import io.github.leonardosponton.inkflow.domain.canvas.element.InkStroke
import io.github.leonardosponton.inkflow.domain.canvas.element.ShapeNode
import io.github.leonardosponton.inkflow.domain.canvas.element.TextElement
import io.github.leonardosponton.inkflow.domain.canvas.element.TextKind
import io.github.leonardosponton.inkflow.domain.canvas.hit.HitTester
import io.github.leonardosponton.inkflow.domain.canvas.hit.PortHit
import io.github.leonardosponton.inkflow.domain.canvas.ink.InkEraser
import io.github.leonardosponton.inkflow.domain.canvas.input.ToolType
import io.github.leonardosponton.inkflow.domain.canvas.snap.Guide
import io.github.leonardosponton.inkflow.domain.canvas.snap.Snapper
import io.github.leonardosponton.inkflow.feature.canvasrender.Handle
import io.github.leonardosponton.inkflow.feature.canvasrender.Overlay
import io.github.leonardosponton.inkflow.feature.canvasrender.OverlayDrawer
import kotlin.math.abs
import kotlin.math.max

/** Continuous pen/finger drags: move, resize, marquee, lasso, erase, connect and place. */
internal class DragOperations(private val env: EditorEnvironment) {
    private sealed interface Drag {
        data class Moving(val ids: Set<ElementId>, val start: Vec2, val startBounds: Rect?, var applied: Vec2 = Vec2.Zero) : Drag

        data class Resizing(val handle: Handle, val ids: Set<ElementId>, val start: Vec2, val startBounds: Rect, var current: Rect) : Drag

        data class Marquee(val start: Vec2, var end: Vec2) : Drag

        data class Lasso(val points: MutableList<Vec2>) : Drag

        data class Erasing(var last: Vec2) : Drag

        data class Connecting(val source: Endpoint, val from: Vec2, var to: Vec2, var target: Endpoint? = null) : Drag

        data class Placing(val text: TextKind?, val start: Vec2, var end: Vec2) : Drag

        data class Waypoint(val connector: ElementId, val index: Int) : Drag
    }

    private var drag: Drag? = null
    private var guides: List<Guide> = emptyList()
    private val snapper = Snapper()
    private val key get() = "drag-${System.identityHashCode(drag)}"

    val currentPoint: Vec2?
        get() = when (val d = drag) {
            is Drag.Connecting -> d.to
            else -> null
        }

    // ---- lifecycle ------------------------------------------------------------------------------

    fun start(tool: ToolKind, world: Vec2, pointer: ToolType) {
        guides = emptyList()
        drag = when (tool) {
            ToolKind.SELECT -> startSelect(world)
            ToolKind.LASSO -> if (insideSelection(world)) moving(env.selection, world) else Drag.Lasso(mutableListOf(world))
            ToolKind.ERASER -> Drag.Erasing(world).also { eraseAt(world) }
            ToolKind.SHAPE -> Drag.Placing(null, snapIfEnabled(world), world)
            ToolKind.TEXT -> Drag.Placing(TextKind.TEXT, snapIfEnabled(world), world)
            ToolKind.STICKY -> Drag.Placing(TextKind.STICKY, snapIfEnabled(world), world)
            ToolKind.CONNECTOR -> startConnector(world)
            ToolKind.PEN, ToolKind.HIGHLIGHTER -> null
        }
        if (pointer == ToolType.FINGER && drag is Drag.Marquee) drag = null
    }

    private fun startSelect(world: Vec2): Drag? {
        handleAt(world)?.let { h ->
            val bounds = selectionBounds() ?: return null
            return Drag.Resizing(h, env.selection, world, bounds, bounds)
        }
        waypointAt(world)?.let { return it }
        portAt(world)?.let { return connectFrom(it) }
        val hit = hitTester().elementAt(world, tolerance()) { it.layerId !in env.lockedLayers }
        if (hit != null) {
            if (hit.id !in env.selection) env.selection = expand(hit)
            return moving(env.selection, world)
        }
        if (insideSelection(world)) return moving(env.selection, world)
        env.selection = emptySet()
        return Drag.Marquee(world, world)
    }

    private fun startConnector(world: Vec2): Drag {
        portAt(world)?.let { return connectFrom(it) }
        val node = hitTester().nodeAt(world, tolerance())
        return if (node != null) {
            Drag.Connecting(Endpoint.Attached(node.id, null), node.frame.center, world)
        } else {
            Drag.Connecting(Endpoint.Free(snapIfEnabled(world)), snapIfEnabled(world), world)
        }
    }

    private fun connectFrom(p: PortHit) = Drag.Connecting(Endpoint.Attached(p.node.id, p.port.id), p.position, p.position)

    private fun moving(ids: Set<ElementId>, world: Vec2): Drag.Moving {
        val nodes = ids.mapNotNull { env.session.scene[it] }.filter { it !is InkStroke && it !is Connector }
        return Drag.Moving(ids, world, Rect.unionAll(nodes.map { it.bounds }))
    }

    fun move(world: Vec2) {
        when (val d = drag) {
            is Drag.Moving -> moveBy(d, world)
            is Drag.Resizing -> resize(d, world)
            is Drag.Marquee -> d.end = world
            is Drag.Lasso -> if (d.points.last().distanceTo(world) * env.camera.zoom > LASSO_STEP_PX) d.points += world
            is Drag.Erasing -> eraseAlong(d, world)
            is Drag.Connecting -> updateConnecting(d, world)
            is Drag.Placing -> d.end = world
            is Drag.Waypoint -> moveWaypoint(d, world)
            null -> Unit
        }
    }

    fun end(world: Vec2, isTap: Boolean) {
        move(world)
        when (val d = drag) {
            is Drag.Moving -> if (d.applied != Vec2.Zero) env.execute(MoveElements(d.ids, Vec2.Zero, reparent = true), key)
            is Drag.Marquee -> if (!isTap) env.selection = expandAll(hitTester().inRect(Rect.fromPoints(d.start, d.end)))
            is Drag.Lasso -> if (d.points.size > 2) env.selection = expandAll(hitTester().inLasso(d.points))
            is Drag.Connecting -> finishConnecting(d)
            is Drag.Placing -> if (!isTap) finishPlacing(d)
            else -> Unit
        }
        drag = null
        guides = emptyList()
        env.seal()
    }

    fun cancel() {
        drag = null
        guides = emptyList()
        env.seal()
    }

    // ---- move / resize --------------------------------------------------------------------------

    private fun moveBy(d: Drag.Moving, world: Vec2) {
        val raw = world - d.start
        val target = d.startBounds?.let { b ->
            val others = env.session.index.idsIn(env.camera.visibleWorldRect(env.viewport))
                .filter { it !in d.ids }
                .mapNotNull { env.session.scene[it] as? ShapeNode }
                .map { it.frame }
            val r = snapper.snapMove(b, raw, others, SNAP_PX / env.camera.zoom, env.settings.snapToGrid, env.settings.smartGuides)
            guides = r.guides
            r.delta
        } ?: raw
        val step = target - d.applied
        if (step == Vec2.Zero) return
        env.execute(MoveElements(d.ids, step, reparent = false), key)
        d.applied = target
    }

    private fun resize(d: Drag.Resizing, world: Vec2) {
        val b = d.startBounds
        val delta = world - d.start
        var l = b.left
        var t = b.top
        var r = b.right
        var bt = b.bottom
        if (d.handle in WEST) l = minOf(b.left + delta.x, b.right - MIN_SIDE)
        if (d.handle in EAST) r = maxOf(b.right + delta.x, b.left + MIN_SIDE)
        if (d.handle in NORTH) t = minOf(b.top + delta.y, b.bottom - MIN_SIDE)
        if (d.handle in SOUTH) bt = maxOf(b.bottom + delta.y, b.top + MIN_SIDE)
        var next = Rect(l, t, r, bt)
        if (keepAspect(d.ids)) next = withAspect(next, b, d.handle)
        if (env.settings.snapToGrid && d.ids.size == 1 && single(d.ids) is ShapeNode) next = snapRect(next, d.handle)
        applyResize(d, next)
    }

    private fun applyResize(d: Drag.Resizing, next: Rect) {
        val el = single(d.ids)
        if (el is ShapeNode || el is TextElement || el is ImageElement) {
            env.execute(SetFrame(el.id, next), key)
        } else {
            val prev = d.current
            val sx = next.width / prev.width.coerceAtLeast(EPS)
            val sy = next.height / prev.height.coerceAtLeast(EPS)
            val t = Affine.translate(next.left, next.top) * Affine.scale(sx, sy) * Affine.translate(-prev.left, -prev.top)
            env.execute(TransformElements(d.ids, t), key)
        }
        d.current = next
    }

    private fun keepAspect(ids: Set<ElementId>): Boolean = when (val el = single(ids)) {
        is ImageElement -> true
        is ShapeNode -> env.session.catalog.shapes.resolve(el.shape).keepAspectRatio
        null -> true
        else -> false
    }

    private fun withAspect(r: Rect, original: Rect, h: Handle): Rect {
        val ratio = original.width / original.height.coerceAtLeast(EPS)
        val w = max(r.width, r.height * ratio)
        val ht = w / ratio
        val left = if (h in WEST) r.right - w else r.left
        val top = if (h in NORTH) r.bottom - ht else r.top
        return Rect(left, top, left + w, top + ht)
    }

    private fun snapRect(r: Rect, h: Handle): Rect = Rect(
        if (h in WEST) snapper.snapToGrid(r.left) else r.left,
        if (h in NORTH) snapper.snapToGrid(r.top) else r.top,
        if (h in EAST) snapper.snapToGrid(r.right).coerceAtLeast(r.left + MIN_SIDE) else r.right,
        if (h in SOUTH) snapper.snapToGrid(r.bottom).coerceAtLeast(r.top + MIN_SIDE) else r.bottom,
    )

    private fun single(ids: Set<ElementId>): CanvasElement? = ids.singleOrNull()?.let { env.session.scene[it] }

    // ---- waypoints ------------------------------------------------------------------------------

    private fun waypointAt(world: Vec2): Drag? {
        val c = single(env.selection) as? Connector ?: return null
        val i = c.waypoints.indexOfFirst { it.distanceTo(world) <= tolerance() * 2 }
        return if (i >= 0) Drag.Waypoint(c.id, i) else null
    }

    private fun moveWaypoint(d: Drag.Waypoint, world: Vec2) {
        val p = snapIfEnabled(world)
        env.execute(
            UpdateConnector(d.connector) { c ->
                c.copy(
                    waypoints = c.waypoints.mapIndexed { i, w ->
                        if (i ==
                            d.index
                        ) {
                            p
                        } else {
                            w
                        }
                    },
                )
            },
            key,
        )
    }

    // ---- erase ----------------------------------------------------------------------------------

    private fun eraseAlong(d: Drag.Erasing, world: Vec2) {
        val radius = env.tools.eraserSize / env.camera.zoom
        val dist = d.last.distanceTo(world)
        val steps = (dist / (radius / 2)).toInt().coerceIn(1, MAX_ERASE_STEPS)
        for (i in 1..steps) eraseAt(d.last.lerp(world, i.toDouble() / steps))
        d.last = world
    }

    private fun eraseAt(p: Vec2) {
        val radius = env.tools.eraserSize / env.camera.zoom
        val hit = hitTester()
        val strokes = env.session.index.idsIn(Rect.fromCenter(p, Size(2 * radius, 2 * radius)))
            .mapNotNull { env.session.scene[it] as? InkStroke }
            .filter { it.layerId !in env.lockedLayers && it.layerId !in env.hiddenLayers && !it.locked && hit.hits(it, p, radius) }
        if (strokes.isEmpty()) return
        val command = when (env.tools.eraserMode) {
            EraserMode.STROKE -> ApplyEdits(emptyList(), strokes.map { it.id })
            EraserMode.PARTIAL -> {
                val pieces = strokes.flatMap { s -> InkEraser.erase(s, p, radius) { env.newId() }.orEmpty() }
                ApplyEdits(pieces, strokes.map { it.id })
            }
        }
        env.execute(command, "erase")
    }

    fun eraserCursor(): Pair<Vec2, Double>? = (drag as? Drag.Erasing)?.let { it.last to env.tools.eraserSize / env.camera.zoom }

    // ---- connect --------------------------------------------------------------------------------

    private fun updateConnecting(d: Drag.Connecting, world: Vec2) {
        d.to = world
        val exclude = (d.source as? Endpoint.Attached)?.node
        val port = hitTester().portAt(world, PORT_PX / env.camera.zoom, exclude)
        d.target = when {
            port != null -> Endpoint.Attached(port.node.id, port.port.id).also { d.to = port.position }
            else -> hitTester().nodeAt(world, tolerance(), exclude)?.let { Endpoint.Attached(it.id, null) }
        }
    }

    private fun finishConnecting(d: Drag.Connecting) {
        val target =
            d.target ?: if (d.from.distanceTo(d.to) * env.camera.zoom > MIN_CONNECTOR_PX) Endpoint.Free(snapIfEnabled(d.to)) else null
        target ?: return
        val tools = env.tools
        val base = Connector(
            env.newId(),
            d.source,
            target,
            routing = tools.routing,
            startMarker = tools.startMarker,
            endMarker = tools.endMarker,
            layerId = env.activeLayer,
        )
        val catalog = env.session.catalog
        val decorated = catalog.decorateNewConnector(base, env.session.scene)
        env.execute(AddElements(listOf(decorated)))
        env.seal()
        env.selection = setOf(decorated.id)
        val stored = env.session.scene[decorated.id] as? Connector ?: return
        catalog.followUpFor(stored, env.session.scene)?.let(env::offerFollowUp)
    }

    // ---- placement ------------------------------------------------------------------------------

    fun placeDefaultShape(world: Vec2) {
        val def = env.session.catalog.shapes.resolve(env.tools.shape)
        placeShape(Rect.fromCenter(snapIfEnabled(world), def.defaultSize))
    }

    private fun placeShape(frame: Rect) {
        val catalog = env.session.catalog
        val def = catalog.shapes.resolve(env.tools.shape)
        val content = def.createContent()
        val preferred = catalog.layouts.layouterFor(content).preferredSize(content, def, def.defaultStyle, env.session.textMetrics)
        val size = Size(max(frame.width, preferred?.width ?: 0.0), max(frame.height, preferred?.height ?: 0.0))
        val node =
            ShapeNode(env.newId(), def.key, Rect.fromOriginSize(frame.topLeft, size), content, def.defaultStyle, layerId = env.activeLayer)
        env.execute(CompositeCommand(listOf(AddElements(listOf(node)), MoveElements(listOf(node.id), Vec2.Zero, reparent = true))))
        env.seal()
        env.selection = setOf(node.id)
        env.shapePlaced(def.key)
    }

    fun placeText(world: Vec2, frame: Rect?) {
        val kind = if (env.tools.kind == ToolKind.STICKY) TextKind.STICKY else TextKind.TEXT
        val size = if (kind == TextKind.STICKY) STICKY_SIZE else TEXT_SIZE
        val f = frame ?: Rect.fromOriginSize(snapIfEnabled(world), size)
        val style = if (kind == TextKind.STICKY) {
            ShapeStyle(fill = ColorArgb.StickyYellow, textColor = ColorArgb.Black, textAlign = TextAlign.START, fontSize = STICKY_FONT)
        } else {
            ShapeStyle(fill = null, textColor = env.tools.pen.color, textAlign = TextAlign.START, fontSize = TEXT_FONT)
        }
        val el = TextElement(env.newId(), f, "", kind, style, layerId = env.activeLayer)
        env.execute(AddElements(listOf(el)))
        env.seal()
        env.selection = setOf(el.id)
        env.editText(el.id)
    }

    private fun finishPlacing(d: Drag.Placing) {
        val r = Rect.fromPoints(d.start, snapIfEnabled(d.end))
        val big = r.width * env.camera.zoom > MIN_PLACE_PX && r.height * env.camera.zoom > MIN_PLACE_PX
        when {
            d.text != null -> placeText(d.start, if (big) r else null)
            big -> placeShape(r)
            else -> placeDefaultShape(d.start)
        }
    }

    // ---- overlay --------------------------------------------------------------------------------

    fun decorate(base: Overlay): Overlay = when (val d = drag) {
        is Drag.Marquee -> base.copy(marquee = Rect.fromPoints(d.start, d.end))
        is Drag.Lasso -> base.copy(lasso = d.points.toList())
        is Drag.Moving -> base.copy(guides = guides, dropTarget = dropTarget(d))
        is Drag.Connecting -> base.copy(
            connectorPreview = listOf(d.from, d.to),
            activePort = (d.target as? Endpoint.Attached)?.takeIf { it.port != null }?.let { d.to },
            dropTarget = (d.target as? Endpoint.Attached)?.takeIf { it.port == null }?.let { env.session.scene[it.node]?.bounds },
        )
        is Drag.Placing -> base.copy(marquee = Rect.fromPoints(d.start, d.end))
        else -> base
    }

    private fun dropTarget(d: Drag.Moving): Rect? {
        val node = single(d.ids) as? ShapeNode ?: return null
        val c = node.frame.center
        return env.session.scene.ordered.asReversed().asSequence()
            .filterIsInstance<ShapeNode>()
            .filter { it.id != node.id && env.session.catalog.shapes.resolve(it.shape).isContainer && it.frame.contains(c) }
            .firstOrNull()?.frame
    }

    // ---- helpers --------------------------------------------------------------------------------

    private fun handleAt(world: Vec2): Handle? {
        if (env.selection.isEmpty() || env.selection.any { env.session.scene[it]?.locked == true }) return null
        if (single(env.selection) is Connector) return null
        val sel = selectionBounds() ?: return null
        val r = OverlayDrawer.HANDLE_PX / env.camera.zoom
        return OverlayDrawer.handlePositions(sel).entries.firstOrNull { (_, p) -> abs(p.x - world.x) <= r && abs(p.y - world.y) <= r }?.key
    }

    private fun portAt(world: Vec2): PortHit? {
        val node = hitTester().nodeAt(world, PORT_PX / env.camera.zoom) ?: return null
        val port = hitTester().portAt(world, PORT_PX / env.camera.zoom) ?: return null
        return port.takeIf { it.node.id == node.id && !node.frame.inflate(-PORT_PX / env.camera.zoom).contains(world) }
    }

    private fun selectionBounds(): Rect? = Rect.unionAll(env.selection.mapNotNull { env.session.scene[it]?.bounds })

    private fun insideSelection(world: Vec2) = selectionBounds()?.contains(world) == true

    private fun expand(el: CanvasElement): Set<ElementId> {
        val g = el.groupId ?: return setOf(el.id)
        return env.session.scene.groupMembers(g).map { it.id }.toSet()
    }

    private fun expandAll(els: List<CanvasElement>): Set<ElementId> =
        els.filter { it.layerId !in env.lockedLayers }.flatMap { expand(it) }.toSet()

    private fun snapIfEnabled(p: Vec2) = if (env.settings.snapToGrid && env.tools.kind != ToolKind.LASSO) snapper.snapPoint(p) else p

    private fun hitTester(): HitTester = env.session.hitTester(env.hiddenLayers)

    private fun tolerance() = InteractionController.TOUCH_TOLERANCE_PX / env.camera.zoom

    private companion object {
        const val SNAP_PX = 8.0
        const val PORT_PX = 14.0
        const val LASSO_STEP_PX = 3.0
        const val MIN_SIDE = 12.0
        const val MIN_CONNECTOR_PX = 30.0
        const val MIN_PLACE_PX = 20.0
        const val MAX_ERASE_STEPS = 40
        const val EPS = 1e-6
        const val STICKY_FONT = 20.0
        const val TEXT_FONT = 22.0
        val TEXT_SIZE = Size(280.0, 56.0)
        val STICKY_SIZE = Size(200.0, 200.0)
        val WEST = setOf(Handle.NW, Handle.W, Handle.SW)
        val EAST = setOf(Handle.NE, Handle.E, Handle.SE)
        val NORTH = setOf(Handle.NW, Handle.N, Handle.NE)
        val SOUTH = setOf(Handle.SW, Handle.S, Handle.SE)
    }
}
