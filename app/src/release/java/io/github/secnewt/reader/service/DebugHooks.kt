package io.github.secnewt.reader.service

import android.content.Context
import io.github.secnewt.reader.reader.ReaderController
import kotlinx.coroutines.CoroutineScope

/** Release builds: no test hooks. (The debug build's version drives the emulator test.) */
@Suppress("UNUSED_PARAMETER")
object DebugHooks {
    fun register(context: Context, scope: CoroutineScope, controller: ReaderController, open: () -> Unit) = Unit
}
