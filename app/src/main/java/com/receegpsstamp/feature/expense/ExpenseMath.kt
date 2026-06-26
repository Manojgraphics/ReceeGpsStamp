package com.receegpsstamp.feature.expense

import com.receegpsstamp.data.model.Expense

/**
 * Khatabook-style running totals for the expense list: money taken as an ADVANCE minus what was
 * SPENT (excluding company-paid expenses, which never touch the surveyor's balance) = what's still
 * owed back. Extracted from ExpenseScreen so it's unit-testable (see ExpenseMathTest).
 */
internal data class ExpenseTotals(
    val advance: Double,
    val spent: Double,
    val companyPaid: Double,
) {
    /** What the surveyor must return (>= 0) or has overspent (< 0). Company-paid is excluded. */
    val balance: Double get() = advance - spent
}

internal fun expenseTotalsOf(expenses: List<Expense>): ExpenseTotals {
    val advance = expenses.filter { it.kind == "ADVANCE" }.sumOf { it.amount }
    val spent = expenses.filter { it.kind == "EXPENSE" && it.paymentMode != "Company" }.sumOf { it.amount }
    val companyPaid = expenses.filter { it.kind == "EXPENSE" && it.paymentMode == "Company" }.sumOf { it.amount }
    return ExpenseTotals(advance, spent, companyPaid)
}

/** Distance / avg mileage / cost-per-km from fuel expenses; null when fewer than two odometer readings. */
internal data class FuelStats(val distance: Double, val avgMileage: Double, val costPerKm: Double)

internal fun fuelStatsOf(expenses: List<Expense>): FuelStats? {
    val fuels = expenses.filter { it.category == "Fuel" && it.odometer > 0 }.sortedBy { it.odometer }
    if (fuels.size < 2) return null
    val dist = fuels.last().odometer - fuels.first().odometer
    val litresAfterFirst = fuels.drop(1).sumOf { it.litres }
    val avg = if (litresAfterFirst > 0) dist / litresAfterFirst else 0.0
    // Cost counts ALL fuel spend (even fills with no odometer), divided by the measured distance.
    val fuelCost = expenses.filter { it.category == "Fuel" }.sumOf { it.amount }
    val perKm = if (dist > 0) fuelCost / dist else 0.0
    return FuelStats(dist, avg, perKm)
}
