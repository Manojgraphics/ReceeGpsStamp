package com.receegpsstamp.data.sync

/**
 * Restore safety net. A corrupt/missing/empty cloud doc parses to an empty list (see
 * [FirestoreSync.parse]); applying that during a restore would BLANK a populated local collection
 * and lose the user's work. Rule: an empty incoming must never overwrite a non-empty local one —
 * parse-failure/empty ⇒ keep what we already have. A genuinely non-empty cloud collection still wins.
 *
 * Pure function — unit-tested in RestoreGuardTest.
 */
internal object RestoreGuard {
    fun <T> keep(incoming: List<T>, current: List<T>): List<T> =
        if (incoming.isEmpty() && current.isNotEmpty()) current else incoming
}
