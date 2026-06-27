package com.receegpsstamp.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.receegpsstamp.data.model.MediaItem
import com.receegpsstamp.data.model.RecceEntry
import com.receegpsstamp.feature.app.AppViewModel
import com.receegpsstamp.feature.recce.StartRecceScreen
import com.receegpsstamp.feature.setup.ProjectSetupScreen

import com.receegpsstamp.ui.theme.AppYellow
import com.receegpsstamp.ui.theme.AppYellowDark
import com.receegpsstamp.ui.theme.BrandGradient
import com.receegpsstamp.ui.theme.BrandGrey
import com.receegpsstamp.ui.theme.SoftChipGradient
import com.receegpsstamp.ui.theme.NeutralBg
import com.receegpsstamp.ui.theme.NeutralOutline
import com.receegpsstamp.ui.theme.NeutralSurface
import com.receegpsstamp.ui.theme.NeutralSurfaceV
import com.receegpsstamp.ui.theme.NeutralText
import com.receegpsstamp.ui.theme.NeutralTextSoft
import com.receegpsstamp.ui.theme.RgsIcons
import com.receegpsstamp.ui.theme.YellowContainer
import kotlinx.coroutines.launch

private data class Tab(val route: String, val icon: ImageVector, val label: String)

private val tabs = listOf(
    Tab("tab_dashboard", RgsIcons.Dashboard, "Dashboard"),
    Tab("tab_start", RgsIcons.Play, "Start Work"),
    Tab("tab_project", RgsIcons.Project, "Project"),
)

private data class DrawerItem(val icon: ImageVector, val label: String, val subtitle: String, val route: String)

private val drawerItems = listOf(
    DrawerItem(RgsIcons.Dashboard, "Dashboard", "Overview & projects", "tab_dashboard"),
    DrawerItem(RgsIcons.Gallery, "Gallery", "All captured photos", Routes.GALLERY),
    DrawerItem(RgsIcons.Wallet, "Expenses", "Trip & job expenses", Routes.EXPENSES),
    DrawerItem(RgsIcons.Fleet, "Fleet Manage", "Vehicles, fuel & service", Routes.FLEET),
    DrawerItem(RgsIcons.Settings, "Settings", "Watermark, sync & more", Routes.SETTINGS),
    DrawerItem(RgsIcons.Account, "Account Setting", "Profile & sign in/out", "${Routes.SETTINGS}?page=account"),
    DrawerItem(RgsIcons.Info, "About App", "Version & developer info", "${Routes.SETTINGS}?page=about"),
)

@Composable
fun MainScaffold(onNavigate: (String) -> Unit, appViewModel: AppViewModel = hiltViewModel()) {
    val appState by appViewModel.state.collectAsStateWithLifecycle()
    // State-driven tab selection (rememberSaveable) instead of a nested NavController.
    // A nested NavHost loses its back stack when the app navigates out to the full-screen
    // Camera and back, snapping the user to the start tab (Project). A saveable selectedTab
    // survives that round-trip and keeps the user on Start Recce.
    var current by rememberSaveable { mutableStateOf("tab_project") }
    // If a project is already selected when the app opens, land on Start Recce instead of Project
    // setup. Fires once per process; the user's manual tab choice afterwards is respected.
    var didAutoRoute by rememberSaveable { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(appState.selectedDistributor?.id) {
        if (!didAutoRoute && appState.selectedDistributor != null) {
            current = "tab_start"
            didAutoRoute = true
        }
    }
    // A recce reopened from the Dashboard ("Update Store Details") → jump to Start Work to edit it.
    val editRecceId by appViewModel.editRecceId.collectAsStateWithLifecycle()
    val recceToEdit = appState.allRecces.find { it.id == editRecceId }
    androidx.compose.runtime.LaunchedEffect(editRecceId) {
        if (editRecceId != null) current = "tab_start"
    }
    // Camera is shown as an overlay INSIDE this scaffold (not a separate nav destination)
    // so MAIN never leaves composition. Navigating out to a full-screen Camera destination
    // was destroying this subtree and snapping the user back to the Project tab on return.
    var cameraOpen by rememberSaveable { mutableStateOf(false) }
    var pendingWatermark by remember { mutableStateOf(com.receegpsstamp.feature.camera.PhotoWatermark()) }
    // WhatsApp batch share — selected stores are grouped 10-per-message (combined details + all
    // their photos). >10 shops split into batches, sent one message at a time.
    var waQueue by remember { mutableStateOf<List<List<String>>>(emptyList()) }
    var waPos by remember { mutableStateOf(0) }
    // Shop work-list → Start Work hand-off: which pending shop to pre-fill, and whether to return to the list after saving.
    var startShopId by rememberSaveable { mutableStateOf<String?>(null) }
    var startedFromShopList by rememberSaveable { mutableStateOf(false) }
    var openInstallShop by rememberSaveable { mutableStateOf<String?>(null) }   // shop key whose install screen is open
    var installCamMode by rememberSaveable { mutableStateOf("") }   // "" | "before" | "after" — routes the camera photo
    var installCamTarget by rememberSaveable { mutableStateOf<String?>(null) }  // InstallEntry id the camera photo attaches to
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Web approvals (Installation list, assigned shops, fleet) only pull on cold start — re-pull
    // whenever the app returns to the foreground so an admin's approve reaches this phone without a restart.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) appViewModel.syncNow()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // System back closes the camera overlay instead of exiting the app.
    androidx.activity.compose.BackHandler(enabled = cameraOpen) { cameraOpen = false }
    // System back closes the shop install screen (when the camera isn't up).
    androidx.activity.compose.BackHandler(enabled = openInstallShop != null && !cameraOpen) { openInstallShop = null }

    // Surface one-off ViewModel messages (e.g. "Sign in to continue") as toasts.
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val appVersion = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "" }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        appViewModel.messages.collect { msg ->
            android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    // Show the share sheet when a report (PDF/Excel) is generated.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        appViewModel.shareFile.collect { (uri, mime) ->
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = mime
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(android.content.Intent.createChooser(intent, "Share report"))
        }
    }
    // Share one/many stores' details — text (+ photos for a single store).
    // The Dashboard tab lives inside this scaffold, so the share request must be
    // collected here too (not only on the standalone DASHBOARD destination).
    androidx.compose.runtime.LaunchedEffect(Unit) {
        appViewModel.shareReq.collect { req ->
            val uris = req.photoPaths.mapNotNull { path ->
                val f = java.io.File(path)
                if (f.exists()) androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f) else null
            }
            val intent = when {
                uris.size > 1 -> android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "image/*"
                    putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, ArrayList(uris))
                    putExtra(android.content.Intent.EXTRA_TEXT, req.text)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                uris.size == 1 -> android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(android.content.Intent.EXTRA_STREAM, uris.first())
                    putExtra(android.content.Intent.EXTRA_TEXT, req.text)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                else -> android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, req.text)
                }
            }
            // WhatsApp drops the caption on multi-image shares — copy text so it can be pasted.
            val clip = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clip.setPrimaryClip(android.content.ClipData.newPlainText("Store", req.text))
            // Open WhatsApp directly (Business-first per Settings) so each shop lands in one chat;
            // fall back to the system chooser if WhatsApp isn't installed.
            val pkgs = if (appViewModel.settings.value.waBusiness) listOf("com.whatsapp.w4b", "com.whatsapp") else listOf("com.whatsapp", "com.whatsapp.w4b")
            val opened = pkgs.any { pkg ->
                try { ctx.startActivity(android.content.Intent(intent).setPackage(pkg)); true } catch (_: Exception) { false }
            }
            if (!opened) ctx.startActivity(android.content.Intent.createChooser(intent, "Share store"))
        }
    }
    val settings by appViewModel.settings.collectAsStateWithLifecycle()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = NeutralSurface,
                modifier = Modifier.width(280.dp),
            ) {
                Column(
                    Modifier.fillMaxHeight()
                        .windowInsetsPadding(WindowInsets.statusBars),
                ) {
                    // Header — logo + name + tagline + current project
                    Column(
                        Modifier.fillMaxWidth().background(BrandGrey).padding(horizontal = 18.dp, vertical = 16.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(11.dp),
                        ) {
                            Box(
                                Modifier.size(44.dp).background(BrandGradient, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("RGS", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Recce GPS Stamp", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text(
                                    appState.userName.ifBlank { "Field execution, verified." },
                                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(0.7f),
                                )
                            }
                        }
                        appState.selectedDistributor?.let { dist ->
                            Spacer(Modifier.height(13.dp))
                            Row(
                                Modifier.fillMaxWidth().background(Color.White.copy(0.08f), RoundedCornerShape(10.dp)).padding(horizontal = 11.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(RgsIcons.Project, null, tint = AppYellow, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(9.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("CURRENT PROJECT", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(0.45f))
                                    Text(
                                        listOfNotNull(appState.selectedCompany?.name, dist.name).joinToString(" · "),
                                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Menu items — icon badge + label + subtitle
                    drawerItems.forEach { item ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    // Close the drawer, then either switch to the in-scaffold Dashboard
                                    // tab or navigate to the item's route.
                                    scope.launch { drawerState.close() }
                                    if (item.route == "tab_dashboard") current = "tab_dashboard" else onNavigate(item.route)
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(38.dp).background(SoftChipGradient, RoundedCornerShape(11.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(item.icon, contentDescription = item.label, tint = AppYellowDark, modifier = Modifier.size(19.dp))
                            }
                            Spacer(Modifier.width(13.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.label, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = NeutralText)
                                Text(item.subtitle, fontSize = 11.sp, color = NeutralTextSoft)
                            }
                        }
                    }

                    // ── Project — expandable; pick a project to open & work on it ──
                    var projectMenuOpen by remember { mutableStateOf(false) }
                    Row(
                        Modifier.fillMaxWidth().clickable { projectMenuOpen = !projectMenuOpen }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(38.dp).background(SoftChipGradient, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                            Icon(RgsIcons.Project, "Project", tint = AppYellowDark, modifier = Modifier.size(19.dp))
                        }
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Project", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = NeutralText)
                            Text("${appState.distributors.size} projects · tap to open", fontSize = 11.sp, color = NeutralTextSoft)
                        }
                        Icon(if (projectMenuOpen) RgsIcons.DropUp else RgsIcons.DropDown, null, tint = NeutralTextSoft, modifier = Modifier.size(20.dp))
                    }
                    AnimatedVisibility(projectMenuOpen, enter = expandVertically(), exit = shrinkVertically()) {
                        Column {
                            appState.distributors.forEach { dist ->
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        appViewModel.openProject(dist.companyId, dist.id)
                                        current = "tab_start"
                                        scope.launch { drawerState.close() }
                                    }.padding(start = 66.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(AppYellow))
                                    Spacer(Modifier.width(11.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(dist.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = NeutralText, maxLines = 1)
                                        Text(
                                            appState.companies.find { it.id == dist.companyId }?.name ?: dist.companyName,
                                            fontSize = 10.5.sp, color = NeutralTextSoft, maxLines = 1,
                                        )
                                    }
                                }
                            }
                            if (appState.distributors.isEmpty()) {
                                Text("No projects yet", fontSize = 12.sp, color = NeutralTextSoft, modifier = Modifier.padding(start = 66.dp, top = 4.dp, bottom = 6.dp))
                            }
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    // Footer
                    Box(Modifier.fillMaxWidth().height(1.dp).background(NeutralOutline))
                    Text(
                        "v$appVersion · Manoj Graphics",
                        fontSize = 11.sp, color = NeutralTextSoft,
                        modifier = Modifier.padding(16.dp, 12.dp),
                    )
                }
            }
        },
    ) {
      Box(Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                NavigationBar(
                    containerColor = NeutralSurface,
                    tonalElevation = NavigationBarDefaults.Elevation,
                ) {
                    tabs.forEach { tab ->
                        val sel = current == tab.route
                        NavigationBarItem(
                            selected = sel,
                            onClick = { current = tab.route },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = {
                                Text(
                                    tab.label, fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = NeutralText,
                                selectedTextColor = NeutralText,
                                unselectedIconColor = NeutralTextSoft,
                                unselectedTextColor = NeutralTextSoft,
                                indicatorColor = YellowContainer,
                            ),
                        )
                    }
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad)) {
                when (current) {
                    "tab_dashboard" -> com.receegpsstamp.feature.dashboard.DashboardScreen(
                        onMenuClick = { scope.launch { drawerState.open() } },
                        distributorCount = appState.distributors.size,
                        distributorName = appState.selectedDistributor?.name ?: "",
                        selectedDistributorId = appState.selectedDistributor?.id ?: "",
                        distributors = appState.distributors,
                        companies = appState.companies,
                        allShops = appState.allShops,
                        allRecces = appState.allRecces,
                        expenses = appState.expenses,
                        onOpenExpenses = { onNavigate(Routes.EXPENSES) },
                        onSetProjectStage = { id, stg -> appViewModel.setProjectStage(id, stg) },
                        onProjectExportPdf = { appViewModel.exportProjectPdf(it) },
                        onProjectExportExcel = { appViewModel.exportProjectExcel(it) },
                        onProjectExportPptx = { appViewModel.exportProjectPptx(it) },
                        onRemoveProjectPhotos = { appViewModel.removeProjectPhotos(it) },
                        onDeleteProject = { appViewModel.deleteProject(it) },
                        onImportProject = { appViewModel.importProjectFile(it) },
                        onEditStore = { appViewModel.startEditRecce(it); current = "tab_start" },
                        onShareStore = { appViewModel.shareStore(it.id) },
                        onShareStores = { ids ->
                            val batches = ids.chunked(10)   // max 10 shops per WhatsApp message
                            if (batches.size <= 1) appViewModel.shareStoresGroup(ids)  // one message — go straight to WhatsApp
                            else { waQueue = batches; waPos = 0 }                       // many batches — step through them
                        },
                        onExportStoresPdf = { appViewModel.exportStoresPdf(it) },
                        onExportStoresExcel = { appViewModel.exportStoresExcel(it) },
                        onExportStoresPptx = { appViewModel.exportStoresPptx(it) },
                    )
                    "tab_start" -> StartRecceScreen(
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onCameraClick = { wm -> pendingWatermark = wm; cameraOpen = true },
                        shareToWhatsApp = settings.whatsappShare,
                        waIncludePhotos = settings.waIncludePhotos,
                        waIncludeLocation = settings.waIncludeLocation,
                        waIncludeRecceBy = settings.waIncludeRecceBy,
                        preferWhatsAppBusiness = settings.waBusiness,
                        hapticsEnabled = settings.haptics,
                        shops = appState.shops,
                        distributorName = appState.selectedDistributor?.name ?: "",
                        companyName = appState.selectedCompany?.name ?: "",
                        waGroup = appState.selectedDistributor?.waGroup ?: "",
                        userName = appState.userName,
                        selectedDistributorId = appState.selectedDistributor?.id ?: "",
                        onSaveRecce = { shopId, status, remark, media ->
                            appViewModel.saveRecce(
                                RecceEntry(shopId = shopId, status = status, remark = remark, media = media),
                            )
                        },
                        capturedPhotos = appViewModel.capturedPhotos,
                        onClearPhotos = { appViewModel.clearCapturedPhotos() },
                        onRemovePhoto = { appViewModel.removeCapturedPhoto(it) },
                        onEditPhoto = { path -> appViewModel.startAnnotate(path); onNavigate(Routes.ANNOTATE) },
                        drafts = appState.drafts,
                        onSaveDraft = { appViewModel.saveDraft(it) },
                        onDeleteDraft = { id, delPhotos -> appViewModel.deleteDraft(id, delPhotos) },
                        onLoadPhotos = { appViewModel.loadCapturedPhotos(it) },
                        recceToEdit = recceToEdit,
                        onConsumeEdit = { appViewModel.consumeEditRecce() },
                        onUpdateRecce = { id, status, remark, media -> appViewModel.updateRecce(id, status, remark, media) },
                        preselectShopId = startShopId,
                        onConsumePreselect = { startShopId = null },
                        onAfterSave = { if (startedFromShopList) { startedFromShopList = false; current = "tab_project" } },
                        creatives = appState.selectedCompany?.creatives ?: emptyList(),
                        projectCreative = appState.selectedDistributor?.defaultCreative ?: "",
                        onSetProjectCreative = { appViewModel.setProjectCreative(it) },
                        mediaTypes = appState.selectedCompany?.mediaTypes ?: emptyList(),
                        onSaveRecceNewShop = { name, city, contact, status, remark, media ->
                            appViewModel.saveRecceWithNewShop(name, city, contact, status, remark, media)
                        },
                        onFetchLocation = { callback -> appViewModel.fetchLocation(callback) },
                    )
                    else -> ProjectSetupScreen(
                        // "Start Recce" switches to the in-scaffold tab instead of navigating
                        // out to a duplicate standalone screen.
                        onNavigate = { route ->
                            if (route == Routes.RECCE) current = "tab_start" else onNavigate(route)
                        },
                        onMenuClick = { scope.launch { drawerState.open() } },
                        distributorName = appState.selectedDistributor?.name ?: "",
                        companyName = appState.selectedCompany?.name ?: "",
                        shopCount = appState.shops.size,
                        companies = appState.companies,
                        distributors = appState.distributors,
                        selectedCompany = appState.selectedCompany,
                        selectedDistributor = appState.selectedDistributor,
                        onSelectCompany = { appViewModel.selectCompany(it) },
                        onSelectDistributor = { appViewModel.selectDistributor(it) },
                        onAddCompany = { name -> appViewModel.addCompany(name) },
                        onUpdateCompany = { appViewModel.updateCompany(it) },
                        onDeleteCompany = { appViewModel.deleteCompany(it) },
                        onAddDistributor = { name, city, companyId, companyName, contact ->
                            appViewModel.addDistributor(name, city, companyId, companyName, contact)
                        },
                        onUpdateDistributor = { appViewModel.updateDistributor(it) },
                        onDeleteDistributor = { appViewModel.deleteProject(it) },
                        creatives = appState.selectedCompany?.creatives ?: emptyList(),
                        mediaTypes = appState.selectedCompany?.mediaTypes ?: emptyList(),
                        onAddCreative = { appViewModel.addCreative(it) },
                        onRemoveCreative = { appViewModel.removeCreative(it) },
                        onAddMediaType = { appViewModel.addMediaType(it) },
                        onRemoveMediaType = { appViewModel.removeMediaType(it) },
                        shops = appState.shops,
                        onAddShop = { n, c, ct -> appViewModel.addShop(n, c, ct) },
                        onImportShops = { appViewModel.importShops(it) },
                        onStartShop = { shop -> startShopId = shop.id; startedFromShopList = true; current = "tab_start" },
                        onSetShopStatus = { shop, status -> appViewModel.setShopStatus(shop, status) },
                        onUpdateShop = { appViewModel.updateShop(it) },
                        installs = appState.installs,
                        onOpenInstallShop = { openInstallShop = it },
                        onRefreshInstalls = { appViewModel.syncNow() },
                    )
                }
            }
        }

        // Full-screen camera overlay — keeps MAIN composed so the tab/form state survives.
        if (cameraOpen) {
            com.receegpsstamp.feature.camera.CameraScreen(
                onBack = { cameraOpen = false; installCamMode = "" },
                onPhotoUsed = { path ->
                    when (installCamMode) {
                        "after" -> installCamTarget?.let { appViewModel.addInstallAfterPhoto(it, path) }
                        "before" -> installCamTarget?.let { appViewModel.addInstallBeforePhoto(it, path) }
                        "front" -> installCamTarget?.let { appViewModel.addInstallFrontPhoto(it, path) }
                        else -> appViewModel.addCapturedPhoto(path)
                    }
                    installCamMode = ""
                    cameraOpen = false
                },
                companyName = appState.selectedCompany?.name ?: "",
                distributorName = appState.selectedDistributor?.name ?: "",
                watermark = pendingWatermark,
            )
        }

        // Install overlay — SHOP-level: all approved sizes of the tapped shop, captured per size.
        openInstallShop?.let { shopKey ->
            val entries = appState.installs.filter { (it.shopId.ifBlank { it.shopName }) == shopKey }.sortedBy { it.mediaIndex }
            if (entries.isEmpty()) {
                openInstallShop = null
            } else if (!cameraOpen) {
                val head = entries.first()
                com.receegpsstamp.feature.install.InstallShopScreen(
                    shopName = head.shopName,
                    city = head.city,
                    entries = entries,
                    frontPhotos = entries.first().frontPhotos,
                    recceBefore = { e ->
                        val recce = appState.allRecces.find { it.id == e.recceId }
                        recce?.media?.getOrNull(e.mediaIndex)?.photos?.takeIf { it.isNotEmpty() }
                            ?: recce?.shopPhotos
                            ?: emptyList()
                    },
                    onStart = { appViewModel.markInstallStarted(it) },
                    onAddFront = { installCamTarget = entries.first().id; installCamMode = "front"; cameraOpen = true },
                    onAddBefore = { installCamTarget = it; installCamMode = "before"; cameraOpen = true },
                    onAddAfter = { installCamTarget = it; installCamMode = "after"; cameraOpen = true },
                    onDone = { appViewModel.markInstalled(it) },
                    onNotDone = { id, reason -> appViewModel.markInstallNotDone(id, reason) },
                    onReopen = { appViewModel.reopenInstall(it) },
                    onShareWhatsApp = { appViewModel.shareInstallShop(shopKey); openInstallShop = null },
                    onBack = { openInstallShop = null },
                )
            }
        }

        // ── WhatsApp: batch share (only when >10 shops, split into 10-per-message batches) ──
        // Each Send opens WhatsApp with one batch's combined details + all its photos; send it,
        // come back, then send the next batch.
        if (waQueue.isNotEmpty()) {
            val total = waQueue.size
            if (waPos < total) {
                val batch = waQueue[waPos]
                val recces = appState.allRecces.filter { it.id in batch }
                val photoCount = recces.flatMap { it.shopPhotos + it.media.flatMap { m -> m.photos } }.distinct().size
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { waQueue = emptyList() },
                    title = { androidx.compose.material3.Text("Send to WhatsApp · batch ${waPos + 1} of $total") },
                    text = {
                        androidx.compose.material3.Text(
                            "${batch.size} shops + $photoCount photos in this message.\n\n" +
                                "Tap Send, pick the chat in WhatsApp, then come back here for the next batch.",
                        )
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = { appViewModel.shareStoresGroup(batch); waPos++ }) {
                            androidx.compose.material3.Text(if (waPos == 0) "Send batch 1" else "Send next batch")
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { waQueue = emptyList() }) {
                            androidx.compose.material3.Text("Stop")
                        }
                    },
                )
            } else {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { waQueue = emptyList() },
                    title = { androidx.compose.material3.Text("All batches shared") },
                    text = { androidx.compose.material3.Text("$total messages sent to WhatsApp.") },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = { waQueue = emptyList() }) {
                            androidx.compose.material3.Text("Done")
                        }
                    },
                )
            }
        }
      }
    }
}
