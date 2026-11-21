package io.github.secnewt.reader.speech

import io.github.secnewt.reader.engine.Block

/** What read-aloud says for each block, and how long text is cut into pieces the engine accepts. */
object Speakable {
    private val sentenceEnd = Regex("(?<=[.!?…])\\s+")
    private val spaces = Regex("\\s+")

    /** The words read out for [block], or null if it's skipped (buttons, toolbars, fields, unlabelled pictures). */
    fun text(block: Block): String? = when (block) {
        is Block.Heading -> block.text
        is Block.Paragraph -> block.text
        is Block.ListItem -> block.text
        is Block.Image -> block.description?.takeIf { it.isNotBlank() }?.let { "Picture: ${it.trim()}" }
        is Block.Button, is Block.ControlRow, is Block.Field -> null
    }?.trim()?.takeIf { it.isNotEmpty() }

    /** [text] as sentences, each no longer than [max]; a longer sentence is split at spaces, a longer word cut. */
    fun pieces(text: String, max: Int): List<String> =
        text.trim().split(sentenceEnd).filter { it.isNotBlank() }.flatMap { fit(it.trim(), max) }

    private fun fit(sentence: String, max: Int): List<String> {
        if (sentence.length <= max) return listOf(sentence)
        val out = mutableListOf<String>()
        val line = StringBuilder()
        for (word in sentence.split(spaces)) {
            if (line.isNotEmpty() && line.length + 1 + word.length > max) { out += line.toString(); line.clear() }
            if (word.length > max) {
                word.chunked(max).forEach { if (line.isEmpty() && it.length == max) out += it else line.append(it) }
                continue
            }
            if (line.isNotEmpty()) line.append(' ')
            line.append(word)
        }
        if (line.isNotEmpty()) out += line.toString()
        return out
    }

    /** The first readable block at or after [from], or null. */
    fun next(blocks: List<Block>, from: Int): Int? =
        (from.coerceAtLeast(0) until blocks.size).firstOrNull { text(blocks[it]) != null }

    /** The last readable block before [before], or null. */
    fun previous(blocks: List<Block>, before: Int): Int? =
        (minOf(before, blocks.size) - 1 downTo 0).firstOrNull { text(blocks[it]) != null }
}
