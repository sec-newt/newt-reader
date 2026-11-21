package io.github.secnewt.reader.speech

import io.github.secnewt.reader.engine.Block
import io.github.secnewt.reader.engine.Bounds
import io.github.secnewt.reader.engine.Control
import io.github.secnewt.reader.engine.NodeRef
import io.github.secnewt.reader.engine.Span
import io.github.secnewt.reader.engine.TapKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeakableTest {
    private val ref = NodeRef(emptyList(), null, null, "x", Bounds(0, 0, 0, 0))

    @Test fun textBlocksAreReadAndControlsAreSkipped() {
        assertEquals("Causes", Speakable.text(Block.Heading("Causes", true)))
        assertEquals("Read the article.", Speakable.text(Block.Paragraph(listOf(Span.Plain("Read the "), Span.Link("article", ref), Span.Plain(".")), true)))
        assertEquals("Today: sunny.", Speakable.text(Block.ListItem("Today: sunny.", ref, true)))
        assertEquals("Picture: An eye chart", Speakable.text(Block.Image("An eye chart", true)))
        assertNull(Speakable.text(Block.Image(null, true)))
        assertNull(Speakable.text(Block.Image("  ", true)))
        assertNull(Speakable.text(Block.Button(Control("Send", ref, TapKind.CLOSE_FIRST), true)))
        assertNull(Speakable.text(Block.ControlRow(listOf(Control("Like", ref, TapKind.FOLLOW)), true)))
        assertNull(Speakable.text(Block.Field("Search", ref, false, true)))
        assertNull(Speakable.text(Block.Paragraph(listOf(Span.Plain("   ")), true)))
    }

    @Test fun piecesAreSentences() {
        assertEquals(
            listOf("It rained.", "Then it stopped!", "Did it?", "Yes"),
            Speakable.pieces("It rained. Then it stopped!  Did it? Yes", max = 4000),
        )
    }

    @Test fun shortTextIsOnePiece() = assertEquals(listOf("Hello there"), Speakable.pieces("  Hello there ", max = 4000))

    @Test fun aSentenceLongerThanTheEngineAllowsIsSplitAtSpaces() {
        val long = (1..50).joinToString(" ") { "word$it" }
        val pieces = Speakable.pieces(long, max = 40)
        assertTrue(pieces.all { it.length <= 40 })
        assertEquals(long, pieces.joinToString(" "))
    }

    @Test fun aWordLongerThanTheEngineAllowsIsCut() {
        val pieces = Speakable.pieces("a".repeat(25), max = 10)
        assertEquals(listOf(10, 10, 5), pieces.map { it.length })
    }

    @Test fun nextAndPreviousSkipWhatIsNotRead() {
        val blocks = listOf(
            Block.Heading("Title", true),
            Block.Button(Control("Send", ref, TapKind.CLOSE_FIRST), true),
            Block.Image(null, true),
            Block.Paragraph(listOf(Span.Plain("Body")), true),
        )
        assertEquals(0, Speakable.next(blocks, 0))
        assertEquals(3, Speakable.next(blocks, 1))
        assertNull(Speakable.next(blocks, 4))
        assertEquals(0, Speakable.previous(blocks, 3))
        assertNull(Speakable.previous(blocks, 0))
    }
}
