package io.github.secnewt.reader.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class MergeTest {
    private fun p(t: String) = Block.Paragraph(listOf(Span.Plain(t)), true)
    private fun ps(vararg t: String) = t.map(::p)

    @Test fun bodyLoadedLaterGoesUnderTheHeader() = assertEquals(
        ps("From Sam Example", "Weekly garden news", "Planting starts Saturday", "Trash", "1 message"),
        Merge.placeNew(
            ps("From Sam Example", "Trash", "1 message"),
            ps("From Sam Example", "Weekly garden news", "Planting starts Saturday", "Trash", "1 message"),
        ),
    )

    @Test fun feedWithNoOverlapGoesBeforeTheBottomBar() =
        assertEquals(ps("Top", "post 1", "post 2", "Nav"), Merge.placeNew(ps("Top", "post 1", "Nav"), ps("Top", "post 2", "Nav")))

    @Test fun nothingKnownAppendsAtTheEnd() =
        assertEquals(ps("a", "b", "c"), Merge.placeNew(ps("a"), ps("b", "c")))

    @Test fun newBeforeTheFirstKnownGoesBeforeIt() =
        assertEquals(ps("a", "x", "b"), Merge.placeNew(ps("a", "b"), ps("x", "b")))

    @Test fun repeatsInsideFreshCountOnce() =
        assertEquals(ps("a", "c"), Merge.placeNew(ps("a"), ps("c", "c")))

    @Test fun nothingNew() = assertEquals(ps("a", "b"), Merge.placeNew(ps("a", "b"), ps("a")))

    @Test fun sameTextDifferentTypeIsNew() =
        assertEquals(2, Merge.placeNew(ps("Causes"), listOf(Block.Heading("Causes", true))).size)
}
