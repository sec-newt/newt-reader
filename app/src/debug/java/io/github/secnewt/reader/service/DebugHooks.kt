package io.github.secnewt.reader.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.view.accessibility.AccessibilityWindowInfo
import io.github.secnewt.reader.engine.AndroidSnapshot
import io.github.secnewt.reader.engine.ScreenNode
import io.github.secnewt.reader.engine.Block
import io.github.secnewt.reader.reader.ReaderController
import io.github.secnewt.reader.reader.findTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Debug build only: lets the emulator test drive Reader with adb broadcasts and see what
 * it shows in logcat (tag NewtReaderTest). Release builds have a no-op version.
 */
object DebugHooks {
    private const val TAG = "NewtReaderTest"
    private const val OPEN = "io.github.secnewt.reader.debug.OPEN"
    private const val TAP = "io.github.secnewt.reader.debug.TAP"
    private const val BACK = "io.github.secnewt.reader.debug.BACK"
    private const val DUMP = "io.github.secnewt.reader.debug.DUMP"

    fun register(context: Context, scope: CoroutineScope, controller: ReaderController, open: () -> Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                when (intent.action) {
                    OPEN -> open()
                    BACK -> controller.back()
                    DUMP -> dump(context)
                    TAP -> {
                        val label = intent.getStringExtra("label").orEmpty()
                        val target = controller.state.value.findTarget(label)
                        if (target == null) Log.i(TAG, "no target: $label") else controller.tap(target.first, target.second)
                    }
                }
            }
        }
        val filter = IntentFilter().apply { addAction(OPEN); addAction(TAP); addAction(BACK); addAction(DUMP) }
        context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        scope.launch {
            controller.state.collect { s ->
                // What Reader read, so a failed emulator run shows why (test page content only; debug builds only).
                if (s.open && !s.busy) {
                    Log.i(TAG, "blocks: " + s.blocks.take(8).joinToString(" | ") { "${it::class.simpleName}:${it.text.take(40)}" })
                }
                Log.i(TAG, when {
                    !s.open -> "closed"
                    s.busy -> "busy"
                    // The first heading, else the first block: the emulator's WebView doesn't always mark <h1> as a heading.
                    else -> "shows: " + (s.blocks.firstOrNull { it is Block.Heading } ?: s.blocks.firstOrNull())?.text
                })
            }
        }
    }

    /** Logs the raw tree of the app on screen, one node per line (tag NewtReaderDump), to see what an app shares. */
    private fun dump(context: Context) {
        val service = context as? AccessibilityService ?: return
        val window = service.windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }.maxByOrNull { it.layer }
        val root = window?.root ?: run { Log.i(DUMP_TAG, "no app window"); return }
        fun line(n: ScreenNode, depth: Int) {
            val flags = listOfNotNull("click".takeIf { n.clickable }, "scroll".takeIf { n.scrollable }, "edit".takeIf { n.editable },
                "heading".takeIf { n.heading }, "hidden".takeIf { !n.visible }).joinToString(",")
            Log.i(DUMP_TAG, "  ".repeat(depth) + "${n.cls.substringAfterLast('.')} [$flags] ${n.bounds} text=${n.text?.take(60)} desc=${n.desc?.take(60)} kids=${n.children.size}")
            n.children.forEach { line(it, depth + 1) }
        }
        line(AndroidSnapshot.capture(root, window.title?.toString().orEmpty()).root, 0)
    }

    private const val DUMP_TAG = "NewtReaderDump"
}
