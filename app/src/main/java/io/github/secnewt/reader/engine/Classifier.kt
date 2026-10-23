package io.github.secnewt.reader.engine

/**
 * Buttons that move through content (more, next, expand…) keep Reader open; anything else
 * (Send, Call, Play, or a label we don't know) closes Reader first, so nothing happens hidden behind it.
 */
object Classifier {
    private val followWords =
        Regex("""\b(more|next|previous|prev|show|expand|see all|read more|page \d+|older|newer)\b""", RegexOption.IGNORE_CASE)

    fun forButton(label: String): TapKind =
        if (followWords.containsMatchIn(label)) TapKind.FOLLOW else TapKind.CLOSE_FIRST
}
