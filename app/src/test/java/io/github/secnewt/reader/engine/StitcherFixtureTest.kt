package io.github.secnewt.reader.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class StitcherFixtureTest {
    private fun blocks(name: String) = Stitcher.stitch(Fixtures.load(name))

    @Test fun wikipediaLeadIsOneParagraphWithItsLinks() {
        val lead = blocks("wikipedia").filterIsInstance<Block.Paragraph>()
            .first { it.text.startsWith("Visual or vision impairment") }
        assertTrue(lead.text, lead.text.startsWith("Visual or vision impairment (VI or VIP) is the partial or total inability of visual perception."))
        assertTrue(lead.spans.any { it is Span.Link && it.text == "visual perception" })
        assertTrue(lead.onScreen)
    }

    @Test fun wikipediaOffScreenPartsAreThereButMarked() {
        val b = blocks("wikipedia")
        val caption = b.first { it.text.contains("Snellen chart that is frequently used") }
        assertFalse(caption.onScreen)
        // Mobile Wikipedia's section headings are clickable expand toggles; their bodies aren't in the tree until expanded.
        val heading = b.filterIsInstance<Block.Heading>().first { it.text == "Classification" }
        assertFalse(heading.onScreen)
        assertTrue(heading.target != null)
        assertTrue(b.indexOfFirst { it.onScreen } < b.indexOf(heading))
    }

    @Test fun vergeFrontPageReads() {
        val b = blocks("verge")
        assertTrue(b.size > 50)
        assertTrue(b.any { it.text.contains("The iPhone Duo is a messy, expensive, good time") })
    }

    @Test fun kagiSearchBoxIsAField() {
        val f = blocks("kagi-home").filterIsInstance<Block.Field>().single()
        assertTrue(f.label, f.label.startsWith("Let's fetch"))
    }

    @Test fun weatherComposeScreen() {
        val b = blocks("weather")
        assertTrue(b.any { it is Block.Heading && it.text == "Next 7 days" })
        val today = b.filterIsInstance<Block.ListItem>().first { it.text.startsWith("Today: sunny. High 81.") }
        assertTrue(today.onScreen)
        // The city is a clickable heading (it opens the ZIP card): a tappable heading.
        val city = b.filterIsInstance<Block.Heading>().single { it.text == "Springfield" }
        assertTrue(city.target != null)
    }

    @Test fun noFixtureHasTheSameBlockTwiceInARow() {
        for (name in listOf("wikipedia", "verge", "kagi-home", "weather")) {
            blocks(name).zipWithNext().forEach { (a, b) -> assertTrue("$name: ${a.key}", a.key != b.key) }
        }
    }
}
