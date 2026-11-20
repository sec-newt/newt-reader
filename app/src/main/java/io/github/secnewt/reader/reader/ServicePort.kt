package io.github.secnewt.reader.reader

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import io.github.secnewt.reader.engine.AndroidSnapshot
import io.github.secnewt.reader.engine.NodeRef
import io.github.secnewt.reader.engine.RefMatcher
import io.github.secnewt.reader.engine.Screen
import io.github.secnewt.reader.engine.Settle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * ScreenPort on a real phone. Reads the top app window: Reader's own overlay is an
 * accessibility-overlay window, never an application window, so it's never read.
 */
class ServicePort(
    private val service: AccessibilityService,
    private val changes: Flow<Unit>,
    private val globalBack: suspend () -> Unit,
) : ScreenPort {
    override suspend fun read(): Screen? {
        Settle.await(changes)
        val window = appWindow() ?: return null
        val root = window.root ?: return null
        val title = window.title?.toString().orEmpty()
        return withContext(Dispatchers.Default) { AndroidSnapshot.capture(root, title) }
    }

    override suspend fun tap(target: NodeRef): Boolean {
        val root = appWindow()?.root ?: return false
        val fresh = withContext(Dispatchers.Default) { AndroidSnapshot.capture(root, "") }
        val match = RefMatcher.find(fresh.root, target) ?: return false
        return AndroidSnapshot.liveAt(root, match.path)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    override suspend fun scrollForward(): Boolean {
        val root = appWindow()?.root ?: return false
        val fresh = withContext(Dispatchers.Default) { AndroidSnapshot.capture(root, "") }
        val list = fresh.root.walk().filter { it.scrollable && it.visible }.maxByOrNull { it.bounds.area } ?: return false
        return AndroidSnapshot.liveAt(root, list.path)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true
    }

    override suspend fun back() = globalBack()

    private fun appWindow(): AccessibilityWindowInfo? =
        service.windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }.maxByOrNull { it.layer }
}
