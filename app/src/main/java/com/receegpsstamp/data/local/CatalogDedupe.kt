package com.receegpsstamp.data.local

import com.receegpsstamp.data.model.Company
import com.receegpsstamp.data.model.Distributor

/**
 * Collapses duplicate companies that pile up because seed companies get a fresh random UUID on each
 * device, while the shared catalog merges by id — so the same company name ends up stored under
 * several ids and shows twice in the app.
 *
 * Companies are deduped by name (case-insensitive, keeping the entry with the most creatives/media
 * types). Every distributor that pointed at a dropped company id is remapped to the kept one.
 * Distributors themselves are left untouched — shops/recces/drafts reference `distributorId`, so
 * changing distributor ids could orphan them; only the `companyId` field is repaired.
 *
 * Pure function — unit-tested in CatalogDedupeTest.
 */
internal object CatalogDedupe {
    data class Result(val companies: List<Company>, val distributors: List<Distributor>)

    private fun richness(c: Company) = c.creatives.size + c.mediaTypes.size

    fun dedupe(companies: List<Company>, distributors: List<Distributor>): Result {
        // Pass 1 — pick the canonical company per name (richest; ties keep the first seen).
        val canonByName = LinkedHashMap<String, Company>()
        val unnamed = ArrayList<Company>()
        for (c in companies) {
            val key = c.name.trim().lowercase()
            if (key.isEmpty()) { unnamed += c; continue } // can't merge blank names — pass through
            val prev = canonByName[key]
            if (prev == null || richness(c) > richness(prev)) canonByName[key] = c
        }

        // Pass 2 — map every company id onto its canonical id, and note whether anything changed.
        val canonicalId = HashMap<String, String>()
        var hadDuplicate = false
        for (c in companies) {
            val key = c.name.trim().lowercase()
            if (key.isEmpty()) { canonicalId[c.id] = c.id; continue }
            val canon = canonByName.getValue(key)
            canonicalId[c.id] = canon.id
            if (canon.id != c.id) hadDuplicate = true
        }
        if (!hadDuplicate) return Result(companies, distributors) // no churn when already clean

        val companiesOut = canonByName.values.toList() + unnamed
        val distributorsOut = distributors.map { d ->
            val cid = canonicalId[d.companyId]
            if (cid != null && cid != d.companyId) d.copy(companyId = cid) else d
        }
        return Result(companiesOut, distributorsOut)
    }
}
