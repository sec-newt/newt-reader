package io.github.secnewt.reader.engine

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/** The one place the engine touches Android: copies a live accessibility tree into ScreenNodes. */
object AndroidSnapshot {
    /** Huge pages are cut off here so a runaway tree can't stall Reader (The Verge was 1,112). */
    private const val MAX_NODES = 5_000

    fun capture(root: AccessibilityNodeInfo, title: String): Screen {
        var count = 0
        fun copy(n: AccessibilityNodeInfo, path: List<Int>): ScreenNode {
            count++
            val r = Rect().also(n::getBoundsInScreen)
            val kids = if (count > MAX_NODES) emptyList()
            else (0 until n.childCount).mapNotNull { i -> n.getChild(i)?.let { copy(it, path + i) } }
            return ScreenNode(
                cls = n.className?.toString().orEmpty(),
                text = n.text?.toString(),
                desc = n.contentDescription?.toString(),
                hint = n.hintText?.toString(),
                viewId = n.viewIdResourceName,
                clickable = n.isClickable,
                scrollable = n.isScrollable,
                editable = n.isEditable,
                password = n.isPassword,
                heading = n.isHeading,
                visible = n.isVisibleToUser,
                bounds = Bounds(r.left, r.top, r.right, r.bottom),
                path = path,
                children = kids,
            )
        }
        return Screen(root.packageName?.toString().orEmpty(), title, copy(root, emptyList()))
    }

    /** Follows [path] (child indexes) from [root] in the live tree. */
    fun liveAt(root: AccessibilityNodeInfo, path: List<Int>): AccessibilityNodeInfo? =
        path.fold(root as AccessibilityNodeInfo?) { n, i -> n?.getChild(i) }
}
