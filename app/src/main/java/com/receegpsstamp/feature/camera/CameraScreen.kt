package com.receegpsstamp.feature.camera

import android.Manifest
import androidx.camera.core.CameraSelector
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.receegpsstamp.ui.theme.AppYellow
import com.receegpsstamp.ui.theme.AppYellowDark
import com.receegpsstamp.ui.theme.YellowContainer
import com.receegpsstamp.ui.theme.GpsLocked
import com.receegpsstamp.ui.theme.NeutralSurface
import com.receegpsstamp.ui.theme.NeutralSurfaceV
import com.receegpsstamp.ui.theme.NeutralText
import com.receegpsstamp.ui.theme.NeutralTextSoft
import com.receegpsstamp.ui.theme.RgsIcons
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Context stamped onto a captured photo. Media fields are non-empty only for media photos. */
data class PhotoWatermark(
    val shopName: String = "",
    val city: String = "",
    val contact: String = "",
    val status: String = "",
    val mediaType: String = "",
    val creative: String = "",
    val size: String = "",
)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    onBack: () -> Unit,
    onPhotoUsed: (String) -> Unit = {},
    companyName: String = "",
    distributorName: String = "",
    watermark: PhotoWatermark = PhotoWatermark(),
    viewModel: CameraViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // The camera is now an overlay backed by a reused ViewModel — start every open with a
    // clean slate so a previous capture's review screen doesn't reappear.
    LaunchedEffect(Unit) { viewModel.clearCaptureRef() }

    LaunchedEffect(companyName, distributorName) {
        viewModel.setWatermarkCompanyDistributor(companyName, distributorName)
        viewModel.setFolderName(distributorName.ifEmpty { companyName })
    }
    LaunchedEffect(watermark) {
        viewModel.setWatermarkContext(watermark.shopName, watermark.city, watermark.contact, watermark.status, watermark.mediaType, watermark.creative, watermark.size)
    }

    val permissions = rememberMultiplePermissionsState(
        listOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION),
    )

    LaunchedEffect(Unit) {
        if (!permissions.allPermissionsGranted) permissions.launchMultiplePermissionRequest()
    }

    if (!permissions.permissions.first { it.permission == Manifest.permission.CAMERA }.status.isGranted) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Camera permission required", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(AppYellow).clickable { permissions.launchMultiplePermissionRequest() }.padding(horizontal = 24.dp, vertical = 12.dp),
                ) { Text("Grant Permission", fontWeight = FontWeight.Bold, color = Color.Black) }
            }
        }
        return
    }

    if (state.error != null) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Error: ${state.error}", color = Color.Red, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(AppYellow).clickable { viewModel.retake() }.padding(horizontal = 24.dp, vertical = 12.dp),
                ) { Text("Try Again", fontWeight = FontWeight.Bold, color = Color.Black) }
            }
        }
        return
    }

    if (state.capturedFile != null) {
        ReviewScreen(
            filePath = state.capturedFile!!.absolutePath,
            onRetake = { viewModel.retake() },
            onUse = {
                val path = state.capturedFile!!.absolutePath
                viewModel.clearCaptureRef()
                onPhotoUsed(path)
            },
        )
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Track physical device orientation so overlays (watermark) stay gravity-upright even
    // though the UI itself is portrait-locked.
    var deviceRotation by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val listener = object : android.view.OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == android.view.OrientationEventListener.ORIENTATION_UNKNOWN) return
                deviceRotation = when {
                    orientation >= 315 || orientation < 45 -> 0
                    orientation < 135 -> 90
                    orientation < 225 -> 180
                    else -> 270
                }
            }
        }
        if (listener.canDetectOrientation()) listener.enable()
        onDispose { listener.disable() }
    }
    val cameraController = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    // Apply the chosen aspect ratio to both preview and capture, rebinding when it changes.
    val cameraSettings by viewModel.settingsFlow.collectAsStateWithLifecycle()
    LaunchedEffect(lifecycleOwner, cameraSettings.cameraRatio) {
        val strategy = if (cameraSettings.cameraRatio == "16:9")
            androidx.camera.core.resolutionselector.AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
        else androidx.camera.core.resolutionselector.AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
        val sel = androidx.camera.core.resolutionselector.ResolutionSelector.Builder()
            .setAspectRatioStrategy(strategy).build()
        cameraController.previewResolutionSelector = sel
        cameraController.imageCaptureResolutionSelector = sel
        cameraController.unbind()
        cameraController.bindToLifecycle(lifecycleOwner)
    }

    DisposableEffect(Unit) {
        onDispose { cameraController.unbind() }
    }

    var showSettings by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(false) }
    // Flash: 0 = off, 1 = auto, 2 = on
    var flashMode by remember { mutableIntStateOf(0) }
    LaunchedEffect(flashMode) {
        cameraController.imageCaptureFlashMode = when (flashMode) {
            1 -> androidx.camera.core.ImageCapture.FLASH_MODE_AUTO
            2 -> androidx.camera.core.ImageCapture.FLASH_MODE_ON
            else -> androidx.camera.core.ImageCapture.FLASH_MODE_OFF
        }
    }
    val lastPhoto by viewModel.lastPhoto.collectAsStateWithLifecycle()

    // Brightness — camera exposure compensation. Range read once the camera is bound.
    var expIndex by remember { mutableIntStateOf(0) }
    var expRange by remember { mutableStateOf(0..0) }
    LaunchedEffect(Unit) {
        repeat(25) {
            val st = cameraController.cameraInfo?.exposureState
            if (st != null && st.isExposureCompensationSupported &&
                st.exposureCompensationRange.lower < st.exposureCompensationRange.upper
            ) {
                expRange = st.exposureCompensationRange.lower..st.exposureCompensationRange.upper
                expIndex = st.exposureCompensationIndex
                return@LaunchedEffect
            }
            delay(120)
        }
    }

    // Volume Up/Down act as the shutter while the camera is open.
    val shutterFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { shutterFocus.requestFocus() } }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(shutterFocus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && (e.key == Key.VolumeUp || e.key == Key.VolumeDown)) {
                    if (!state.capturing) viewModel.capture(cameraController)
                    true
                } else false
            },
    ) {
        // Preview is constrained to the capture aspect ratio so the live view matches exactly
        // what gets saved (WYSIWYG) — the watermark sits in the same spot as in the photo.
        val cfg by viewModel.settingsFlow.collectAsStateWithLifecycle()
        val frameRatio = if (cfg.cameraRatio == "16:9") 9f / 16f else 3f / 4f
        // Nudge the preview frame up a touch so the watermark (at its bottom) clears the zoom pills.
        BoxWithConstraints(Modifier.align(Alignment.Center).offset(y = (-8).dp).fillMaxWidth().aspectRatio(frameRatio)) {
            val frameW = maxWidth
            val frameH = maxHeight
            // CameraX preview
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply { controller = cameraController }
                },
                modifier = Modifier.fillMaxSize(),
            )

            // Grid overlay
            if (showGrid) {
                Canvas(Modifier.fillMaxSize()) {
                    val gridPaint = Color.White.copy(0.3f)
                    val w = size.width; val h = size.height
                    drawLine(gridPaint, Offset(w / 3, 0f), Offset(w / 3, h), strokeWidth = 1f)
                    drawLine(gridPaint, Offset(2 * w / 3, 0f), Offset(2 * w / 3, h), strokeWidth = 1f)
                    drawLine(gridPaint, Offset(0f, h / 3), Offset(w, h / 3), strokeWidth = 1f)
                    drawLine(gridPaint, Offset(0f, 2 * h / 3), Offset(w, 2 * h / 3), strokeWidth = 1f)
                }
            }

            // Live watermark — extracted to LiveWatermark.kt (gravity-aware, mirrors the photo).
            // Photo-orientation lock: Portrait/Landscape pin the watermark to a fixed orientation
            // (live won't rotate with tilt); Auto follows the device.
            val effectiveRotation = when (cfg.captureOrientation) {
                "Portrait" -> 0
                "Landscape" -> 270
                else -> deviceRotation
            }
            LiveWatermark(
                cfg = cfg,
                watermark = watermark,
                companyName = companyName,
                distributorName = distributorName,
                gps = state.gps,
                deviceRotation = effectiveRotation,
                frameWidth = frameW,
                frameHeight = frameH,
            )
        }

        // Top bar — extends behind the status bar, content sits below it (no overlap).
        Row(
            Modifier.fillMaxWidth().background(Color.Black.copy(0.8f))
                .statusBarsPadding().padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CamIcon(RgsIcons.Back) { onBack() }
            Spacer(Modifier.width(6.dp))
            val gps = state.gps
            Row(
                Modifier.background(if (gps != null) GpsLocked else NeutralTextSoft, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("⌖", fontSize = 11.sp, color = Color.White)
                Spacer(Modifier.width(4.dp))
                Text(
                    if (state.gpsLoading) "Locating…" else if (gps != null) "±${"%.0f".format(gps.accuracy)}m" else "No GPS",
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White,
                )
            }
            Spacer(Modifier.weight(1f))
            val flashIcon = when (flashMode) { 1 -> RgsIcons.FlashAuto; 2 -> RgsIcons.FlashOn; else -> RgsIcons.FlashOff }
            CamIcon(flashIcon, tint = if (flashMode == 0) Color.White else AppYellow) { flashMode = (flashMode + 1) % 3 }
            CamIcon(RgsIcons.Refresh) { viewModel.refreshGps() }
            CamIcon(RgsIcons.Settings) { showSettings = true }
        }

        // Zoom pills — built from the camera's real zoom range so every option works.
        var zoomLevel by remember { mutableFloatStateOf(1f) }
        var minZoom by remember { mutableFloatStateOf(1f) }
        var maxZoom by remember { mutableFloatStateOf(4f) }
        LaunchedEffect(Unit) {
            repeat(25) {
                val zs = cameraController.zoomState.value
                if (zs != null) { minZoom = zs.minZoomRatio; maxZoom = zs.maxZoomRatio; return@LaunchedEffect }
                delay(120)
            }
        }
        val zoomPresets = buildList {
            // 0.6x is always shown; it zooms out on phones with an ultra-wide lens and
            // clamps to 1x on phones that don't have one (hardware limit).
            add(0.6f to "0.6x")
            add(1f to "1x")
            if (maxZoom >= 2f) add(2f to "2x")
            if (maxZoom >= 4f) add(4f to "4x")
        }

        // Zoom — vertical slider on the left edge (drag up = zoom in), mirrors the brightness one.
        val zoomSpan = maxZoom - minZoom
        if (zoomSpan > 0.05f) {
            val zfrac = ((zoomLevel - minZoom) / zoomSpan).coerceIn(0f, 1f)
            val trackH = 156.dp
            val thumb = 34.dp
            Box(
                Modifier.align(Alignment.CenterEnd).padding(end = 10.dp).width(thumb).height(trackH)
                    .pointerInput(minZoom, maxZoom) {
                        detectVerticalDragGestures { change, _ ->
                            change.consume()
                            val f = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                            val z = (minZoom + f * zoomSpan).coerceIn(minZoom, maxZoom)
                            zoomLevel = z
                            cameraController.setZoomRatio(z)
                        }
                    },
            ) {
                Box(
                    Modifier.align(Alignment.Center).width(3.dp).fillMaxHeight()
                        .background(Color.White.copy(0.30f), RoundedCornerShape(50)),
                )
                Box(
                    Modifier.align(Alignment.BottomCenter).width(3.dp)
                        .height(trackH * zfrac).background(AppYellow.copy(0.9f), RoundedCornerShape(50)),
                )
                Box(
                    Modifier.align(Alignment.TopCenter).offset(y = (trackH - thumb) * (1f - zfrac))
                        .size(thumb).clip(CircleShape).background(Color.Black.copy(0.55f))
                        .border(2.dp, AppYellow, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    val zLabel = (if (zoomLevel % 1f == 0f) "%.0f".format(zoomLevel) else "%.1f".format(zoomLevel)) + "x"
                    Text(
                        zLabel, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = AppYellow, maxLines = 1,
                        modifier = Modifier.rotate((-deviceRotation).toFloat()),
                    )
                }
            }
        }

        // Brightness / exposure — sleek vertical control on the right edge (drag the sun up/down).
        if (expRange.first < expRange.last) {
            val span = (expRange.last - expRange.first).toFloat()
            val frac = (expIndex - expRange.first) / span        // 0 = darkest, 1 = brightest
            val trackH = 156.dp
            val thumb = 30.dp
            Box(
                Modifier.align(Alignment.CenterStart).padding(start = 10.dp).width(thumb).height(trackH)
                    .pointerInput(expRange) {
                        detectVerticalDragGestures { change, _ ->
                            change.consume()
                            val f = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                            val v = (expRange.first + f * span).roundToInt().coerceIn(expRange.first, expRange.last)
                            if (v != expIndex) {
                                expIndex = v
                                cameraController.cameraControl?.setExposureCompensationIndex(v)
                            }
                        }
                    },
            ) {
                // Track
                Box(
                    Modifier.align(Alignment.Center).width(3.dp).fillMaxHeight()
                        .background(Color.White.copy(0.30f), RoundedCornerShape(50)),
                )
                // Active (bright) portion from the thumb up to the top
                Box(
                    Modifier.align(Alignment.TopCenter).width(3.dp)
                        .height(trackH * (1f - frac)).background(AppYellow.copy(0.9f), RoundedCornerShape(50)),
                )
                // Thumb with the sun glyph
                Box(
                    Modifier.align(Alignment.TopCenter)
                        .offset(y = (trackH - thumb) * (1f - frac))
                        .size(thumb).clip(CircleShape)
                        .background(Color.Black.copy(0.55f))
                        .border(2.dp, AppYellow, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(RgsIcons.Brightness, null, tint = AppYellow, modifier = Modifier.size(16.dp))
                }
            }
        }

        // Bottom controls — zoom pills sit inside the same translucent black bar as the capture row.
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(0.82f))
                .navigationBarsPadding().padding(horizontal = 26.dp).padding(top = 8.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Zoom pills
            Row(
                Modifier.padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                zoomPresets.forEach { (level, label) ->
                    val sel = zoomLevel == level
                    Box(
                        Modifier.size(34.dp).clip(CircleShape)
                            .background(if (sel) AppYellow else Color.White.copy(0.14f))
                            .clickable {
                                zoomLevel = level
                                cameraController.setZoomRatio(level.coerceIn(minZoom, maxZoom))
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            color = if (sel) Color.Black else Color.White,
                            // Rotate the label with gravity (same as the watermark) in landscape.
                            modifier = Modifier.rotate((-deviceRotation).toFloat()),
                        )
                    }
                }
            }
            // Capture row
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
            // Last-photo thumbnail (tap → back to the recce form)
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(8.dp))
                    .border(1.5.dp, Color.White.copy(0.6f), RoundedCornerShape(8.dp))
                    .background(Color.White.copy(0.12f))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                val lp = lastPhoto
                if (lp != null && java.io.File(lp).exists()) {
                    coil.compose.AsyncImage(
                        model = java.io.File(lp),
                        contentDescription = "Last photo",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                    )
                } else {
                    Icon(RgsIcons.Gallery, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }

            // Shutter — outer ring + inner disc (classic camera look)
            Box(
                Modifier.size(76.dp).clip(CircleShape)
                    .border(3.dp, Color.White, CircleShape)
                    .clickable(enabled = !state.capturing) { viewModel.capture(cameraController) }
                    .padding(6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.fillMaxSize().clip(CircleShape)
                        .background(if (state.capturing) Color.White.copy(0.45f) else Color.White),
                )
            }

            // Flip camera
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(0.12f))
                    .clickable {
                        cameraController.cameraSelector = if (cameraController.cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA)
                            CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                    },
                contentAlignment = Alignment.Center,
            ) { Icon(RgsIcons.Sync, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
            }
        }

        // Flash overlay
        AnimatedVisibility(state.showFlash, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Color.White))
            LaunchedEffect(Unit) { delay(200); viewModel.clearFlash() }
        }

        // Settings sheet
        if (showSettings) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(0.6f)).clickable { showSettings = false },
                contentAlignment = Alignment.BottomCenter,
            ) {
                Column(
                    Modifier.fillMaxWidth().background(NeutralSurface, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .clickable(enabled = false) {}.navigationBarsPadding().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Camera Settings", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = NeutralText)
                    SettingToggle("Grid overlay", showGrid) { showGrid = it }
                    // Aspect ratio
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Aspect ratio", fontSize = 14.sp, color = NeutralText, modifier = Modifier.weight(1f))
                        listOf("4:3", "16:9").forEach { r ->
                            val sel = cameraSettings.cameraRatio == r
                            Box(
                                Modifier.padding(start = 6.dp).clip(RoundedCornerShape(8.dp))
                                    .background(if (sel) YellowContainer else NeutralSurfaceV)
                                    .clickable { viewModel.setCameraRatio(r) }
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                            ) {
                                Text(r, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (sel) AppYellowDark else NeutralTextSoft)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.sp, color = NeutralText, modifier = Modifier.weight(1f))
        androidx.compose.material3.Switch(
            checked = checked, onCheckedChange = onCheckedChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = Color.Black, checkedTrackColor = AppYellow,
                uncheckedThumbColor = NeutralTextSoft, uncheckedTrackColor = NeutralSurfaceV,
            ),
        )
    }
}

@Composable
private fun CamIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color = Color.White, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun ReviewScreen(filePath: String, onRetake: () -> Unit, onUse: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Coil decodes off the main thread with automatic downsampling — avoids the
        // main-thread freeze / OOM that a full-res BitmapFactory.decodeFile caused.
        coil.compose.AsyncImage(
            model = java.io.File(filePath),
            contentDescription = "Captured photo",
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )

        // Top badge
        Box(
            Modifier.align(Alignment.TopCenter).padding(top = 48.dp)
                .background(Color.Black.copy(0.7f), RoundedCornerShape(50))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✓", color = AppYellow, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Text("Captured · GPS stamped", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Bottom actions
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Color.Black.copy(0.85f)).navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50))
                    .background(Color.White.copy(0.12f)).clickable { onRetake() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text("↺ Retake", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }

            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50))
                    .background(AppYellow).clickable { onUse() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Use Photo ✓", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        }
    }
}
