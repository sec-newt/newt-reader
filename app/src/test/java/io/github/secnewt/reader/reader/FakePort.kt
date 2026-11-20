package io.github.secnewt.reader.reader

import io.github.secnewt.reader.engine.NodeRef
import io.github.secnewt.reader.engine.Screen
import io.github.secnewt.reader.engine.ScreenNode

/** Hands out [screens] in order; the last one repeats. Records what the controller did. */
class FakePort(vararg screens: Screen?) : ScreenPort {
    private val queue = ArrayDeque(screens.toList())
    val taps = mutableListOf<NodeRef>()
    var tapWorks = true
    var scrollWorks = true
    var scrolls = 0
    var backs = 0

    override suspend fun read(): Screen? = if (queue.size > 1) queue.removeFirst() else queue.firstOrNull()
    override suspend fun tap(target: NodeRef): Boolean { taps += target; return tapWorks }
    override suspend fun scrollForward(): Boolean { scrolls++; return scrollWorks }
    override suspend fun back() { backs++ }
}

/** A non-web screen: each line is a paragraph, "[Label]" is a button. Lines before [firstOnScreen] are off-screen. */
fun page(title: String, vararg lines: String, firstOnScreen: Int = 0): Screen =
    Screen("app", title, ScreenNode("root", children = lines.mapIndexed { i, t ->
        if (t.startsWith("[")) ScreenNode("android.widget.Button", text = t.trim('[', ']'), clickable = true, visible = i >= firstOnScreen)
        else ScreenNode("android.widget.TextView", text = t, visible = i >= firstOnScreen)
    }).withPaths())
