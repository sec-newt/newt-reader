package io.github.secnewt.reader.engine

import java.text.Normalizer

/** Look-alike letters (𝚕𝚒𝚗𝚞𝚡, fullwidth, ligatures) become plain ones so they draw in Atkinson. Spacing is kept. */
fun nfkc(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFKC)

/** [nfkc], then runs of spaces become one, each line is trimmed, and at most one blank line in a row. */
fun clean(s: String): String =
    nfkc(s)
        .replace(Regex("[ \\t\\u00A0]+"), " ")
        .lines().joinToString("\n") { it.trim() }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
