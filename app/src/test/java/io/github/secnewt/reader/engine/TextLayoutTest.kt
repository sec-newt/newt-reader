package io.github.secnewt.reader.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextLayoutTest {
    /** Lays [lines] out like a PDF text layer: one string with nothing between lines, 20 px per letter, 40 px lines. */
    private class Page {
        val text = StringBuilder()
        val boxes = mutableListOf<Bounds?>()
        var y = 0

        fun line(s: String, x: Int = 0, height: Int = 40, gapAfter: Int = 8) = apply {
            s.forEachIndexed { i, c ->
                text.append(c)
                boxes += Bounds(x + i * 20, y, x + i * 20 + 20, y + height)
            }
            y += height + gapAfter
        }

        /** Two runs on one row with blank space between them (table columns). */
        fun row(a: String, b: String, bAt: Int) = apply {
            a.forEachIndexed { i, c -> text.append(c); boxes += Bounds(i * 20, y, i * 20 + 20, y + 40) }
            b.forEachIndexed { i, c -> text.append(c); boxes += Bounds(bAt + i * 20, y, bAt + i * 20 + 20, y + 40) }
            y += 48
        }

        fun paragraphs() = TextLayout.paragraphs(text.toString(), boxes)
    }

    private val full = "a".repeat(40)

    @Test fun gluedLinesOfProseJoinIntoOneParagraph() {
        val p = Page()
            .line("The quick brown fox jumps over the lazy")
            .line("dog and runs off into the wide green")
            .line("field.")
            .paragraphs()
        assertEquals(listOf("The quick brown fox jumps over the lazy dog and runs off into the wide green field."), p)
    }

    @Test fun shortLinesEndParagraphs() {
        val p = Page()
            .line("Welcome")
            .line("Morning class 9:30")
            .line("Evening class 6:00")
            .line(full)
            .paragraphs()
        assertEquals(listOf("Welcome", "Morning class 9:30", "Evening class 6:00", full), p)
    }

    @Test fun extraSpaceBetweenLinesStartsAParagraph() {
        val p = Page()
            .line(full, gapAfter = 60)
            .line(full)
            .line(full)
            .paragraphs()
        assertEquals(listOf(full, "$full $full"), p)
    }

    @Test fun tableColumnsGetASpace() {
        val p = Page()
            .line(full)
            .row("Attendance", "120", bAt = 300)
            .row("Visitors", "4", bAt = 300)
            .paragraphs()
        assertEquals(listOf(full, "Attendance 120", "Visitors 4"), p)
    }

    @Test fun hyphenAtLineEndJoinsTheWord() {
        val p = Page()
            .line("The meeting will discuss the new commu-")
            .line("nity garden plans for the spring and the")
            .line("summer.")
            .paragraphs()
        assertEquals(listOf("The meeting will discuss the new community garden plans for the spring and the summer."), p)
    }

    @Test fun ordinaryWrappedTextIsLeftAlone() {
        // A web paragraph wraps at spaces, so nothing is glued: no layout, the text stays as it is.
        val p = Page()
            .line("The quick brown fox jumps over the lazy ")
            .line("dog and runs off into the wide green ")
            .line("field.")
            .paragraphs()
        assertNull(p)
    }

    @Test fun lettersWithNoBoxStayInTheirLine() {
        val page = Page()
            .line("The quick brown fox jumps over the lazy")
            .line("dog and runs off into the wide green")
            .line("field.")
        page.boxes[2] = null
        assertEquals(listOf("The quick brown fox jumps over the lazy dog and runs off into the wide green field."), page.paragraphs())
    }
}
