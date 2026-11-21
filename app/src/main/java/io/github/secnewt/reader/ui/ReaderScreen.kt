package io.github.secnewt.reader.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.secnewt.reader.engine.Block
import io.github.secnewt.reader.engine.NodeRef
import io.github.secnewt.reader.engine.TapKind
import io.github.secnewt.reader.reader.Footer
import io.github.secnewt.reader.reader.ReaderController
import io.github.secnewt.reader.reader.ReaderPrefs
import io.github.secnewt.reader.reader.ReaderState
import io.github.secnewt.reader.speech.ListenState
import io.github.secnewt.reader.speech.Playback
import io.github.secnewt.reader.speech.ReadAloud
import io.github.secnewt.ui.NewtFonts
import io.github.secnewt.ui.NewtTheme
import io.github.secnewt.ui.neonOutline
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/** Connects the overlay to the controller and the saved text size. */
@Composable
fun ReaderRoute(controller: ReaderController, prefs: ReaderPrefs, readAloud: ReadAloud) {
    val state by controller.state.collectAsState()
    val listen by readAloud.state.collectAsState()
    var scale by remember { mutableFloatStateOf(prefs.textScale) }
    NewtTheme {
        ReaderScreen(
            state = state,
            textScale = scale,
            onTextScale = { scale = it; prefs.textScale = it },
            onTap = controller::tap,
            onClose = controller::close,
            onNearEnd = controller::nearEnd,
            onLoadMore = controller::loadMore,
            onJumpDone = controller::jumpDone,
            listen = listen,
            listenActions = remember(readAloud) {
                ListenActions(readAloud::start, readAloud::pause, readAloud::resume, readAloud::next, readAloud::back, readAloud::stop)
            },
        )
    }
}

/** What the read-aloud buttons do. [start] gets the first block on screen. */
class ListenActions(
    val start: (Int) -> Unit = {},
    val pause: () -> Unit = {},
    val resume: () -> Unit = {},
    val next: () -> Unit = {},
    val back: () -> Unit = {},
    val stop: () -> Unit = {},
)

@Composable
fun ReaderScreen(
    state: ReaderState,
    textScale: Float,
    onTextScale: (Float) -> Unit,
    onTap: (NodeRef, TapKind) -> Unit,
    onClose: () -> Unit,
    onNearEnd: () -> Unit,
    onLoadMore: () -> Unit,
    onJumpDone: () -> Unit,
    listen: ListenState = ListenState(),
    listenActions: ListenActions = ListenActions(),
) {
    val list = rememberLazyListState()
    val nearEnd by rememberUpdatedState(onNearEnd)
    val currentScale by rememberUpdatedState(textScale)

    LaunchedEffect(state.jumpTo) {
        state.jumpTo?.let { list.scrollToItem(it); onJumpDone() }
    }
    // "Near the end" = the last three items are on screen.
    LaunchedEffect(list) {
        snapshotFlow {
            val info = list.layoutInfo
            info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 3
        }.distinctUntilChanged().filter { it }.collect { nearEnd() }
    }
    // Keep the block being read on screen.
    LaunchedEffect(listen.current) {
        val current = listen.current ?: return@LaunchedEffect
        val info = list.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == current }
        val whole = item != null && item.offset >= info.viewportStartOffset && item.offset + item.size <= info.viewportEndOffset
        if (!whole) list.animateScrollToItem(current)
    }
    // The screen stays on while reading aloud.
    val view = LocalView.current
    val reading = listen.playback != Playback.IDLE
    DisposableEffect(view, reading) {
        view.keepScreenOn = reading
        onDispose { view.keepScreenOn = false }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            TopBar(state.title, textScale, onTextScale, onClose)
            if (state.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Loading" })
            }
            (state.message ?: listen.message)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            val keys = remember(state.blocks) { itemKeys(state.blocks) }
            val density = LocalDensity.current
            // Reader's own text size, on top of the phone's; the top bar above stays normal size.
            val playerOpen = listen.playback == Playback.PLAYING || listen.playback == Playback.PAUSED
            Box(Modifier.weight(1f).fillMaxWidth()) {
                CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * textScale)) {
                    LazyColumn(
                        state = list,
                        modifier = Modifier.fillMaxSize().pinchToScale({ currentScale }, onTextScale),
                        // Room at the bottom so the Listen button never hides the last lines.
                        contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = if (playerOpen) 20.dp else 96.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Keys keep the reader's place when a load puts text above it (see Merge.placeNew).
                        items(state.blocks.size, key = { keys[it] }) { i ->
                            val beingRead = i == listen.current
                            Box(if (beingRead) Modifier.beingRead() else Modifier) { BlockView(state.blocks[i], onTap) }
                        }
                        item { FooterView(state.footer, onLoadMore) }
                    }
                }
                if (!playerOpen && state.blocks.isNotEmpty()) {
                    ListenButton(
                        starting = listen.playback == Playback.STARTING,
                        onClick = { listenActions.start(list.firstVisibleItemIndex) },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    )
                }
            }
            if (playerOpen) PlayerBar(listen.playback == Playback.PAUSED, listenActions)
        }
    }
}

@Composable
private fun TopBar(title: String, scale: Float, onScale: (Float) -> Unit, onClose: () -> Unit) {
    val titleText = @Composable { modifier: Modifier ->
        Text(
            title.ifBlank { "Newt Reader" },
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = NewtFonts.Display),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier.semantics { heading() },
        )
    }
    val buttons = @Composable {
        BarButton("A−", "Smaller text") { onScale(ReaderPrefs.stepped(scale, up = false)) }
        BarButton("A+", "Bigger text") { onScale(ReaderPrefs.stepped(scale, up = true)) }
        BarButton("Close", null, onClose)
    }
    val padding = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
    // With the phone's text size turned up, the title gets its own line instead of being squeezed to a few letters.
    if (LocalDensity.current.fontScale >= 1.5f) {
        Column(padding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            titleText(Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) { buttons() }
        }
    } else {
        Row(padding, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            titleText(Modifier.weight(1f))
            buttons()
        }
    }
}

@Composable
private fun BarButton(label: String, description: String?, onClick: () -> Unit) {
    val talk = if (description != null) Modifier.semantics { contentDescription = description } else Modifier
    OutlinedButton(onClick = onClick, border = neonOutline(), modifier = Modifier.heightIn(min = 56.dp).then(talk)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

/** The block being read: a thick gradient outline drawn just outside it, so the text doesn't move. */
@Composable
private fun Modifier.beingRead(): Modifier {
    val brush = Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))
    return semantics { stateDescription = "Being read aloud" }.drawWithContent {
        drawContent()
        val out = 8.dp.toPx()
        drawRoundRect(
            brush,
            topLeft = Offset(-out, -out),
            size = Size(size.width + 2 * out, size.height + 2 * out),
            cornerRadius = CornerRadius(12.dp.toPx()),
            style = Stroke(3.dp.toPx()),
        )
    }
}

@Composable
private fun ListenButton(starting: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        enabled = !starting,
        border = neonOutline(),
        // Filled, so text scrolling underneath doesn't show through.
        colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.background),
        modifier = modifier.heightIn(min = 56.dp),
    ) {
        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (starting) "Starting…" else "Listen", style = MaterialTheme.typography.titleMedium)
    }
}

/** Back, Pause or Resume, Next, Stop. Two rows when the phone's text is large. */
@Composable
private fun PlayerBar(paused: Boolean, actions: ListenActions) {
    val back = @Composable { m: Modifier -> PlayerButton(Icons.Filled.SkipPrevious, "Back", "Read the block before", m, actions.back) }
    val toggle = @Composable { m: Modifier ->
        if (paused) PlayerButton(Icons.Filled.PlayArrow, "Resume", null, m, actions.resume)
        else PlayerButton(Icons.Filled.Pause, "Pause", null, m, actions.pause)
    }
    val next = @Composable { m: Modifier -> PlayerButton(Icons.Filled.SkipNext, "Next", "Skip to the next block", m, actions.next) }
    val stop = @Composable { m: Modifier -> PlayerButton(Icons.Filled.Stop, "Stop", "Stop reading aloud", m, actions.stop) }
    val padding = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
    val gap = Arrangement.spacedBy(8.dp)
    if (LocalDensity.current.fontScale >= 1.5f) {
        Column(padding, verticalArrangement = gap) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) { back(Modifier.weight(1f)); toggle(Modifier.weight(1f)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) { next(Modifier.weight(1f)); stop(Modifier.weight(1f)) }
        }
    } else {
        Row(padding, horizontalArrangement = gap) {
            back(Modifier.weight(1f)); toggle(Modifier.weight(1f)); next(Modifier.weight(1f)); stop(Modifier.weight(1f))
        }
    }
}

@Composable
private fun PlayerButton(icon: ImageVector, label: String, description: String?, modifier: Modifier, onClick: () -> Unit) {
    val talk = if (description != null) Modifier.semantics { contentDescription = description } else Modifier
    OutlinedButton(
        onClick = onClick,
        border = neonOutline(),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
        modifier = modifier.heightIn(min = 64.dp).then(talk),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
    }
}

@Composable
private fun FooterView(footer: Footer, onLoadMore: () -> Unit) {
    when (footer) {
        Footer.NONE -> Spacer(Modifier.height(1.dp))
        Footer.LOADING -> Text("Loading more…", style = readingStyle())
        Footer.LOAD_MORE -> ReaderButton("Load more", TapKind.FOLLOW, onLoadMore)
        Footer.END -> Text("That's everything this app is showing.", style = readingStyle())
    }
}

/** Two-finger pinch changes Reader's text size; one finger still scrolls. */
private fun Modifier.pinchToScale(current: () -> Float, onScale: (Float) -> Unit) = pointerInput(Unit) {
    awaitEachGesture {
        var scale = current()
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                val zoom = event.calculateZoom()
                if (zoom != 1f) {
                    scale = (scale * zoom).coerceIn(ReaderPrefs.MIN_SCALE, ReaderPrefs.MAX_SCALE)
                    onScale(scale)
                    event.changes.forEach { it.consume() }
                }
            }
        } while (event.changes.any { it.pressed })
    }
}

/** A key per block, unique even when a block repeats ("Share" under every post): its key and how many came before. */
internal fun itemKeys(blocks: List<Block>): List<String> {
    val counts = HashMap<String, Int>()
    return blocks.map { b -> val n = counts.merge(b.key, 1, Int::plus)!!; "${b.key}#$n" }
}
