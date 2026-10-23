package io.github.secnewt.reader.reader

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ReaderPrefsTest {
    private val prefs = ReaderPrefs(ApplicationProvider.getApplicationContext())

    @Test fun defaults() {
        assertEquals(1.5f, prefs.textScale)
        assertFalse(prefs.floatingButton)
    }

    @Test fun remembersAndClamps() {
        prefs.textScale = 9f
        assertEquals(3.0f, prefs.textScale)
        prefs.floatingButton = true
        assertTrue(ReaderPrefs(ApplicationProvider.getApplicationContext()).floatingButton)
    }

    @Test fun stepsByTenths() {
        assertEquals(1.6f, ReaderPrefs.stepped(1.5f, up = true))
        assertEquals(1.4f, ReaderPrefs.stepped(1.5f, up = false))
        assertEquals(1.0f, ReaderPrefs.stepped(1.0f, up = false))
        assertEquals(3.0f, ReaderPrefs.stepped(3.0f, up = true))
        assertEquals(1.7f, ReaderPrefs.stepped(1.63f, up = true))
    }

    @Test fun watchSeesChanges() {
        var seen = 0
        prefs.watch { seen++ }
        prefs.floatingButton = !prefs.floatingButton
        prefs.unwatch()
        assertTrue(seen >= 1)
    }
}
