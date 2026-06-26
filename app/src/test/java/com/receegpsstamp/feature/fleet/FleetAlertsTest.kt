package com.receegpsstamp.feature.fleet

import com.receegpsstamp.data.model.MaintItem
import com.receegpsstamp.data.model.Vehicle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards [FleetAlerts] — the km-based service reminders and document-expiry warnings that drive
 * BOTH the in-app banner and the background maintenance notification. Wrong math here means a field
 * vehicle silently misses an oil change / service / insurance renewal.
 */
class FleetAlertsTest {

    private val DAY = 86_400_000L

    private fun vehicle(
        name: String = "Activa",
        number: String = "MH12 AB 1234",
        currentKm: Int = 0,
        maintItems: List<MaintItem> = emptyList(),
        insuranceExpiry: Long = 0L,
        pucExpiry: Long = 0L,
        fitnessExpiry: Long = 0L,
    ) = Vehicle(
        name = name, number = number, currentKm = currentKm, maintItems = maintItems,
        insuranceExpiry = insuranceExpiry, pucExpiry = pucExpiry, fitnessExpiry = fitnessExpiry,
    )

    // ── km-based maintenance (fully deterministic) ──

    @Test fun maintenance_overdue_when_remaining_km_not_positive() {
        // last done at 6000 + interval 3000 = due at 9000; current 10000 → 1000 km past due.
        val v = vehicle(currentKm = 10_000, maintItems = listOf(MaintItem("Oil change", 3000, 6000)))
        assertEquals(listOf("Activa — oil change OVERDUE"), FleetAlerts.alertsFor(listOf(v)))
    }

    @Test fun maintenance_due_soon_within_500km() {
        // due at 9000, current 8800 → 200 km remaining.
        val v = vehicle(currentKm = 8800, maintItems = listOf(MaintItem("Oil change", 3000, 6000)))
        assertEquals(listOf("Activa — oil change due in 200 km"), FleetAlerts.alertsFor(listOf(v)))
    }

    @Test fun maintenance_due_soon_is_inclusive_at_exactly_500km() {
        val v = vehicle(currentKm = 8500, maintItems = listOf(MaintItem("Oil change", 3000, 6000)))
        assertEquals(listOf("Activa — oil change due in 500 km"), FleetAlerts.alertsFor(listOf(v)))
    }

    @Test fun maintenance_not_flagged_beyond_500km() {
        val v = vehicle(currentKm = 8499, maintItems = listOf(MaintItem("Oil change", 3000, 6000)))
        assertTrue(FleetAlerts.alertsFor(listOf(v)).isEmpty())
    }

    @Test fun maintenance_skipped_when_never_recorded() {
        // lastKm 0 → no baseline; must NOT alert even with an interval set and a high odometer.
        val v = vehicle(currentKm = 99_999, maintItems = listOf(MaintItem("Service", 5000, 0)))
        assertTrue(FleetAlerts.alertsFor(listOf(v)).isEmpty())
    }

    @Test fun maintenance_skipped_when_interval_zero() {
        val v = vehicle(currentKm = 99_999, maintItems = listOf(MaintItem("Service", 0, 5000)))
        assertTrue(FleetAlerts.alertsFor(listOf(v)).isEmpty())
    }

    // ── tag fallback: name → number → "Vehicle" ──

    @Test fun tag_falls_back_to_number_then_literal() {
        val byNumber = vehicle(name = "", number = "GJ01 X 9", currentKm = 10_000, maintItems = listOf(MaintItem("Brake", 1000, 1000)))
        assertEquals(listOf("GJ01 X 9 — brake OVERDUE"), FleetAlerts.alertsFor(listOf(byNumber)))

        val byLiteral = vehicle(name = "", number = "", currentKm = 10_000, maintItems = listOf(MaintItem("Brake", 1000, 1000)))
        assertEquals(listOf("Vehicle — brake OVERDUE"), FleetAlerts.alertsFor(listOf(byLiteral)))
    }

    // ── document expiry (time-relative; assert wording, not the exact day count, to avoid clock flakiness) ──

    @Test fun insurance_expired_in_the_past() {
        val now = System.currentTimeMillis()
        val v = vehicle(insuranceExpiry = now - 5 * DAY)
        assertEquals(listOf("Activa — insurance EXPIRED"), FleetAlerts.alertsFor(listOf(v)))
    }

    @Test fun puc_expiring_within_15_days_warns() {
        val now = System.currentTimeMillis()
        val v = vehicle(pucExpiry = now + 10 * DAY)
        val alerts = FleetAlerts.alertsFor(listOf(v))
        assertEquals(1, alerts.size)
        assertTrue(alerts.first(), alerts.first().startsWith("Activa — PUC expires in "))
    }

    @Test fun fitness_far_in_future_is_not_flagged() {
        val now = System.currentTimeMillis()
        val v = vehicle(fitnessExpiry = now + 60 * DAY)
        assertTrue(FleetAlerts.alertsFor(listOf(v)).isEmpty())
    }

    @Test fun expiry_unset_is_ignored() {
        val v = vehicle(insuranceExpiry = 0L, pucExpiry = 0L, fitnessExpiry = 0L)
        assertTrue(FleetAlerts.alertsFor(listOf(v)).isEmpty())
    }

    // ── aggregation across items and vehicles ──

    @Test fun aggregates_multiple_issues_and_vehicles() {
        val now = System.currentTimeMillis()
        val a = vehicle(
            name = "Bike A", number = "", currentKm = 10_000,
            maintItems = listOf(MaintItem("Oil change", 3000, 6000)), insuranceExpiry = now - DAY,
        )
        // due at 4800 + 5000 = 9800, current 9600 → 200 km remaining.
        val b = vehicle(name = "Car B", currentKm = 9600, maintItems = listOf(MaintItem("Service", 5000, 4800)))
        val alerts = FleetAlerts.alertsFor(listOf(a, b))
        assertTrue(alerts.toString(), alerts.contains("Bike A — oil change OVERDUE"))
        assertTrue(alerts.toString(), alerts.contains("Bike A — insurance EXPIRED"))
        assertTrue(alerts.toString(), alerts.contains("Car B — service due in 200 km"))
    }

    @Test fun empty_fleet_has_no_alerts() {
        assertTrue(FleetAlerts.alertsFor(emptyList()).isEmpty())
    }
}
