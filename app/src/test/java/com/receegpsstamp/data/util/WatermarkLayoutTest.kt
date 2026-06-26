package com.receegpsstamp.data.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** Guards the watermark line grouping: 2, 3, 2, 3, … fields per line; address on its own line. */
class WatermarkLayoutTest {

    @Test fun groups_eleven_fields_as_2_3_2_3_then_remaining() {
        val fields = (1..11).map { "f$it" to "f$it" }
        val lines = WatermarkLayout.groupLines(fields)
        assertEquals(
            listOf(
                "f1 · f2",          // line 1 → 1,2
                "f3 · f4 · f5",     // line 2 → 3,4,5
                "f6 · f7",          // line 3 → 6,7
                "f8 · f9 · f10",    // line 4 → 8,9,10
                "f11",              // remaining
            ),
            lines,
        )
    }

    @Test fun address_always_gets_its_own_line() {
        val fields = listOf(
            "company" to "Dollar", "distributor" to "Hitesh",
            "shop" to "Shop", "address" to "MG Road, Jalgaon, MH",
        )
        // company·distributor → line 1; shop → line 2; address forced onto its own line.
        assertEquals(listOf("Dollar · Hitesh", "Shop", "MG Road, Jalgaon, MH"), WatermarkLayout.groupLines(fields))
    }

    @Test fun partial_list_stops_cleanly() {
        assertEquals(listOf("a · b", "c"), WatermarkLayout.groupLines(listOf("a" to "a", "b" to "b", "c" to "c")))
        assertEquals(listOf("a"), WatermarkLayout.groupLines(listOf("a" to "a")))
    }

    @Test fun empty_list_gives_no_lines() {
        assertEquals(emptyList<String>(), WatermarkLayout.groupLines(emptyList()))
    }
}
