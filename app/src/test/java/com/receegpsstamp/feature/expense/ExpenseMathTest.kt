package com.receegpsstamp.feature.expense

import com.receegpsstamp.data.model.Expense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guards [expenseTotalsOf] (Khatabook advance − spent balance, with company-paid excluded) and
 * [fuelStatsOf] (distance / mileage / cost-per-km). These feed the surveyor's reimbursement balance
 * and the fuel card — wrong sums here mean wrong money owed.
 */
class ExpenseMathTest {

    private fun exp(kind: String, amount: Double, paymentMode: String = "Cash", category: String = "Misc") =
        Expense(kind = kind, amount = amount, paymentMode = paymentMode, category = category)

    private fun fuelExp(odometer: Double, litres: Double, amount: Double) =
        Expense(kind = "EXPENSE", category = "Fuel", odometer = odometer, litres = litres, amount = amount)

    // ── expenseTotalsOf / balance ──

    @Test fun balance_is_advance_minus_spent() {
        val totals = expenseTotalsOf(listOf(
            exp("ADVANCE", 5000.0),
            exp("EXPENSE", 1200.0),
            exp("EXPENSE", 800.0),
        ))
        assertEquals(5000.0, totals.advance, 1e-9)
        assertEquals(2000.0, totals.spent, 1e-9)
        assertEquals(0.0, totals.companyPaid, 1e-9)
        assertEquals(3000.0, totals.balance, 1e-9)
    }

    @Test fun company_paid_is_excluded_from_spent_and_balance() {
        val totals = expenseTotalsOf(listOf(
            exp("ADVANCE", 5000.0),
            exp("EXPENSE", 1000.0, paymentMode = "Cash"),
            exp("EXPENSE", 3000.0, paymentMode = "Company"),
        ))
        assertEquals(1000.0, totals.spent, 1e-9)        // company-paid not counted as spent
        assertEquals(3000.0, totals.companyPaid, 1e-9)
        assertEquals(4000.0, totals.balance, 1e-9)      // 5000 − 1000, company-paid left out
    }

    @Test fun overspending_gives_a_negative_balance() {
        val totals = expenseTotalsOf(listOf(exp("ADVANCE", 1000.0), exp("EXPENSE", 1500.0)))
        assertEquals(-500.0, totals.balance, 1e-9)
    }

    @Test fun advance_only_counts_advance_kind() {
        val totals = expenseTotalsOf(listOf(exp("EXPENSE", 700.0), exp("ADVANCE", 200.0)))
        assertEquals(200.0, totals.advance, 1e-9)
        assertEquals(700.0, totals.spent, 1e-9)
    }

    @Test fun empty_totals_are_zero() {
        val totals = expenseTotalsOf(emptyList())
        assertEquals(0.0, totals.advance, 1e-9)
        assertEquals(0.0, totals.spent, 1e-9)
        assertEquals(0.0, totals.companyPaid, 1e-9)
        assertEquals(0.0, totals.balance, 1e-9)
    }

    // ── fuelStatsOf ──

    @Test fun fuel_stats_null_with_fewer_than_two_readings() {
        assertNull(fuelStatsOf(emptyList()))
        assertNull(fuelStatsOf(listOf(fuelExp(1000.0, 10.0, 900.0))))
    }

    @Test fun fuel_distance_mileage_and_cost_per_km() {
        val s = fuelStatsOf(listOf(fuelExp(1000.0, 10.0, 900.0), fuelExp(1500.0, 10.0, 950.0)))!!
        assertEquals(500.0, s.distance, 1e-9)           // 1500 − 1000
        assertEquals(50.0, s.avgMileage, 1e-9)          // 500 km / 10 L after first fill
        assertEquals(3.7, s.costPerKm, 1e-9)            // (900 + 950) / 500
    }

    @Test fun fuel_cost_counts_fills_without_odometer_but_distance_does_not() {
        val s = fuelStatsOf(listOf(
            fuelExp(1000.0, 10.0, 900.0),
            fuelExp(1500.0, 10.0, 950.0),
            fuelExp(0.0, 0.0, 100.0),   // no odometer → out of distance, but its ₹ still counts
        ))!!
        assertEquals(500.0, s.distance, 1e-9)
        assertEquals(3.9, s.costPerKm, 1e-9)            // (900 + 950 + 100) / 500
    }

    @Test fun fuel_ignores_non_fuel_expenses() {
        val s = fuelStatsOf(listOf(
            fuelExp(1000.0, 10.0, 900.0),
            fuelExp(1500.0, 10.0, 950.0),
            Expense(kind = "EXPENSE", category = "Lunch", amount = 300.0, odometer = 2000.0),
        ))!!
        assertEquals(500.0, s.distance, 1e-9)           // lunch odometer ignored
        assertEquals(3.7, s.costPerKm, 1e-9)            // lunch ₹ not in fuel cost
    }

    @Test fun fuel_sorts_by_odometer_regardless_of_order() {
        val s = fuelStatsOf(listOf(fuelExp(1500.0, 10.0, 950.0), fuelExp(1000.0, 10.0, 900.0)))!!
        assertEquals(500.0, s.distance, 1e-9)
        assertEquals(50.0, s.avgMileage, 1e-9)
    }

    @Test fun fuel_zero_distance_gives_zero_avg_and_cost() {
        val s = fuelStatsOf(listOf(fuelExp(1000.0, 10.0, 900.0), fuelExp(1000.0, 10.0, 950.0)))!!
        assertEquals(0.0, s.distance, 1e-9)
        assertEquals(0.0, s.avgMileage, 1e-9)
        assertEquals(0.0, s.costPerKm, 1e-9)
    }
}
