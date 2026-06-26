package com.receegpsstamp.data.local

import com.receegpsstamp.data.model.InstallEntry

/**
 * Pure dedupe for web-pulled installs. Installation is tracked **per media-SIZE**, so an install's
 * identity is `recceId#mediaIndex` — one entry per recce × media item. Returns only the incoming
 * entries that are genuinely new: not already present, and not duplicated within the same batch.
 * Entries with neither a shop nor a recce are dropped. No id-stamping / I/O here so it stays testable.
 */
internal object InstallDedupe {
    fun selectNew(existing: List<InstallEntry>, incoming: List<InstallEntry>): List<InstallEntry> {
        val have = existing.map { it.recceId + "#" + it.mediaIndex }.toMutableSet()
        val add = mutableListOf<InstallEntry>()
        for (e in incoming) {
            if (e.shopId.isBlank() && e.recceId.isBlank()) continue
            val k = e.recceId + "#" + e.mediaIndex
            if (k in have) continue
            have.add(k); add.add(e)
        }
        return add
    }
}
