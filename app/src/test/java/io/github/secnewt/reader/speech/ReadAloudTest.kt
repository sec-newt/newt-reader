package io.github.secnewt.reader.speech

import io.github.secnewt.reader.engine.Block
import io.github.secnewt.reader.engine.Bounds
import io.github.secnewt.reader.engine.Control
import io.github.secnewt.reader.engine.NodeRef
import io.github.secnewt.reader.engine.Span
import io.github.secnewt.reader.engine.TapKind
import io.github.secnewt.reader.reader.Footer
import io.github.secnewt.reader.reader.ReaderState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A pretend speech engine: records what it was asked to say; the test says when each piece finishes. */
class FakeSpeech(private val ready: SpeechPort.Ready = SpeechPort.Ready.YES, override val maxInput: Int = 4000) : SpeechPort {
    override var listener: SpeechPort.Listener? = null
    val said = mutableListOf<Pair<String, String>>()  // id to text, in order
    var flushes = 0
    var stops = 0
    var shutdowns = 0
    var started = 0
    private var onReady: ((SpeechPort.Ready) -> Unit)? = null

    override fun start(onReady: (SpeechPort.Ready) -> Unit) { started++; this.onReady = onReady }
    fun connect() = onReady!!(ready)
    override fun speak(id: String, text: String, flush: Boolean) { if (flush) flushes++; said += id to text }
    override fun stop() { stops++ }
    override fun shutdown() { shutdowns++ }

    val texts get() = said.map { it.second }
    fun finishLast() = listener!!.onDone(said.last().first)
    fun finish(text: String) = listener!!.onDone(said.last { it.second == text }.first)
    fun fail(text: String) = listener!!.onError(said.last { it.second == text }.first)
}

class ReadAloudTest {
    private val ref = NodeRef(emptyList(), null, null, "x", Bounds(0, 0, 0, 0))
    private fun p(text: String) = Block.Paragraph(listOf(Span.Plain(text)), true)
    private fun button(label: String) = Block.Button(Control(label, ref, TapKind.FOLLOW), true)

    private class Rig(val reader: MutableStateFlow<ReaderState>, val speech: FakeSpeech, val player: ReadAloud, var wantedMore: Int = 0)

    private fun TestScope.rig(vararg blocks: Block, speech: FakeSpeech = FakeSpeech(), loadStarts: Boolean = true): Rig {
        val reader = MutableStateFlow(ReaderState(open = true, blocks = blocks.toList()))
        lateinit var r: Rig
        val player = ReadAloud({ speech }, reader, backgroundScope, wantMore = { r.wantedMore++; loadStarts })
        r = Rig(reader, speech, player)
        runCurrent()
        return r
    }

    private fun Rig.startAndConnect(from: Int = 0) { player.start(from); speech.connect() }

    @Test fun readsFromTheGivenBlockSkippingControls() = runTest {
        val r = rig(p("One."), p("Two."), button("Send"), p("Three."))
        r.player.start(1)
        assertEquals(Playback.STARTING, r.player.state.value.playback)
        r.speech.connect()
        assertEquals(Playback.PLAYING, r.player.state.value.playback)
        assertEquals(1, r.player.state.value.current)
        assertEquals(listOf("Two."), r.speech.texts)
        r.speech.finishLast()
        assertEquals(3, r.player.state.value.current)
        assertEquals(listOf("Two.", "Three."), r.speech.texts)
    }

    @Test fun aBlockQueuesAllItsSentencesAtOnce() = runTest {
        val r = rig(p("First. Second. Third."), p("Next."))
        r.startAndConnect()
        assertEquals(listOf("First.", "Second.", "Third."), r.speech.texts)
        assertEquals(1, r.speech.flushes)
        r.speech.finish("First.")
        r.speech.finish("Second.")
        assertEquals(0, r.player.state.value.current)
        r.speech.finish("Third.")
        assertEquals(1, r.player.state.value.current)
    }

    @Test fun stopsAtTheEndWhenNothingMoreLoads() = runTest {
        val r = rig(p("Only."))
        r.reader.update { it.copy(footer = Footer.END) }
        runCurrent()
        r.startAndConnect()
        r.speech.finishLast()
        assertEquals(Playback.IDLE, r.player.state.value.playback)
        assertNull(r.player.state.value.current)
        assertEquals(0, r.wantedMore)
    }

    @Test fun atTheEndItAsksForMoreAndKeepsGoing() = runTest {
        val r = rig(p("One."))
        r.startAndConnect()
        r.speech.finishLast()
        assertEquals(1, r.wantedMore)
        assertEquals(Playback.PLAYING, r.player.state.value.playback)
        r.reader.update { it.copy(footer = Footer.LOADING) }
        runCurrent()
        r.reader.update { it.copy(footer = Footer.NONE, blocks = it.blocks + p("Two.")) }
        runCurrent()
        assertEquals(listOf("One.", "Two."), r.speech.texts)
        assertEquals(1, r.player.state.value.current)
    }

    @Test fun atTheEndItStopsIfAskingForMoreBroughtNothing() = runTest {
        val r = rig(p("One."))
        r.startAndConnect()
        r.speech.finishLast()
        r.reader.update { it.copy(footer = Footer.LOADING) }
        runCurrent()
        r.reader.update { it.copy(footer = Footer.LOAD_MORE) }
        runCurrent()
        assertEquals(Playback.IDLE, r.player.state.value.playback)
    }

    @Test fun pauseStopsTheEngineAndResumeRestartsTheSentence() = runTest {
        val r = rig(p("First. Second. Third."))
        r.startAndConnect()
        r.speech.finish("First.")
        r.player.pause()
        assertEquals(Playback.PAUSED, r.player.state.value.playback)
        assertEquals(1, r.speech.stops)
        r.speech.finish("Second.")  // a late callback from before the pause changes nothing
        r.player.resume()
        assertEquals(listOf("First.", "Second.", "Third.", "Second.", "Third."), r.speech.texts)
        assertEquals(Playback.PLAYING, r.player.state.value.playback)
    }

    @Test fun nextAndBackJumpBetweenReadableBlocks() = runTest {
        val r = rig(p("A."), button("Send"), p("B."), p("C."))
        r.startAndConnect()
        r.player.next()
        assertEquals(2, r.player.state.value.current)
        r.player.next()
        assertEquals(3, r.player.state.value.current)
        r.player.back()
        assertEquals(2, r.player.state.value.current)
        r.player.back()
        r.player.back()  // already at the first: reads it again
        assertEquals(0, r.player.state.value.current)
        assertEquals(listOf("A.", "B.", "C.", "B.", "A.", "A."), r.speech.texts)
    }

    @Test fun nextFromThePausedStateKeepsPlaying() = runTest {
        val r = rig(p("A."), p("B."))
        r.startAndConnect()
        r.player.pause()
        r.player.next()
        assertEquals(Playback.PLAYING, r.player.state.value.playback)
        assertEquals(1, r.player.state.value.current)
    }

    @Test fun nothingToReadSaysSo() = runTest {
        val r = rig(button("Send"))
        r.player.start(0)
        assertEquals(Playback.IDLE, r.player.state.value.playback)
        assertEquals(ReadAloud.NOTHING_TO_READ, r.player.state.value.message)
        assertEquals(0, r.speech.started)
    }

    @Test fun noEngineSaysSoAndLetsGo() = runTest {
        val r = rig(p("A."), speech = FakeSpeech(SpeechPort.Ready.NO_ENGINE))
        r.startAndConnect()
        assertEquals(Playback.IDLE, r.player.state.value.playback)
        assertEquals(ReadAloud.NO_ENGINE, r.player.state.value.message)
        assertEquals(1, r.speech.shutdowns)
    }

    @Test fun oneErrorSkipsTheBlockTwoInARowStop() = runTest {
        val r = rig(p("A."), p("B."), p("C."))
        r.startAndConnect()
        r.speech.fail("A.")
        assertEquals(1, r.player.state.value.current)
        r.speech.fail("B.")
        assertEquals(Playback.IDLE, r.player.state.value.playback)
        assertEquals(ReadAloud.ENGINE_FAILED, r.player.state.value.message)
    }

    @Test fun stopsWhenThePageChangesButNotWhenItChangesInPlace() = runTest {
        val r = rig(p("A."), p("B."))
        r.startAndConnect()
        r.speech.finishLast()
        // Text loaded above (email bodies): still reading B, now at index 2.
        r.reader.update { it.copy(blocks = listOf(p("New."), p("A."), p("B."))) }
        runCurrent()
        assertEquals(2, r.player.state.value.current)
        assertEquals(Playback.PLAYING, r.player.state.value.playback)
        // A different page: B is gone.
        r.reader.update { it.copy(blocks = listOf(p("Other page."))) }
        runCurrent()
        assertEquals(Playback.IDLE, r.player.state.value.playback)
        assertTrue(r.speech.stops > 0)
    }

    @Test fun closingReaderLetsTheEngineGo() = runTest {
        val r = rig(p("A."))
        r.startAndConnect()
        r.reader.value = ReaderState()
        runCurrent()
        assertEquals(Playback.IDLE, r.player.state.value.playback)
        assertEquals(1, r.speech.shutdowns)
        // Opening again and listening connects a fresh engine.
        r.reader.value = ReaderState(open = true, blocks = listOf(p("A.")))
        runCurrent()
        r.player.start(0)
        assertEquals(2, r.speech.started)
    }

    @Test fun theEngineConnectsOnlyOnceWhileReaderIsOpen() = runTest {
        val r = rig(p("A."), p("B."))
        r.startAndConnect()
        r.player.stop()
        r.player.start(1)
        assertEquals(1, r.speech.started)
        assertEquals(Playback.PLAYING, r.player.state.value.playback)
        assertNotNull(r.player.state.value.current)
    }

    @Test fun startingAgainClearsTheOldMessage() = runTest {
        val r = rig(button("Send"))
        r.player.start(0)
        assertEquals(ReadAloud.NOTHING_TO_READ, r.player.state.value.message)
        r.reader.update { it.copy(blocks = listOf(p("A."))) }
        runCurrent()
        r.startAndConnect()
        assertNull(r.player.state.value.message)
        assertFalse(r.speech.texts.isEmpty())
    }

    @Test fun anOnlineOnlyVoiceIsRefused() = runTest {
        val r = rig(p("A."), speech = FakeSpeech(SpeechPort.Ready.ONLINE_ONLY))
        r.startAndConnect()
        assertEquals(Playback.IDLE, r.player.state.value.playback)
        assertEquals(ReadAloud.ONLINE_VOICE, r.player.state.value.message)
        assertTrue(r.speech.texts.isEmpty())
    }

    @Test fun textLoadingAboveWhileConnectingKeepsTheChosenBlock() = runTest {
        val r = rig(p("A."), p("B."))
        r.player.start(1)
        r.reader.update { it.copy(blocks = listOf(p("New."), p("A."), p("B."))) }
        runCurrent()
        r.speech.connect()
        assertEquals(listOf("B."), r.speech.texts)
        assertEquals(2, r.player.state.value.current)
    }

    @Test fun aNewPageWhileConnectingCancels() = runTest {
        val r = rig(p("A."))
        r.player.start(0)
        r.reader.update { it.copy(blocks = listOf(p("Other page."))) }
        runCurrent()
        r.speech.connect()
        assertEquals(Playback.IDLE, r.player.state.value.playback)
        assertTrue(r.speech.texts.isEmpty())
    }

    @Test fun resumeWhileWaitingForMoreMovesOn() = runTest {
        val r = rig(p("One."))
        r.startAndConnect()
        r.speech.finishLast()  // end of the page: asked for more
        r.player.pause()
        r.reader.update { it.copy(blocks = it.blocks + p("Two.")) }
        runCurrent()
        r.player.resume()
        assertEquals(listOf("One.", "Two."), r.speech.texts)
        assertEquals(Playback.PLAYING, r.player.state.value.playback)
    }

    @Test fun ifNoLoadStartsItFinishes() = runTest {
        val r = rig(p("One."), loadStarts = false)
        r.startAndConnect()
        r.speech.finishLast()
        assertEquals(1, r.wantedMore)
        assertEquals(Playback.IDLE, r.player.state.value.playback)
    }
}
