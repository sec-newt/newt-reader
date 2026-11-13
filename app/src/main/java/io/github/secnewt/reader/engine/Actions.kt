package io.github.secnewt.reader.engine

import java.util.Locale

/** The everyday things you do to a post, which Reader shows as icons with their counts. */
enum class Action { READ_REPLIES, REPLY, REPOST, LIKE, BOOKMARK, SHARE, VIEWS }

/** A recognized action: what it is, its count if the app gave one, and whether it's already on (liked, bookmarked). */
data class ActionInfo(val action: Action, val count: Long? = null, val on: Boolean = false) {
    /** Like, bookmark, views and reading replies happen inside Reader; the rest open something in the app. */
    val kind: TapKind get() = when (action) {
        Action.READ_REPLIES, Action.LIKE, Action.BOOKMARK, Action.VIEWS -> TapKind.FOLLOW
        Action.REPLY, Action.REPOST, Action.SHARE -> TapKind.CLOSE_FIRST
    }
}

object Actions {
    private val leadingCount = Regex("""^([\d,]+)\s""")

    /** X labels its buttons "47 Replies. Reply", "1419 Likes. Liked", "Share post": the part after the last ". " says what it does. */
    fun parse(label: String): ActionInfo? {
        val verb = label.substringAfterLast(". ").trim().lowercase()
        val count = leadingCount.find(label)?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull()
        return when (verb) {
            "reply" -> ActionInfo(Action.REPLY, count)
            "repost" -> ActionInfo(Action.REPOST, count)
            "reposted", "undo repost" -> ActionInfo(Action.REPOST, count, on = true)
            "like" -> ActionInfo(Action.LIKE, count)
            "liked", "unlike" -> ActionInfo(Action.LIKE, count, on = true)
            "bookmark", "add bookmark" -> ActionInfo(Action.BOOKMARK, count)
            "bookmarked", "remove bookmark" -> ActionInfo(Action.BOOKMARK, count, on = true)
            "share", "share post" -> ActionInfo(Action.SHARE, count)
            "view post analytics" -> ActionInfo(Action.VIEWS, count)
            else -> null
        }
    }

    /** 4473 → "4.5K", 6283570 → "6.3M", the way the apps themselves shorten counts. */
    fun compact(n: Long): String {
        fun short(x: Double, unit: String) = (if (x >= 100) "%.0f".format(Locale.ROOT, x) else "%.1f".format(Locale.ROOT, x)).removeSuffix(".0") + unit
        return when {
            n < 1_000 -> n.toString()
            n < 1_000_000 -> short(n / 1_000.0, "K")
            n < 1_000_000_000 -> short(n / 1_000_000.0, "M")
            else -> short(n / 1_000_000_000.0, "B")
        }
    }

    /** "7 hours ago", "3m", "Nov 3", "Nov 3, 2025": a post's time stamp, which links to the post and its replies. */
    val timeStamp = Regex(
        """^(\d+ (second|minute|hour|day|week|month|year)s? ago|\d+[smhd]|[A-Z][a-z]{2} \d{1,2}(, \d{4})?)$""",
    )
}
