package io.github.secnewt.reader.reader

import io.github.secnewt.reader.engine.Block
import io.github.secnewt.reader.engine.Bounds
import io.github.secnewt.reader.engine.Control
import io.github.secnewt.reader.engine.NodeRef
import io.github.secnewt.reader.engine.Span
import io.github.secnewt.reader.engine.TapKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderStateTest {
    private fun ref(n: Int) = NodeRef(listOf(n), null, null, "x", Bounds(0, 0, 0, 0))
    private val state = ReaderState(open = true, blocks = listOf(
        Block.Paragraph(listOf(Span.Plain("See the "), Span.Link("Next page", ref(1))), true),
        Block.Button(Control("Send", ref(2), TapKind.CLOSE_FIRST), true),
        Block.ControlRow(listOf(Control("Like", ref(3), TapKind.CLOSE_FIRST)), true),
        Block.ListItem("A forecast row that is long", ref(4), true),
        Block.Field("Search", ref(5), false, true),
        Block.Heading("Causes", true, ref(6)),
    ))

    @Test fun findsEveryKindOfTarget() {
        assertEquals(ref(1) to TapKind.FOLLOW, state.findTarget("Next page"))
        assertEquals(ref(2) to TapKind.CLOSE_FIRST, state.findTarget("Send"))
        assertEquals(ref(3) to TapKind.CLOSE_FIRST, state.findTarget("Like"))
        assertEquals(ref(4) to TapKind.FOLLOW, state.findTarget("A forecast row that is long"))
        assertEquals(ref(5) to TapKind.CLOSE_FIRST, state.findTarget("Search"))
        assertEquals(ref(6) to TapKind.FOLLOW, state.findTarget("Causes"))
        assertNull(state.findTarget("Nope"))
    }
}
