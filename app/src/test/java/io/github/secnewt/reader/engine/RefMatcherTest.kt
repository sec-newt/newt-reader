package io.github.secnewt.reader.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RefMatcherTest {
    private fun btn(t: String?, id: String? = null, top: Int = 0) =
        ScreenNode("android.widget.Button", text = t, viewId = id, clickable = true, bounds = Bounds(0, top, 100, top + 50))

    private val old = ScreenNode("r", children = listOf(btn("Next", top = 100), btn("Send", top = 200))).withPaths()

    @Test fun samePathStillMatches() = assertEquals(listOf(1), RefMatcher.find(old, old.children[1].ref())?.path)

    @Test fun movedNodeFoundByLabel() {
        val fresh = ScreenNode("r", children = listOf(btn("Ad"), btn("Next", top = 150), btn("Send", top = 250))).withPaths()
        assertEquals(listOf(2), RefMatcher.find(fresh, old.children[1].ref())?.path)
    }

    @Test fun idWins() {
        val o = ScreenNode("r", children = listOf(btn("Go", id = "go"))).withPaths()
        val fresh = ScreenNode("r", children = listOf(btn("Go"), btn("Go", id = "go", top = 400))).withPaths()
        assertEquals(listOf(1), RefMatcher.find(fresh, o.children[0].ref())?.path)
    }

    @Test fun unlabelledFoundNearby() {
        val o = ScreenNode("r", children = listOf(btn(null, top = 300))).withPaths()
        val fresh = ScreenNode("r", children = listOf(btn("x", top = 0), btn(null, top = 310))).withPaths()
        assertEquals(listOf(1), RefMatcher.find(fresh, o.children[0].ref())?.path)
    }

    @Test fun goneMeansNull() {
        val fresh = ScreenNode("r", children = listOf(btn("Other", top = 900))).withPaths()
        assertNull(RefMatcher.find(fresh, old.children[1].ref()))
    }
}
