package io.github.secnewt.reader.speech

import io.github.secnewt.reader.engine.Block
import io.github.secnewt.reader.reader.Footer
import io.github.secnewt.reader.reader.ReaderState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class Playback { IDLE, STARTING, PLAYING, PAUSED }

/** What the player shows: whether it's reading, which block (index into Reader's blocks), and any problem to report. */
data class ListenState(
    val playback: Playback = Playback.IDLE,
    val current: Int? = null,
    val message: String? = null,
)

/**
 * Read aloud: speaks Reader's blocks one at a time through [newEngine]'s engine. The engine connects
 * on the first Listen and disconnects when Reader closes. It stops by itself when the block it's
 * reading disappears (a new page). At the end it calls [wantMore] once, and keeps going if more loads;
 * [wantMore] returns false if no load started, and then it finishes.
 * Call everything on the main thread.
 */
class ReadAloud(
    private val newEngine: () -> SpeechPort,
    private val reader: StateFlow<ReaderState>,
    scope: CoroutineScope,
    private val wantMore: () -> Boolean = { false },
) {
    private val _state = MutableStateFlow(ListenState())
    val state: StateFlow<ListenState> = _state.asStateFlow()

    private var engine: SpeechPort? = null
    private var ready = false

    /** The block being read and its sentences; [piece] is the one being spoken now. */
    private var block: Block? = null
    private var pieces: List<String> = emptyList()
    private var piece = 0

    /** Bumped whenever speech is stopped, so callbacks for what was dropped are ignored. */
    private var round = 0
    private var errorsInARow = 0
    private var askedForMore = false
    private var waitingForMore = false

    private val blocks get() = reader.value.blocks

    init {
        scope.launch { reader.collect(::onReader) }
    }

    /** Starts reading at the first readable block at or after [from]. */
    fun start(from: Int) {
        val index = Speakable.next(blocks, from)
        if (index == null) {
            reset(message = NOTHING_TO_READ)
            return
        }
        silence()
        block = blocks[index]  // tracked while the engine connects, so text loading above it can't shift it
        _state.value = ListenState(Playback.STARTING, current = index)
        val e = engine
        if (e != null && ready) return play(index)
        if (e != null) return  // still connecting; play when it's ready
        newEngine().also { engine = it }.apply {
            listener = Callbacks()
            start { ok -> onConnected(this, ok) }
        }
    }

    fun pause() {
        if (_state.value.playback != Playback.PLAYING) return
        silence()
        waitingForMore = false
        _state.value = _state.value.copy(playback = Playback.PAUSED)
    }

    fun resume() {
        if (_state.value.playback != Playback.PAUSED) return
        _state.value = _state.value.copy(playback = Playback.PLAYING)
        // Paused after the block's last sentence (waiting for more): move on rather than speak nothing.
        if (piece >= pieces.size) advance() else speakFrom(piece)
    }

    fun next() {
        val current = _state.value.current ?: return
        val index = Speakable.next(blocks, current + 1) ?: return finish()
        silence()
        play(index)
    }

    /** The block before; at the first one, reads it again. */
    fun back() {
        val current = _state.value.current ?: return
        silence()
        play(Speakable.previous(blocks, current) ?: current)
    }

    fun stop() = reset()

    /** Stops and disconnects from the engine. */
    fun shutdown() {
        reset()
        engine?.shutdown()
        engine = null
        ready = false
    }

    private fun onConnected(e: SpeechPort, result: SpeechPort.Ready) {
        if (e !== engine) return  // shut down while connecting
        if (result != SpeechPort.Ready.YES) {
            shutdown()
            _state.value = ListenState(
                message = when (result) {
                    SpeechPort.Ready.ONLINE_ONLY -> ONLINE_VOICE
                    SpeechPort.Ready.UNKNOWN_VOICE -> UNKNOWN_VOICE
                    else -> NO_ENGINE
                },
            )
            return
        }
        ready = true
        val s = _state.value
        if (s.playback == Playback.STARTING && s.current != null) play(s.current)
    }

    private fun play(index: Int) {
        val b = blocks.getOrNull(index) ?: return finish()
        val text = Speakable.text(b) ?: return finish()
        block = b
        pieces = Speakable.pieces(text, engine?.maxInput ?: DEFAULT_MAX)
        askedForMore = false
        waitingForMore = false
        _state.value = ListenState(Playback.PLAYING, current = index)
        speakFrom(0)
    }

    private fun speakFrom(first: Int) {
        val e = engine ?: return
        piece = first
        round++
        for (i in first until pieces.size) e.speak("$round/$i", pieces[i], flush = i == first)
    }

    private fun onDone(id: String) {
        val i = pieceIn(id) ?: return
        errorsInARow = 0
        piece = i + 1
        if (piece >= pieces.size) advance()
    }

    private fun onError(id: String) {
        pieceIn(id) ?: return
        silence()
        if (++errorsInARow >= 2) return reset(message = ENGINE_FAILED)
        advance()
    }

    /** The piece number in [id], if it's from the current round while playing. */
    private fun pieceIn(id: String): Int? {
        if (_state.value.playback != Playback.PLAYING) return null
        val (r, i) = id.split('/').mapNotNull(String::toIntOrNull).takeIf { it.size == 2 } ?: return null
        return i.takeIf { r == round }
    }

    /** After a block: the next readable one, or wait for more to load, or finish. */
    private fun advance() {
        val current = _state.value.current ?: return
        Speakable.next(blocks, current + 1)?.let { return play(it) }
        when (reader.value.footer) {
            Footer.LOADING -> waitingForMore = true
            Footer.NONE -> if (askedForMore) finish() else {
                askedForMore = true
                waitingForMore = true
                if (!wantMore()) finish()
            }
            Footer.LOAD_MORE, Footer.END -> finish()
        }
    }

    private fun onReader(s: ReaderState) {
        if (!s.open) return shutdown()
        val current = _state.value.current ?: return
        val b = block ?: return
        // Find the block being read in the new list: same place, or the nearest equal block (text loaded above it).
        val index = if (s.blocks.getOrNull(current) == b) current
        else s.blocks.indices.filter { s.blocks[it] == b }.minByOrNull { kotlin.math.abs(it - current) }
        if (index == null) return reset()
        if (index != current) _state.value = _state.value.copy(current = index)
        if (waitingForMore) {
            waitingForMore = false
            advance()
        }
    }

    private fun finish() = reset()

    private fun reset(message: String? = null) {
        silence()
        block = null
        pieces = emptyList()
        piece = 0
        errorsInARow = 0
        waitingForMore = false
        askedForMore = false
        _state.value = ListenState(message = message)
    }

    private fun silence() {
        round++
        engine?.stop()
    }

    private inner class Callbacks : SpeechPort.Listener {
        override fun onDone(id: String) = this@ReadAloud.onDone(id)
        override fun onError(id: String) = this@ReadAloud.onError(id)
    }

    companion object {
        const val NOTHING_TO_READ = "There's no text to read here"
        const val NO_ENGINE = "No text-to-speech engine is ready. Pick one in Speech settings."
        const val ENGINE_FAILED = "The speech engine stopped working"
        const val UNKNOWN_VOICE = "Your speech engine won't say whether its voice works offline, so Reader won't use it. Pick another engine in Speech settings."
        const val ONLINE_VOICE = "Your speech voice needs the internet. Pick a voice that works offline in Speech settings."
        private const val DEFAULT_MAX = 4000
    }
}
