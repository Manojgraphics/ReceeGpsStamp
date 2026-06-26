package com.receegpsstamp.data.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** Guards the flex area formula — especially the inch → sq.ft (÷144) conversion. */
class MediaMathTest {

    @Test fun feet_area_is_width_times_height_times_qty() {
        assertEquals(12f, MediaMath.areaSqFt(3f, 4f, 1, "ft"), 0.001f)
        assertEquals(24f, MediaMath.areaSqFt(3f, 4f, 2, "ft"), 0.001f)
    }

    @Test fun inch_area_converts_to_sqft_via_144() {
        assertEquals(12f, MediaMath.areaSqFt(36f, 48f, 1, "in"), 0.001f) // 1728 / 144
        assertEquals(3f, MediaMath.areaSqFt(18f, 24f, 1, "in"), 0.001f)  // 432 / 144
        assertEquals(6f, MediaMath.areaSqFt(18f, 24f, 2, "in"), 0.001f)  // ×2 qty
    }

    @Test fun zero_dimensions_give_zero() {
        assertEquals(0f, MediaMath.areaSqFt(0f, 4f, 1, "ft"), 0.001f)
    }

    @Test fun format_drops_trailing_zeros() {
        assertEquals("24", MediaMath.formatArea(24f))
        assertEquals("100", MediaMath.formatArea(100f))
        assertEquals("1.5", MediaMath.formatArea(1.5f))
        assertEquals("12.25", MediaMath.formatArea(12.25f))
        assertEquals("0", MediaMath.formatArea(0f))
    }
}
