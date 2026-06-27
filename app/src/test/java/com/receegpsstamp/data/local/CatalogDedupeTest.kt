package com.receegpsstamp.data.local

import com.receegpsstamp.data.model.Company
import com.receegpsstamp.data.model.Distributor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards [CatalogDedupe] — seed companies get a random UUID per device and the catalog merges by id,
 * so the same company name piles up under several ids and shows twice in the app. Dedupe must keep
 * one company per name and repair distributor.companyId, without touching distributor ids (shops/
 * recces reference distributorId).
 */
class CatalogDedupeTest {

    private fun co(id: String, name: String, creatives: List<String> = emptyList()) =
        Company(id = id, name = name, creatives = creatives)

    private fun dist(id: String, companyId: String, city: String = "Pune") =
        Distributor(id = id, companyId = companyId, city = city)

    @Test fun collapses_same_name_companies_to_one() {
        val out = CatalogDedupe.dedupe(listOf(co("a", "Dollar"), co("b", "Dollar")), emptyList())
        assertEquals(1, out.companies.size)
        assertEquals("Dollar", out.companies.first().name)
    }

    @Test fun remaps_distributor_companyId_to_kept() {
        // Distributor points at the dropped duplicate id "b" — must be repaired to the kept id "a".
        val out = CatalogDedupe.dedupe(
            listOf(co("a", "Dollar"), co("b", "Dollar")),
            listOf(dist("d1", companyId = "b")),
        )
        val keptId = out.companies.first().id
        assertEquals(keptId, out.distributors.first().companyId)
        assertEquals("d1", out.distributors.first().id)   // distributor id is never changed
    }

    @Test fun keeps_the_richest_company() {
        // The entry with more creatives/media types wins, regardless of order.
        val out = CatalogDedupe.dedupe(
            listOf(co("a", "Dollar"), co("b", "Dollar", creatives = listOf("Banner", "Standee"))),
            emptyList(),
        )
        assertEquals("b", out.companies.first().id)
        assertEquals(2, out.companies.first().creatives.size)
    }

    @Test fun name_match_is_case_and_space_insensitive() {
        val out = CatalogDedupe.dedupe(listOf(co("a", "Dollar"), co("b", " dollar ")), emptyList())
        assertEquals(1, out.companies.size)
    }

    @Test fun distinct_names_are_all_kept() {
        val out = CatalogDedupe.dedupe(
            listOf(co("a", "Dollar"), co("b", "Lux"), co("c", "Sheetal")),
            emptyList(),
        )
        assertEquals(3, out.companies.size)
    }

    @Test fun blank_name_companies_pass_through() {
        // Can't merge nameless companies — both survive.
        val out = CatalogDedupe.dedupe(listOf(co("a", ""), co("b", "")), emptyList())
        assertEquals(2, out.companies.size)
    }

    @Test fun no_duplicates_returns_inputs_untouched() {
        val companies = listOf(co("a", "Dollar"), co("b", "Lux"))
        val distributors = listOf(dist("d1", companyId = "a"))
        val out = CatalogDedupe.dedupe(companies, distributors)
        assertSame(companies, out.companies)        // same instance — no needless rebuild/persist
        assertSame(distributors, out.distributors)
    }

    @Test fun three_way_duplicate_collapses_and_all_distributors_remap() {
        val out = CatalogDedupe.dedupe(
            listOf(co("a", "Dollar"), co("b", "Dollar", creatives = listOf("X")), co("c", "Dollar")),
            listOf(dist("d1", "a"), dist("d2", "b"), dist("d3", "c")),
        )
        assertEquals(1, out.companies.size)
        val keptId = out.companies.first().id
        assertEquals("b", keptId)   // richest wins
        assertTrue(out.distributors.all { it.companyId == keptId })
    }
}
