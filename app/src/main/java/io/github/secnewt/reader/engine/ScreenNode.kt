package io.github.secnewt.reader.engine

/** A rectangle on screen, in pixels. */
data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val area: Long get() = maxOf(0, right - left).toLong() * maxOf(0, bottom - top)
}

/**
 * One node of an app's accessibility tree, copied into plain Kotlin so the rest of the
 * engine can be tested without a phone. [path] is the child indexes from the root.
 */
data class ScreenNode(
    val cls: String,
    val text: String? = null,
    val desc: String? = null,
    val hint: String? = null,
    val viewId: String? = null,
    val clickable: Boolean = false,
    val scrollable: Boolean = false,
    val editable: Boolean = false,
    val password: Boolean = false,
    val heading: Boolean = false,
    val visible: Boolean = true,
    val bounds: Bounds = Bounds(0, 0, 0, 0),
    val path: List<Int> = emptyList(),
    val children: List<ScreenNode> = emptyList(),
) {
    /** What the node says: its text, else its description, else its hint. */
    val label: String? get() = listOf(text, desc, hint).firstOrNull { !it.isNullOrBlank() }
    val isLeaf: Boolean get() = children.isEmpty()

    fun walk(): Sequence<ScreenNode> = sequence {
        yield(this@ScreenNode)
        children.forEach { yieldAll(it.walk()) }
    }

    fun ref() = NodeRef(path, viewId, label, cls, bounds)

    /** A copy with every node's [path] filled in. */
    fun withPaths(at: List<Int> = emptyList()): ScreenNode =
        copy(path = at, children = children.mapIndexed { i, c -> c.withPaths(at + i) })
}

/** A whole screen: the app's package, its window title, and the tree. */
data class Screen(val packageName: String, val title: String, val root: ScreenNode)

/** How to find a node again later, when the live tree may have changed. */
data class NodeRef(val path: List<Int>, val viewId: String?, val label: String?, val cls: String, val bounds: Bounds)
