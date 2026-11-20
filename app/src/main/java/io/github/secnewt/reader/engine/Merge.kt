package io.github.secnewt.reader.engine

object Merge {
    /** The blocks in [fresh] that [shown] doesn't already have (same type + text), in order, each once. */
    fun appendNew(shown: List<Block>, fresh: List<Block>): List<Block> {
        val seen = shown.mapTo(HashSet()) { it.key }
        return fresh.filter { seen.add(it.key) }
    }
}
