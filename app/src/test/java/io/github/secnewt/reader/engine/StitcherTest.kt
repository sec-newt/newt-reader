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

    @Test fun webBlocksWithSpaceBetweenThemStaySeparate() {
        val blocks = Stitcher.stitch(web(
            ScreenNode("android.widget.TextView", text = "Test page two", bounds = Bounds(8, 45, 312, 84)),
            ScreenNode("android.widget.TextView", text = "You followed the link.", bounds = Bounds(8, 104, 312, 124))))
        assertEquals(listOf("Test page two", "You followed the link."), blocks.map { it.text })
    }

    @Test fun webRunsOnTouchingLinesStayOneParagraph() {
        val p = Stitcher.stitch(web(ScreenNode("android.view.View", children = listOf(
            ScreenNode("android.widget.TextView", text = "A run that wraps ", bounds = Bounds(8, 100, 300, 160)),
            ScreenNode("android.widget.TextView", text = "and goes on.", bounds = Bounds(8, 130, 200, 160)))))).single()
        assertEquals("A run that wraps and goes on.", p.text)
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

    /** A web link wrapping a picture (Wikipedia's article images): Chromium copies the alt text, or the file name, onto both. */
    private fun pictureLink(desc: String, w: Int = 528, h: Int = 400) = ScreenNode("android.view.View", desc = desc, clickable = true,
        bounds = Bounds(0, 0, w, h), children = listOf(ScreenNode("android.widget.Image", desc = desc, bounds = Bounds(0, 0, w, h))))

    @Test fun linkedPictureIsATappablePicture() {
        val img = Stitcher.stitch(web(pictureLink("Lines of letters of decreasing size"))).single() as Block.Image
        assertEquals("Lines of letters of decreasing size", img.description)
        assertEquals(listOf(0, 0), img.target?.path)
    }

    @Test fun pictureFileNameIsNotADescription() {
        val img = Stitcher.stitch(web(pictureLink("500px-Batsuit_from_The_Dark_Knight_Rises.jpg"))).single() as Block.Image
        assertEquals(null, img.description)
        assertTrue(img.target != null)
    }

    @Test fun offScreenPictureLinkIsStillAPicture() {
        // Below the fold Android reports zero height; Wikipedia puts the file name in the image's text.
        val name = "500px-Batsuit_from_The_Dark_Knight_Rises_film_-_closer_view.jpg?utm_source=en.wikipedia"
        val link = ScreenNode("android.view.View", desc = name, clickable = true, visible = false, bounds = Bounds(210, 2337, 871, 2337),
            children = listOf(ScreenNode("android.widget.Image", text = name, visible = false, bounds = Bounds(210, 2337, 871, 2337))))
        val img = Stitcher.stitch(web(link)).single() as Block.Image
        assertEquals(null, img.description)
        assertTrue(img.target != null)
    }

    @Test fun spaceBetweenTwoLinksIsKept() {
        val p = Stitcher.stitch(web(ScreenNode("android.view.View", children = listOf(
            tv("worn by the "), link("superhero"), tv(" "), link("Batman"), tv(", which appears"),
        )))).single() as Block.Paragraph
        assertEquals("worn by the superhero Batman, which appears", p.text)
    }

    @Test fun smallLinkedLogoStaysAButton() {
        val b = Stitcher.stitch(web(pictureLink("Wikipedia", w = 373, h = 63))).single()
        assertTrue(b is Block.Button)
        assertEquals("Wikipedia", b.text)
    }

    @Test fun laidOutTextBecomesItsParagraphsWithoutThePagePicture() {
        // A PDF viewer: the page drawn as a picture, and its text as one glued string that TextLayout split up.
        val page = ScreenNode("android.view.View", children = listOf(
            ScreenNode("android.widget.Image", bounds = Bounds(0, 0, 1080, 2000)),
            ScreenNode("android.widget.TextView", text = "Weekly newsMorning class 9:30", paragraphs = listOf("Weekly news", "Morning class 9:30")),
        ))
        assertEquals(listOf("Weekly news", "Morning class 9:30"), Stitcher.stitch(web(page)).map { it.text })
    }

    /** An X post's action bar, as the phone reported it (counts made up). */
    private fun xPost(): Array<ScreenNode> {
        val icon = ScreenNode("android.widget.TextView", text = "", clickable = true)
        fun action(cls: String, text: String? = null, desc: String? = null, shown: String? = null) =
            ScreenNode("android.view.View", children = listOf(ScreenNode(cls, text = text, desc = desc, clickable = true,
                children = if (shown == null) emptyList() else listOf(ScreenNode("android.view.View", children = listOf(
                    ScreenNode("android.view.View", children = listOf(icon)), ScreenNode("android.widget.TextView", text = shown)))))))
        val time = ScreenNode("android.view.View", text = "2120636067879571488", desc = "7 hours ago", clickable = true,
            children = listOf(ScreenNode("android.view.View", text = "7h")))
        val body = ScreenNode("android.view.View", children = listOf(tv("A post about tiling window managers.")))
        val bar = ScreenNode("android.view.View", text = "47 replies, 32 reposts, 1419 likes", children = listOf(
            action("android.widget.Button", desc = "47 Replies. Reply"),
            action("android.widget.Button", text = "32 reposts. Repost", shown = "32"),
            action("android.widget.Button", desc = "1419 Likes. Like"),
            action("android.view.View", text = "analytics", desc = "14370 views. View post analytics", shown = "14K"),
        ))
        return arrayOf(time, body, bar)
    }

    @Test fun xActionBarIsOneRowOfActions() {
        val blocks = Stitcher.stitch(web(*xPost()))
        val row = blocks.last() as Block.ControlRow
        assertEquals(
            listOf(Action.READ_REPLIES, Action.REPLY, Action.REPOST, Action.LIKE, Action.VIEWS),
            row.controls.map { it.action?.action },
        )
        assertEquals(listOf(47L, 47L, 32L, 1419L, 14370L), row.controls.map { it.action?.count })
        // No stray "32" or "14K" left over from the icons X draws inside its buttons.
        assertFalse(blocks.any { it.text == "32" || it.text == "14K" })
    }

    @Test fun readRepliesOpensThePostThroughItsTimeStamp() {
        val blocks = Stitcher.stitch(web(*xPost()))
        val time = blocks.filterIsInstance<Block.Button>().single { it.text == "7 hours ago" }
        val read = (blocks.last() as Block.ControlRow).controls.first()
        assertEquals("47 replies", read.label)
        assertEquals(TapKind.FOLLOW, read.kind)
        assertEquals(time.control.target, read.target)
        val reply = (blocks.last() as Block.ControlRow).controls[1]
        assertEquals(TapKind.CLOSE_FIRST, reply.kind)
    }

    @Test fun postHeaderAndActionsAreSeparateRows() {
        // A post with only a picture: the header's buttons sit right before the action bar.
        val (time, _, bar) = xPost()
        val blocks = Stitcher.stitch(web(link("Sam Example"), link("@sam_example"), time, link("More"), bar))
        val (header, actions) = blocks.map { it as Block.ControlRow }
        assertEquals(listOf("Sam Example", "@sam_example", "7 hours ago", "More"), header.controls.map { it.label })
        assertTrue(actions.controls.all { it.action != null })
        assertEquals("47 replies", actions.controls.first().label)
        assertEquals(header.controls[2].target, actions.controls.first().target)
    }

    @Test fun pictureFileNameIsNotPartOfTheText() {
        // X puts the picture's file name in its text and "Image" in its description.
        val img = ScreenNode("android.widget.Image", text = "HW4L8p2WIAA1q5j?format=jpg&name=360x360", desc = "Image")
        val p = Stitcher.stitch(web(ScreenNode("android.view.View", children = listOf(
            tv("Look at this "), ScreenNode("android.view.View", children = listOf(img)), tv(" garden."),
        )))).single() as Block.Paragraph
        assertEquals("Look at this garden.", p.text)
    }

    @Test fun separatorAtTheEndOfALineIsDropped() {
        // Footer links: "Privacy policy •", "Contact •", each its own line.
        val p = Stitcher.stitch(web(ScreenNode("android.view.View", children = listOf(link("Privacy policy"), tv("•"))))).single()
        assertEquals("Privacy policy", p.text)
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
