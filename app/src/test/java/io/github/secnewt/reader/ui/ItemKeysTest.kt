package io.github.secnewt.reader.ui

import io.github.secnewt.reader.engine.Block
import io.github.secnewt.reader.engine.Span
import org.junit.Assert.assertEquals
import org.junit.Test

class ItemKeysTest {
    private fun p(t: String) = Block.Paragraph(listOf(Span.Plain(t)), true)

    @Test fun repeatedBlocksGetDifferentKeys() {
        val keys = itemKeys(listOf(p("Share"), p("post"), p("Share")))
        assertEquals(3, keys.toSet().size)
    }

    @Test fun aBlocksKeyDoesNotChangeWhenTextIsInsertedAfterIt() {
        val before = itemKeys(listOf(p("header"), p("Trash")))
        val after = itemKeys(listOf(p("header"), p("body"), p("Trash")))
        assertEquals(before[1], after[2])
    }
}
