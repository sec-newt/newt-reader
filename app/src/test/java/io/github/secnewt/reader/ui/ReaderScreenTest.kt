package io.github.secnewt.reader.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import io.github.secnewt.reader.engine.Block
import io.github.secnewt.reader.engine.Bounds
import io.github.secnewt.reader.engine.Control
import io.github.secnewt.reader.engine.NodeRef
import io.github.secnewt.reader.engine.Span
import io.github.secnewt.reader.engine.TapKind
import io.github.secnewt.reader.reader.Footer
import io.github.secnewt.reader.reader.ReaderState
import io.github.secnewt.test.newtScreenshot
import io.github.secnewt.ui.NewtTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ReaderScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private val ref = NodeRef(emptyList(), null, null, "x", Bounds(0, 0, 0, 0))
    private val sample = ReaderState(
        open = true,
        title = "Visual impairment - Wikipedia",
        blocks = listOf(
            Block.Heading("Visual impairment", true),
            Block.Paragraph(listOf(
                Span.Plain("Visual or vision impairment is the partial or total inability of "),
                Span.Link("visual perception", ref),
                Span.Plain(". Without treatment it can make everyday tasks hard."),
            ), true),
            Block.ControlRow(listOf(Control("Article", ref, TapKind.FOLLOW), Control("Talk", ref, TapKind.FOLLOW), Control("Edit", ref, TapKind.CLOSE_FIRST)), true),
            Block.ListItem("Today: sunny. High 81. Low 58.", ref, true),
            Block.Button(Control("Send", ref, TapKind.CLOSE_FIRST), true),
            Block.Field("Search", ref, false, true),
            Block.Image("A Snellen eye chart", true),
            Block.Heading("Causes", true, ref),
        ),
    )

    @Composable private fun Screen(state: ReaderState = sample, onTap: (NodeRef, TapKind) -> Unit = { _, _ -> }) =
        ReaderScreen(state, 1.5f, {}, onTap, {}, {}, {}, {})

    @Test fun dark() {
        composeRule.newtScreenshot("reader-dark", dark = true) { Screen() }
        composeRule.onNodeWithText("visual perception", substring = true).assertIsDisplayed()
    }

    @Test fun light() = composeRule.newtScreenshot("reader-light", dark = false) { Screen() }

    @Test fun largeText() = composeRule.newtScreenshot("reader-200pct", dark = true, fontScale = 2f) { Screen() }

    @Test fun busyWithMessage() = composeRule.newtScreenshot("reader-busy", dark = true) {
        Screen(sample.copy(busy = true, message = "That changed — here's the screen now"))
    }

    @Test fun loadMoreFooter() = composeRule.newtScreenshot("reader-load-more", dark = true) {
        Screen(sample.copy(blocks = sample.blocks.take(2), footer = Footer.LOAD_MORE))
    }

    @Test fun tappingSendReportsCloseFirst() {
        var tapped: TapKind? = null
        composeRule.setContent { NewtTheme(darkTheme = true) { Screen { _, kind -> tapped = kind } } }
        composeRule.onNodeWithText("Send", substring = true).performClick()
        assertEquals(TapKind.CLOSE_FIRST, tapped)
    }
}
