package io.github.secnewt.reader.engine

/**
 * Turns a screen's tree into reading blocks.
 *
 * Web pages (anything under a WebView) split text at every link and nest footnotes three
 * levels deep, so a paragraph is a node with a direct text child whose other children are all
 * inline (a link, or short text); its text is every leaf below it, with links kept as spans.
 * Outside web pages each piece of text is its own paragraph.
 */
object Stitcher {
    private const val SHORT = 25
    private const val INLINE_LINK_MAX = 200

    fun stitch(screen: Screen): List<Block> {
        val out = mutableListOf<Block>()
        visit(screen.root, inWeb = false, out)
        return foldControlRows(dropRepeats(out))
    }

    private fun visit(n: ScreenNode, inWeb: Boolean, out: MutableList<Block>) {
        val web = inWeb || n.cls.endsWith("WebView")
        when {
            n.editable || n.password -> out += Block.Field(fieldLabel(n), n.ref(), n.password, n.anyVisible())
            isHeadingToggle(n) -> out += Block.Heading(allText(n, web), n.anyVisible(), n.ref())
            isClickableUnit(n) -> clickable(n, web)?.let { out += it }
            n.heading && allText(n, web).isNotEmpty() -> out += Block.Heading(allText(n, web), n.anyVisible())
            web && isParagraph(n) -> out += Block.Paragraph(spans(n), n.anyVisible())
            n.isLeaf && isImage(n) -> if (worthShowing(n)) out += Block.Image(n.desc?.let(::clean)?.ifEmpty { null }, n.visible)
            n.isLeaf -> n.label?.let(::clean)?.takeIf { it.isNotEmpty() }?.let { out += Block.Paragraph(listOf(Span.Plain(it)), n.visible) }
            else -> n.children.forEach { visit(it, web, out) }
        }
    }

    /**
     * A clickable node holding a heading, where anything else clickable inside is unlabelled
     * (mobile Wikipedia's section toggle: the heading plus a bare ▸ button). Tapping it expands in place.
     */
    private fun isHeadingToggle(n: ScreenNode) =
        n.clickable && !n.cls.endsWith("WebView") && n.walk().any { it.heading } &&
            n.walk().drop(1).none { it.editable || (it.clickable && allText(it, true).isNotEmpty()) }

    /** A clickable node with nothing clickable inside it is one control. */
    private fun isClickableUnit(n: ScreenNode) =
        n.clickable && !n.cls.endsWith("WebView") && n.walk().drop(1).none { it.clickable || it.editable }

    private fun clickable(n: ScreenNode, web: Boolean): Block? {
        val text = n.label?.let(::clean)?.ifEmpty { null } ?: allText(n, web)
        if (text.isEmpty()) return null
        // A clickable heading (a collapsed section's toggle) stays a heading, and tapping it expands in place.
        if (n.walk().any { it.heading }) return Block.Heading(text, n.anyVisible(), n.ref())
        if (text.length >= SHORT) return Block.ListItem(text, n.ref(), n.anyVisible())
        // In web content, Chromium reports <button> as a Button class; anything else clickable is a link.
        val kind = if (web && !n.cls.endsWith("Button")) TapKind.FOLLOW else Classifier.forButton(text)
        return Block.Button(Control(text, n.ref(), kind), n.anyVisible())
    }

    private fun isParagraph(n: ScreenNode): Boolean =
        n.children.isNotEmpty() && !hasBlockGap(n) &&
            n.children.any { it.isLeaf && it.cls.endsWith("TextView") && !it.label.isNullOrBlank() } &&
            n.children.all { it.isLeaf || (it.clickable && allText(it, true).length < INLINE_LINK_MAX) || allText(it, true).length < SHORT }

    /**
     * Runs of one paragraph start on the line where the last one ended; separate blocks (an h1
     * then a p, both bare text under the page) have space between them. Only text runs count:
     * small inline boxes like footnote markers leave gaps between lines inside a paragraph.
     */
    private fun hasBlockGap(n: ScreenNode): Boolean =
        n.children.zipWithNext().any { (a, b) -> isTextRun(a) && isTextRun(b) && b.bounds.top > a.bounds.bottom }

    private fun isTextRun(n: ScreenNode) = n.isLeaf && n.cls.endsWith("TextView")

    private fun spans(n: ScreenNode): List<Span> {
        val raw = mutableListOf<Span>()
        fun go(x: ScreenNode) {
            when {
                x.clickable -> allText(x, true).ifEmpty { x.label?.let(::clean).orEmpty() }
                    .takeIf { it.isNotEmpty() }?.let { raw += Span.Link(it, x.ref()) }
                x.isLeaf -> x.label?.let { raw += Span.Plain(nfkc(it)) }
                else -> x.children.forEach(::go)
            }
        }
        n.children.forEach(::go)
        // Join neighbouring plain runs, collapse spaces, and trim the paragraph's two ends.
        val merged = mutableListOf<Span>()
        for (s in raw) {
            val last = merged.lastOrNull()
            if (s is Span.Plain && last is Span.Plain) merged[merged.lastIndex] = Span.Plain(last.text + s.text) else merged += s
        }
        return merged.mapIndexed { i, s ->
            if (s !is Span.Plain) return@mapIndexed s
            var t = s.text.replace(Regex("[ \\t\\u00A0]+"), " ")
            if (i == 0) t = t.trimStart()
            if (i == merged.lastIndex) t = t.trimEnd()
            Span.Plain(t)
        }.filter { it.text.isNotEmpty() }
    }

    /** Every leaf's words in order; joined with nothing on the web (runs carry their own spaces), new lines elsewhere. */
    private fun allText(n: ScreenNode, web: Boolean): String =
        clean(n.walk().filter { it.isLeaf }.mapNotNull { it.label }.map(::nfkc).joinToString(if (web) "" else "\n"))

    private fun fieldLabel(n: ScreenNode): String =
        clean(listOfNotNull(n.hint, n.desc, if (n.password) null else n.text).firstOrNull { it.isNotBlank() } ?: "text field")

    private fun isImage(n: ScreenNode) = n.cls.endsWith("Image") || n.cls.endsWith("ImageView")

    /** Skip small unlabelled icons; keep described images and big pictures. */
    private fun worthShowing(n: ScreenNode) =
        !n.desc.isNullOrBlank() || (n.bounds.right - n.bounds.left >= 300 && n.bounds.bottom - n.bounds.top >= 200)

    private fun ScreenNode.anyVisible() = walk().any { it.visible && it.isLeaf } || (isLeaf && visible)

    private fun dropRepeats(blocks: List<Block>): List<Block> =
        blocks.filterIndexed { i, b -> i == 0 || blocks[i - 1].key != b.key }

    private fun foldControlRows(blocks: List<Block>): List<Block> {
        val out = mutableListOf<Block>()
        var run = mutableListOf<Block.Button>()
        fun flush() {
            if (run.size >= 3) out += Block.ControlRow(run.map { it.control }, run.any { it.onScreen }) else out += run
            run = mutableListOf()
        }
        for (b in blocks) if (b is Block.Button) run += b else { flush(); out += b }
        flush()
        return out
    }
}
