package io.github.secnewt.reader.ui

import androidx.compose.foundation.gestures.awaitEachGesture
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
import io.github.secnewt.reader.engine.NodeRef
import io.github.secnewt.reader.engine.TapKind
import io.github.secnewt.reader.reader.Footer
import io.github.secnewt.reader.reader.ReaderController
import io.github.secnewt.reader.reader.ReaderPrefs
import io.github.secnewt.reader.reader.ReaderState
import io.github.secnewt.ui.NewtFonts
import io.github.secnewt.ui.NewtTheme
import io.github.secnewt.ui.neonOutline
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/** Connects the overlay to the controller and the saved text size. */
@Composable
fun ReaderRoute(controller: ReaderController, prefs: ReaderPrefs) {
    val state by controller.state.collectAsState()
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
        )
    }
}

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

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            TopBar(state.title, textScale, onTextScale, onClose)
            if (state.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Loading" })
            }
            state.message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            val density = LocalDensity.current
            // Reader's own text size, on top of the phone's; the top bar above stays normal size.
            CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * textScale)) {
                LazyColumn(
                    state = list,
                    modifier = Modifier.fillMaxSize().pinchToScale({ currentScale }, onTextScale),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(state.blocks.size) { i -> BlockView(state.blocks[i], onTap) }
                    item { FooterView(state.footer, onLoadMore) }
                }
            }
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
