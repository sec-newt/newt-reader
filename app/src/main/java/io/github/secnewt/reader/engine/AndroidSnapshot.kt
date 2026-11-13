package io.github.secnewt.reader.engine

import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo

/** The one place the engine touches Android: copies a live accessibility tree into ScreenNodes. */
object AndroidSnapshot {
    /** Huge pages are cut off here so a runaway tree can't stall Reader (The Verge was 1,112). */
    private const val MAX_NODES = 5_000

    /** Only long web text gets laid out (a PDF page's text layer), and only a few per read: each one asks the app. */
    private const val LAYOUT_MIN_CHARS = 120
    private const val MAX_LAYOUTS = 10

    fun capture(root: AccessibilityNodeInfo, title: String): Screen {
        var count = 0
        var layouts = 0
        fun copy(n: AccessibilityNodeInfo, path: List<Int>, inWeb: Boolean): ScreenNode {
            count++
            val r = Rect().also(n::getBoundsInScreen)
            val cls = n.className?.toString().orEmpty()
            val web = inWeb || cls.endsWith("WebView")
            val kids = if (count > MAX_NODES) emptyList()
            else (0 until n.childCount).mapNotNull { i -> n.getChild(i)?.let { copy(it, path + i, web) } }
            val text = n.text?.toString()
            val paragraphs = if (web && kids.isEmpty() && text != null && text.length >= LAYOUT_MIN_CHARS && layouts < MAX_LAYOUTS) {
                layouts++
                layOut(n, text)
            } else null
            return ScreenNode(
                cls = cls,
                text = text,
                desc = n.contentDescription?.toString(),
                hint = n.hintText?.toString(),
                viewId = n.viewIdResourceName,
                clickable = n.isClickable,
                scrollable = n.isScrollable,
                editable = n.isEditable,
                password = n.isPassword,
                heading = isHeading(n),
                visible = n.isVisibleToUser,
                bounds = Bounds(r.left, r.top, r.right, r.bottom),
                paragraphs = paragraphs,
                path = path,
                children = kids,
            )
        }
        return Screen(root.packageName?.toString().orEmpty(), title, copy(root, emptyList(), inWeb = false))
    }

    /** Asks the app where each letter of [text] is, then lets [TextLayout] rebuild its lines; null if it can't or needn't. */
    private fun layOut(n: AccessibilityNodeInfo, text: String): List<String>? {
        val key = AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY
        if (key !in n.availableExtraData) return null
        val length = minOf(text.length, AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_MAX_LENGTH)
        val args = Bundle().apply {
            putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX, 0)
            putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH, length)
        }
        if (!n.refreshWithExtraData(key, args)) return null
        val rects = n.extras.getParcelableArray(key, RectF::class.java) ?: return null
        val boxes = rects.map { it?.let { r -> Bounds(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt()) } }
        return TextLayout.paragraphs(text.substring(0, length), boxes)
    }

    /** Older WebView builds report headings only through the collection item info. */
    @Suppress("DEPRECATION")
    private fun isHeading(n: AccessibilityNodeInfo) = n.isHeading || n.collectionItemInfo?.isHeading == true

    /** Follows [path] (child indexes) from [root] in the live tree. */
    fun liveAt(root: AccessibilityNodeInfo, path: List<Int>): AccessibilityNodeInfo? =
        path.fold(root as AccessibilityNodeInfo?) { n, i -> n?.getChild(i) }
}
