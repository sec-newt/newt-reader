package io.github.secnewt.reader.service

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import io.github.secnewt.reader.reader.ReaderController
import io.github.secnewt.reader.reader.ReaderPrefs
import io.github.secnewt.reader.reader.ServicePort
import io.github.secnewt.reader.ui.ReaderRoute
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * Newt Reader's accessibility service. It does nothing until asked (two-finger swipe,
 * the tile, or the floating button); only then does it listen for screen changes and read.
 */
class ReaderService : AccessibilityService() {
    private val scope = MainScope()
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    @Volatile private var listening = false
    private lateinit var prefs: ReaderPrefs
    private lateinit var overlay: OverlayHost
    private lateinit var bubble: FloatingButton
    private lateinit var controller: ReaderController

    override fun onServiceConnected() {
        prefs = ReaderPrefs(this)
        overlay = OverlayHost(this)
        bubble = FloatingButton(this, ::toggle)
        val port = ServicePort(this, changes) {
            overlay.withoutFocus { performGlobalAction(GLOBAL_ACTION_BACK); delay(100) }
        }
        controller = ReaderController(port, scope, onClosed = { listening = false })
        accessibilityButtonController.registerAccessibilityButtonCallback(
            object : AccessibilityButtonController.AccessibilityButtonCallback() {
                override fun onClicked(controller: AccessibilityButtonController) = toggle()
            },
        )
        scope.launch { controller.state.collect { render(it.open) } }
        prefs.watch(::refreshBubble)
        refreshBubble()
        DebugHooks.register(this, scope, controller, ::open)
        running = this
    }

    fun toggle() = if (controller.state.value.open) controller.close() else open()

    fun open() {
        listening = true
        controller.open()
    }

    /** The tile: close the shade first; reading waits for the screen to settle anyway. */
    fun openFromTile() {
        listening = true
        performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        controller.open()
    }

    private fun render(open: Boolean) {
        if (open) overlay.show(onBack = controller::back) { ReaderRoute(controller, prefs) } else overlay.hide()
        refreshBubble()
    }

    private fun refreshBubble() {
        if (prefs.floatingButton && !overlay.showing) bubble.show() else bubble.hide()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!listening) return
        // Ignore Reader's own overlay redrawing; anything else (including our own test page) counts.
        if (event.packageName == packageName && fromOverlay(event)) return
        changes.tryEmit(Unit)
    }

    private fun fromOverlay(event: AccessibilityEvent) =
        windows.firstOrNull { it.id == event.windowId }?.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        running = null
        if (::prefs.isInitialized) {
            prefs.unwatch()
            overlay.hide()
            bubble.hide()
        }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** The connected service, for the tile. Null when Reader is turned off. */
        @Volatile var running: ReaderService? = null
            private set
    }
}
