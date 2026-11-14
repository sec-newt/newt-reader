package io.github.secnewt.reader.engine

object Merge {
    /**
     * [shown] with the blocks from [fresh] it doesn't already have (same type + text), each once,
     * placed where they sit in the app: a run of new blocks goes just before the next block in
     * [fresh] that's already shown, or at the end when none follows (a feed scrolled past
     * everything shown). Some apps only share part of a page once it has scrolled (Proton Mail's
     * message body), so new text can belong in the middle.
     */
    fun placeNew(shown: List<Block>, fresh: List<Block>): List<Block> {
        val out = shown.toMutableList()
        val known = shown.mapTo(HashSet()) { it.key }
        val added = HashSet<String>()
        val run = mutableListOf<Block>()
        // How far through [out] the fresh read has matched. Short text repeats ("2h" under every
        // post), so a known block only anchors at or after this point; an earlier copy is skipped.
        var cursor = 0
        for (b in fresh) {
            when {
                b.key in known -> {
                    val at = (cursor until out.size).firstOrNull { out[it].key == b.key } ?: continue
                    out.addAll(at, run)
                    cursor = at + run.size + 1
                    run.clear()
                }
                added.add(b.key) -> run += b
            }
        }
        out += run
        return out
    }
}
