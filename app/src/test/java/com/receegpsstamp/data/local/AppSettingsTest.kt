package com.receegpsstamp.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the watermark field order/visibility logic and stale-key resilience. */
class AppSettingsTest {

    @Test fun null_order_falls_back_to_default() {
        assertEquals(AppSettings.DEFAULT_WM_ORDER, AppSettings().order())
    }

    @Test fun order_always_contains_every_default_key() {
        val s = AppSettings(wmOrder = listOf("status", "company"))
        assertTrue(s.order().toSet().containsAll(AppSettings.DEFAULT_WM_ORDER.toSet()))
    }

    @Test fun order_drops_unknown_or_legacy_keys() {
        // "companyDistributor" is a removed (legacy) grouped key — must not appear.
        val s = AppSettings(wmOrder = listOf("companyDistributor", "status"))
        assertFalse("companyDistributor" in s.order())
        assertTrue("status" in s.order())
    }

    @Test fun order_keeps_custom_priority_for_valid_keys() {
        val s = AppSettings(wmOrder = listOf("status", "company"))
        val order = s.order()
        assertTrue(order.indexOf("status") < order.indexOf("company"))
    }

    @Test fun company_and_distributor_toggle_independently() {
        assertTrue(AppSettings(wmCompany = true).enabled("company"))
        assertFalse(AppSettings(wmCompany = false).enabled("company"))
        assertTrue(AppSettings(wmDistributor = true).enabled("distributor"))
        assertFalse(AppSettings(wmDistributor = false).enabled("distributor"))
    }

    @Test fun unknown_field_key_is_disabled() {
        assertFalse(AppSettings().enabled("nonsense"))
    }
}
