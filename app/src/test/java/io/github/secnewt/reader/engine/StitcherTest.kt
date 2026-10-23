package io.github.secnewt.reader.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StitcherTest {
    private fun tv(t: String, visible: Boolean = true) = ScreenNode("android.widget.TextView", text = t, visible = visible)
    private fun link(t: String) = ScreenNode("android.view.View", text = t, clickable = true, children = listOf(tv(t)))
    private fun web(vararg kids: ScreenNode) =
        Screen("p", "T", ScreenNode("root", children = listOf(ScreenNode("android.webkit.WebView", children = kids.toList()))).withPaths())
    private fun app(vararg kids: ScreenNode) = Screen("p", "T", ScreenNode("root", children = kids.toList()).withPaths())

    @Test fun webParagraphKeepsLinksInline() {
        val blocks = Stitcher.stitch(web(ScreenNode("android.view.View", children = listOf(
            tv("Visual"), tv(" or "), tv("vision impairment"), tv(" is the inability of "), link("visual perception"), tv(". It matters."),
        ))))
        val p = blocks.single() as Block.Paragraph
        assertEquals("Visual or vision impairment is the inability of visual perception. It matters.", p.text)
        val l = p.spans.filterIsInstance<Span.Link>().single()
        assertEquals("visual perception", l.text)
        assertEquals(listOf(0, 0, 4), l.target.path)
    }

    @Test fun nestedFootnoteStaysInTheParagraph() {
        val footnote = ScreenNode("android.view.View", children = listOf(
            ScreenNode("android.view.View", desc = "[8]", clickable = true, children = listOf(
                ScreenNode("android.view.View", children = listOf(tv("["), tv("8"), tv("]")))))))
        val p = Stitcher.stitch(web(ScreenNode("android.view.View", children = listOf(tv("Treatment helps."), footnote, tv(" More text follows here."))))).single() as Block.Paragraph
        assertEquals("Treatment helps.[8] More text follows here.", p.text)
        assertEquals("[8]", p.spans.filterIsInstance<Span.Link>().single().text)
    }

    @Test fun appLeavesAreSeparateParagraphs() {
        val blocks = Stitcher.stitch(app(tv("Running"), tv("The probe connected fine.")))
        assertEquals(listOf("Running", "The probe connected fine."), blocks.map { it.text })
    }

    @Test fun clickableWithLongTextIsAListItem() {
        val row = ScreenNode("android.view.View", clickable = true, children = listOf(
            ScreenNode("android.view.View", text = "Today: sunny. High 81. Low 58."), ScreenNode("android.widget.Button")))
        val b = Stitcher.stitch(app(row)).single()
        assertTrue(b is Block.ListItem)
        assertEquals("Today: sunny. High 81. Low 58.", b.text)
    }

    @Test fun shortButtonsAndTheirKind() {
        val blocks = Stitcher.stitch(app(
            ScreenNode("android.widget.Button", text = "Send", clickable = true), tv("gap"),
            ScreenNode("android.widget.Button", text = "Show more", clickable = true)))
        assertEquals(TapKind.CLOSE_FIRST, (blocks[0] as Block.Button).control.kind)
        assertEquals(TapKind.FOLLOW, (blocks[2] as Block.Button).control.kind)
    }

    @Test fun webLinksOutsideParagraphsFollow() {
        val b = Stitcher.stitch(web(link("Blindness"))).single() as Block.Button
        assertEquals(TapKind.FOLLOW, b.control.kind)
    }

    @Test fun threeButtonsInARowFold() {
        val blocks = Stitcher.stitch(app(
            ScreenNode("android.widget.Button", text = "Like", clickable = true),
            ScreenNode("android.widget.Button", text = "Reply", clickable = true),
            ScreenNode("android.widget.Button", text = "Share", clickable = true)))
        val row = blocks.single() as Block.ControlRow
        assertEquals(listOf("Like", "Reply", "Share"), row.controls.map { it.label })
    }

    @Test fun unlabelledButtonsAreSkipped() =
        assertTrue(Stitcher.stitch(app(ScreenNode("android.widget.Button", clickable = true))).isEmpty())

    @Test fun clickableContainerWithClickableInsideIsOpened() {
        val post = ScreenNode("android.view.View", clickable = true, children = listOf(
            tv("A long post body that is well over the limit."), ScreenNode("android.widget.Button", text = "Reply", clickable = true)))
        val blocks = Stitcher.stitch(app(post))
        assertEquals(listOf("A long post body that is well over the limit.", "Reply"), blocks.map { it.text })
    }

    @Test fun passwordFieldsNeverShowContents() {
        val f = Stitcher.stitch(app(ScreenNode("android.widget.EditText", text = "hunter2", hint = "Password", editable = true, password = true))).single() as Block.Field
        assertTrue(f.password)
        assertFalse(f.label.contains("hunter2"))
        assertEquals("Password", f.label)
    }

    @Test fun headingsAndImages() {
        val blocks = Stitcher.stitch(app(
            ScreenNode("android.widget.TextView", text = "Causes", heading = true),
            ScreenNode("android.widget.Image", desc = "A Snellen chart"),
            ScreenNode("android.widget.ImageView", bounds = Bounds(0, 0, 40, 40))))
        assertEquals(listOf("Causes", "A Snellen chart"), blocks.map { it.text })
        assertTrue(blocks[0] is Block.Heading)
        assertTrue(blocks[1] is Block.Image)
    }

    @Test fun clickableHeadingIsATappableHeading() {
        val toggle = ScreenNode("android.view.View", clickable = true, children = listOf(
            ScreenNode("android.widget.TextView", text = "Classification", heading = true), ScreenNode("android.view.View")))
        val h = Stitcher.stitch(web(toggle)).single() as Block.Heading
        assertEquals("Classification", h.text)
        assertEquals(listOf(0, 0), h.target?.path)
    }

    @Test fun repeatsInARowAreDropped() =
        assertEquals(1, Stitcher.stitch(app(tv("Save"), tv("Save"))).size)

    @Test fun mathLettersArePlain() =
        assertEquals("linuxnewt", Stitcher.stitch(app(tv("𝚕𝚒𝚗𝚞𝚡𝚗𝚎𝚠𝚝"))).single().text)

    @Test fun onScreenComesFromVisibility() {
        val blocks = Stitcher.stitch(app(tv("above", visible = false), tv("here")))
        assertEquals(listOf(false, true), blocks.map { it.onScreen })
    }
}
