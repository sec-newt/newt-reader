package io.github.secnewt.reader.service

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView

/**
 * The full-screen Reader window. It's an accessibility overlay, so it needs no "draw over
 * other apps" permission and the app underneath keeps running. It takes focus so Back
 * comes to Reader; [withoutFocus] hands focus back to the app for one action.
 */
class OverlayHost(private val service: AccessibilityService) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private var root: View? = null
    private var owner: OverlayOwner? = null
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.OPAQUE,
    )

    val showing: Boolean get() = root != null

    fun show(onBack: () -> Unit, content: @Composable () -> Unit) {
        if (root != null) return
        val frame = object : FrameLayout(service) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) onBack()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        frame.addView(ComposeView(service).apply { setContent(content) })
        val o = OverlayOwner().also { it.attach(frame) }
        wm.addView(frame, params)
        root = frame
        owner = o
    }

    fun hide() {
        root?.let(wm::removeView)
        owner?.detach()
        root = null
        owner = null
    }

    suspend fun withoutFocus(block: suspend () -> Unit) {
        val v = root ?: return block()
        setFocusable(v, false)
        try { block() } finally { if (root === v) setFocusable(v, true) }
    }

    private fun setFocusable(v: View, focusable: Boolean) {
        val flag = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        params.flags = if (focusable) params.flags and flag.inv() else params.flags or flag
        wm.updateViewLayout(v, params)
    }
}
