package io.github.secnewt.reader.service

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.secnewt.ui.NewtTheme
import io.github.secnewt.ui.neonOutline
import kotlin.math.roundToInt

/** The optional floating Newt button (off by default). Tap to read; drag to move. */
class FloatingButton(private val service: AccessibilityService, private val onTap: () -> Unit) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private var view: View? = null
    private var owner: OverlayOwner? = null
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 0
        y = 600
    }

    fun show() {
        if (view != null) return
        val v = ComposeView(service).apply { setContent { NewtTheme { Bubble(onTap, ::move) } } }
        val o = OverlayOwner().also { it.attach(v) }
        wm.addView(v, params)
        view = v
        owner = o
    }

    fun hide() {
        view?.let(wm::removeView)
        owner?.detach()
        view = null
        owner = null
    }

    private fun move(dx: Float, dy: Float) {
        val v = view ?: return
        params.x += dx.roundToInt()
        params.y += dy.roundToInt()
        wm.updateViewLayout(v, params)
    }
}

@Composable
private fun Bubble(onTap: () -> Unit, onDrag: (Float, Float) -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.background,
        border = neonOutline(2.dp),
        modifier = Modifier
            .size(64.dp)
            .pointerInput(Unit) { detectDragGestures { change, drag -> change.consume(); onDrag(drag.x, drag.y) } }
            .clickable(onClickLabel = "Read this screen", onClick = onTap)
            .semantics { contentDescription = "Newt Reader" },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("Read", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}
