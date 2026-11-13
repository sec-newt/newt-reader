package io.github.secnewt.reader.reader

import io.github.secnewt.reader.engine.NodeRef
import io.github.secnewt.reader.engine.Screen

/** Everything the controller needs from the phone. The real one is ServicePort; tests use a fake. */
interface ScreenPort {
    /** Waits for the app to stop changing, then reads it. Null if the app shares nothing. */
    suspend fun read(): Screen?
    /** Taps the real control. False if it can't be found any more. */
    suspend fun tap(target: NodeRef): Boolean
    /** Scrolls the app's main list forward. False if there's nothing to scroll. */
    suspend fun scrollForward(): Boolean
    /** Android's Back, delivered to the app under Reader. */
    suspend fun back()
}
