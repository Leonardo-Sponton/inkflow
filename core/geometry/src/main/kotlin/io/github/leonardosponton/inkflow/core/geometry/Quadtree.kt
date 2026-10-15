package io.github.leonardosponton.inkflow.core.geometry

import io.github.leonardosponton.inkflow.core.model.Rect

/**
 * Spatial index over axis-aligned bounds for viewport culling and hit testing.
 *
 * Items live in the deepest node whose bounds fully contain them, so large items stay high in the
 * tree. The root grows (doubling) to cover anything inserted, which keeps the canvas unbounded.
 */
class Quadtree<K : Any>(
    initialBounds: Rect = Rect(-INITIAL_HALF, -INITIAL_HALF, INITIAL_HALF, INITIAL_HALF),
    private val nodeCapacity: Int = DEFAULT_CAPACITY,
    private val maxDepth: Int = DEFAULT_MAX_DEPTH,
) {
    private inner class Node(val bounds: Rect, val depth: Int) {
        val items = LinkedHashMap<K, Rect>()
        var children: Array<Node>? = null

        fun childFor(r: Rect): Node? = children?.firstOrNull { it.bounds.contains(r) }

        fun split() {
            val cx = bounds.center.x
            val cy = bounds.center.y
            children = arrayOf(
                Node(Rect(bounds.left, bounds.top, cx, cy), depth + 1),
                Node(Rect(cx, bounds.top, bounds.right, cy), depth + 1),
                Node(Rect(bounds.left, cy, cx, bounds.bottom), depth + 1),
                Node(Rect(cx, cy, bounds.right, bounds.bottom), depth + 1),
            )
            val moved = items.entries.mapNotNull { (k, r) -> childFor(r)?.let { Triple(k, r, it) } }
            moved.forEach { (k, r, child) ->
                items.remove(k)
                child.items[k] = r
                owner[k] = child
            }
        }
    }

    private var root = Node(initialBounds, 0)
    private val owner = HashMap<K, Node>()

    val size: Int get() = owner.size

    operator fun contains(key: K): Boolean = key in owner

    fun boundsOf(key: K): Rect? = owner[key]?.items?.get(key)

    /** Inserts or moves [key] to [bounds]. */
    fun put(key: K, bounds: Rect) {
        remove(key)
        while (!root.bounds.contains(bounds)) grow(bounds)
        reinsert(key, bounds)
    }

    fun remove(key: K): Boolean {
        val node = owner.remove(key) ?: return false
        node.items.remove(key)
        return true
    }

    fun clear() {
        root = Node(root.bounds, 0)
        owner.clear()
    }

    /** Calls [visit] for every item whose bounds intersect [area]. */
    fun query(area: Rect, visit: (K, Rect) -> Unit) {
        val stack = ArrayDeque<Node>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            if (!node.bounds.intersects(area)) continue
            for ((k, r) in node.items) if (r.intersects(area)) visit(k, r)
            node.children?.forEach(stack::addLast)
        }
    }

    fun query(area: Rect): List<K> = buildList { query(area) { k, _ -> add(k) } }

    /** Bounds covering every item, or `null` when empty. */
    fun contentBounds(): Rect? = Rect.unionAll(owner.entries.map { (k, n) -> n.items.getValue(k) })

    private fun grow(target: Rect) {
        val b = root.bounds
        val growLeft = target.left < b.left
        val growUp = target.top < b.top
        val w = b.width
        val h = b.height
        val newBounds = Rect(
            if (growLeft) b.left - w else b.left,
            if (growUp) b.top - h else b.top,
            if (growLeft) b.right else b.right + w,
            if (growUp) b.bottom else b.bottom + h,
        )
        val entries = owner.mapValues { (k, n) -> n.items.getValue(k) }
        root = Node(newBounds, 0)
        owner.clear()
        entries.forEach { (k, r) -> reinsert(k, r) }
    }

    private fun reinsert(key: K, bounds: Rect) {
        var node = root
        while (true) {
            if (node.children == null && node.items.size >= nodeCapacity && node.depth < maxDepth) node.split()
            node = node.childFor(bounds) ?: break
        }
        node.items[key] = bounds
        owner[key] = node
    }

    private companion object {
        const val INITIAL_HALF = 8192.0
        const val DEFAULT_CAPACITY = 16
        const val DEFAULT_MAX_DEPTH = 18
    }
}
