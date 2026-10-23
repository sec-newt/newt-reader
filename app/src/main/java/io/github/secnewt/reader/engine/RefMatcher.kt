package io.github.secnewt.reader.engine

import kotlin.math.abs

/**
 * Finds a node again in a fresh tree, because apps rebuild their trees all the time:
 * the same path if it still says the same thing, else the same view id and label,
 * else the same label and class nearest the old spot, else an unlabelled node of the
 * same class within 50 px of where it was.
 */
object RefMatcher {
    private const val NEAR_PX = 50

    fun find(root: ScreenNode, ref: NodeRef): ScreenNode? {
        fun same(n: ScreenNode) = n.cls == ref.cls && n.label == ref.label
        at(root, ref.path)?.let { if (same(it) && (ref.viewId == null || it.viewId == ref.viewId)) return it }
        val all = root.walk().toList()
        if (ref.viewId != null) all.firstOrNull { it.viewId == ref.viewId && it.label == ref.label }?.let { return it }
        if (ref.label != null) return all.filter(::same).minByOrNull { distance(it.bounds, ref.bounds) }
        return all.filter { it.cls == ref.cls && it.label == null && distance(it.bounds, ref.bounds) <= NEAR_PX }
            .minByOrNull { distance(it.bounds, ref.bounds) }
    }

    fun at(root: ScreenNode, path: List<Int>): ScreenNode? =
        path.fold(root as ScreenNode?) { n, i -> n?.children?.getOrNull(i) }

    private fun distance(a: Bounds, b: Bounds) = abs(a.left - b.left) + abs(a.top - b.top)
}
