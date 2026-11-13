package io.github.secnewt.reader.engine

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettleTest {
    @Test fun quietAppSettlesAfter300ms() = runTest {
        assertTrue(Settle.await(MutableSharedFlow()))
        assertEquals(300, currentTime)
    }

    @Test fun waitsForChangesToStop() = runTest {
        val changes = MutableSharedFlow<Unit>()
        backgroundScope.launch { repeat(10) { delay(100); changes.emit(Unit) } }  // last change at 1000 ms
        assertTrue(Settle.await(changes))
        assertEquals(1300, currentTime)
    }

    @Test fun givesUpAt3s() = runTest {
        val changes = MutableSharedFlow<Unit>()
        backgroundScope.launch { while (true) { delay(100); changes.emit(Unit) } }
        assertFalse(Settle.await(changes))
        assertEquals(3000, currentTime)
    }
}
