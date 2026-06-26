package com.receegpsstamp.feature.fleet

import com.receegpsstamp.data.model.Vehicle

/** Shared fleet-alert computation used by both the in-app banner and the background worker. */
object FleetAlerts {
    fun alertsFor(vehicles: List<Vehicle>): List<String> {
        val out = mutableListOf<String>()
        for (v in vehicles) {
            val tag = v.name.ifBlank { v.number }.ifBlank { "Vehicle" }
            val km = v.currentKm
            v.maintItems.forEach { mi ->
                if (mi.lastKm > 0 && mi.intervalKm > 0) {
                    val rem = mi.lastKm + mi.intervalKm - km
                    if (rem <= 0) out.add("$tag — ${mi.name.lowercase()} OVERDUE")
                    else if (rem <= 500) out.add("$tag — ${mi.name.lowercase()} due in $rem km")
                }
            }
            fun days(exp: Long): Long? = if (exp <= 0L) null else (exp - System.currentTimeMillis()) / 86_400_000L
            days(v.insuranceExpiry)?.let {
                if (it < 0) out.add("$tag — insurance EXPIRED") else if (it <= 15) out.add("$tag — insurance expires in $it days")
            }
            days(v.pucExpiry)?.let {
                if (it < 0) out.add("$tag — PUC EXPIRED") else if (it <= 15) out.add("$tag — PUC expires in $it days")
            }
            days(v.fitnessExpiry)?.let {
                if (it < 0) out.add("$tag — fitness EXPIRED") else if (it <= 15) out.add("$tag — fitness expires in $it days")
            }
        }
        return out
    }
}
