package io.github.secnewt.reader.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
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

    fun register(context: Context, scope: CoroutineScope, controller: ReaderController, open: () -> Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                when (intent.action) {
                    OPEN -> open()
                    BACK -> controller.back()
                    TAP -> {
                        val label = intent.getStringExtra("label").orEmpty()
                        val target = controller.state.value.findTarget(label)
                        if (target == null) Log.i(TAG, "no target: $label") else controller.tap(target.first, target.second)
                    }
                }
            }
        }
        val filter = IntentFilter().apply { addAction(OPEN); addAction(TAP); addAction(BACK) }
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
}
