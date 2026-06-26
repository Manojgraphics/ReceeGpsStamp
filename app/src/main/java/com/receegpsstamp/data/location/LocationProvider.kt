package com.receegpsstamp.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class GpsInfo(
    val lat: Double,
    val lng: Double,
    val accuracy: Float,
    val bearing: Float,
    val address: String,   // full formatted address (street, area, city, state…)
    val city: String = "", // locality / city only
)

@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    /** Instant best-effort lat/lng from the cached fix — for stamping recce coordinates without waiting. */
    suspend fun lastCoordinates(): Pair<Double, Double>? {
        val loc = lastLocation() ?: return null
        return loc.latitude to loc.longitude
    }

    // The most recent fully-geocoded fix (set by getCurrentLocation at camera/form time). Used as a
    // reliable address fallback when the save-time geocode fails — e.g. the site was offline.
    @Volatile private var cachedGps: GpsInfo? = null

    /** Last fix as (lat, lng, full address) — geocoded at save time; falls back to the camera/form address. */
    suspend fun lastFix(): Triple<Double, Double, String>? {
        val cached = cachedGps
        val loc = lastLocation() ?: return cached?.let { Triple(it.lat, it.lng, it.address) }
        var address = withContext(Dispatchers.IO) { reverseGeocode(loc.latitude, loc.longitude).first }
        if (address.isBlank()) address = cached?.address ?: ""   // offline / geocoder failed → reuse captured address
        return Triple(loc.latitude, loc.longitude, address)
    }

    suspend fun getCurrentLocation(): GpsInfo? {
        val loc = awaitLocation() ?: return null
        // Reverse geocoding is a synchronous blocking network call — run it off the main thread
        // so it never freezes the UI (this was causing ANR / white-screen freezes).
        val (address, city) = withContext(Dispatchers.IO) { reverseGeocode(loc.latitude, loc.longitude) }
        val info = GpsInfo(loc.latitude, loc.longitude, loc.accuracy, loc.bearing, address, city)
        if (address.isNotBlank()) cachedGps = info   // remember the address for the recce save
        return info
    }

    @SuppressLint("MissingPermission")
    private suspend fun awaitLocation(): Location? {
        // Fast path: use the cached last known fix if it's recent (< 2 min). This is instant and
        // covers the common case; only fall back to a (slower) fresh fix when there isn't one.
        lastLocation()?.let { last ->
            val ageMs = System.currentTimeMillis() - last.time
            if (ageMs in 0..120_000) return last
        }
        return freshLocation()
    }

    @SuppressLint("MissingPermission")
    private suspend fun lastLocation(): Location? = suspendCancellableCoroutine { cont ->
        client.lastLocation
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resume(null) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun freshLocation(): Location? = suspendCancellableCoroutine { cont ->
        val cts = CancellationTokenSource()
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            .addOnSuccessListener { loc -> cont.resume(loc) }
            .addOnFailureListener { cont.resume(null) }
        cont.invokeOnCancellation { cts.cancel() }
    }

    /** Returns (full address, city). Full address is the complete formatted line; city is the locality. */
    @Suppress("DEPRECATION")
    private fun reverseGeocode(lat: Double, lng: Double): Pair<String, String> {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val results = geocoder.getFromLocation(lat, lng, 1)
            if (!results.isNullOrEmpty()) {
                val a = results[0]
                val city = a.locality ?: a.subLocality ?: a.subAdminArea ?: a.adminArea ?: ""
                // Prefer the geocoder's full formatted line; otherwise assemble from parts.
                val full = (a.getAddressLine(0) ?: listOfNotNull(
                    a.subThoroughfare, a.thoroughfare, a.subLocality, a.locality,
                    a.subAdminArea, a.adminArea, a.postalCode, a.countryName,
                ).distinct().joinToString(", ")).ifBlank { city }
                full to city
            } else "" to ""
        } catch (_: Exception) { "" to "" }
    }
}
