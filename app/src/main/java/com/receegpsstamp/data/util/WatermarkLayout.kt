package com.receegpsstamp.data.util

/**
 * Groups the enabled watermark field values into display lines.
 *
 * Pattern (cycling): 2, 3, 2, 3, … fields per line —
 *   Line 1 → fields 1-2 · Line 2 → 3-5 · Line 3 → 6-7 · Line 4 → 8-10 · then repeat.
 * Fields within a line are joined with " · ". The Settings order list stays per-field (no merge);
 * only the rendered stamp groups them. Pure logic — unit-tested in WatermarkLayoutTest.
 */
object WatermarkLayout {
    private val PATTERN = listOf(2, 3)
    private val SOLO_KEYS = setOf("address") // long fields that always get their own line

    /**
     * Groups (fieldKey, value) pairs into display lines — 2, 3, 2, 3, … per line, joined with " · ".
     * A field whose key is in [SOLO_KEYS] (e.g. the full address) always lands on its own line, so
     * with the default order the address is the 5th line.
     */
    fun groupLines(fields: List<Pair<String, String>>): List<String> {
        val lines = mutableListOf<String>()
        val group = mutableListOf<String>()
        var p = 0
        fun flush() {
            if (group.isNotEmpty()) { lines.add(group.joinToString(" · ")); group.clear(); p++ }
        }
        for ((key, value) in fields) {
            if (key in SOLO_KEYS) {
                flush()
                lines.add(value)
            } else {
                group.add(value)
                if (group.size >= PATTERN[p % PATTERN.size]) flush()
            }
        }
        flush()
        return lines
    }
}
