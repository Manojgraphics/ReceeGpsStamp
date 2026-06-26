package com.receegpsstamp.data.local

import com.receegpsstamp.data.model.InstallEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards [InstallDedupe] — installation is tracked per media-SIZE, so the dedupe key is
 * recceId#mediaIndex (one entry per recce × media item). A wrong key would either drop real sizes
 * or duplicate them on every web re-pull.
 */
class InstallDedupeTest {

    private fun entry(recceId: String, mediaIndex: Int, shopId: String = "shop1") =
        InstallEntry(recceId = recceId, mediaIndex = mediaIndex, shopId = shopId)

    @Test fun keeps_one_entry_per_media_index_of_same_recce() {
        val incoming = listOf(entry("r1", 0), entry("r1", 1), entry("r1", 2))
        val out = InstallDedupe.selectNew(emptyList(), incoming)
        assertEquals(listOf("r1#0", "r1#1", "r1#2"), out.map { it.recceId + "#" + it.mediaIndex })
    }

    @Test fun drops_entries_already_present() {
        val existing = listOf(entry("r1", 0))
        val incoming = listOf(entry("r1", 0), entry("r1", 1))
        val out = InstallDedupe.selectNew(existing, incoming)
        assertEquals(listOf("r1#1"), out.map { it.recceId + "#" + it.mediaIndex })
    }

    @Test fun dedupes_repeats_within_the_same_batch() {
        val incoming = listOf(entry("r1", 0), entry("r1", 0), entry("r2", 0))
        val out = InstallDedupe.selectNew(emptyList(), incoming)
        assertEquals(listOf("r1#0", "r2#0"), out.map { it.recceId + "#" + it.mediaIndex })
    }

    @Test fun different_recces_with_same_media_index_are_distinct() {
        val incoming = listOf(entry("r1", 0), entry("r2", 0))
        val out = InstallDedupe.selectNew(emptyList(), incoming)
        assertEquals(2, out.size)
    }

    @Test fun drops_entries_with_no_shop_and_no_recce() {
        val incoming = listOf(
            entry(recceId = "", mediaIndex = 0, shopId = ""),    // dropped — no identity at all
            entry(recceId = "r1", mediaIndex = 0, shopId = ""),  // kept — has a recce
        )
        val out = InstallDedupe.selectNew(emptyList(), incoming)
        assertEquals(listOf("r1#0"), out.map { it.recceId + "#" + it.mediaIndex })
    }

    @Test fun returns_original_unstamped_entries() {
        val e = entry("r1", 0)
        val out = InstallDedupe.selectNew(emptyList(), listOf(e))
        assertEquals(1, out.size)
        assertEquals(e, out.first())   // the pure step does no id/userId/createdAt mutation
    }

    @Test fun empty_incoming_yields_empty() {
        assertTrue(InstallDedupe.selectNew(listOf(entry("r1", 0)), emptyList()).isEmpty())
    }
}
