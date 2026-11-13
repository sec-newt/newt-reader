package io.github.secnewt.reader.engine

/**
 * Rebuilds lines and paragraphs from where each letter sits on screen. Some apps (the
 * GrapheneOS PDF viewer's text layer) share a whole page as one string with nothing between
 * its lines, so names, table columns and sentences run together.
 */
object TextLayout {
    /** Lines glued together this many times or more means the text needs laying out. */
    private const val MIN_GLUED = 2

    private class Line(val text: StringBuilder = StringBuilder()) {
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = Int.MIN_VALUE
        var bottom = Int.MIN_VALUE
        /** Has blank space across it between words: a table row. */
        var columns = false
        val placed get() = top != Int.MAX_VALUE
        val height get() = bottom - top

        fun add(b: Bounds) {
            left = minOf(left, b.left); top = minOf(top, b.top); right = maxOf(right, b.right); bottom = maxOf(bottom, b.bottom)
        }
    }

    /**
     * [text]'s paragraphs, given each letter's box ([boxes], null where unknown), or null when
     * its lines already end in spaces or new lines (ordinary text, best left as it is).
     */
    fun paragraphs(text: String, boxes: List<Bounds?>): List<String>? {
        val lines = mutableListOf(Line())
        var glued = 0
        text.forEachIndexed { i, c ->
            val b = boxes.getOrNull(i)?.takeIf { it.bottom > it.top }
            val line = lines.last()
            if (b == null || !line.placed) {
                line.text.append(c); b?.let(line::add); return@forEachIndexed
            }
            val mid = (b.top + b.bottom) / 2
            val prev = line.text.lastOrNull()
            if (mid > line.bottom || mid < line.top) {
                if (prev != null && !prev.isWhitespace() && !c.isWhitespace()) glued++
                lines += Line().also { it.text.append(c); it.add(b) }
                return@forEachIndexed
            }
            // Blank space across the line between two letters with no space character: table columns.
            if (b.left - line.right > line.height * 3 / 10 && prev != null && !prev.isWhitespace() && !c.isWhitespace()) {
                line.text.append(' ')
                line.columns = true
            }
            line.text.append(c)
            line.add(b)
        }
        if (glued < MIN_GLUED) return null

        val placed = lines.filter { it.placed && it.text.isNotBlank() }
        if (placed.isEmpty()) return null
        val lineHeight = placed.map { it.height }.sorted()[placed.size / 2]
        val gaps = placed.zipWithNext { a, b -> b.top - a.bottom }
        val usualGap = gaps.filter { it >= 0 }.sorted().let { if (it.isEmpty()) 0 else it[(it.size - 1) / 2] }
        val left = placed.minOf { it.left }
        val width = placed.maxOf { it.right } - left

        val out = mutableListOf<StringBuilder>()
        placed.forEachIndexed { i, line ->
            val words = line.text.toString().trim()
            val prev = placed.getOrNull(i - 1)
            val newParagraph = prev == null ||
                gaps[i - 1] < 0 ||                                          // moved up: a new column
                gaps[i - 1] > usualGap + lineHeight * 6 / 10 ||              // extra space between lines
                kotlin.math.abs(line.height - prev.height) > lineHeight / 4 || // a different text size
                prev.right < left + width * 3 / 4 ||                          // the line before ended short
                line.columns || prev.columns                                 // table rows stand alone
            if (newParagraph) {
                out += StringBuilder(words)
            } else {
                val p = out.last()
                // "commu-" + "nity" is one word broken across lines.
                if (p.endsWith("-") && p.length > 1 && p[p.length - 2].isLetter() && words.firstOrNull()?.isLowerCase() == true) {
                    p.setLength(p.length - 1)
                } else {
                    p.append(' ')
                }
                p.append(words)
            }
        }
        return out.map { clean(it.toString()) }.filter { it.isNotEmpty() }
    }
}
