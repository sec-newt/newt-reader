package io.github.secnewt.reader.engine

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withTimeoutOrNull

/** Waits for an app to stop changing before Reader reads it (pages load, lists animate in). */
object Settle {
    const val QUIET_MS = 300L
    const val MAX_MS = 3_000L

    /** True once [changes] has been quiet for [quietMs]; false if it is still changing after [maxMs]. */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun await(changes: Flow<Unit>, quietMs: Long = QUIET_MS, maxMs: Long = MAX_MS): Boolean =
        withTimeoutOrNull(maxMs) {
            changes.onStart { emit(Unit) }.transformLatest { delay(quietMs); emit(Unit) }.first()
        } != null
}
