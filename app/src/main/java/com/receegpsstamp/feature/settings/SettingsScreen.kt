package com.receegpsstamp.feature.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import coil.compose.AsyncImage
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.receegpsstamp.data.auth.PhoneAuthState
import com.receegpsstamp.data.local.AppProfile
import com.receegpsstamp.data.local.AppSettings
import com.receegpsstamp.ui.components.RgsCard
import com.receegpsstamp.ui.components.RgsTopBar
import com.receegpsstamp.ui.theme.*

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSignedOut: () -> Unit = {},
    initialPage: String = "menu",
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val phoneState by viewModel.phoneState.collectAsStateWithLifecycle()
    val phoneError by viewModel.phoneError.collectAsStateWithLifecycle()
    val displayName = profile.fullName.ifBlank { user?.displayName ?: "Guest" }
    val initials = displayName.split(" ").take(2).joinToString("") { it.take(1).uppercase() }.ifEmpty { "?" }
    val accountEmail = profile.email.ifBlank { user?.email ?: "" }
    val accountSubtitle = when {
        user == null -> "Not signed in"
        accountEmail.isNotBlank() -> accountEmail
        profile.mobile.isNotBlank() -> "+91 ${profile.mobile}"
        else -> "Signed in"
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }

    val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.handleSignInResult(result.data)
    }
    // Pick a company logo → copy into app storage and save its path for the PDF footer.
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) runCatching {
            val dest = java.io.File(context.filesDir, "report_logo.png")
            context.contentResolver.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } }
            viewModel.update { it.copy(reportLogoPath = dest.absolutePath) }
        }
    }

    var page by remember { mutableStateOf(initialPage) }
    androidx.activity.compose.BackHandler(enabled = page != "menu" && page != initialPage) { page = "menu" }
    val pageTitle = when (page) {
        "watermark" -> "Watermark Setting"
        "fields" -> "Watermark Fields"
        "camera" -> "Camera Setting"
        "whatsapp" -> "WhatsApp Sharing"
        "report" -> "Report (PDF)"
        "account" -> "Account Setting"
        "about" -> "About App"
        "privacy" -> "Privacy Policy"
        else -> "Settings"
    }

    Column(Modifier.fillMaxSize().background(NeutralBg).navigationBarsPadding()) {
        RgsTopBar(pageTitle, onNav = { if (page == "menu" || page == initialPage) onBack() else page = "menu" })

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (page) {
                // ── MENU ──
                "menu" -> {
                    MenuRow(RgsIcons.Grid, "Watermark Fields", "Show or hide fields on the stamp") { page = "fields" }
                    MenuRow(RgsIcons.Photo, "Watermark Setting", "Order, font & colours") { page = "watermark" }
                    MenuRow(RgsIcons.Camera, "Camera Setting", "Aspect ratio & photo quality") { page = "camera" }
                    MenuRow(RgsIcons.Share, "WhatsApp Sharing", "Share on save, photos, location") { page = "whatsapp" }
                    MenuRow(RgsIcons.Download, "Report (PDF)", "Page numbers, photo quality, logo") { page = "report" }
                    MenuRow(RgsIcons.Info, "Privacy Policy", "What the app accesses & how your data is handled") { page = "privacy" }
                    Spacer(Modifier.height(2.dp))
                    RgsCard {
                        ToggleRow("Save a copy without watermark", s.saveNoWatermarkCopy, "Also keeps a clean photo in the phone gallery") { v -> viewModel.update { it.copy(saveNoWatermarkCopy = v) } }
                        SettingDivider()
                        ToggleRow("Save a copy without markup", s.saveNoMarkupCopy, "When you edit a photo, also keep the un-marked version in the gallery") { v -> viewModel.update { it.copy(saveNoMarkupCopy = v) } }
                        SettingDivider()
                        ToggleRow("Vibration on capture & save", s.haptics, "Short buzz when you take a photo or save a recce") { v -> viewModel.update { it.copy(haptics = v) } }
                    }
                }

                // ── WATERMARK FIELDS (show/hide) ──
                "fields" -> {
                    SectionLabel("FIELDS — what appears on the stamp")
                    RgsCard {
                        val order = s.order()
                        order.forEachIndexed { i, key ->
                            ToggleRow(AppSettings.FIELD_LABELS[key] ?: key, s.enabled(key)) { v -> viewModel.update { setField(it, key, v) } }
                            if (i < order.size - 1) SettingDivider()
                        }
                    }
                }

                // ── WATERMARK · Order / Style (field show/hide is its own menu) ──
                "watermark" -> {
                    SectionLabel("ORDER — top to bottom")
                    RgsCard {
                        val order = s.order()
                        order.forEachIndexed { i, key ->
                            FieldReorderRow(
                                label = AppSettings.FIELD_LABELS[key] ?: key,
                                canUp = i > 0, canDown = i < order.size - 1,
                                onUp = { viewModel.update { moveField(it, key, -1) } },
                                onDown = { viewModel.update { moveField(it, key, 1) } },
                            )
                            if (i < order.size - 1) SettingDivider()
                        }
                    }
                    SectionLabel("STYLE")
                    RgsCard {
                        SliderRow("Font size", s.wmFontSize, 1, 10) { v -> viewModel.update { it.copy(wmFontSize = v) } }
                        SettingDivider()
                        ColorRow("Font colour", s.wmTextColor, WM_PALETTE) { c -> viewModel.update { it.copy(wmTextColor = c) } }
                        SettingDivider()
                        Text("Watermark position", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = NeutralText)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Bottom left", "Bottom right").forEach { pos ->
                                val sel = s.wmPosition == pos
                                Box(
                                    Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                        .background(if (sel) YellowContainer else NeutralSurfaceV)
                                        .clickable { viewModel.update { it.copy(wmPosition = pos) } }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(pos.replace("Bottom ", "B-").replace("Top ", "T-"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (sel) AppYellowDark else NeutralTextSoft)
                                }
                            }
                        }
                        SettingDivider()
                        SliderRow("Corner radius", s.wmRadius, 1, 10, "1 = square · 10 = round") { v -> viewModel.update { it.copy(wmRadius = v) } }
                        SettingDivider()
                        ToggleRow("Background box", s.greyBackground, "Box behind the text") { v -> viewModel.update { it.copy(greyBackground = v) } }
                        if (s.greyBackground) {
                            SettingDivider()
                            ColorRow("Background colour", s.wmBgColor, BG_PALETTE) { c -> viewModel.update { it.copy(wmBgColor = c) } }
                            SettingDivider()
                            SliderRow("Background opacity", s.wmBgOpacity, 1, 10, "10% (faint) … 100% (solid)", valueLabel = { "${it * 10}%" }) { v -> viewModel.update { it.copy(wmBgOpacity = v) } }
                        }
                    }
                }

                // ── CAMERA ──
                "camera" -> RgsCard {
                    Text("Aspect ratio", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = NeutralText)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("4:3", "16:9").forEach { r ->
                            val sel = s.cameraRatio == r
                            Box(
                                Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                    .background(if (sel) YellowContainer else NeutralSurfaceV)
                                    .clickable { viewModel.update { it.copy(cameraRatio = r) } }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(r, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (sel) AppYellowDark else NeutralTextSoft)
                            }
                        }
                    }
                    SettingDivider()
                    Text("Photo orientation", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = NeutralText)
                    Text("Auto follows how you hold the phone", fontSize = 11.sp, color = NeutralTextSoft)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Auto", "Portrait", "Landscape").forEach { o ->
                            val sel = s.captureOrientation == o
                            Box(
                                Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                    .background(if (sel) YellowContainer else NeutralSurfaceV)
                                    .clickable { viewModel.update { it.copy(captureOrientation = o) } }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(o, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (sel) AppYellowDark else NeutralTextSoft)
                            }
                        }
                    }
                    SettingDivider()
                    SliderRow("Photo quality", s.photoQuality, 50, 100, "Higher = sharper & larger file (95 recommended)") { v -> viewModel.update { it.copy(photoQuality = v) } }
                }

                // ── REPORT (PDF) ──
                "report" -> RgsCard {
                    ToggleRow("Page numbers", s.reportPageNumbers, "Show \"1 / N\" at the bottom of each page") { v -> viewModel.update { it.copy(reportPageNumbers = v) } }
                    SettingDivider()
                    SliderRow("Photo quality", s.reportJpegQuality, 70, 100, "Higher = sharper & larger PDF (90 recommended)", valueLabel = { "$it%" }) { v -> viewModel.update { it.copy(reportJpegQuality = v) } }
                    SettingDivider()
                    Text("Company logo", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = NeutralText)
                    Text("Shown in the bottom-right corner of every page", fontSize = 11.sp, color = NeutralTextSoft)
                    Spacer(Modifier.height(8.dp))
                    if (s.reportLogoPath.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AsyncImage(
                                model = java.io.File(s.reportLogoPath), contentDescription = null,
                                modifier = Modifier.height(40.dp).clip(RoundedCornerShape(6.dp)).background(NeutralSurfaceV),
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "Change", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = AppYellowDark,
                                modifier = Modifier.clip(RoundedCornerShape(7.dp)).clickable { logoPicker.launch("image/*") }.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                            Text(
                                "Remove", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = StatusError,
                                modifier = Modifier.clip(RoundedCornerShape(7.dp)).clickable { viewModel.update { it.copy(reportLogoPath = "") } }.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    } else {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(YellowContainer).clickable { logoPicker.launch("image/*") }.padding(vertical = 11.dp),
                            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(RgsIcons.Add, null, tint = AppYellowDark, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add company logo", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = AppYellowDark)
                        }
                    }
                }

                // ── WHATSAPP SHARING ──
                "whatsapp" -> RgsCard {
                    ToggleRow("Share to WhatsApp on save", s.whatsappShare, "Open WhatsApp after saving a recce") { v -> viewModel.update { it.copy(whatsappShare = v) } }
                    if (s.whatsappShare) {
                        SettingDivider()
                        ToggleRow("Attach photos", s.waIncludePhotos, "Send the captured photos along") { v -> viewModel.update { it.copy(waIncludePhotos = v) } }
                        SettingDivider()
                        ToggleRow("Include location", s.waIncludeLocation, "GPS coordinates + Google Maps link") { v -> viewModel.update { it.copy(waIncludeLocation = v) } }
                        SettingDivider()
                        ToggleRow("Include \"Recce by\" name", s.waIncludeRecceBy, "Your name at the bottom of the message") { v -> viewModel.update { it.copy(waIncludeRecceBy = v) } }
                        SettingDivider()
                        ToggleRow("Use WhatsApp Business", s.waBusiness, "Open WhatsApp Business instead of regular WhatsApp") { v -> viewModel.update { it.copy(waBusiness = v) } }
                    }
                }

                // ── ACCOUNT ──
                "account" -> RgsCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).background(BrandGradient, CircleShape), contentAlignment = Alignment.Center) {
                            Text(initials, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(displayName, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(accountSubtitle, fontSize = 12.sp, color = NeutralTextSoft)
                        }
                    }
                    SettingDivider()
                    if (user != null) {
                        AccountProfileForm(
                            profile = profile,
                            accountEmail = accountEmail,
                            onSave = { n, su, m, e, g, c, st ->
                                viewModel.saveProfile(n, su, m, e, g, c, st)
                                android.widget.Toast.makeText(context, "Profile saved", android.widget.Toast.LENGTH_SHORT).show()
                            },
                        )
                        SettingDivider()
                        Text(
                            "Sign out", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = StatusError,
                            modifier = Modifier.fillMaxWidth().clickable { viewModel.signOut(); onSignedOut() },
                        )
                    } else {
                        AccountPhoneSignIn(
                            phoneState = phoneState,
                            error = phoneError,
                            onSend = { e164 -> activity?.let { viewModel.sendOtp(e164, it) } },
                            onVerify = { code -> viewModel.verifyOtp(code) },
                            onResend = { activity?.let { viewModel.resendOtp(it) } },
                            onChangeNumber = { viewModel.resetPhoneFlow() },
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f).height(1.dp).background(NeutralOutline))
                            Text("  OR  ", fontSize = 11.sp, color = NeutralTextSoft)
                            Box(Modifier.weight(1f).height(1.dp).background(NeutralOutline))
                        }
                        Spacer(Modifier.height(14.dp))
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(YellowContainer)
                                .clickable { signInLauncher.launch(viewModel.getSignInIntent()) }.padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("Sign in with Google", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppYellowDark) }
                    }
                }

                // ── PRIVACY POLICY ──
                "privacy" -> {
                    val open = { url: String ->
                        runCatching {
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                        }
                        Unit
                    }
                    RgsCard {
                        Text("Recce GPS Stamp — Privacy Policy", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = NeutralText)
                        Spacer(Modifier.height(2.dp))
                        Text("By Manoj Graphics (Media Earth), Jalgaon · Updated 25 Jun 2026", fontSize = 11.sp, color = NeutralTextSoft)
                    }

                    SectionLabel("WHAT THE APP STORES")
                    RgsCard {
                        Text(
                            "Shop details you enter (name, contact, city, status, remarks), the photos you " +
                                "capture, and the GPS location stamped on them. All of this is stored ONLY on your " +
                                "device — the app works fully offline and we do not collect it on any server.",
                            fontSize = 12.5.sp, color = NeutralText, lineHeight = 18.sp,
                        )
                    }

                    SectionLabel("PERMISSIONS USED")
                    RgsCard {
                        FeatureLine("Camera", "To take shop-front and media photos")
                        SettingDivider()
                        FeatureLine("Location (GPS)", "To stamp coordinates & address on photos — only while capturing")
                        SettingDivider()
                        FeatureLine("Photos / Storage", "To save stamped photos to your gallery")
                    }

                    SectionLabel("GOOGLE SIGN-IN (OPTIONAL)")
                    RgsCard {
                        Text(
                            "Sign-in is optional — the app works fully without it. If you sign in with Google, it is " +
                                "only to identify you (your name and email). Your shop data and photos stay on this " +
                                "device — we do not store them on any server.",
                            fontSize = 12.5.sp, color = NeutralText, lineHeight = 18.sp,
                        )
                    }

                    SectionLabel("SHARING & DELETION")
                    RgsCard {
                        FeatureLine("Sharing", "You choose when to share (WhatsApp / PDF / Excel) — nothing is sent automatically")
                        SettingDivider()
                        FeatureLine("Delete data", "Uninstall the app to remove all local data")
                    }

                    SectionLabel("CONTACT")
                    RgsCard {
                        Text(
                            "info4manojgraphics@gmail.com",
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppYellowDark,
                            modifier = Modifier.clickable { open("mailto:info4manojgraphics@gmail.com") },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // ── ABOUT APP ──
                "about" -> {
                    RgsCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(BrandGradient), contentAlignment = Alignment.Center) {
                                Text("RGS", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Recce GPS Stamp", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = NeutralText)
                                Text("Version ${versionName.ifEmpty { "—" }}", fontSize = 12.sp, color = NeutralTextSoft)
                            }
                        }
                        SettingDivider()
                        AboutRow("Developer", "Manoj Graphics")
                        SettingDivider()
                        AboutRow("Made for", "Field survey & branding recce")
                    }

                    SectionLabel("WHAT IT DOES")
                    RgsCard {
                        FeatureLine("GPS-stamp camera", "Shop-front photos stamped with location, date & shop details")
                        SettingDivider()
                        FeatureLine("Projects", "Organise recces by company and distributor")
                        SettingDivider()
                        FeatureLine("Photo editor", "Mark up any photo — shapes, arrows, text, measurements, crop, rotate")
                        SettingDivider()
                        FeatureLine("Drafts & auto-save", "Pause a visit and continue later — nothing is lost")
                        SettingDivider()
                        FeatureLine("Reports & sharing", "Export PDF / Excel and share stores on WhatsApp")
                        SettingDivider()
                        FeatureLine("Offline-first", "Works fully without internet")
                    }

                    SectionLabel("HOW TO USE")
                    RgsCard {
                        StepLine(1, "Project tab — choose Company & Distributor")
                        StepLine(2, "Start Work — enter shop name, contact & status")
                        StepLine(3, "Take shop-front + media photos (auto GPS stamp)")
                        StepLine(4, "Save & Share via WhatsApp")
                        StepLine(5, "Dashboard — view stores, edit & export reports")
                    }

                    SectionLabel("DETAILED GUIDE — tap a topic")
                    HelpCard(
                        RgsIcons.Project, "1 · Set up a project",
                        listOf(
                            "Open the Project tab. Pick a Company (e.g. Dollar, OPPO) or tap 'Add new company'.",
                            "Pick a Distributor, or add one with its name, city and contact number.",
                            "Creatives are the brand / sub-brand names; Media Types are the board kinds (Frontlite, Glow sign…). Both come pre-filled per company and stay editable.",
                            "A company can be deleted only once it has no distributors left — edit or move its distributors first.",
                            "Tap 'Start Project' to open the Start Work screen for that distributor.",
                        ),
                    )
                    HelpCard(
                        RgsIcons.Camera, "2 · Capture a store visit",
                        listOf(
                            "Fill Shop name, Contact and City — these are required before the camera unlocks.",
                            "Choose the status: Interested, Not Interested or Closed. Media is asked only when Interested.",
                            "Tap the camera tile for the shop-front photo — it is auto-stamped with GPS location, date, time and shop details.",
                            "Add one or more Media cards (creative, type, width × height, qty, unit, remark) and photograph each.",
                            "Tap a photo's ✕ (top-right) to delete it. Width/Height accept feet or inch — toggle the unit on the card.",
                            "See the yellow ✎ pencil on each photo? Tap the photo to open it in the Photo Editor right away (see topic 7) — mark a board, add a size, then come back and save.",
                            "Tap 'Save & Share via WhatsApp' (or 'Save Recce') to finish the visit.",
                        ),
                    )
                    HelpCard(
                        RgsIcons.Edit, "3 · Drafts — pause & continue",
                        listOf(
                            "Tap 'Save as Draft' to store a half-finished visit. It appears in the DRAFTS strip at the top of Start Work.",
                            "If you press back or minimise mid-visit, the app auto-saves a draft — nothing is lost.",
                            "Tap a draft to reopen it with every field and photo, add more, then Save & Send.",
                            "To remove a draft, open it and tap 'Delete this draft' (with confirm) — so it is never deleted by accident.",
                        ),
                    )
                    HelpCard(
                        RgsIcons.Dashboard, "4 · Dashboard & stores",
                        listOf(
                            "Every project shows as a card. Tap to expand its stats, status bar and menus.",
                            "Open 'Retail Stores' and tap a store to see its full details and photos.",
                            "Tap the ✎ pencil on a store → 'Update Store Details' reopens it in the form to edit & re-save; 'Share Store Details' sends it on WhatsApp.",
                            "Tap 'Select' to tick several stores, then Share them together or Export as PDF / Excel.",
                            "'Manage' holds Export Report, Mark project done, Export project file, Remove Photo Gallery and Delete.",
                        ),
                    )
                    HelpCard(
                        RgsIcons.Download, "5 · Reports & sharing",
                        listOf(
                            "Export Report makes a PDF (with embedded photos) or an Excel / CSV with Sqft, Rate and Amount columns.",
                            "Reports open in the Android share sheet — send on WhatsApp, Gmail or Drive.",
                            "Saving a recce builds a WhatsApp message with shop, contact, status, each media's size, photo count and a Google Maps link.",
                            "Control WhatsApp sharing, attached photos, location and 'Use WhatsApp Business' from Settings.",
                        ),
                    )
                    HelpCard(
                        RgsIcons.Gallery, "6 · Gallery",
                        listOf(
                            "Open Gallery from the side menu or the bottom bar. It shows every captured photo.",
                            "ALBUMS row (when more than one distributor): each distributor is an album with a cover photo and a photo count — tap one to see only its photos.",
                            "Filter by status (Interested / Not Interested / Closed) or search by shop or city.",
                            "Tap any photo to open it full-screen. The top bar shows the shop, date-time, status and a 1 / N counter.",
                            "Gestures: swipe left/right to change photo; pinch to zoom; double-tap to zoom in/out; drag to pan when zoomed.",
                            "Filmstrip (bottom): tap a thumbnail to jump straight to that photo.",
                            "Three buttons at the bottom: Share, Edit (opens the Photo Editor — topic 7) and WhatsApp.",
                        ),
                    )
                    HelpCard(
                        RgsIcons.Edit, "7 · Photo editor (mark-up)",
                        listOf(
                            "Open it two ways: from Gallery → tap a photo → Edit; or from Start Work → tap a captured photo (yellow ✎). Pressing Done saves the marks onto that same photo.",
                            "TOOLS (bottom): Edit (select/move), Shapes ▾, Text ▾, Pen ▾, a Weight selector, a Colour swatch and Delete.",
                            "Shapes ▾ = Rectangle, Round shape (circle/oval), Arrow, Line. Drag on the photo to draw; the shape is selected automatically and the tool switches to Edit so you don't draw a second one by mistake.",
                            "Text ▾ = Text (a label), Text box (text inside a box) and Size label (e.g. 4 ft × 3 ft). A dialog opens with QUICK LABELS — Board here, Board damage, Need bracket, Replace, New site, Existing, Check — tap one or type your own.",
                            "Pen ▾ = Free pen (freehand) or Straight line.",
                            "Weight ▾ sets line thickness (XS–XL); for text it sets the font size. Colour swatch opens a 12-colour palette. Both also re-style the selected mark.",
                            "Editing a shape: it shows 4 corner handles + a centre grip. Drag a CORNER to reshape (each corner moves on its own — a 'vector box'); drag the CENTRE grip to move the whole shape. While dragging a corner, a magnifier circle shows the exact point so your finger doesn't hide it.",
                            "When a shape is selected, the properties bar gives Undo · Redo · Reset · Apply · Save, a Corners/Rotate switch, a Fill (own colour + see-through %), and a centred degree slider to rotate left or right.",
                            "Top bar: Back, Rotate 90°, Crop (tap, drag the box, tap again to apply), Adjust (Brightness / Contrast sliders), Share and Done. Undo/Redo float on the photo when nothing is selected.",
                            "Done bakes everything (with a soft shadow so marks stand out) onto the photo; Share sends the marked-up photo straight to WhatsApp or anywhere.",
                        ),
                    )
                    HelpCard(
                        RgsIcons.Sync, "8 · Transfer between phones",
                        listOf(
                            "Everything works fully offline — your recces are saved on this device.",
                            "Export Project (file) makes a .rgsproj file (data + photos) to send to another phone.",
                            "On the other phone, open the Dashboard and import the .rgsproj file to recreate the project.",
                        ),
                    )
                    HelpCard(
                        RgsIcons.Settings, "9 · Watermark & camera",
                        listOf(
                            "Watermark Fields: choose which details appear on the photo stamp.",
                            "Watermark Setting: reorder fields, change font size & colour, position, the background box and its opacity.",
                            "Camera Setting: aspect ratio (4:3 / 16:9), orientation, photo quality, and an optional clean copy without the watermark.",
                        ),
                    )
                    HelpCard(
                        RgsIcons.Check, "10 · Tips",
                        listOf(
                            "Fill the shop details before capturing — the watermark reads them live.",
                            "Hold the phone steady; the GPS stamp needs a moment to lock onto the location.",
                            "On a long route, use drafts — capture now and finish the details later.",
                            "Mark a board's exact spot or size right after capturing — tap the photo (yellow ✎) and use the editor, then save.",
                            "Editor: drag a corner to reshape, the centre grip to move; one tap on the photo toggles which tool draws.",
                            "Always export the report before deleting a project or removing its photos.",
                        ),
                    )

                    RgsCard {
                        Text(
                            "Offline-first GPS-stamp camera built for retail branding surveys.",
                            fontSize = 12.sp, color = NeutralTextSoft,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("© 2026 Manoj Graphics · Jalgaon", fontSize = 11.sp, color = NeutralTextSoft)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    RgsCard(Modifier.clickable { onClick() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).background(SoftChipGradient, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = AppYellowDark, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = NeutralText)
                Text(subtitle, fontSize = 11.5.sp, color = NeutralTextSoft)
            }
            Icon(RgsIcons.DropDown, null, tint = NeutralTextSoft, modifier = Modifier.size(22.dp).rotate(-90f))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp, color = NeutralTextSoft, modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp))
}

@Composable
private fun FieldReorderRow(label: String, canUp: Boolean, canDown: Boolean, onUp: () -> Unit, onDown: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = NeutralText, modifier = Modifier.weight(1f))
        Icon(
            RgsIcons.DropUp, "Move up", tint = if (canUp) NeutralText else NeutralOutline,
            modifier = Modifier.size(28.dp).clip(CircleShape).then(if (canUp) Modifier.clickable { onUp() } else Modifier).padding(3.dp),
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            RgsIcons.DropDown, "Move down", tint = if (canDown) NeutralText else NeutralOutline,
            modifier = Modifier.size(28.dp).clip(CircleShape).then(if (canDown) Modifier.clickable { onDown() } else Modifier).padding(3.dp),
        )
    }
}

private fun setField(s: AppSettings, key: String, v: Boolean): AppSettings = when (key) {
    "company" -> s.copy(wmCompany = v)
    "distributor" -> s.copy(wmDistributor = v)
    "shop" -> s.copy(wmShop = v)
    "city" -> s.copy(wmCity = v)
    "contact" -> s.copy(wmContact = v)
    "status" -> s.copy(wmStatus = v)
    "media" -> s.copy(wmMedia = v)
    "coordinates" -> s.copy(wmCoordinates = v)
    "accuracy" -> s.copy(wmAccuracy = v); "datetime" -> s.copy(wmDateTime = v)
    "address" -> s.copy(wmAddress = v); else -> s
}

private fun moveField(s: AppSettings, key: String, delta: Int): AppSettings {
    val list = s.order().toMutableList()
    val idx = list.indexOf(key)
    val target = idx + delta
    if (idx < 0 || target !in list.indices) return s
    list.removeAt(idx); list.add(target, key)
    return s.copy(wmOrder = list)
}

// Font colour palette (background uses the limited BG_PALETTE below).
private val WM_PALETTE = listOf(
    "#FFFFFF", "#000000", "#FFC400", "#E53935", "#43A047",
    "#1E88E5", "#FB8C00", "#00ACC1", "#8E24AA", "#795548",
)
private val BG_PALETTE = listOf("#FFFFFF", "#000000", "#FFC400")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorRow(label: String, selected: String, palette: List<String>, onSelect: (String) -> Unit) {
    val current = runCatching { Color(android.graphics.Color.parseColor(selected)) }.getOrDefault(Color.White)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = NeutralText, modifier = Modifier.weight(1f))
            // Live preview of the chosen colour.
            Box(Modifier.size(22.dp).clip(CircleShape).background(current).border(1.dp, NeutralOutline, CircleShape))
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            palette.forEach { hex ->
                val sel = selected.equals(hex, ignoreCase = true)
                Box(
                    Modifier.size(30.dp).clip(CircleShape)
                        .background(Color(android.graphics.Color.parseColor(hex)))
                        .border(if (sel) 3.dp else 1.dp, if (sel) AppYellowDark else NeutralOutline, CircleShape)
                        .clickable { onSelect(hex) },
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = NeutralText)
            if (subtitle != null) Text(subtitle, fontSize = 11.5.sp, color = NeutralTextSoft)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = AppYellow,
                uncheckedThumbColor = NeutralTextSoft,
                uncheckedTrackColor = NeutralSurfaceV,
            ),
        )
    }
}

@Composable
private fun SliderRow(label: String, value: Int, min: Int, max: Int, subtitle: String? = null, valueLabel: (Int) -> String = { "$it" }, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = NeutralText)
            if (subtitle != null) Text(subtitle, fontSize = 11.5.sp, color = NeutralTextSoft)
        }
        // Value chip
        Box(
            Modifier.background(YellowContainer, RoundedCornerShape(7.dp)).padding(horizontal = 9.dp, vertical = 3.dp),
        ) {
            Text(valueLabel(value), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = AppYellowDark)
        }
    }
    Spacer(Modifier.height(10.dp))
    RgsSlider(value, min, max, onChange)
    Spacer(Modifier.height(2.dp))
}

/** Clean enterprise-style horizontal slider — thin rounded track + elevated round thumb. */
@Composable
private fun RgsSlider(value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    val span = (max - min).toFloat()
    val frac = if (span > 0f) ((value - min) / span).coerceIn(0f, 1f) else 0f
    val thumbSize = 20.dp
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(thumbSize)
            .pointerInput(min, max) {
                val half = thumbSize.toPx() / 2f
                val usable = (size.width - thumbSize.toPx()).coerceAtLeast(1f)
                detectTapGestures { o ->
                    val f = ((o.x - half) / usable).coerceIn(0f, 1f)
                    onChange((min + f * span).roundToInt().coerceIn(min, max))
                }
            }
            .pointerInput(min, max) {
                val half = thumbSize.toPx() / 2f
                val usable = (size.width - thumbSize.toPx()).coerceAtLeast(1f)
                detectHorizontalDragGestures { ch, _ ->
                    ch.consume()
                    val f = ((ch.position.x - half) / usable).coerceIn(0f, 1f)
                    onChange((min + f * span).roundToInt().coerceIn(min, max))
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val trackW = maxWidth - thumbSize
        // Inactive track
        Box(
            Modifier.padding(horizontal = thumbSize / 2).fillMaxWidth().height(4.dp)
                .clip(RoundedCornerShape(50)).background(NeutralOutlineV),
        )
        // Active track
        Box(
            Modifier.padding(start = thumbSize / 2).width(trackW * frac).height(4.dp)
                .clip(RoundedCornerShape(50)).background(AppYellow),
        )
        // Elevated round thumb
        Box(
            Modifier.offset(x = trackW * frac).size(thumbSize)
                .shadow(3.dp, CircleShape).clip(CircleShape)
                .background(Color.White).border(2.dp, AppYellowDark, CircleShape),
        )
    }
}

@Composable
private fun SettingDivider() {
    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = NeutralOutline, thickness = 0.5.dp)
}

@Composable
private fun AboutRow(key: String, value: String) {
    Row {
        Text("$key: ", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NeutralText)
        Text(value, fontSize = 14.sp, color = NeutralText)
    }
}

@Composable
private fun FeatureLine(title: String, desc: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = NeutralText)
        Text(desc, fontSize = 11.5.sp, color = NeutralTextSoft)
    }
}

@Composable
private fun StepLine(num: Int, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(YellowContainer), contentAlignment = Alignment.Center) {
            Text("$num", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppYellowDark)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 12.5.sp, color = NeutralText, modifier = Modifier.weight(1f).padding(top = 2.dp))
    }
}

@Composable
private fun HelpCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, lines: List<String>) {
    var open by remember { mutableStateOf(false) }
    RgsCard(Modifier.clickable { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(SoftChipGradient), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = AppYellowDark, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(11.dp))
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = NeutralText, modifier = Modifier.weight(1f))
            Icon(if (open) RgsIcons.DropUp else RgsIcons.DropDown, null, tint = NeutralTextSoft, modifier = Modifier.size(20.dp))
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                lines.forEach { line ->
                    Row {
                        Text("•  ", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = AppYellowDark)
                        Text(line, fontSize = 12.5.sp, color = NeutralTextSoft, lineHeight = 18.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountPhoneSignIn(
    phoneState: PhoneAuthState,
    error: String?,
    onSend: (String) -> Unit,
    onVerify: (String) -> Unit,
    onResend: () -> Unit,
    onChangeNumber: () -> Unit,
) {
    var phoneDigits by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    val showCode = phoneState is PhoneAuthState.CodeSent || phoneState is PhoneAuthState.Verifying
    Column {
        if (!showCode) {
            Text("Sign in with mobile number", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = NeutralText)
            Spacer(Modifier.height(8.dp))
            AccountField("Mobile number (+91)", phoneDigits, KeyboardType.Phone) { phoneDigits = it.filter { c -> c.isDigit() }.take(10) }
            Spacer(Modifier.height(8.dp))
            AccountButton(
                if (phoneState is PhoneAuthState.Sending) "Sending OTP…" else "Send OTP",
                enabled = phoneDigits.length == 10 && phoneState !is PhoneAuthState.Sending,
                loading = phoneState is PhoneAuthState.Sending,
            ) { onSend("+91$phoneDigits") }
        } else {
            Text("Enter the 6-digit code", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = NeutralText)
            Spacer(Modifier.height(8.dp))
            AccountField("6-digit code", otp, KeyboardType.Number) { otp = it.filter { c -> c.isDigit() }.take(6) }
            Spacer(Modifier.height(8.dp))
            AccountButton(
                if (phoneState is PhoneAuthState.Verifying) "Verifying…" else "Verify & sign in",
                enabled = otp.length == 6 && phoneState !is PhoneAuthState.Verifying,
                loading = phoneState is PhoneAuthState.Verifying,
            ) { onVerify(otp) }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Change number", fontSize = 12.sp, color = NeutralTextSoft, modifier = Modifier.clickable { otp = ""; onChangeNumber() })
                Text("Resend code", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppYellowDark, modifier = Modifier.clickable { onResend() })
            }
        }
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error, fontSize = 12.sp, color = StatusError)
        }
    }
}

@Composable
private fun AccountField(label: String, value: String, keyboardType: KeyboardType, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = NeutralText, unfocusedTextColor = NeutralText,
            focusedBorderColor = AppYellowDark, unfocusedBorderColor = NeutralOutline,
            focusedLabelColor = AppYellowDark, cursorColor = AppYellowDark,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AccountButton(text: String, enabled: Boolean, loading: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (enabled || loading) BrandGradient else SolidColor(AppYellow.copy(alpha = 0.3f)))
            .clickable(enabled = enabled) { onClick() }.padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(14.dp), color = Color.Black, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }
    }
}

/** Editable surveyor profile shown on the Account page when signed in. */
@Composable
private fun AccountProfileForm(
    profile: AppProfile,
    accountEmail: String,
    onSave: (name: String, surname: String, mobile: String, email: String, gender: String, city: String, state: String) -> Unit,
) {
    // re-keyed on profile so it resets to the saved values after a successful save
    var name by remember(profile) { mutableStateOf(profile.name) }
    var surname by remember(profile) { mutableStateOf(profile.surname) }
    var mobile by remember(profile) { mutableStateOf(profile.mobile) }
    var email by remember(profile) { mutableStateOf(profile.email.ifBlank { accountEmail }) }
    var city by remember(profile) { mutableStateOf(profile.city) }
    var state by remember(profile) { mutableStateOf(profile.state) }
    var gender by remember(profile) { mutableStateOf(profile.gender) }

    Column {
        SectionLabel("YOUR PROFILE")
        AccountField("First name", name, KeyboardType.Text) { name = it }
        Spacer(Modifier.height(8.dp))
        AccountField("Surname", surname, KeyboardType.Text) { surname = it }
        Spacer(Modifier.height(8.dp))
        AccountField("Mobile number", mobile, KeyboardType.Phone) { mobile = it.filter { c -> c.isDigit() }.take(10) }
        Spacer(Modifier.height(8.dp))
        AccountField("Email", email, KeyboardType.Email) { email = it.trim() }
        Spacer(Modifier.height(8.dp))
        AccountField("City", city, KeyboardType.Text) { city = it }
        Spacer(Modifier.height(8.dp))
        AccountStateDropdown(state) { state = it }
        Spacer(Modifier.height(12.dp))
        Text("Gender", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = NeutralText)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Male", "Female").forEach { g ->
                val sel = gender == g
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                        .background(if (sel) YellowContainer else NeutralSurfaceV)
                        .clickable { gender = g }.padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(g, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (sel) AppYellowDark else NeutralTextSoft)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        val changed = name != profile.name || surname != profile.surname || mobile != profile.mobile ||
            email != profile.email.ifBlank { accountEmail } || city != profile.city ||
            state != profile.state || gender != profile.gender
        AccountButton("Save profile", enabled = changed && name.isNotBlank(), loading = false) {
            onSave(name, surname, mobile, email, gender, city, state)
        }
    }
}

/** State picker — a read-only field that opens a scrollable list of Indian states & UTs. */
@Composable
private fun AccountStateDropdown(value: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("State") },
            trailingIcon = { Icon(RgsIcons.DropDown, null, tint = NeutralTextSoft) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = NeutralText, unfocusedTextColor = NeutralText,
                focusedBorderColor = AppYellowDark, unfocusedBorderColor = NeutralOutline,
                focusedLabelColor = AppYellowDark, unfocusedLabelColor = NeutralTextSoft, cursorColor = AppYellowDark,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        // Transparent overlay so a tap anywhere on the read-only field opens the menu.
        Box(Modifier.matchParentSize().clip(RoundedCornerShape(4.dp)).clickable { expanded = true })
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 320.dp),
        ) {
            INDIAN_STATES.forEach { st ->
                DropdownMenuItem(text = { Text(st) }, onClick = { onSelect(st); expanded = false })
            }
        }
    }
}

private val INDIAN_STATES = listOf(
    "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", "Chhattisgarh", "Goa", "Gujarat",
    "Haryana", "Himachal Pradesh", "Jharkhand", "Karnataka", "Kerala", "Madhya Pradesh",
    "Maharashtra", "Manipur", "Meghalaya", "Mizoram", "Nagaland", "Odisha", "Punjab", "Rajasthan",
    "Sikkim", "Tamil Nadu", "Telangana", "Tripura", "Uttar Pradesh", "Uttarakhand", "West Bengal",
    "Andaman and Nicobar Islands", "Chandigarh", "Dadra and Nagar Haveli and Daman and Diu",
    "Delhi", "Jammu and Kashmir", "Ladakh", "Lakshadweep", "Puducherry",
)

/** Unwraps the hosting Activity from a Compose Context (needed for Firebase phone verification). */
private fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
