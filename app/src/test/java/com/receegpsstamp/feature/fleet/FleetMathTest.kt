package com.receegpsstamp.feature.fleet

import com.receegpsstamp.data.model.FuelLog
import com.receegpsstamp.data.model.MaintItem
import com.receegpsstamp.data.model.Vehicle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards [economyOf] / [statusOf] / [alertsOf] — the fuel-mileage and service-due math extracted from
 * FleetScreen. economyOf is the tank-to-tank km/L shown per vehicle; statusOf feeds the maintenance
 * rows and the screen's alert banner.
 */
class FleetMathTest {

    private val DAY = 86_400_000L

    private fun fuel(odometer: Int, litres: Double) = FuelLog(odometer = odometer, litres = litres)
    private fun vehicle(
        name: String = "Activa",
        currentKm: Int = 0,
        maintItems: List<MaintItem> = emptyList(),
        insuranceExpiry: Long = 0L,
    ) = Vehicle(name = name, currentKm = currentKm, maintItems = maintItems, insuranceExpiry = insuranceExpiry)

    // ── economyOf (tank-to-tank km/L) ──

    @Test fun economy_needs_at_least_two_valid_fills() {
        assertEquals(0.0, economyOf(emptyList()), 0.0)
        assertEquals(0.0, economyOf(listOf(fuel(1000, 10.0))), 0.0)
    }

    @Test fun economy_excludes_the_first_fills_litres() {
        // 1000→1500 = 500 km on the 10 L bought at the SECOND fill → 50 km/L.
        assertEquals(50.0, economyOf(listOf(fuel(1000, 10.0), fuel(1500, 10.0))), 1e-9)
    }

    @Test fun economy_over_three_fills() {
        // 1000→2000 = 1000 km; litres after the first fill = 25 + 25 = 50 → 20 km/L.
        assertEquals(20.0, economyOf(listOf(fuel(1000, 10.0), fuel(1500, 25.0), fuel(2000, 25.0))), 1e-9)
    }

    @Test fun economy_ignores_fills_missing_odometer_or_litres() {
        val logs = listOf(fuel(1000, 10.0), fuel(0, 5.0), fuel(1200, 0.0), fuel(1500, 10.0))
        assertEquals(50.0, economyOf(logs), 1e-9)   // only the two valid fills count
    }

    @Test fun economy_sorts_by_odometer_regardless_of_input_order() {
        assertEquals(50.0, economyOf(listOf(fuel(1500, 10.0), fuel(1000, 10.0))), 1e-9)
    }

    @Test fun economy_zero_when_no_distance_covered() {
        assertEquals(0.0, economyOf(listOf(fuel(1000, 10.0), fuel(1000, 10.0))), 0.0)
    }

    // ── statusOf (items + economy + expiry days) ──

    @Test fun status_maps_recorded_item_remaining_km() {
        val v = vehicle(currentKm = 8000, maintItems = listOf(MaintItem("Oil change", 3000, 6000)))
        val st = statusOf(v, emptyList())
        assertEquals(8000, st.km)
        assertEquals(listOf(ItemDue("Oil change", 3000, true, 1000)), st.items)
    }

    @Test fun status_marks_item_unrecorded_when_never_done() {
        val v = vehicle(currentKm = 8000, maintItems = listOf(MaintItem("Service", 5000, 0)))
        assertEquals(listOf(ItemDue("Service", 5000, false, 0)), statusOf(v, emptyList()).items)
    }

    @Test fun status_wires_in_fuel_economy() {
        val st = statusOf(vehicle(currentKm = 1500), listOf(fuel(1000, 10.0), fuel(1500, 10.0)))
        assertEquals(50.0, st.economy, 1e-9)
    }

    @Test fun status_expiry_days_null_when_unset_and_present_when_set() {
        assertNull(statusOf(vehicle(insuranceExpiry = 0L), emptyList()).insDays)
        val soon = statusOf(vehicle(insuranceExpiry = System.currentTimeMillis() + 10 * DAY), emptyList())
        assertNotNull(soon.insDays)
        assertTrue(soon.insDays.toString(), soon.insDays!! in 0L..10L)
    }

    // ── alertsOf (screen variant; fully deterministic when handed a Status) ──

    private fun status(items: List<ItemDue> = emptyList(), insDays: Long? = null, pucDays: Long? = null, fitDays: Long? = null) =
        Status(km = 0, economy = 0.0, items = items, insDays = insDays, pucDays = pucDays, fitDays = fitDays)

    @Test fun alerts_overdue_and_due_soon_from_items() {
        val st = status(items = listOf(
            ItemDue("Oil change", 3000, recorded = true, remainingKm = -100),
            ItemDue("Service", 5000, recorded = true, remainingKm = 300),
        ))
        assertEquals(
            listOf("Activa — oil change OVERDUE", "Activa — service due in 300 km"),
            alertsOf(vehicle(), st),
        )
    }

    @Test fun alerts_skip_unrecorded_and_far_items() {
        val st = status(items = listOf(
            ItemDue("Service", 5000, recorded = false, remainingKm = 0),
            ItemDue("Brake", 10000, recorded = true, remainingKm = 900),
        ))
        assertTrue(alertsOf(vehicle(), st).isEmpty())
    }

    @Test fun alerts_document_expiry_wording() {
        assertEquals(listOf("Activa — insurance EXPIRED"), alertsOf(vehicle(), status(insDays = -1)))
        assertEquals(listOf("Activa — PUC expires in 10 days"), alertsOf(vehicle(), status(pucDays = 10)))
        assertEquals(listOf("Activa — fitness expires in 0 days"), alertsOf(vehicle(), status(fitDays = 0)))
        assertTrue(alertsOf(vehicle(), status(insDays = 20)).isEmpty())
    }

    @Test fun fitness_applies_only_to_commercial_vehicles() {
        val soon = System.currentTimeMillis() + 5 * DAY
        // Private Bikes & Cars don't need a fitness certificate → fitDays is null (no row, no alert).
        assertNull(statusOf(Vehicle(type = "Bike", fitnessExpiry = soon), emptyList()).fitDays)
        assertNull(statusOf(Vehicle(type = "Car", fitnessExpiry = soon), emptyList()).fitDays)
        // Transport / Other vehicles still track it.
        assertNotNull(statusOf(Vehicle(type = "Transport vehicle", fitnessExpiry = soon), emptyList()).fitDays)
    }
}
