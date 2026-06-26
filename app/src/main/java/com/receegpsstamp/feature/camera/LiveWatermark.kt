package com.receegpsstamp.feature.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.receegpsstamp.data.local.AppSettings
import com.receegpsstamp.data.location.GpsInfo
import com.receegpsstamp.data.util.WatermarkLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Live watermark overlay for the camera preview.
 *
 * It mirrors the stamped photo (CameraViewModel.stampWatermark): sizes proportional to the frame
 * width, address on its own line, uniform line spacing. For landscape it draws a "gravity-oriented
 * frame" — the preview frame with width/height swapped — anchors the watermark to that frame's
 * bottom-left/right, then rotates the whole frame back onto the screen. Because the rotated frame
 * exactly overlays the preview, the watermark always stays INSIDE the frame and bottom-aligned,
 * with the text upright — matching the orientation-aware captured photo. Portrait is unchanged.
 */
@Composable
fun BoxScope.LiveWatermark(
    cfg: AppSettings,
    watermark: PhotoWatermark,
    companyName: String,
    distributorName: String,
    gps: GpsInfo?,
    deviceRotation: Int,
    frameWidth: Dp,
    frameHeight: Dp,
) {
    val d = LocalDensity.current
    val frameWpx = with(d) { frameWidth.toPx() }
    val isRight = cfg.wmPosition.contains("right", true)
    val landscape = deviceRotation == 90 || deviceRotation == 270
    val gravityW = if (landscape) frameHeight else frameWidth
    val gravityH = if (landscape) frameWidth else frameHeight

    val overlayBg = if (cfg.greyBackground) {
        val base = runCatching { Color(android.graphics.Color.parseColor(cfg.wmBgColor)) }.getOrDefault(Color.Black)
        base.copy(alpha = (cfg.wmBgOpacity.coerceIn(1, 10) / 10f))
    } else Color.Transparent

    val fontFactor = 0.7f + (cfg.wmFontSize.coerceIn(1, 10) - 1) * 0.1f
    val wmScale = frameWpx / 1080f * fontFactor
    val titleSp = with(d) { (22f * wmScale).toSp() }
    val bodySp = with(d) { (24f * wmScale).toSp() }
    val bodyLineSp = with(d) { (24f * wmScale * 1.35f).toSp() }

    // Gravity-oriented frame, rotated back onto the preview — keeps the watermark inside & bottom-aligned.
    // requiredSize (not size) so the landscape frame can be wider than the preview without being clamped
    // (clamping was pushing the watermark to the centre instead of the corner).
    Box(
        Modifier.align(Alignment.Center).requiredSize(gravityW, gravityH).rotate((-deviceRotation).toFloat()),
    ) {
        Box(
            Modifier.align(if (isRight) Alignment.BottomEnd else Alignment.BottomStart)
                .padding(with(d) { (16f * wmScale).toDp() })
                .background(overlayBg, RoundedCornerShape(with(d) { ((cfg.wmRadius.coerceIn(1, 10) - 1) * 4f * wmScale).toDp() }))
                .padding(with(d) { (15f * wmScale).toDp() }),
        ) {
            Column {
                val dateStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
                fun fieldValue(key: String): String? = when (key) {
                    "company" -> companyName.ifEmpty { null }
                    "distributor" -> distributorName.ifEmpty { null }
                    "shop" -> watermark.shopName.ifEmpty { null }
                    "city" -> watermark.city.ifEmpty { null }
                    "contact" -> watermark.contact.ifEmpty { null }
                    "status" -> watermark.status.ifEmpty { null }
                    "media" -> listOf(watermark.mediaType, watermark.creative, watermark.size).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { null }
                    "coordinates" -> gps?.let { "${"%.5f".format(it.lat)}, ${"%.5f".format(it.lng)}" }
                    "accuracy" -> gps?.let { "±${"%.0f".format(it.accuracy)}m" }
                    "datetime" -> dateStr
                    "address" -> gps?.address?.ifEmpty { null }
                    else -> null
                }
                val ordered = cfg.order().filter { cfg.enabled(it) }.mapNotNull { key -> fieldValue(key)?.let { key to it } }
                // Group fields into lines (2,3,2,3…); address gets its own line — same as the stamped photo.
                val shown = WatermarkLayout.groupLines(ordered).ifEmpty { listOf("RECCE GPS STAMP") }
                // Title + body all follow the Font colour setting; same lineHeight → uniform line gaps.
                val wmColor = runCatching { Color(android.graphics.Color.parseColor(cfg.wmTextColor)) }.getOrDefault(Color.White)
                shown.forEachIndexed { i, line ->
                    if (i == 0) {
                        Text(line, fontSize = titleSp, fontWeight = FontWeight.Bold, color = wmColor, lineHeight = bodyLineSp, maxLines = 1)
                    } else {
                        Text(line, fontSize = bodySp, color = wmColor, lineHeight = bodyLineSp)
                    }
                }
            }
        }
    }
}
