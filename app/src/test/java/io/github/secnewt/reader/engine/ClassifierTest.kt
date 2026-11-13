package io.github.secnewt.reader.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class ClassifierTest {
    @Test fun followWords() {
        listOf("Show more", "Next", "See all", "Read more", "Page 3", "Older posts", "Expand", "prev")
            .forEach { assertEquals(it, TapKind.FOLLOW, Classifier.forButton(it)) }
    }

    @Test fun everythingElseClosesFirst() {
        listOf("Send", "Call", "Play", "Post", "Delete", "Comments 73", "Moreover", "Download PDF", "")
            .forEach { assertEquals(it, TapKind.CLOSE_FIRST, Classifier.forButton(it)) }
    }
}
