package com.receegpsstamp.feature.recce

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.receegpsstamp.navigation.Routes
import com.receegpsstamp.ui.components.CompactDropdown
import com.receegpsstamp.ui.components.CompactTextField
import com.receegpsstamp.ui.components.PrimaryButton
import com.receegpsstamp.ui.components.RgsCard
import com.receegpsstamp.ui.components.RgsTopBar
import com.receegpsstamp.ui.components.SecondaryButton
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.receegpsstamp.data.model.MediaItem
import com.receegpsstamp.data.model.Shop
import java.io.File

import com.receegpsstamp.data.util.MediaMath
import com.receegpsstamp.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StartRecceScreen(
    onMenuClick: () -> Unit = {},
    onCameraClick: (com.receegpsstamp.feature.camera.PhotoWatermark) -> Unit = {},
    shops: List<Shop> = emptyList(),
    distributorName: String = "",
    companyName: String = "",
    waGroup: String = "",
    userName: String = "",
    selectedDistributorId: String = "",
    shareToWhatsApp: Boolean = true,
    waIncludePhotos: Boolean = true,
    waIncludeLocation: Boolean = true,
    waIncludeRecceBy: Boolean = true,
    preferWhatsAppBusiness: Boolean = false,
    hapticsEnabled: Boolean = true,
    onSaveRecce: (shopId: String, status: String, remark: String, media: List<MediaItem>) -> Unit = { _, _, _, _ -> },
    onSaveRecceNewShop: (shopName: String, shopCity: String, shopContact: String, status: String, remark: String, media: List<MediaItem>) -> Unit = { _, _, _, _, _, _ -> },
    capturedPhotos: List<String> = emptyList(),
    onClearPhotos: () -> Unit = {},
    onRemovePhoto: (String) -> Unit = {},
    onEditPhoto: (String) -> Unit = {},
    drafts: List<com.receegpsstamp.data.model.RecceDraft> = emptyList(),
    onSaveDraft: (com.receegpsstamp.data.model.RecceDraft) -> Unit = {},
    onDeleteDraft: (id: String, deletePhotos: Boolean) -> Unit = { _, _ -> },
    onLoadPhotos: (List<String>) -> Unit = {},
    // Edit flow — a recce reopened from the Dashboard ("Update Store Details").
    recceToEdit: com.receegpsstamp.data.model.RecceEntry? = null,
    onConsumeEdit: () -> Unit = {},
    onUpdateRecce: (recceId: String, status: String, remark: String, media: List<MediaItem>) -> Unit = { _, _, _, _ -> },
    // Shop work-list — a Pending shop tapped "Start ›" in the Project tab; pre-fill the form for it,
    // then (if it came from the list) return to the list after saving.
    preselectShopId: String? = null,
    onConsumePreselect: () -> Unit = {},
    onAfterSave: () -> Unit = {},
    creatives: List<String> = emptyList(),
    projectCreative: String = "",
    onSetProjectCreative: (String) -> Unit = {},
    mediaTypes: List<String> = emptyList(),
    onFetchLocation: ((city: String, lat: Double, lng: Double) -> Unit) -> Unit = {},
) {
    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    var remarkFromVoice by remember { mutableStateOf("") }
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) remarkFromVoice = spoken
        }
    }
    // NOTE: form state uses rememberSaveable so it survives navigating to the full-screen
    // Camera destination (which disposes this screen) and back. Plain remember would reset
    // the form to blank on return, collapsing the save button.
    var selectedShopId by rememberSaveable { mutableStateOf("") }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val currentShop = shops.find { it.id == selectedShopId }
    // Picker shows only shops still awaiting survey. Shop.status is the single source of truth —
    // recced shops flip out of "Pending" and disappear here (view/edit them on the Dashboard).
    val pendingShops = shops.filter { it.status == "Pending" }
    var shopName by rememberSaveable { mutableStateOf("") }
    var shopContact by rememberSaveable { mutableStateOf("") }
    var shopCity by rememberSaveable { mutableStateOf("") }
    var selectedStatus by rememberSaveable { mutableIntStateOf(0) }
    var remark by rememberSaveable { mutableStateOf("") }
    if (remarkFromVoice.isNotBlank()) { remark = remarkFromVoice; remarkFromVoice = "" }

    // Save-then-share popup: holds the prepared WhatsApp intent until the user taps "Open WhatsApp".
    var pendingShareIntent by remember { mutableStateOf<Intent?>(null) }
    var pendingPasteHint by remember { mutableStateOf(false) }

    // Multi-media: a shop can have several media, each with its own creative, type, size, qty & unit.
    var mediaList by rememberSaveable(stateSaver = MediaListSaver) { mutableStateOf(listOf(MediaEntry())) }
    fun setMedia(i: Int, e: MediaEntry) { mediaList = mediaList.toMutableList().also { if (i in it.indices) it[i] = e } }
    // The draft currently open in the form ("" = a fresh recce, not from a draft).
    var loadedDraftId by rememberSaveable { mutableStateOf("") }
    var deleteDraftConfirm by remember { mutableStateOf(false) }
    // The saved recce currently being edited ("" = creating a new recce). Set from the Dashboard.
    var editingRecceId by rememberSaveable { mutableStateOf("") }

    // Which media the next capture belongs to (-1 = shop-front). Survives the Camera round-trip so the
    // returned photo lands on the right media card; photoBaseline = photo count when capture started.
    var captureTargetMedia by rememberSaveable { mutableStateOf(-1) }
    var photoBaseline by rememberSaveable { mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(capturedPhotos.size) {
        val t = captureTargetMedia
        if (t in mediaList.indices && capturedPhotos.size > photoBaseline) {
            val newOnes = capturedPhotos.drop(photoBaseline)
            setMedia(t, mediaList[t].copy(photos = mediaList[t].photos + newOnes))
            captureTargetMedia = -1
        }
    }

    // When the project is switched (from the Project tab), clear the form so data can't mix.
    var lastDistId by rememberSaveable { mutableStateOf(selectedDistributorId) }
    androidx.compose.runtime.LaunchedEffect(selectedDistributorId) {
        if (selectedDistributorId != lastDistId) {
            lastDistId = selectedDistributorId
            selectedShopId = ""; shopName = ""; shopContact = ""; shopCity = ""
            selectedStatus = 0; remark = ""
            mediaList = listOf(MediaEntry(creative = projectCreative))
            loadedDraftId = ""
            editingRecceId = ""
            onClearPhotos()
        }
    }

    // ── Draft helpers — save the in-progress recce and reopen it later ──
    fun resetForm() {
        selectedShopId = ""; shopName = ""; shopContact = ""; shopCity = ""
        selectedStatus = 0; remark = ""
        mediaList = listOf(MediaEntry(creative = projectCreative))
        loadedDraftId = ""
        editingRecceId = ""
        onClearPhotos()
    }
    fun buildDraft(): com.receegpsstamp.data.model.RecceDraft {
        // Keep a stable id so repeated auto-saves update the same draft instead of piling up copies.
        val id = loadedDraftId.ifBlank { java.util.UUID.randomUUID().toString() }
        loadedDraftId = id
        return com.receegpsstamp.data.model.RecceDraft(
            id = id,
            distributorId = selectedDistributorId,
            shopId = selectedShopId,
            shopName = if (selectedShopId.isNotEmpty()) currentShop?.name ?: shopName else shopName,
            shopCity = shopCity, shopContact = shopContact,
            status = selectedStatus, remark = remark,
            media = mediaList.map { com.receegpsstamp.data.model.DraftMedia(it.creative, it.mediaType, it.width, it.height, it.qty, it.unit, it.remark, it.photos) },
            photos = capturedPhotos.toList(),
        )
    }
    fun loadDraft(d: com.receegpsstamp.data.model.RecceDraft) {
        selectedShopId = d.shopId
        shopName = d.shopName; shopCity = d.shopCity; shopContact = d.shopContact
        selectedStatus = d.status; remark = d.remark
        mediaList = d.media.map { MediaEntry(it.creative, it.mediaType, it.width, it.height, it.qty, it.unit, it.remark, it.photos) }.ifEmpty { listOf(MediaEntry()) }
        onLoadPhotos(d.photos)
        loadedDraftId = d.id
    }
    // Load a Pending shop into the form for a fresh recce (used by the preselect hand-off and the pending strip).
    fun pickShop(shop: Shop) {
        selectedShopId = shop.id
        shopName = shop.name
        shopCity = shop.city
        shopContact = shop.contact
        selectedStatus = 0; remark = ""
        mediaList = listOf(MediaEntry(creative = projectCreative))
        loadedDraftId = ""; editingRecceId = ""
        onClearPhotos()
    }

    // Auto-save the in-progress recce as a draft when the app goes to the background (minimize / back-exit)
    // so nothing is lost. Repeated saves reuse the same draft id (no duplicate drafts).
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP && (shopName.isNotBlank() || capturedPhotos.isNotEmpty())) {
                onSaveDraft(buildDraft())
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Load a recce reopened from the Dashboard ("Update Store Details") into the form, once.
    androidx.compose.runtime.LaunchedEffect(recceToEdit?.id) {
        val r = recceToEdit ?: return@LaunchedEffect
        if (editingRecceId == r.id) return@LaunchedEffect
        val shop = shops.find { it.id == r.shopId }
        selectedShopId = r.shopId
        shopName = shop?.name ?: ""
        shopCity = shop?.city ?: ""
        shopContact = shop?.contact ?: ""
        selectedStatus = listOf("Interested", "Not Interested", "Closed").indexOf(r.status).coerceAtLeast(0)
        remark = r.remark
        mediaList = r.media.map { m ->
            MediaEntry(m.creative, m.type, dimText(m.width), dimText(m.height), m.qty.toString(), m.unit, m.remark, m.photos)
        }.ifEmpty { listOf(MediaEntry()) }
        onLoadPhotos((r.shopPhotos + r.media.flatMap { it.photos }).distinct())
        editingRecceId = r.id
        loadedDraftId = ""
        onConsumeEdit()
    }

    // Pre-fill the form with a shop tapped from the Project shop-list ("Start ›"), once.
    androidx.compose.runtime.LaunchedEffect(preselectShopId) {
        val sid = preselectShopId ?: return@LaunchedEffect
        shops.find { it.id == sid }?.let { pickShop(it) }
        onConsumePreselect()
    }

    // Pre-fill the first blank media card with the project's sticky default creative.
    androidx.compose.runtime.LaunchedEffect(projectCreative) {
        if (projectCreative.isNotEmpty() && loadedDraftId.isEmpty() && editingRecceId.isEmpty() &&
            mediaList.size == 1 && mediaList[0].creative.isEmpty() && mediaList[0].mediaType.isEmpty() && mediaList[0].photos.isEmpty()
        ) {
            setMedia(0, mediaList[0].copy(creative = projectCreative))
        }
    }

    // Watermark context built from the form, stamped onto captured photos.
    val statusText = listOf("Interested", "Not Interested", "Closed")[selectedStatus]
    val shopWatermark = com.receegpsstamp.feature.camera.PhotoWatermark(
        shopName = shopName, city = shopCity, contact = shopContact, status = statusText,
    )

    // Required before a photo can be captured. Status always has a value (defaults to Interested).
    // Remark is required only when the shop is NOT interested.
    val missingFields = buildList {
        if (shopName.isBlank()) add("Shop name")
        if (shopContact.isBlank()) add("Mobile no.")
        if (shopCity.isBlank()) add("City")
        if (selectedStatus != 0 && remark.isBlank()) add("Remark")
    }
    val canCapture = missingFields.isEmpty()
    val statusLocked = capturedPhotos.isNotEmpty()
    fun attemptCapture(wm: com.receegpsstamp.feature.camera.PhotoWatermark) {
        if (canCapture) {
            if (hapticsEnabled) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            onCameraClick(wm)
        } else {
            android.widget.Toast.makeText(context, "Fill: ${missingFields.joinToString(", ")}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // GPS coordinates captured for the WhatsApp share text.
    var gpsLat by rememberSaveable { mutableStateOf(0.0) }
    var gpsLng by rememberSaveable { mutableStateOf(0.0) }

    // Location: request permission on demand (only the Camera screen requested it before,
    // so the GPS/city button would silently fail if the camera was never opened).
    val locationPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onFetchLocation { city, lat, lng -> shopCity = city; gpsLat = lat; gpsLng = lng }
    }
    fun fetchCity() {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) onFetchLocation { city, lat, lng -> shopCity = city; gpsLat = lat; gpsLng = lng }
        else locationPermLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
    }
    // Capture coordinates automatically when the screen opens (if permission already granted),
    // so latitude/longitude are in the share text even without tapping the GPS button.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted && gpsLat == 0.0) onFetchLocation { city, lat, lng ->
            gpsLat = lat; gpsLng = lng; if (shopCity.isEmpty()) shopCity = city
        }
    }

    // Save-then-share popup — names the WhatsApp group to send to, then opens WhatsApp on tap.
    pendingShareIntent?.let { shareIntent ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingShareIntent = null },
            title = { Text("Recce saved ✓", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        if (waGroup.isNotBlank()) "Open WhatsApp and send to your group:" else "Open WhatsApp to share this recce.",
                        fontSize = 13.sp,
                    )
                    if (waGroup.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(waGroup, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppYellowDark)
                    }
                    if (pendingPasteHint) {
                        Spacer(Modifier.height(8.dp))
                        Text("Multiple photos — caption copied; long-press to paste it in the chat.", fontSize = 11.sp, color = NeutralTextSoft)
                    }
                }
            },
            confirmButton = {
                Text(
                    "Open WhatsApp", color = AppYellowDark, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        // Business-first or regular-first depending on the Settings toggle.
                        val pkgs = if (preferWhatsAppBusiness) listOf("com.whatsapp.w4b", "com.whatsapp") else listOf("com.whatsapp", "com.whatsapp.w4b")
                        val opened = pkgs.any { pkg ->
                            try { context.startActivity(Intent(shareIntent).setPackage(pkg)); true } catch (_: Exception) { false }
                        }
                        if (!opened) context.startActivity(Intent.createChooser(Intent(shareIntent), "Share Recce"))
                        pendingShareIntent = null
                    }.padding(8.dp),
                )
            },
            dismissButton = {
                Text("Later", color = NeutralTextSoft, modifier = Modifier.clickable { pendingShareIntent = null }.padding(8.dp))
            },
        )
    }

    if (deleteDraftConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleteDraftConfirm = false },
            title = { Text("Delete this draft?", fontWeight = FontWeight.Bold) },
            text = { Text("The draft and its photos will be removed. This can't be undone.", fontSize = 13.sp) },
            confirmButton = {
                Text(
                    "Delete draft", color = StatusError, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        val d = drafts.find { it.id == loadedDraftId }
                        resetForm()
                        deleteDraftConfirm = false
                        if (d != null) {
                            onDeleteDraft(d.id, false)   // remove the record now; keep photos in case of undo
                            scope.launch {
                                val res = snackbarHostState.showSnackbar("Draft deleted", "UNDO", duration = androidx.compose.material3.SnackbarDuration.Short)
                                if (res == androidx.compose.material3.SnackbarResult.ActionPerformed) onSaveDraft(d)
                                else d.photos.distinct().forEach { onRemovePhoto(it) }
                            }
                        }
                    }.padding(8.dp),
                )
            },
            dismissButton = {
                Text("Cancel", color = NeutralTextSoft, modifier = Modifier.clickable { deleteDraftConfirm = false }.padding(8.dp))
            },
        )
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(NeutralBg)) {
        val header = listOfNotNull(
            companyName.ifEmpty { null },
            distributorName.ifEmpty { null },
        ).joinToString(" · ").ifEmpty { "Recce GPS Stamp" }
        RgsTopBar(header, titleBold = true, showBack = false, onNav = onMenuClick)

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // ── Editing banner — a saved store reopened from the Dashboard ("Update Store Details") ──
            if (editingRecceId.isNotBlank()) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(YellowContainer).padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(RgsIcons.Edit, null, tint = AppYellowDark, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Editing saved store", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = AppYellowDark, modifier = Modifier.weight(1f))
                    Text("Cancel", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = StatusError, modifier = Modifier.clickable { resetForm() }.padding(4.dp))
                }
            }

            // ── Drafts strip — saved in-progress recces; tap to continue (hidden while editing) ──
            if (editingRecceId.isBlank() && drafts.isNotEmpty()) {
                Text("DRAFTS — tap to continue", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp, color = NeutralTextSoft, modifier = Modifier.padding(horizontal = 2.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    drafts.sortedByDescending { it.updatedAt }.forEach { d ->
                        DraftChip(
                            name = d.shopName.ifBlank { "Untitled" },
                            media = d.media.count { it.mediaType.isNotBlank() },
                            photos = d.photos.size,
                            isOpen = d.id == loadedDraftId,
                            onClick = { loadDraft(d) },
                        )
                    }
                }
            }

            // ── Pending shops strip — quick-pick a to-visit shop without leaving Start Work ──
            if (editingRecceId.isBlank() && loadedDraftId.isBlank() && pendingShops.isNotEmpty()) {
                Text("PENDING SHOPS — tap to start", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp, color = NeutralTextSoft, modifier = Modifier.padding(horizontal = 2.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    pendingShops.forEach { shop ->
                        PendingShopChip(
                            name = shop.name,
                            city = shop.city,
                            selected = shop.id == selectedShopId,
                            onClick = { pickShop(shop) },
                        )
                    }
                }
            }

            PartHeader("SHOP DETAILS", if (pendingShops.isNotEmpty()) "${pendingShops.size} pending" else null)

            // Shop details card — always visible, type directly or pick from dropdown
            RgsCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CompactTextField(label = "Shop name", value = shopName, onValueChange = { shopName = it; if (it != currentShop?.name) selectedShopId = "" }, modifier = Modifier.weight(1f))
                        if (pendingShops.isNotEmpty()) {
                            Box {
                                Box(
                                    Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(NeutralSurfaceV)
                                        .clickable { dropdownExpanded = true },
                                    contentAlignment = Alignment.Center,
                                ) { Icon(RgsIcons.DropDown, null, tint = NeutralTextSoft, modifier = Modifier.size(18.dp)) }
                                DropdownMenu(
                                    expanded = dropdownExpanded,
                                    onDismissRequest = { dropdownExpanded = false },
                                    modifier = Modifier.fillMaxWidth(0.7f).background(NeutralSurface),
                                ) {
                                    pendingShops.forEach { shop ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(shop.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                                    if (shop.city.isNotBlank()) Text(shop.city, fontSize = 11.sp, color = NeutralTextSoft)
                                                }
                                            },
                                            onClick = {
                                                selectedShopId = shop.id
                                                shopName = shop.name
                                                shopContact = shop.contact
                                                shopCity = shop.city
                                                dropdownExpanded = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    CompactTextField(label = "Contact no.", value = shopContact, onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 10) shopContact = v }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CompactTextField(label = "City", value = shopCity, onValueChange = { shopCity = it }, modifier = Modifier.weight(1f))
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(BrandGradient).clickable {
                                fetchCity()
                            },
                            contentAlignment = Alignment.Center,
                        ) { Icon(RgsIcons.Location, null, tint = Color.Black, modifier = Modifier.size(22.dp)) }
                    }
                }
            }

            AnimatedVisibility(shopName.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {

                    // Status chips — prototype: .sec margin-top:4px + flex gap:6px
                    Spacer(Modifier.height(0.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("SHOP STATUS", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp, color = NeutralTextSoft, modifier = Modifier.padding(horizontal = 2.dp))
                        if (statusLocked) Text("  · locked after photo", fontSize = 10.sp, color = NeutralTextSoft)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Interested", "Not Interested", "Closed").forEachIndexed { i, label ->
                            val sel = i == selectedStatus
                            Box(
                                Modifier.weight(1f).height(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (sel) YellowContainer else Color.Transparent)
                                    .border(if (sel) 0.dp else 1.dp, if (sel) Color.Transparent else NeutralOutline, RoundedCornerShape(8.dp))
                                    // Status can't change once a photo is captured.
                                    .clickable(enabled = !statusLocked) { selectedStatus = i }
                                    .alpha(if (statusLocked && !sel) 0.4f else 1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                    if (sel) { Icon(RgsIcons.Check, null, tint = BrandGrey, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(3.dp)) }
                                    Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = if (sel) BrandGrey else NeutralText, maxLines = 2)
                                }
                            }
                        }
                    }

                    // Remark — required for Not Interested / Closed, hidden for Interested.
                    AnimatedVisibility(selectedStatus != 0, enter = expandVertically() + fadeIn(), exit = shrinkVertically()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("REMARK", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp, color = NeutralTextSoft, modifier = Modifier.padding(horizontal = 2.dp))
                            CompactTextField(
                                value = remark, onValueChange = { remark = it }, placeholder = "Add a remark…",
                                trailingIcon = {
                                    Icon(
                                        RgsIcons.Mic, null, tint = NeutralTextSoft,
                                        modifier = Modifier.size(20.dp).clickable {
                                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                            }
                                            try { voiceLauncher.launch(intent) } catch (_: Exception) {}
                                        },
                                    )
                                },
                            )
                        }
                    }

                    Text("SHOP FRONT PHOTO (AT LEAST 1)", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp, color = NeutralTextSoft, modifier = Modifier.padding(horizontal = 2.dp))
                    if (!canCapture) {
                        Text(
                            "Fill ${missingFields.joinToString(", ")} to enable capture",
                            fontSize = 11.sp, color = Color(0xFFC0605F), modifier = Modifier.padding(horizontal = 2.dp),
                        )
                    }
                    // Front photos = captured photos not assigned to any media card.
                    val mediaPhotoSet = mediaList.flatMap { it.photos }.toSet()
                    val frontPhotos = capturedPhotos.filter { it !in mediaPhotoSet }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        frontPhotos.forEach { path ->
                            CapturedThumb(path, onDelete = { onRemovePhoto(path) }, onEdit = onEditPhoto)
                        }
                        Box(Modifier.alpha(if (canCapture) 1f else 0.4f)) {
                            PhotoThumb(filled = false) { captureTargetMedia = -1; attemptCapture(shopWatermark) }
                        }
                    }

                    // ── PART 2 · MEDIA (only when interested) ──
                    AnimatedVisibility(selectedStatus == 0, enter = expandVertically(), exit = shrinkVertically()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Spacer(Modifier.height(6.dp))
                            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x14000000)))
                            Spacer(Modifier.height(8.dp))
                            PartHeader("MEDIA", "required when interested")
                            mediaList.forEachIndexed { i, entry ->
                                MediaCard(
                                    index = i + 1,
                                    canDelete = mediaList.size > 1,
                                    onDelete = { mediaList = mediaList.filterIndexed { idx, _ -> idx != i } },
                                    width = entry.width, onWidthChange = { setMedia(i, entry.copy(width = it)) },
                                    height = entry.height, onHeightChange = { setMedia(i, entry.copy(height = it)) },
                                    qty = entry.qty, onQtyChange = { setMedia(i, entry.copy(qty = it)) },
                                    unit = entry.unit, onUnitChange = { setMedia(i, entry.copy(unit = it)) },
                                    remark = entry.remark, onRemarkChange = { setMedia(i, entry.copy(remark = it)) },
                                    photos = entry.photos,
                                    onCameraClick = {
                                        captureTargetMedia = i
                                        photoBaseline = capturedPhotos.size
                                        attemptCapture(shopWatermark.copy(mediaType = entry.mediaType, creative = entry.creative, size = mediaSizeText(entry)))
                                    },
                                    onDeletePhoto = { path ->
                                        setMedia(i, entry.copy(photos = entry.photos - path))
                                        onRemovePhoto(path)
                                    },
                                    onEditPhoto = onEditPhoto,
                                    creatives = creatives, mediaTypes = mediaTypes,
                                    selectedCreative = entry.creative, onSelectCreative = { setMedia(i, entry.copy(creative = it)); onSetProjectCreative(it) },
                                    selectedMediaType = entry.mediaType, onSelectMediaType = { setMedia(i, entry.copy(mediaType = it)) },
                                )
                            }
                            SecondaryButton("+ Add another media", onClick = { mediaList = mediaList + MediaEntry(creative = projectCreative) })
                        }
                    }

                    // "Save as Draft" only makes sense for a fresh recce — hidden while editing a saved one.
                    if (editingRecceId.isBlank()) {
                        SecondaryButton(
                            if (loadedDraftId.isNotBlank()) "Update Draft" else "Save as Draft",
                            onClick = {
                                if (shopName.isBlank() && capturedPhotos.isEmpty()) {
                                    android.widget.Toast.makeText(context, "Add a shop name or photo first", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    onSaveDraft(buildDraft())
                                    android.widget.Toast.makeText(context, "Draft saved", android.widget.Toast.LENGTH_SHORT).show()
                                    resetForm()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    PrimaryButton(
                        when {
                            editingRecceId.isNotBlank() && shareToWhatsApp -> "Update & Share via WhatsApp"
                            editingRecceId.isNotBlank() -> "Update Store"
                            shareToWhatsApp -> "Save & Share via WhatsApp"
                            else -> "Save Recce"
                        },
                        onClick = {
                        if (hapticsEnabled) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        val statusText = listOf("Interested", "Not Interested", "Closed")[selectedStatus]
                        val media = if (statusText == "Interested") {
                            mediaList.filter { it.mediaType.isNotEmpty() }.map { e ->
                                MediaItem(
                                    type = e.mediaType,
                                    creative = e.creative,
                                    width = e.width.toDoubleOrNull() ?: 0.0,
                                    height = e.height.toDoubleOrNull() ?: 0.0,
                                    qty = e.qty.toIntOrNull() ?: 1,
                                    unit = e.unit,
                                    remark = e.remark,
                                    photos = e.photos,
                                )
                            }
                        } else emptyList()

                        val finalShopName = if (selectedShopId.isNotEmpty()) currentShop?.name ?: shopName else shopName
                        val finalCity = if (selectedShopId.isNotEmpty()) currentShop?.city ?: shopCity else shopCity

                        // Copy the captured photos to a share cache BEFORE saving. Save renames the
                        // originals (to <shopCode>_<shop>_<n>.jpg), which would leave these share URIs
                        // pointing at now-gone files — and WhatsApp then attaches no image. The cache
                        // copies survive the rename, so the photos always go through.
                        val shareDir = File(context.cacheDir, "exports").apply { mkdirs() }
                        shareDir.listFiles { f -> f.name.startsWith("share_") }?.forEach { it.delete() }
                        val photoUris = capturedPhotos.mapNotNull { path ->
                            val src = File(path)
                            if (!src.exists()) return@mapNotNull null
                            val copy = File(shareDir, "share_${src.name}")
                            try { src.copyTo(copy, overwrite = true) } catch (_: Exception) { return@mapNotNull null }
                            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", copy)
                        }
                        val photoCount = capturedPhotos.size

                        // Save recce — update the existing one when editing, else create new.
                        if (editingRecceId.isNotBlank()) {
                            onUpdateRecce(editingRecceId, statusText, remark, media)
                        } else if (selectedShopId.isNotEmpty()) {
                            onSaveRecce(selectedShopId, statusText, remark, media)
                        } else if (shopName.isNotBlank()) {
                            onSaveRecceNewShop(shopName.trim(), shopCity.trim(), shopContact.trim(), statusText, remark, media)
                        }

                        if (shareToWhatsApp) {
                            // Line 1: Company · Distributor. Line 2: Shop · Contact · City. Then the rest.
                            val summary = buildString {
                                val coDist = listOf(companyName, distributorName).filter { it.isNotBlank() }.joinToString(" · ")
                                if (coDist.isNotBlank()) appendLine(coDist)
                                // Shop name and contact each on their own separate line.
                                if (finalShopName.isNotBlank()) appendLine("Shop: ${finalShopName.trim()}")
                                if (shopContact.isNotBlank()) appendLine("Contact: $shopContact")
                                if (finalCity.isNotBlank()) appendLine("City: ${finalCity.trim()}")
                                appendLine("Status: $statusText")
                                // Each media on ONE line (type · creative · size · qty) — clear with multiple media.
                                media.forEachIndexed { idx, m ->
                                    val label = if (media.size > 1) "Media ${idx + 1}" else "Media"
                                    val sizeStr = if (m.width > 0 || m.height > 0) "${m.width} x ${m.height} ${m.unit}" else ""
                                    val parts = listOf(
                                        m.type, m.creative, sizeStr,
                                        if (m.qty > 0) "Qty ${m.qty}" else "",
                                        m.remark,
                                        if (m.photos.isNotEmpty()) "Photos ${m.photos.size}" else "",
                                    ).filter { it.isNotBlank() }
                                    appendLine("$label: ${parts.joinToString(" · ")}")
                                }
                                if (waIncludeLocation && (gpsLat != 0.0 || gpsLng != 0.0)) {
                                    // Latitude, longitude & the tappable Maps link — all on one line.
                                    appendLine("Location: ${"%.6f".format(gpsLat)}, ${"%.6f".format(gpsLng)} · https://maps.google.com/?q=${"%.6f".format(gpsLat)},${"%.6f".format(gpsLng)}")
                                }
                                if (remark.isNotBlank()) appendLine("Remark: $remark")
                                appendLine("Photos: $photoCount")
                                if (waIncludeRecceBy && userName.isNotBlank()) append("Recce by: $userName")
                            }

                            val sharePhotos = if (waIncludePhotos) photoUris else emptyList()
                            val intent = if (sharePhotos.size > 1) {
                                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                    type = "image/*"
                                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(sharePhotos))
                                    putExtra(Intent.EXTRA_TEXT, summary)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                            } else if (sharePhotos.size == 1) {
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "image/*"
                                    putExtra(Intent.EXTRA_STREAM, sharePhotos.first())
                                    putExtra(Intent.EXTRA_TEXT, summary)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                            } else {
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, summary)
                                }
                            }
                            // WhatsApp drops the caption when multiple images are shared, so copy the
                            // details to the clipboard — the user can long-press → paste in the chat.
                            val clip = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clip.setPrimaryClip(android.content.ClipData.newPlainText("Recce", summary))
                            // Show the save-then-share popup (group reminder) instead of jumping straight out.
                            pendingPasteHint = sharePhotos.size > 1
                            pendingShareIntent = intent
                        } else {
                            android.widget.Toast.makeText(context, "Recce saved", android.widget.Toast.LENGTH_SHORT).show()
                        }

                        // Finalised — remove the source draft (its photos now belong to the saved recce).
                        if (loadedDraftId.isNotBlank()) onDeleteDraft(loadedDraftId, false)
                        // Reset form
                        resetForm()
                        onAfterSave()
                    })

                    // Delete option lives here (inside the open draft) — not on the chip — so a draft
                    // can't be removed by an accidental tap. Confirms before deleting.
                    if (loadedDraftId.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .clickable { deleteDraftConfirm = true }
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Delete this draft", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = StatusError)
                        }
                    }
                }
            }
        }
    }
        androidx.compose.material3.SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp))
    }
}

// Double → form text: 0 shows blank, whole numbers drop the ".0".
private fun dimText(d: Double): String = when {
    d <= 0.0 -> ""
    d == d.toLong().toDouble() -> d.toLong().toString()
    else -> d.toString()
}

@Composable
private fun DraftChip(name: String, media: Int, photos: Int, isOpen: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(10.dp))
            .background(if (isOpen) YellowContainer else NeutralSurfaceV)
            .border(1.dp, if (isOpen) AppYellowDark else NeutralOutline, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(RgsIcons.Edit, null, tint = AppYellowDark, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Column {
                Text(name, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = NeutralText, maxLines = 1)
                Text("$media media · $photos photo", fontSize = 9.5.sp, color = NeutralTextSoft, maxLines = 1)
            }
        }
    }
}

@Composable
private fun PendingShopChip(name: String, city: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(10.dp))
            .background(if (selected) YellowContainer else NeutralSurfaceV)
            .border(1.dp, if (selected) AppYellowDark else NeutralOutline, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(RgsIcons.Store, null, tint = AppYellowDark, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Column {
                Text(name, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = NeutralText, maxLines = 1)
                Text(if (city.isNotBlank()) city else "tap to start", fontSize = 9.5.sp, color = NeutralTextSoft, maxLines = 1)
            }
        }
    }
}

@Composable
private fun CapturedThumb(path: String, onDelete: (() -> Unit)? = null, onEdit: ((String) -> Unit)? = null) {
    // Key on lastModified too so the thumb reloads after the photo is edited (overwritten in place).
    val bitmap = remember(path, java.io.File(path).lastModified()) { android.graphics.BitmapFactory.decodeFile(path) }
    Box(
        Modifier.size(64.dp).clip(RoundedCornerShape(8.dp))
            .then(if (onEdit != null) Modifier.clickable { onEdit(path) } else Modifier),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Captured",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Floating edit (pencil) badge — tap the photo to annotate it.
        if (onEdit != null) {
            Box(
                Modifier.align(Alignment.BottomStart).padding(2.dp)
                    .size(18.dp).clip(RoundedCornerShape(50)).background(AppYellow)
                    .clickable { onEdit(path) },
                contentAlignment = Alignment.Center,
            ) { Icon(RgsIcons.Edit, null, tint = Color.Black, modifier = Modifier.size(11.dp)) }
        }
        if (onDelete != null) {
            // Tap the X to delete this photo from the recce.
            Box(
                Modifier.align(Alignment.TopEnd).padding(2.dp)
                    .size(18.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(0.55f))
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center,
            ) { Icon(RgsIcons.Close, null, tint = Color.White, modifier = Modifier.size(12.dp)) }
        } else {
            Box(
                Modifier.align(Alignment.TopEnd).padding(2.dp)
                    .size(18.dp).background(Color.Black.copy(0.6f), RoundedCornerShape(50)),
                contentAlignment = Alignment.Center,
            ) { Icon(RgsIcons.Check, null, tint = AppYellow, modifier = Modifier.size(12.dp)) }
        }
    }
}

@Composable
private fun PhotoThumb(filled: Boolean, onClick: () -> Unit = {}) {
    Box(
        Modifier.size(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (filled) NeutralSurfaceV else Color.Transparent)
            .border(if (filled) 0.dp else 1.5.dp, NeutralOutline, RoundedCornerShape(8.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (filled) Icon(RgsIcons.Camera, null, tint = NeutralTextSoft, modifier = Modifier.size(20.dp))
        else Icon(RgsIcons.Add, null, tint = NeutralTextSoft, modifier = Modifier.size(20.dp))
    }
}

/** One media row in a recce — a shop can have several (board + glow sign + standee…). */
private data class MediaEntry(
    val creative: String = "",
    val mediaType: String = "",
    val width: String = "",
    val height: String = "",
    val qty: String = "1",
    val unit: String = "in",
    val remark: String = "",
    val photos: List<String> = emptyList(),
)

/** Saves the media list through the full-screen Camera round-trip (7 strings/entry; photos newline-joined). */
private val MediaListSaver = androidx.compose.runtime.saveable.listSaver<List<MediaEntry>, String>(
    save = { list -> list.flatMap { listOf(it.creative, it.mediaType, it.width, it.height, it.qty, it.unit, it.remark, it.photos.joinToString("\n")) } },
    restore = { flat ->
        flat.chunked(8).map { MediaEntry(it[0], it[1], it[2], it[3], it[4], it[5], it[6], it[7].split("\n").filter { p -> p.isNotBlank() }) }
            .ifEmpty { listOf(MediaEntry()) }
    },
)

/** Size label for watermark/share — e.g. "3×4 ft · Qty 2 · 24 sq.ft" (inches convert ÷144). */
private fun mediaSizeText(e: MediaEntry): String = buildString {
    fun sep() { if (isNotEmpty()) append(" · ") }
    if (e.width.isNotBlank() && e.height.isNotBlank()) append("${e.width}×${e.height} ${e.unit}")
    if (e.qty.isNotBlank()) { sep(); append("Qty ${e.qty}") }
    val w = e.width.toFloatOrNull() ?: 0f
    val h = e.height.toFloatOrNull() ?: 0f
    val q = e.qty.toIntOrNull() ?: 1
    val sqft = MediaMath.areaSqFt(w, h, q, e.unit)
    if (sqft > 0f) { sep(); append("${MediaMath.formatArea(sqft)} sq.ft") }
}

/** Numbered part header (1 = Shop details, 2 = Media) — splits the recce form into two clear parts. */
@Composable
private fun PartHeader(title: String, subtitle: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 2.dp)) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp, color = NeutralText)
        subtitle?.let {
            Spacer(Modifier.width(6.dp))
            Text("· $it", fontSize = 10.5.sp, color = NeutralTextSoft)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MediaCard(
    index: Int = 1,
    canDelete: Boolean = false,
    onDelete: () -> Unit = {},
    width: String, onWidthChange: (String) -> Unit,
    height: String, onHeightChange: (String) -> Unit,
    qty: String, onQtyChange: (String) -> Unit,
    unit: String = "in", onUnitChange: (String) -> Unit = {},
    remark: String = "", onRemarkChange: (String) -> Unit = {},
    photos: List<String> = emptyList(),
    onCameraClick: () -> Unit = {},
    onDeletePhoto: (String) -> Unit = {},
    onEditPhoto: ((String) -> Unit)? = null,
    creatives: List<String> = emptyList(),
    mediaTypes: List<String> = emptyList(),
    selectedCreative: String = "",
    onSelectCreative: (String) -> Unit = {},
    selectedMediaType: String = "",
    onSelectMediaType: (String) -> Unit = {},
) {
    var mediaTypeDropdown by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current

    RgsCard {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Media $index", fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (canDelete) {
                    Box(Modifier.size(24.dp).clickable { onDelete() }, contentAlignment = Alignment.Center) {
                        Icon(RgsIcons.Delete, null, tint = NeutralTextSoft, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Text("Creative (brand)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NeutralTextSoft)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                creatives.forEach { name ->
                    val sel = name == selectedCreative
                    Box(
                        Modifier.clip(RoundedCornerShape(8.dp))
                            .background(if (sel) NeutralSurfaceV else Color.Transparent)
                            .border(if (sel) 0.dp else 1.dp, NeutralOutline, RoundedCornerShape(8.dp))
                            .clickable { onSelectCreative(name) }
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (sel) { Icon(RgsIcons.Check, null, tint = BrandGrey, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)) }
                            Text(name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = NeutralText)
                        }
                    }
                }
                if (creatives.isEmpty()) {
                    Text("No creatives — add in Project Setup", fontSize = 12.sp, color = NeutralTextSoft)
                }
            }

            Box {
                CompactDropdown(label = "Media type", value = selectedMediaType.ifEmpty { "Select media type…" }, onClick = { mediaTypeDropdown = true })
                DropdownMenu(
                    expanded = mediaTypeDropdown,
                    onDismissRequest = { mediaTypeDropdown = false },
                    modifier = Modifier.fillMaxWidth(0.7f).background(NeutralSurface),
                ) {
                    mediaTypes.forEach { mt ->
                        DropdownMenuItem(
                            text = { Text(mt, fontSize = 14.sp, fontWeight = FontWeight.Medium) },
                            onClick = { onSelectMediaType(mt); mediaTypeDropdown = false },
                        )
                    }
                    if (mediaTypes.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No media types — add in Project Setup", fontSize = 13.sp, color = NeutralTextSoft) },
                            onClick = { mediaTypeDropdown = false },
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                val decimalKb = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
                val numberKb = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                // Only digits (and a single decimal point for size); no letters.
                fun decimalOnly(v: String) = v.all { it.isDigit() || it == '.' } && v.count { it == '.' } <= 1
                CompactTextField(label = "Width", value = width, onValueChange = { if (decimalOnly(it)) onWidthChange(it) }, keyboardOptions = decimalKb, modifier = Modifier.weight(1f))
                CompactTextField(label = "Height", value = height, onValueChange = { if (decimalOnly(it)) onHeightChange(it) }, keyboardOptions = decimalKb, modifier = Modifier.weight(1f))
                CompactTextField(label = "Qty", value = qty, onValueChange = { if (it.all { c -> c.isDigit() }) onQtyChange(it) }, keyboardOptions = numberKb, modifier = Modifier.weight(1f))
                UnitToggle(unit, onUnitChange)
            }

            val w = width.toFloatOrNull() ?: 0f
            val h = height.toFloatOrNull() ?: 0f
            val q = qty.toIntOrNull() ?: 1
            // Flex is always priced in sq.ft (inches convert ÷144) — see MediaMath.
            val areaStr = MediaMath.formatArea(MediaMath.areaSqFt(w, h, q, unit))
            Text("Area: $areaStr sq.ft", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)

            CompactTextField(label = "Remark (optional)", value = remark, onValueChange = onRemarkChange, placeholder = "Note for this media…")

            // Photo capture is gated — creative + media type + size (W×H) must be filled first.
            val mediaReady = selectedCreative.isNotBlank() && selectedMediaType.isNotBlank() &&
                width.isNotBlank() && height.isNotBlank()
            Text("Media photo (at least 1)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NeutralTextSoft)
            if (!mediaReady) {
                Text("Add creative, media type & size first", fontSize = 11.sp, color = Color(0xFFC0605F))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                photos.forEach { p -> CapturedThumb(p, onDelete = { onDeletePhoto(p) }, onEdit = onEditPhoto) }
                Box(Modifier.alpha(if (mediaReady) 1f else 0.4f)) {
                    PhotoThumb(false) {
                        if (mediaReady) onCameraClick()
                        else android.widget.Toast.makeText(ctx, "Add creative, media type & size first", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}

@Composable
private fun UnitToggle(unit: String, onUnitChange: (String) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(7.dp)).background(NeutralSurfaceV).padding(2.dp),
    ) {
        listOf("ft", "in").forEach { u ->
            val sel = unit == u
            Box(
                Modifier.clip(RoundedCornerShape(5.dp))
                    .background(if (sel) AppYellow else Color.Transparent)
                    .clickable { onUnitChange(u) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(u, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (sel) Color.Black else NeutralTextSoft)
            }
        }
    }
}
