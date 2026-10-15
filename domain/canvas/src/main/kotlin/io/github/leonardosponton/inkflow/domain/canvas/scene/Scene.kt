package io.github.leonardosponton.inkflow.domain.canvas.scene

import io.github.leonardosponton.inkflow.core.model.ElementId
import io.github.leonardosponton.inkflow.core.model.Rect
import io.github.leonardosponton.inkflow.domain.canvas.element.CanvasElement
import io.github.leonardosponton.inkflow.domain.canvas.element.Connector
import io.github.leonardosponton.inkflow.domain.canvas.element.ShapeNode

/**
 * Immutable snapshot of one tab's elements. Derived lookups (z-order, connectors per node,
 * children per container) are computed lazily once per snapshot.
 */
class Scene(val elements: Map<ElementId, CanvasElement> = emptyMap()) {
    val size: Int get() = elements.size

    operator fun get(id: ElementId): CanvasElement? = elements[id]

    operator fun contains(id: ElementId): Boolean = id in elements

    /** Back-to-front drawing order. */
    val ordered: List<CanvasElement> by lazy { elements.values.sortedWith(Z_ORDER) }

    val maxZ: Long get() = ordered.lastOrNull()?.zIndex ?: 0L

    val minZ: Long get() = ordered.firstOrNull()?.zIndex ?: 0L

    private val connectorsByNode: Map<ElementId, List<Connector>> by lazy {
        val map = HashMap<ElementId, MutableList<Connector>>()
        elements.values.filterIsInstance<Connector>().forEach { c ->
            c.attachedNodes().distinct().forEach { map.getOrPut(it) { mutableListOf() }.add(c) }
        }
        map
    }

    private val childrenByParent: Map<ElementId, List<ShapeNode>> by lazy {
        elements.values.filterIsInstance<ShapeNode>().filter { it.parent != null }.groupBy { requireNotNull(it.parent) }
    }

    private val membersByGroup: Map<ElementId, List<CanvasElement>> by lazy {
        elements.values.filter { it.groupId != null }.groupBy { requireNotNull(it.groupId) }
    }

    fun connectorsOf(node: ElementId): List<Connector> = connectorsByNode[node].orEmpty()

    fun childrenOf(container: ElementId): List<ShapeNode> = childrenByParent[container].orEmpty()

    /** [container] and every nested child, depth-first. */
    fun descendantsOf(container: ElementId): List<ShapeNode> {
        val out = mutableListOf<ShapeNode>()
        val stack = ArrayDeque(childrenOf(container))
        val seen = HashSet<ElementId>()
        while (stack.isNotEmpty()) {
            val n = stack.removeLast()
            if (!seen.add(n.id)) continue
            out += n
            stack.addAll(childrenOf(n.id))
        }
        return out
    }

    fun groupMembers(group: ElementId): List<CanvasElement> = membersByGroup[group].orEmpty()

    fun contentBounds(): Rect? = Rect.unionAll(elements.values.map { it.bounds })

    fun apply(change: SceneChange): Scene {
        if (change.isEmpty) return this
        val next = HashMap(elements)
        change.after.forEach { (id, el) -> if (el == null) next.remove(id) else next[id] = el }
        return Scene(next)
    }

    fun with(vararg added: CanvasElement): Scene = apply(SceneChange.of(this, added.toList()))

    companion object {
        val Empty = Scene()

        val Z_ORDER: Comparator<CanvasElement> = compareBy<CanvasElement> { it.zIndex }.thenBy { it.id.value }

        fun of(elements: Iterable<CanvasElement>) = Scene(elements.associateBy { it.id })
    }
}
