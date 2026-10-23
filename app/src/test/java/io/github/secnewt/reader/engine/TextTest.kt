package io.github.secnewt.reader.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TextTest {
    @Test fun nfkcTurnsMathLettersPlain() = assertEquals("linuxnewt", nfkc("𝚕𝚒𝚗𝚞𝚡𝚗𝚎𝚠𝚝"))

    @Test fun nfkcKeepsEdgeSpaces() = assertEquals(" or ", nfkc(" or "))

    @Test fun cleanCollapsesSpacesKeepsLines() =
        assertEquals("a b\nc", clean("  a    b \n  c  "))

    @Test fun cleanLimitsBlankLines() = assertEquals("a\n\nb", clean("a\n\n\n\nb"))

    @Test fun pathsFollowChildIndexes() {
        val tree = ScreenNode("r", children = listOf(ScreenNode("a"), ScreenNode("b", children = listOf(ScreenNode("c"))))).withPaths()
        assertEquals(listOf(1, 0), tree.children[1].children[0].path)
        assertEquals(listOf("r", "a", "b", "c"), tree.walk().map { it.cls }.toList())
    }

    @Test fun labelPrefersTextThenDescThenHint() {
        assertEquals("t", ScreenNode("x", text = "t", desc = "d").label)
        assertEquals("d", ScreenNode("x", text = " ", desc = "d").label)
        assertEquals("h", ScreenNode("x", hint = "h").label)
    }

    @Test fun fixturesLoad() {
        val wiki = Fixtures.load("wikipedia")
        assertEquals("app.vanadium.browser", wiki.packageName)
        assertTrue(wiki.root.walk().count() > 500)
    }
}
