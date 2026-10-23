package io.github.secnewt.reader.reader

import io.github.secnewt.reader.engine.Block
import io.github.secnewt.reader.engine.NodeRef
import io.github.secnewt.reader.engine.Span
import io.github.secnewt.reader.engine.TapKind

/** What's at the bottom of the reading view. */
enum class Footer { NONE, LOADING, LOAD_MORE, END }

data class ReaderState(
    val open: Boolean = false,
    val title: String = "",
    val blocks: List<Block> = emptyList(),
    /** Scroll here once (then call jumpDone): the first on-screen block when opening, 0 after following a link. */
    val jumpTo: Int? = null,
    /** Shows the slim "Loading…" bar. */
    val busy: Boolean = false,
    val footer: Footer = Footer.NONE,
    val message: String? = null,
)

/** The tappable thing labelled [label], and what tapping it does. Used by the emulator test. */
fun ReaderState.findTarget(label: String): Pair<NodeRef, TapKind>? {
    for (b in blocks) {
        when (b) {
            is Block.Paragraph -> b.spans.filterIsInstance<Span.Link>().firstOrNull { it.text == label }?.let { return it.target to TapKind.FOLLOW }
            is Block.Button -> if (b.control.label == label) return b.control.target to b.control.kind
            is Block.ControlRow -> b.controls.firstOrNull { it.label == label }?.let { return it.target to it.kind }
            is Block.ListItem -> if (b.text == label) return b.target to TapKind.FOLLOW
            is Block.Field -> if (b.label == label) return b.target to TapKind.CLOSE_FIRST
            is Block.Heading -> if (b.text == label && b.target != null) return b.target to TapKind.FOLLOW
            is Block.Image -> Unit
        }
    }
    return null
}
