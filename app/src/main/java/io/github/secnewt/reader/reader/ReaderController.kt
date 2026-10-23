package io.github.secnewt.reader.reader

import io.github.secnewt.reader.engine.Block
import io.github.secnewt.reader.engine.Merge
import io.github.secnewt.reader.engine.NodeRef
import io.github.secnewt.reader.engine.Screen
import io.github.secnewt.reader.engine.Stitcher
import io.github.secnewt.reader.engine.TapKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Reader's behaviour, with the phone behind [port]. One thing runs at a time: a new action
 * cancels the one before. [onClosed] runs every time Reader closes.
 */
class ReaderController(
    private val port: ScreenPort,
    private val scope: CoroutineScope,
    private val onClosed: () -> Unit = {},
) {
    private val _state = MutableStateFlow(ReaderState())
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    /** How many links deep we've followed; Back at 0 closes Reader. */
    private var depth = 0
    private var emptyLoads = 0
    private var job: Job? = null

    fun open() = run {
        depth = 0
        emptyLoads = 0
        _state.value = ReaderState(open = true, busy = true)
        var blocks = port.read()?.let(::stitched)
        if (blocks.isNullOrEmpty()) blocks = port.read()?.let(::stitched)
        val screen = lastScreen
        if (blocks.isNullOrEmpty() || screen == null) return@run nothingShared()
        show(screen, blocks, jumpTo = blocks.indexOfFirst { it.onScreen }.coerceAtLeast(0))
    }

    fun close() {
        job?.cancel()
        _state.value = ReaderState()
        onClosed()
    }

    fun tap(target: NodeRef, kind: TapKind) {
        if (kind == TapKind.CLOSE_FIRST) {
            close()
            scope.launch {
                delay(CLOSE_BEFORE_TAP_MS)  // let the overlay go so the app gets focus (keyboard, playback)
                port.tap(target)
            }
            return
        }
        run {
            _state.update { it.copy(busy = true, message = null) }
            val worked = port.tap(target)
            if (worked) depth++
            reread(message = if (worked) null else CHANGED)
        }
    }

    fun back() {
        if (depth == 0) return close()
        run {
            _state.update { it.copy(busy = true, message = null) }
            port.back()
            depth--
            reread(message = null)
        }
    }

    /** The reading view scrolled near its end: load more, unless a load or the fallback button is already showing. */
    fun nearEnd() {
        val s = _state.value
        if (s.open && !s.busy && s.footer == Footer.NONE) loadMore()
    }

    fun loadMore() = run {
        _state.update { it.copy(footer = Footer.LOADING) }
        val fresh = if (port.scrollForward()) port.read()?.let(::stitched).orEmpty() else emptyList()
        val added = Merge.appendNew(_state.value.blocks, fresh)
        if (added.isNotEmpty()) {
            emptyLoads = 0
            _state.update { it.copy(blocks = it.blocks + added, footer = Footer.NONE) }
        } else {
            emptyLoads++
            _state.update { it.copy(footer = if (emptyLoads >= 2) Footer.END else Footer.LOAD_MORE) }
        }
    }

    fun jumpDone() = _state.update { it.copy(jumpTo = null) }

    private var lastScreen: Screen? = null

    private fun stitched(screen: Screen): List<Block> {
        lastScreen = screen
        return Stitcher.stitch(screen)
    }

    private suspend fun reread(message: String?) {
        emptyLoads = 0
        val before = _state.value.blocks.firstOrNull()?.key
        val blocks = port.read()?.let(::stitched)
        val screen = lastScreen
        if (blocks.isNullOrEmpty() || screen == null) return nothingShared()
        // Same first block = the page didn't change (e.g. "Show more" expanded in place): keep the reader's place.
        show(screen, blocks, jumpTo = if (blocks.first().key == before) null else 0, message = message)
    }

    private suspend fun nothingShared() {
        _state.value = ReaderState(open = true, message = NOTHING_SHARED)
        delay(NOTHING_SHARED_MS)
        close()
    }

    private fun show(screen: Screen, blocks: List<Block>, jumpTo: Int?, message: String? = null) {
        _state.value = ReaderState(open = true, title = screen.title, blocks = blocks, jumpTo = jumpTo, message = message)
    }

    private fun run(action: suspend () -> Unit) {
        job?.cancel()
        job = scope.launch { action() }
    }

    companion object {
        const val NOTHING_SHARED = "This app isn't sharing its text"
        const val CHANGED = "That changed — here's the screen now"
        private const val NOTHING_SHARED_MS = 2_000L
        private const val CLOSE_BEFORE_TAP_MS = 150L
    }
}
