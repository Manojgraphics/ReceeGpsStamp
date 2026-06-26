package com.receegpsstamp.feature.camera

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.receegpsstamp.data.location.GpsInfo
import com.receegpsstamp.data.location.LocationProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import javax.inject.Inject

data class CameraUiState(
    val gps: GpsInfo? = null,
    val gpsLoading: Boolean = true,
    val capturedFile: File? = null,
    val capturing: Boolean = false,
    val showFlash: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class CameraViewModel @Inject constructor(
    application: Application,
    private val locationProvider: LocationProvider,
    private val settingsStore: com.receegpsstamp.data.local.SettingsStore,
) : AndroidViewModel(application) {

    private val settings get() = settingsStore.settings.value
    val settingsFlow: StateFlow<com.receegpsstamp.data.local.AppSettings> = settingsStore.settings

    fun setCameraRatio(ratio: String) = settingsStore.update { it.copy(cameraRatio = ratio) }

    private val _state = MutableStateFlow(CameraUiState())
    val state: StateFlow<CameraUiState> = _state

    // Last saved photo path — drives the thumbnail in the camera's bottom-left corner.
    private val _lastPhoto = MutableStateFlow<String?>(null)
    val lastPhoto: StateFlow<String?> = _lastPhoto

    private val executor = Executors.newSingleThreadExecutor()

    init { refreshGps() }

    fun refreshGps() {
        viewModelScope.launch {
            _state.value = _state.value.copy(gpsLoading = true)
            val gps = locationProvider.getCurrentLocation()
            _state.value = _state.value.copy(gps = gps, gpsLoading = false)
        }
    }

    private var watermarkCompany = ""
    private var watermarkDistributor = ""
    private var watermarkShopName = ""
    private var watermarkCity = ""
    private var watermarkContact = ""
    private var watermarkStatus = ""
    private var watermarkMediaType = ""    // Media photos only
    private var watermarkCreative = ""     // Media photos only
    private var watermarkSize = ""         // Media photos only: size · qty
    private var folderName = ""

    fun setWatermarkCompanyDistributor(company: String, distributor: String) {
        watermarkCompany = company; watermarkDistributor = distributor
    }
    fun setFolderName(name: String) { folderName = name.trim().replace(Regex("[^a-zA-Z0-9_ \\-]"), "") }
    fun setWatermarkContext(shopName: String, city: String, contact: String, status: String, mediaType: String, creative: String, size: String) {
        watermarkShopName = shopName; watermarkCity = city; watermarkContact = contact
        watermarkStatus = status; watermarkMediaType = mediaType; watermarkCreative = creative; watermarkSize = size
    }

    fun capture(controller: LifecycleCameraController) {
        if (_state.value.capturing) return
        _state.value = _state.value.copy(capturing = true, showFlash = true)

        controller.takePicture(executor, object : androidx.camera.core.ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val raw = image.toBitmap()
                    // CameraController already reports the rotation for the held orientation, so a
                    // landscape hold yields a landscape photo. (Do NOT add deviceRotation — that
                    // double-rotates and forces landscape back to portrait.)
                    val rotation = image.imageInfo.rotationDegrees
                    image.close()
                    val held = if (rotation != 0) {
                        val m = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
                        Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
                            .also { if (it != raw) raw.recycle() }
                    } else raw
                    // Photo orientation setting: Auto = keep as held; Portrait/Landscape force it.
                    val want = settings.captureOrientation
                    val forceSwap = (want == "Portrait" && held.width > held.height) ||
                        (want == "Landscape" && held.height > held.width)
                    val bitmap = if (forceSwap) {
                        val m = android.graphics.Matrix().apply { postRotate(90f) }
                        Bitmap.createBitmap(held, 0, 0, held.width, held.height, m, true)
                            .also { if (it != held) held.recycle() }
                    } else held

                    // Optionally keep a clean, un-stamped copy in the gallery.
                    if (settings.saveNoWatermarkCopy) saveToGalleryOnly(bitmap)

                    val stamped = stampWatermark(bitmap)
                    val file = saveToFile(stamped)
                    bitmap.recycle()
                    stamped.recycle()

                    _lastPhoto.value = file.absolutePath
                    viewModelScope.launch {
                        _state.value = _state.value.copy(capturedFile = file, capturing = false, showFlash = false)
                    }
                } catch (e: Throwable) {  // includes OutOfMemoryError — show error, never crash
                    try { image.close() } catch (_: Throwable) {}
                    System.gc()
                    viewModelScope.launch {
                        _state.value = _state.value.copy(error = "Save failed: ${e.message}", capturing = false, showFlash = false)
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                viewModelScope.launch {
                    _state.value = _state.value.copy(error = exception.message, capturing = false, showFlash = false)
                }
            }
        })
    }

    fun retake() {
        _state.value.capturedFile?.delete()
        _state.value = _state.value.copy(capturedFile = null, error = null)
    }

    /**
     * Clears the captured-file reference WITHOUT deleting the file. Used when the photo has
     * been consumed (Use Photo) or when re-opening the camera overlay, so a reused ViewModel
     * (now scoped to MAIN) doesn't immediately show a stale review of the previous photo.
     */
    fun clearCaptureRef() {
        _state.value = _state.value.copy(capturedFile = null, error = null)
    }

    fun clearFlash() {
        _state.value = _state.value.copy(showFlash = false)
    }

    private fun colorOf(name: String): Int = try {
        when (name.lowercase()) {
            "white" -> Color.WHITE
            "black" -> Color.BLACK
            "yellow" -> Color.rgb(255, 196, 0)
            else -> Color.parseColor(name)
        }
    } catch (_: Throwable) { Color.WHITE }

    /** Display value for a watermark field key, or null if empty/unavailable. */
    private fun fieldValue(key: String, gps: GpsInfo?, dateStr: String): String? = when (key) {
        "company" -> watermarkCompany.ifEmpty { null }
        "distributor" -> watermarkDistributor.ifEmpty { null }
        "shop" -> watermarkShopName.ifEmpty { null }
        "city" -> watermarkCity.ifEmpty { null }
        "contact" -> watermarkContact.ifEmpty { null }
        "status" -> watermarkStatus.ifEmpty { null }
        "media" -> listOf(watermarkMediaType, watermarkCreative, watermarkSize).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { null }
        "coordinates" -> gps?.let { "${"%.5f".format(it.lat)}, ${"%.5f".format(it.lng)}" }
        "accuracy" -> gps?.let { "±${"%.0f".format(it.accuracy)}m" }
        "datetime" -> dateStr
        "address" -> gps?.address?.ifEmpty { null }
        else -> null
    }

    private fun stampWatermark(source: Bitmap): Bitmap {
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val w = result.width.toFloat()
        val h = result.height.toFloat()
        val s = settings
        // Font size 1..5 → 0.7x .. 1.3x of the base size.
        val fontFactor = 0.7f + (s.wmFontSize.coerceIn(1, 10) - 1) * 0.1f
        // Scale by the SHORT side so the watermark is the same proportional size in portrait AND
        // landscape — and matches the live preview, which scales to the (short-side) frame width.
        val scale = (minOf(w, h) / 1080f) * fontFactor
        // Opacity 1..10 → 10% .. 100% of full alpha.
        val bgAlpha = (s.wmBgOpacity.coerceIn(1, 10) / 10f * 255f).toInt()
        val bgRgb = colorOf(s.wmBgColor)
        val textRgb = colorOf(s.wmTextColor)

        val bgPaint = Paint().apply {
            color = Color.argb(bgAlpha, Color.red(bgRgb), Color.green(bgRgb), Color.blue(bgRgb))
        }
        val titlePaint = Paint().apply {
            color = textRgb
            textSize = 22f * scale
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
            letterSpacing = 0.04f
        }
        val bodyPaint = Paint().apply {
            color = textRgb
            textSize = 24f * scale
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }

        val gps = _state.value.gps
        val dateStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
        // Build watermark lines from the user's field order — enabled, non-empty fields only.
        val ordered = s.order().filter { s.enabled(it) }.mapNotNull { key -> fieldValue(key, gps, dateStr)?.let { key to it } }
        // Group fields into lines (2,3,2,3…); address gets its own line. Line 1 is the bold title.
        val grouped = com.receegpsstamp.data.util.WatermarkLayout.groupLines(ordered)
        val title = grouped.firstOrNull() ?: "RECCE GPS STAMP"
        val lines = if (grouped.isEmpty()) mutableListOf() else grouped.drop(1).toMutableList()

        val lineH = bodyPaint.textSize * 1.35f
        val padding = 15f * scale
        val edge = 16f * scale
        // No extra title gap → the title-to-body spacing is just one line height, the same as the
        // spacing between body lines (uniform).
        val titleGap = 0f
        val boxH = padding + titlePaint.textSize + titleGap + lineH * lines.size + padding
        // Size the box to the widest line (+ padding) instead of a fixed 88% — keeps the
        // watermark compact and proportional regardless of how many fields are shown.
        val contentW = maxOf(
            titlePaint.measureText(title),
            lines.maxOfOrNull { bodyPaint.measureText(it) } ?: 0f,
        )
        val boxW = (contentW + 2 * padding).coerceIn(0f, w - 2 * edge)
        // Position the box at the chosen corner.
        val left = if (s.wmPosition.contains("right", true)) w - boxW - edge else edge
        val top = if (s.wmPosition.contains("top", true)) edge else h - boxH - edge

        if (s.greyBackground) {
            // Radius 1 (square) .. 10 (round).
            val cornerR = (s.wmRadius.coerceIn(1, 10) - 1) * 4f * scale
            canvas.drawRoundRect(left, top, left + boxW, top + boxH, cornerR, cornerR, bgPaint)
        }

        var y = top + padding + titlePaint.textSize
        canvas.drawText(title, left + padding, y, titlePaint)
        y += titleGap

        lines.forEach { line ->
            y += lineH
            canvas.drawText(line, left + padding, y, bodyPaint)
        }

        return result
    }

    private fun saveToFile(bitmap: Bitmap): File {
        val app = getApplication<Application>()
        val fileName = "RGS_${System.currentTimeMillis()}.jpg"
        val subFolder = if (folderName.isNotBlank()) folderName else "General"

        // Save to app-private storage (for in-app use & sharing). Compress once.
        val dir = File(app.getExternalFilesDir(null), "Photos/$subFolder")
        dir.mkdirs()
        val file = File(dir, fileName)
        val quality = settings.photoQuality.coerceIn(50, 100)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
        copyToGallery(file, fileName, subFolder)
        return file
    }

    /** Saves a clean, un-stamped copy straight to the device gallery (no app-private copy). */
    private fun saveToGalleryOnly(bitmap: Bitmap) {
        try {
            val app = getApplication<Application>()
            val fileName = "RGS_orig_${System.currentTimeMillis()}.jpg"
            val tmp = File(app.cacheDir, fileName)
            FileOutputStream(tmp).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, settings.photoQuality.coerceIn(50, 100), out)
            }
            copyToGallery(tmp, fileName, if (folderName.isNotBlank()) folderName else "General")
            tmp.delete()
        } catch (_: Throwable) { }
    }

    /**
     * Mirror a written file to the device gallery by COPYING bytes — avoids a second full-bitmap
     * compression (lower peak memory = fewer OOM crashes).
     */
    private fun copyToGallery(file: File, fileName: String, subFolder: String) {
        try {
            val app = getApplication<Application>()
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/ReceeGpsStamp/$subFolder")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = app.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                app.contentResolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    app.contentResolver.update(uri, values, null, null)
                }
            }
        } catch (_: Throwable) { }
    }
}
