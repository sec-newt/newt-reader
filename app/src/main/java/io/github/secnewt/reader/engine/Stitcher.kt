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
        return addReadReplies(foldControlRows(dropRepeats(out)))
    }

    private fun visit(n: ScreenNode, inWeb: Boolean, out: MutableList<Block>) {
        val web = inWeb || n.cls.endsWith("WebView")
        when {
            n.editable || n.password -> out += Block.Field(fieldLabel(n), n.ref(), n.password, n.anyVisible())
            n.paragraphs != null -> n.paragraphs.forEach { out += Block.Paragraph(listOf(Span.Plain(it)), n.visible) }
            // A page drawn as a picture with its text laid over it (PDF viewers): the text, not an "Image" placeholder.
            n.children.any { it.paragraphs != null } ->
                n.children.filterNot { it.isLeaf && isImage(it) && describe(it) == null }.forEach { visit(it, web, out) }
            isHeadingToggle(n) -> out += Block.Heading(allText(n, web), n.anyVisible(), n.ref())
            isClickableUnit(n) -> clickable(n, web)?.let { out += it }
            n.heading && allText(n, web).isNotEmpty() -> out += Block.Heading(allText(n, web), n.anyVisible())
            web && isParagraph(n) -> out += Block.Paragraph(spans(n), n.anyVisible())
            n.isLeaf && isImage(n) -> if (worthShowing(n)) out += Block.Image(describe(n), n.visible)
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

    /**
     * A clickable node with nothing clickable inside it is one control. So is a labelled one whose
     * only clickable insides are unlabelled (X draws each action's icon as its own tappable piece).
     */
    private fun isClickableUnit(n: ScreenNode): Boolean {
        if (!n.clickable || n.cls.endsWith("WebView")) return false
        val inside = n.walk().drop(1)
        if (inside.any { it.editable }) return false
        if (inside.none { it.clickable }) return true
        val labelled = !n.text.isNullOrBlank() || !n.desc.isNullOrBlank()
        return labelled && inside.none { it.clickable && (!it.desc.isNullOrBlank() || allText(it, true).isNotEmpty()) }
    }

    private fun clickable(n: ScreenNode, web: Boolean): Block? {
        // A link around a big picture and nothing else opens the full picture: nothing to read there.
        val leaves = n.walk().filter { it.isLeaf }.toList()
        val picture = leaves.singleOrNull()?.takeIf { isImage(it) && isBig(it) }
        if (picture != null) return Block.Image(describe(picture), n.anyVisible(), n.ref())
        val text = controlLabel(n) ?: allText(n, web)
        if (text.isEmpty()) return null
        Actions.parse(text)?.let { return Block.Button(Control(text, n.ref(), it.kind, it), n.anyVisible()) }
        // A clickable heading (a collapsed section's toggle) stays a heading, and tapping it expands in place.
        if (n.walk().any { it.heading }) return Block.Heading(text, n.anyVisible(), n.ref())
        if (text.length >= SHORT) return Block.ListItem(text, n.ref(), n.anyVisible())
        // In web content, Chromium reports <button> as a Button class; anything else clickable is a link.
        val kind = if (web && !n.cls.endsWith("Button")) TapKind.FOLLOW else Classifier.forButton(text)
        return Block.Button(Control(text, n.ref(), kind), n.anyVisible())
    }

    /**
     * What a control says. The description wins when it says more than the text ("analytics" vs
     * "14370 views. View post analytics") or the text is only an id (X's time stamps link by post number).
     */
    private fun controlLabel(n: ScreenNode): String? {
        val text = n.text?.let(::clean)?.ifEmpty { null }
        val desc = n.desc?.let(::clean)?.ifEmpty { null }
        return when {
            text == null -> desc ?: n.hint?.let(::clean)?.ifEmpty { null }
            desc != null && (desc.contains(text, ignoreCase = true) || text.all { it.isDigit() }) -> desc
            else -> text
        }
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
                x.isLeaf -> words(x)?.let { raw += Span.Plain(nfkc(it)) }
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
        clean(n.walk().filter { it.isLeaf }.mapNotNull(::words).map(::nfkc).joinToString(if (web) "" else "\n"))

    /** A leaf's words. A run that is only a space still counts on the web: it's what keeps two links apart. */
    private fun words(n: ScreenNode): String? = n.text?.takeIf { it.isNotEmpty() && (it.isNotBlank() || it == " ") } ?: n.label

    private fun fieldLabel(n: ScreenNode): String =
        clean(listOfNotNull(n.hint, n.desc, if (n.password) null else n.text).firstOrNull { it.isNotBlank() } ?: "text field")

    private fun isImage(n: ScreenNode) = n.cls.endsWith("Image") || n.cls.endsWith("ImageView")

    /** Skip small unlabelled icons; keep described images and big pictures. */
    private fun worthShowing(n: ScreenNode) = describe(n) != null || isBig(n)

    /** At least 300 × 200 px. Off-screen, Android reports zero height, so only the width can tell. */
    private fun isBig(n: ScreenNode): Boolean {
        val h = n.bounds.bottom - n.bounds.top
        return n.bounds.right - n.bounds.left >= 300 && (h >= 200 || h == 0)
    }

    /** The image's description, unless it's only a file name (what browsers show when a picture has no alt text). */
    private fun describe(n: ScreenNode): String? = n.label?.let(::clean)?.takeIf { it.isNotEmpty() && !FILE_NAME.matches(it) }

    private val FILE_NAME = Regex("""(?i)(\d+px-)?\S+\.(jpe?g|png|gif|svg|webp|tiff?)|\d+px-\S+|\S*_\S*""")

    private fun ScreenNode.anyVisible() = walk().any { it.visible && it.isLeaf } || (isLeaf && visible)

    private fun dropRepeats(blocks: List<Block>): List<Block> =
        blocks.filterIndexed { i, b -> i == 0 || blocks[i - 1].key != b.key }

    /**
     * Reply opens the box for writing one; to read a post's replies you open the post, which its
     * time stamp links to. So a row with "47 Replies. Reply" gets a "47 replies" control in front
     * that opens the post (the nearest time stamp above it, back to the previous post's row).
     */
    private fun addReadReplies(blocks: List<Block>): List<Block> = blocks.mapIndexed { i, b ->
        if (b !is Block.ControlRow) return@mapIndexed b
        val count = b.controls.firstOrNull { it.action?.action == Action.REPLY }?.action?.count ?: return@mapIndexed b
        if (count <= 0) return@mapIndexed b
        val stamp = blocks.subList(0, i).asReversed()
            .takeWhile { prev -> !(prev is Block.ControlRow && prev.controls.any { it.action != null }) }
            .flatMap { prev -> when (prev) { is Block.Button -> listOf(prev.control); is Block.ControlRow -> prev.controls.asReversed(); else -> emptyList() } }
            .firstOrNull { it.kind == TapKind.FOLLOW && Actions.timeStamp.matches(it.label) } ?: return@mapIndexed b
        val label = if (count == 1L) "1 reply" else "$count replies"
        b.copy(controls = listOf(Control(label, stamp.target, TapKind.FOLLOW, ActionInfo(Action.READ_REPLIES, count))) + b.controls)
    }

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
