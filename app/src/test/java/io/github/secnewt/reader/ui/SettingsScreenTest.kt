package io.github.secnewt.reader.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import io.github.secnewt.test.newtScreenshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class SettingsScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun offDark() {
        composeRule.newtScreenshot("settings-off-dark", dark = true) { SettingsScreen(false, 1.5f, {}, false, {}, {}) }
        composeRule.onNodeWithText("Reader is off", substring = true).assertIsDisplayed()
    }

    @Test fun onLight() {
        composeRule.newtScreenshot("settings-on-light", dark = false) { SettingsScreen(true, 1.5f, {}, true, {}, {}) }
        composeRule.onNodeWithText("Reader is on", substring = true).assertIsDisplayed()
    }

    @Test fun largeText() = composeRule.newtScreenshot("settings-200pct", dark = true, fontScale = 2f) {
        SettingsScreen(false, 2.0f, {}, false, {}, {})
    }
}
