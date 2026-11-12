package io.github.secnewt.reader.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionsTest {
    @Test fun readsXsLabels() {
        assertEquals(ActionInfo(Action.REPLY, 47), Actions.parse("47 Replies. Reply"))
        assertEquals(ActionInfo(Action.REPOST, 32), Actions.parse("32 reposts. Repost"))
        assertEquals(ActionInfo(Action.LIKE, 1419), Actions.parse("1419 Likes. Like"))
        assertEquals(ActionInfo(Action.LIKE, 1420, on = true), Actions.parse("1420 Likes. Liked"))
        assertEquals(ActionInfo(Action.BOOKMARK, null, on = true), Actions.parse("Bookmarked"))
        assertEquals(ActionInfo(Action.SHARE), Actions.parse("Share post"))
        assertEquals(ActionInfo(Action.VIEWS, 14370), Actions.parse("14370 views. View post analytics"))
        assertEquals(ActionInfo(Action.REPLY), Actions.parse("Reply"))
    }

    @Test fun otherLabelsAreNotActions() {
        assertNull(Actions.parse("Send"))
        assertNull(Actions.parse("Show more replies"))
        assertNull(Actions.parse("The reply was kind. Thanks"))
    }

    @Test fun likeAndBookmarkStayInReaderReplyLeaves() {
        assertEquals(TapKind.FOLLOW, ActionInfo(Action.LIKE).kind)
        assertEquals(TapKind.FOLLOW, ActionInfo(Action.BOOKMARK).kind)
        assertEquals(TapKind.FOLLOW, ActionInfo(Action.READ_REPLIES).kind)
        assertEquals(TapKind.CLOSE_FIRST, ActionInfo(Action.REPLY).kind)
        assertEquals(TapKind.CLOSE_FIRST, ActionInfo(Action.REPOST).kind)
        assertEquals(TapKind.CLOSE_FIRST, ActionInfo(Action.SHARE).kind)
    }

    @Test fun countsShortenLikeTheAppsDo() {
        assertEquals("58", Actions.compact(58))
        assertEquals("4.5K", Actions.compact(4473))
        assertEquals("21K", Actions.compact(21_000))
        assertEquals("214K", Actions.compact(213_600))
        assertEquals("6.3M", Actions.compact(6_283_570))
    }

    @Test fun timeStamps() {
        listOf("7 hours ago", "1 minute ago", "3m", "12h", "Nov 3", "Nov 3, 2025").forEach { assertTrue(it, Actions.timeStamp.matches(it)) }
        listOf("Next", "Home", "2120636067879571488").forEach { assertTrue(it, !Actions.timeStamp.matches(it)) }
    }
}
