package com.receegpsstamp.feature.fleet

import com.receegpsstamp.data.model.FuelLog
import com.receegpsstamp.data.model.Vehicle

// ── Status computation ── the fuel log's odometer drives every km-based alert.
// Extracted from FleetScreen so it's unit-testable (see FleetMathTest). Pure except statusOf's
// document-expiry days, which read the current time. NOTE: alertsOf mirrors [FleetAlerts.alertsFor]
// (the worker/banner path); keep the two in sync until they're unified.

internal data class ItemDue(val name: String, val intervalKm: Int, val recorded: Boolean, val remainingKm: Int)

internal data class Status(
    val km: Int, val economy: Double,
    val items: List<ItemDue>,
    val insDays: Long?, val pucDays: Long?, val fitDays: Long?,
)

/** Tank-to-tank fuel economy (km/L): distance between first & last fill ÷ litres burned after the first fill. */
internal fun economyOf(logs: List<FuelLog>): Double {
    val s = logs.filter { it.odometer > 0 && it.litres > 0 }.sortedBy { it.odometer }
    if (s.size < 2) return 0.0
    val dist = (s.last().odometer - s.first().odometer).toDouble()
    val litres = s.sumOf { it.litres } - s.first().litres
    return if (litres > 0 && dist > 0) dist / litres else 0.0
}

internal fun statusOf(v: Vehicle, fuel: List<FuelLog>): Status {
    val km = v.currentKm
    val items = v.maintItems.map { mi ->
        val recorded = mi.lastKm > 0 && mi.intervalKm > 0
        ItemDue(mi.name, mi.intervalKm, recorded, if (recorded) mi.lastKm + mi.intervalKm - km else 0)
    }
    fun days(exp: Long): Long? = if (exp <= 0L) null else (exp - System.currentTimeMillis()) / 86_400_000L
    return Status(km, economyOf(fuel), items, days(v.insuranceExpiry), days(v.pucExpiry), days(v.fitnessExpiry))
}

internal fun alertsOf(v: Vehicle, st: Status): List<String> {
    val a = mutableListOf<String>()
    val tag = v.name.ifBlank { v.number }.ifBlank { "Vehicle" }
    st.items.forEach { d ->
        if (d.recorded) {
            if (d.remainingKm <= 0) a.add("$tag — ${d.name.lowercase()} OVERDUE")
            else if (d.remainingKm <= 500) a.add("$tag — ${d.name.lowercase()} due in ${d.remainingKm} km")
        }
    }
    st.insDays?.let { if (it < 0) a.add("$tag — insurance EXPIRED") else if (it <= 15) a.add("$tag — insurance expires in $it days") }
    st.pucDays?.let { if (it < 0) a.add("$tag — PUC EXPIRED") else if (it <= 15) a.add("$tag — PUC expires in $it days") }
    st.fitDays?.let { if (it < 0) a.add("$tag — fitness EXPIRED") else if (it <= 15) a.add("$tag — fitness expires in $it days") }
    return a
}
