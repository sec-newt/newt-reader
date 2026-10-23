package io.github.secnewt.reader.reader

import io.github.secnewt.reader.engine.Block
import io.github.secnewt.reader.engine.TapKind
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderControllerTest {
    private fun TestScope.controller(port: FakePort, onClosed: () -> Unit = {}) = ReaderController(port, this, onClosed)
    private fun ReaderController.texts() = state.value.blocks.map { it.text }
    private fun ReaderController.button(label: String) =
        state.value.blocks.filterIsInstance<Block.Button>().single { it.text == label }.control

    @Test fun opensAtWhatWasOnScreen() = runTest {
        val c = controller(FakePort(page("Article", "a", "b", "c", "d", firstOnScreen = 2)))
        c.open(); advanceUntilIdle()
        with(c.state.value) {
            assertTrue(open); assertFalse(busy)
            assertEquals("Article", title)
            assertEquals(listOf("a", "b", "c", "d"), blocks.map { it.text })
            assertEquals(2, jumpTo)
        }
        c.jumpDone()
        assertNull(c.state.value.jumpTo)
    }

    @Test fun nothingSharedSaysSoThenCloses() = runTest {
        var closed = false
        val port = FakePort(null)
        val c = controller(port) { closed = true }
        c.open()
        testScheduler.advanceTimeBy(100)
        assertEquals(ReaderController.NOTHING_SHARED, c.state.value.message)
        advanceUntilIdle()
        assertFalse(c.state.value.open)
        assertTrue(closed)
    }

    @Test fun followTapStaysOpenAndRereads() = runTest {
        val port = FakePort(page("One", "intro", "[Next]"), page("Two", "second page"))
        val c = controller(port)
        c.open(); advanceUntilIdle()
        val next = c.button("Next")
        assertEquals(TapKind.FOLLOW, next.kind)
        c.tap(next.target, next.kind); advanceUntilIdle()
        assertEquals(listOf(next.target), port.taps)
        assertTrue(c.state.value.open)
        assertEquals(listOf("second page"), c.texts())
        assertEquals(0, c.state.value.jumpTo)
    }

    @Test fun followThatChangesNothingStaysInPlace() = runTest {
        val port = FakePort(page("One", "intro", "[Show more]"))
        val c = controller(port)
        c.open(); advanceUntilIdle(); c.jumpDone()
        val more = c.button("Show more")
        c.tap(more.target, more.kind); advanceUntilIdle()
        assertNull(c.state.value.jumpTo)
    }

    @Test fun closeFirstTapClosesThenTaps() = runTest {
        var closed = false
        val port = FakePort(page("Chat", "hello", "[Send]"))
        val c = controller(port) { closed = true }
        c.open(); advanceUntilIdle()
        val send = c.button("Send")
        assertEquals(TapKind.CLOSE_FIRST, send.kind)
        c.tap(send.target, send.kind)
        assertFalse(c.state.value.open)
        assertTrue(closed)
        advanceUntilIdle()
        assertEquals(listOf(send.target), port.taps)
    }

    @Test fun failedTapSaysItChanged() = runTest {
        val port = FakePort(page("One", "intro", "[Next]")).apply { tapWorks = false }
        val c = controller(port)
        c.open(); advanceUntilIdle()
        val next = c.button("Next")
        c.tap(next.target, next.kind); advanceUntilIdle()
        assertEquals(ReaderController.CHANGED, c.state.value.message)
        assertTrue(c.state.value.open)
    }

    @Test fun backAfterFollowGoesBackInTheApp() = runTest {
        val port = FakePort(page("One", "intro", "[Next]"), page("Two", "second"), page("One", "intro", "[Next]"))
        val c = controller(port)
        c.open(); advanceUntilIdle()
        c.button("Next").let { c.tap(it.target, it.kind) }; advanceUntilIdle()
        c.back(); advanceUntilIdle()
        assertEquals(1, port.backs)
        assertEquals("intro", c.texts().first())
        c.back(); advanceUntilIdle()
        assertEquals(1, port.backs)
        assertFalse(c.state.value.open)
    }

    @Test fun backOnFirstPageCloses() = runTest {
        val port = FakePort(page("One", "intro"))
        val c = controller(port)
        c.open(); advanceUntilIdle()
        c.back(); advanceUntilIdle()
        assertEquals(0, port.backs)
        assertFalse(c.state.value.open)
    }

    @Test fun nearEndAppendsOnlyNewBlocks() = runTest {
        val port = FakePort(page("Feed", "a", "b"), page("Feed", "b", "c"))
        val c = controller(port)
        c.open(); advanceUntilIdle()
        c.nearEnd(); advanceUntilIdle()
        assertEquals(1, port.scrolls)
        assertEquals(listOf("a", "b", "c"), c.texts())
        assertEquals(Footer.NONE, c.state.value.footer)
    }

    @Test fun loadMoreFallbackThenEnd() = runTest {
        val port = FakePort(page("Feed", "a"))
        val c = controller(port)
        c.open(); advanceUntilIdle()
        c.nearEnd(); advanceUntilIdle()
        assertEquals(Footer.LOAD_MORE, c.state.value.footer)
        c.nearEnd(); advanceUntilIdle()
        assertEquals(1, port.scrolls)  // nearEnd does nothing while the button is showing
        c.loadMore(); advanceUntilIdle()
        assertEquals(Footer.END, c.state.value.footer)
    }

    @Test fun closeResets() = runTest {
        val c = controller(FakePort(page("One", "intro")))
        c.open(); advanceUntilIdle()
        c.close()
        assertEquals(ReaderState(), c.state.value)
    }
}
