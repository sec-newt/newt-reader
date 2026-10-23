package io.github.secnewt.reader.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class MergeTest {
    private fun p(t: String) = Block.Paragraph(listOf(Span.Plain(t)), true)

    @Test fun onlyNewBlocksInOrder() =
        assertEquals(listOf(p("c"), p("d")), Merge.appendNew(listOf(p("a"), p("b")), listOf(p("b"), p("c"), p("d"))))

    @Test fun repeatsInsideFreshCountOnce() =
        assertEquals(listOf(p("c")), Merge.appendNew(listOf(p("a")), listOf(p("c"), p("c"))))

    @Test fun nothingNew() = assertEquals(emptyList<Block>(), Merge.appendNew(listOf(p("a")), listOf(p("a"))))

    @Test fun sameTextDifferentTypeIsNew() =
        assertEquals(1, Merge.appendNew(listOf(p("Causes")), listOf(Block.Heading("Causes", true))).size)
}
