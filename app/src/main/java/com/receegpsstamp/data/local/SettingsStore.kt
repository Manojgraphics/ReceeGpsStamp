package com.receegpsstamp.data.local

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** User-configurable app settings (watermark fields, photo quality, etc.) persisted locally. */
data class AppSettings(
    // Which fields appear in the photo watermark
    val wmCompany: Boolean = true,
    val wmDistributor: Boolean = true,
    val wmShop: Boolean = true,
    val wmCity: Boolean = true,
    val wmContact: Boolean = true,
    val wmStatus: Boolean = true,
    val wmMedia: Boolean = true,
    val wmCreative: Boolean = true,
    val wmSize: Boolean = true,
    val wmCoordinates: Boolean = true,
    val wmAccuracy: Boolean = true,
    val wmDateTime: Boolean = true,
    val wmAddress: Boolean = true,
    // Display order of the watermark fields (top to bottom). null = default order.
    val wmOrder: List<String>? = null,
    // Watermark style
    val wmFontSize: Int = 6,               // 1 (small) … 10 (large); default 6
    val wmTextColor: String = "#FFFFFF",   // hex colour
    val wmPosition: String = "Bottom left", // Bottom left | Bottom right | Top left | Top right
    val greyBackground: Boolean = true,    // watermark background box on/off
    val wmBgColor: String = "#000000",     // hex colour
    val wmRadius: Int = 3,                  // box corner radius 1 (square) … 5 (round)
    val wmBgOpacity: Int = 6,              // 1..10 → 10% … 100% opaque; default 6 (60%)
    val greyTransparency: Int = 3,         // legacy (unused) — kept for back-compat
    // Photo
    val photoQuality: Int = 95,            // JPEG compression 50–100
    val cameraRatio: String = "4:3",       // "4:3" or "16:9"
    val captureOrientation: String = "Auto", // "Auto" (follow tilt) | "Portrait" | "Landscape"
    val saveNoWatermarkCopy: Boolean = false,
    val saveNoMarkupCopy: Boolean = false, // on edit, also keep a clean (un-marked) copy in the gallery
    val haptics: Boolean = true,           // vibration on capture & save (default ON)
    val autoBackup: Boolean = false,       // upload photos to Drive after each recce
    val driveUploadQuality: Int = 80,      // re-compress photos to this JPEG quality before Drive upload; 100 = original
    // WhatsApp sharing (all default ON — see raw-JSON backfill in load() for older files)
    val whatsappShare: Boolean = true,     // after save, open the WhatsApp share sheet
    val waIncludePhotos: Boolean = true,   // attach the captured photos
    val waIncludeLocation: Boolean = true, // include GPS + Google Maps link
    val waIncludeRecceBy: Boolean = true,  // include "Recce by: <name>" line
    val waBusiness: Boolean = false,       // open WhatsApp Business first instead of regular WhatsApp
    // Report exports — running serial that ends each Excel filename (Company_Distributor_City_<serial>).
    val exportSerial: Int = 0,
    // One-time flag: the new watermark defaults (font 6, bg 60%) have been applied to older saved files.
    val wmDefaultsV2: Boolean = false,
    // ── PDF report options ──
    val reportPageNumbers: Boolean = true,   // show "X / N" at the bottom of each page
    val reportJpegQuality: Int = 90,         // photo JPEG quality 70..100 (size vs sharpness)
    val reportLogoPath: String = "",         // company logo file (bottom-right of each page); blank = none
) {
    /** Effective field order — falls back to default and appends any missing keys. */
    fun order(): List<String> {
        val base = wmOrder ?: DEFAULT_WM_ORDER
        return base.filter { it in DEFAULT_WM_ORDER } + DEFAULT_WM_ORDER.filter { it !in base }
    }

    /** Whether a given field key is enabled. */
    fun enabled(key: String): Boolean = when (key) {
        "company" -> wmCompany
        "distributor" -> wmDistributor
        "shop" -> wmShop
        "city" -> wmCity
        "contact" -> wmContact
        "status" -> wmStatus
        "media" -> wmMedia
        "coordinates" -> wmCoordinates; "accuracy" -> wmAccuracy
        "datetime" -> wmDateTime; "address" -> wmAddress
        else -> false
    }

    companion object {
        // Each of Company, Distributor, Shop, City, Contact is its own field. "media" combines
        // media type + creative + size + qty into one line.
        val DEFAULT_WM_ORDER = listOf(
            "company", "distributor", "shop", "city", "contact",
            "status", "media",
            "coordinates", "accuracy", "datetime", "address",
        )
        val FIELD_LABELS = mapOf(
            "company" to "Company name",
            "distributor" to "Distributor name",
            "shop" to "Shop name",
            "city" to "City",
            "contact" to "Contact",
            "status" to "Recce status",
            "media" to "Media",
            "coordinates" to "GPS coordinates",
            "accuracy" to "Accuracy (m)", "datetime" to "Date & time", "address" to "Full address",
        )
        // No-longer-valid keys — if a saved order still has these, reset to the new default.
        val LEGACY_WM_KEYS = setOf("companyDistributor", "shopCityContact", "creative", "size")
    }
}

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val file = File(context.filesDir, "rgs_settings.json")
    private val gson = Gson()
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _settings = MutableStateFlow(migrate(load()))
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun load(): AppSettings = try {
        if (file.exists()) {
            val text = file.readText()
            val s = gson.fromJson(text, AppSettings::class.java) ?: AppSettings()
            // Gson ignores Kotlin defaults for fields missing from older files — backfill them.
            // For default-ON booleans, detect old files (key absent) so they stay ON, not false.
            val hasWa = text.contains("whatsappShare")
            s.copy(
                wmOrder = if (s.wmOrder?.any { it in AppSettings.LEGACY_WM_KEYS } == true) null else s.wmOrder,
                wmTextColor = normColor(s.wmTextColor, "#FFFFFF"),
                wmBgColor = normColor(s.wmBgColor, "#000000"),
                wmRadius = if (s.wmRadius in 1..10) s.wmRadius else 3,
                wmBgOpacity = if (s.wmBgOpacity in 1..10) s.wmBgOpacity else 6,
                wmFontSize = if (s.wmFontSize in 1..10) s.wmFontSize else 6,
                reportJpegQuality = if (text.contains("reportJpegQuality")) s.reportJpegQuality.coerceIn(70, 100) else 90,
                driveUploadQuality = if (text.contains("driveUploadQuality")) s.driveUploadQuality.coerceIn(50, 100) else 80,
                reportPageNumbers = if (text.contains("reportPageNumbers")) s.reportPageNumbers else true,
                // "Top" positions were removed — migrate any saved top value to bottom-left.
                wmPosition = s.wmPosition.ifBlankOrNull("Bottom left").let { if (it.contains("top", true)) "Bottom left" else it },
                cameraRatio = s.cameraRatio.ifBlankOrNull("4:3"),
                captureOrientation = s.captureOrientation.ifBlankOrNull("Auto"),
                haptics = if (text.contains("haptics")) s.haptics else true,
                whatsappShare = if (hasWa) s.whatsappShare else true,
                waIncludePhotos = if (hasWa) s.waIncludePhotos else true,
                waIncludeLocation = if (hasWa) s.waIncludeLocation else true,
                waIncludeRecceBy = if (hasWa) s.waIncludeRecceBy else true,
            )
        } else AppSettings()
    } catch (_: Throwable) { AppSettings() }

    @Suppress("USELESS_ELVIS")
    private fun String?.ifBlankOrNull(default: String): String =
        (this ?: default).ifBlank { default }

    // Migrate legacy named colours to hex; fall back to default when missing.
    @Suppress("USELESS_ELVIS")
    private fun normColor(c: String?, default: String): String = when ((c ?: "").lowercase()) {
        "" -> default
        "white" -> "#FFFFFF"
        "black" -> "#000000"
        "yellow" -> "#FFC400"
        else -> c ?: default
    }

    // One-time bump of the previous watermark defaults (font 3, bg 100%) to the new ones (6, 60%),
    // applied to older saved files. Runs once (gated by wmDefaultsV2); later user changes stick.
    private fun migrate(s: AppSettings): AppSettings {
        if (s.wmDefaultsV2) return s
        val m = s.copy(
            wmFontSize = if (s.wmFontSize == 3) 6 else s.wmFontSize,
            wmBgOpacity = if (s.wmBgOpacity == 10) 6 else s.wmBgOpacity,
            wmDefaultsV2 = true,
        )
        persist(m)   // record the bump + flag so it never re-applies
        return m
    }

    private fun persist(s: AppSettings) {
        ioScope.launch {                       // disk write off the main thread
            try {
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(gson.toJson(s))
                tmp.renameTo(file)
            } catch (_: Throwable) { }
        }
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        _settings.value = next                 // instant; UI reacts immediately
        persist(next)
    }
}
