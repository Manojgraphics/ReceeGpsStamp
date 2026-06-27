package com.receegpsstamp.feature.setup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.receegpsstamp.navigation.Routes
import com.receegpsstamp.data.model.Company
import com.receegpsstamp.data.model.Distributor
import com.receegpsstamp.data.model.InstallEntry
import com.receegpsstamp.data.model.Shop
import com.receegpsstamp.ui.components.AddDistributorDialog
import com.receegpsstamp.ui.components.AddItemDialog
import com.receegpsstamp.ui.components.EditDistributorDialog
import com.receegpsstamp.ui.components.CompactDropdown
import com.receegpsstamp.ui.components.PrimaryButton
import com.receegpsstamp.ui.components.RgsCard
import com.receegpsstamp.ui.components.RgsTopBar
import com.receegpsstamp.ui.components.SecondaryButton
import com.receegpsstamp.ui.components.SectionHeader
import com.receegpsstamp.ui.theme.*

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProjectSetupScreen(
    onNavigate: (String) -> Unit = {},
    onMenuClick: () -> Unit = {},
    distributorName: String = "",
    companyName: String = "",
    shopCount: Int = 0,
    companies: List<Company> = emptyList(),
    distributors: List<Distributor> = emptyList(),
    onSelectCompany: (String) -> Unit = {},
    onSelectDistributor: (String) -> Unit = {},
    selectedCompany: Company? = null,
    selectedDistributor: Distributor? = null,
    onAddDistributor: (name: String, city: String, companyId: String, companyName: String, contact: String) -> Unit = { _, _, _, _, _ -> },
    onUpdateDistributor: (Distributor) -> Unit = {},
    onDeleteDistributor: (String) -> Unit = {},
    creatives: List<String> = emptyList(),
    mediaTypes: List<String> = emptyList(),
    onAddCreative: (String) -> Unit = {},
    onAddMediaType: (String) -> Unit = {},
    shops: List<Shop> = emptyList(),
    onAddShop: (name: String, city: String, contact: String) -> Unit = { _, _, _ -> },
    onImportShops: (String) -> Unit = {},
    onStartShop: (Shop) -> Unit = {},
    onSetShopStatus: (Shop, String) -> Unit = { _, _ -> },
    onUpdateShop: (Shop) -> Unit = {},
    installs: List<InstallEntry> = emptyList(),
    onOpenInstallShop: (String) -> Unit = {},
    onRefreshInstalls: () -> Unit = {},
) {
    var creativesOpen by remember { mutableStateOf(false) }
    var mediaOpen by remember { mutableStateOf(false) }
    var showDistDialog by remember { mutableStateOf(false) }
    var showAddCreative by remember { mutableStateOf(false) }
    var showAddMediaType by remember { mutableStateOf(false) }
    var companyDropdownOpen by remember { mutableStateOf(false) }
    var distributorDropdownOpen by remember { mutableStateOf(false) }
    var showEditDistributor by remember { mutableStateOf(false) }
    var deleteDistConfirm by remember { mutableStateOf(false) }
    var showAddShop by remember { mutableStateOf(false) }
    var editShop by remember { mutableStateOf<Shop?>(null) }
    var showImportShops by remember { mutableStateOf(false) }
    var workMode by remember { mutableStateOf("recce") }   // "recce" | "install"

    if (deleteDistConfirm && selectedDistributor != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleteDistConfirm = false },
            title = { Text("Delete distributor?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Deletes \"${selectedDistributor.name}\" and all its shops, recces & photos. Can't be undone.",
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                Text("Delete distributor", color = StatusError, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onDeleteDistributor(selectedDistributor.id); deleteDistConfirm = false }.padding(8.dp))
            },
            dismissButton = {
                Text("Cancel", color = NeutralTextSoft, modifier = Modifier.clickable { deleteDistConfirm = false }.padding(8.dp))
            },
        )
    }

    if (showDistDialog) {
        AddDistributorDialog(
            onDismiss = { showDistDialog = false },
            companies = companies,
            preselectCompany = selectedCompany,
            onSave = onAddDistributor,
        )
    }
    if (showEditDistributor && selectedDistributor != null) {
        EditDistributorDialog(
            distributor = selectedDistributor,
            companies = companies,
            onDismiss = { showEditDistributor = false },
            onSave = onUpdateDistributor,
            onDelete = { showEditDistributor = false; deleteDistConfirm = true },
        )
    }
    if (showAddCreative) {
        AddItemDialog(title = "Add Creative", label = "Creative name", placeholder = "e.g. BigBoss",
            onDismiss = { showAddCreative = false }, onSave = { onAddCreative(it); showAddCreative = false })
    }
    if (showAddMediaType) {
        AddItemDialog(title = "Add Media Type", label = "Media type", placeholder = "e.g. Shop Board",
            onDismiss = { showAddMediaType = false }, onSave = { onAddMediaType(it); showAddMediaType = false })
    }
    editShop?.let { es ->
        AddShopDialog(
            onDismiss = { editShop = null },
            onSave = { n, c, ct -> onUpdateShop(es.copy(name = n, city = c, contact = ct)); editShop = null },
            title = "Edit shop", confirmLabel = "Save",
            initialName = es.name, initialCity = es.city, initialContact = es.contact,
        )
    }
    if (showAddShop) {
        AddShopDialog(onDismiss = { showAddShop = false }, onSave = { n, c, ct -> onAddShop(n, c, ct); showAddShop = false })
    }
    if (showImportShops) {
        ImportShopsDialog(onDismiss = { showImportShops = false }, onImport = { onImportShops(it); showImportShops = false })
    }

    var page by remember { mutableStateOf("main") }
    androidx.activity.compose.BackHandler(enabled = page == "setup") { page = "main" }
    val canStart = selectedCompany != null && selectedDistributor != null

    Column(Modifier.fillMaxSize().background(NeutralBg)) {
        RgsTopBar(
            if (page == "setup") "Project Setup" else "Project",
            showBack = page == "setup",
            onNav = if (page == "setup") {{ page = "main" }} else onMenuClick,
        )

        var refreshing by remember { mutableStateOf(false) }
        if (refreshing) androidx.compose.runtime.LaunchedEffect(Unit) { kotlinx.coroutines.delay(900); refreshing = false }
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { onRefreshInstalls(); refreshing = true },
            modifier = Modifier.weight(1f),
        ) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
            if (page == "main") {
                if (canStart) {
                    CurrentProjectCard(
                        companyName = companyName,
                        distributorName = distributorName,
                        creatives = creatives.size,
                        media = mediaTypes.size,
                        stage = selectedDistributor?.stage ?: "",
                        onSetup = { page = "setup" },
                    )

                    // Quick switch — up to 3 of this company's projects, one tap to jump.
                    val companyDistributors = distributors.filter { it.companyId == (selectedCompany?.id ?: "") }
                    if (companyDistributors.size >= 2) {
                        var quick = companyDistributors.take(3)
                        if (selectedDistributor != null && quick.none { it.id == selectedDistributor.id }) {
                            quick = (listOf(selectedDistributor) + companyDistributors.filter { it.id != selectedDistributor.id }).take(3)
                        }
                        SectionHeader("QUICK SWITCH PROJECT")
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(NeutralSurfaceV).padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            quick.forEach { d ->
                                val sel = d.id == selectedDistributor?.id
                                Box(
                                    Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                        .background(if (sel) AppYellow else Color.Transparent)
                                        .clickable(enabled = !sel) { onSelectDistributor(d.id) }
                                        .padding(vertical = 9.dp, horizontal = 4.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        (if (d.completedAt > 0L) "✓ " else "") + d.name, fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                                        color = if (sel) Color.Black else NeutralTextSoft, maxLines = 1,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    // Recce/Installation toggle lives in the Project Setup screen now; the main page just
                    // shows the current mode's work-list. Switch mode in Setup (gear button).
                    val projInstalls = installs.filter { it.distributorId == (selectedDistributor?.id ?: "_") }
                    if (workMode == "recce") {
                        ShopsWorkList(shops = shops, onAddShop = { showAddShop = true }, onImport = { showImportShops = true }, onStartShop = onStartShop, onEditShop = { editShop = it }, onSetStatus = onSetShopStatus)
                    } else {
                        InstallWorkList(projInstalls, onOpenShop = onOpenInstallShop)
                    }
                } else {
                    EmptyProjectPrompt(onSetup = { page = "setup" })
                }
            } else {
            SectionHeader("COMPANY")
            SelectableFieldRow(
                value = companyName.ifEmpty { "Select company…" },
                expanded = companyDropdownOpen,
                onToggle = { companyDropdownOpen = !companyDropdownOpen },
                onEdit = null, // company add/edit/delete is manager-only (web "Manage catalog")
            ) {
                companies.forEach { co ->
                    DropdownMenuItem(
                        text = { Text(co.name, fontSize = 14.sp, fontWeight = FontWeight.Medium) },
                        onClick = {
                            onSelectCompany(co.id)
                            companyDropdownOpen = false
                        },
                    )
                }
            }

            SectionHeader("DISTRIBUTOR")
            SelectableFieldRow(
                value = distributorName.ifEmpty { "Select distributor…" },
                expanded = distributorDropdownOpen,
                onToggle = { distributorDropdownOpen = !distributorDropdownOpen },
                onEdit = if (selectedDistributor != null) {{ showEditDistributor = true }} else null,
            ) {
                val filteredDists = if (selectedCompany != null) distributors.filter { it.companyId == selectedCompany.id } else distributors
                filteredDists.forEach { d ->
                    DropdownMenuItem(
                        text = { Text(d.name, fontSize = 14.sp, fontWeight = FontWeight.Medium) },
                        onClick = { onSelectDistributor(d.id); distributorDropdownOpen = false },
                    )
                }
                if (filteredDists.isEmpty() && selectedCompany == null) {
                    DropdownMenuItem(
                        text = { Text("Select a company first", fontSize = 13.sp, color = NeutralTextSoft) },
                        onClick = { distributorDropdownOpen = false },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Add new distributor", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppYellowDark) },
                    leadingIcon = { Icon(RgsIcons.Add, null, tint = AppYellowDark, modifier = Modifier.size(18.dp)) },
                    onClick = { distributorDropdownOpen = false; showDistDialog = true },
                )
            }

            SectionHeader("WORK MODE")
            val setupInstalls = installs.filter { it.distributorId == (selectedDistributor?.id ?: "_") }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(NeutralSurfaceV).padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                SegTab("Recce", workMode == "recce", Modifier.weight(1f)) { workMode = "recce" }
                SegTab("Installation · ${setupInstalls.size}", workMode == "install", Modifier.weight(1f)) { workMode = "install" }
            }

            ChipSection("Creatives", RgsIcons.Edit, creatives.size, creativesOpen, { creativesOpen = !creativesOpen }) {
                EditableChipFlow(creatives, onAdd = { showAddCreative = true })
            }
            ChipSection("Media Types", RgsIcons.Grid, mediaTypes.size, mediaOpen, { mediaOpen = !mediaOpen }) {
                EditableChipFlow(mediaTypes, onAdd = { showAddMediaType = true })
            }

            Spacer(Modifier.height(4.dp))
            if (!canStart) {
                Text(
                    "Select a company and distributor to start",
                    fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = StatusError,
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
                )
            }
            PrimaryButton("Start Project", onClick = { onNavigate(Routes.RECCE) }, enabled = canStart)
            if (canStart) {
                Spacer(Modifier.height(2.dp))
                SecondaryButton("Done — view shops", onClick = { page = "main" }, modifier = Modifier.fillMaxWidth())
            }
            }
            }
        }
    }
}

@Composable
private fun CurrentProjectCard(companyName: String, distributorName: String, creatives: Int, media: Int, stage: String, onSetup: () -> Unit) {
    val modeLabel = when (stage.lowercase()) {
        "installation", "installdone" -> "🛠 Installation mode"
        "recce", "reccedone" -> "📋 Recce mode"
        else -> "📋 Recce mode" // default: recce
    }
    val modeBg = if (stage.lowercase().startsWith("install")) Color(0xFFE8F5EC) else Color(0xFFFFF6DC)
    val modeFg = if (stage.lowercase().startsWith("install")) Color(0xFF1B873F) else AppYellowDark
    RgsCard(Modifier.clickable { onSetup() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(SoftChipGradient), contentAlignment = Alignment.Center) {
                Icon(RgsIcons.Project, null, tint = AppYellowDark, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(distributorName.ifBlank { "Project" }, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = NeutralText, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(8.dp))
                    // Mode chip — clear visual cue for Recce vs Installation
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(modeBg).padding(horizontal = 9.dp, vertical = 3.dp),
                    ) {
                        Text(modeLabel, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = modeFg)
                    }
                }
                Text(
                    listOfNotNull(companyName.ifBlank { null }, "$creatives creatives", "$media media").joinToString(" · "),
                    fontSize = 11.5.sp, color = NeutralTextSoft, maxLines = 1,
                )
            }
            Row(
                Modifier.clip(RoundedCornerShape(9.dp)).background(BrandGradient).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(RgsIcons.Settings, null, tint = Color.Black, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text("Select project", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }
    }
}

@Composable
private fun EmptyProjectPrompt(onSetup: () -> Unit) {
    RgsCard {
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(SoftChipGradient), contentAlignment = Alignment.Center) {
                Icon(RgsIcons.Project, null, tint = AppYellowDark, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text("No project selected", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = NeutralText)
            Text("Company aur distributor chuno, phir shops add karo.", fontSize = 12.sp, color = NeutralTextSoft)
            Spacer(Modifier.height(14.dp))
            PrimaryButton("Set up project", onClick = onSetup)
        }
    }
}

@Composable
private fun SelectableFieldRow(
    value: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEdit: (() -> Unit)? = null,
    dropdownContent: @Composable () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.weight(1f)) {
            CompactDropdown(value = value, onClick = onToggle)
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = onToggle,
                modifier = Modifier.fillMaxWidth(0.7f).background(NeutralSurface),
            ) { dropdownContent() }
        }
        // Overflow (⋮) menu — Edit the selected item ("Add new" lives in the list dropdown; Delete is in the Edit dialog).
        if (onEdit != null) {
            Box {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(8.dp))
                        .background(NeutralSurfaceV).clickable { menuOpen = true },
                    contentAlignment = Alignment.Center,
                ) { Icon(RgsIcons.More, null, tint = NeutralTextSoft, modifier = Modifier.size(18.dp)) }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    modifier = Modifier.background(NeutralSurface),
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit", fontSize = 14.sp, fontWeight = FontWeight.Medium) },
                        leadingIcon = { Icon(RgsIcons.Edit, null, tint = NeutralTextSoft, modifier = Modifier.size(18.dp)) },
                        onClick = { menuOpen = false; onEdit() },
                    )
                }
            }
        }
    }
}


// ── Collapsible section card — icon badge + title + count + chips inside ──
@Composable
private fun ChipSection(
    title: String,
    icon: ImageVector,
    count: Int,
    open: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    RgsCard {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onToggle() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(YellowContainer), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = AppYellowDark, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NeutralText, modifier = Modifier.weight(1f))
            Box(Modifier.background(YellowContainer, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text("$count", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppYellowDark)
            }
            Spacer(Modifier.width(6.dp))
            Icon(if (open) RgsIcons.DropUp else RgsIcons.DropDown, null, tint = NeutralTextSoft, modifier = Modifier.size(20.dp))
        }
        AnimatedVisibility(open, enter = expandVertically(), exit = shrinkVertically()) {
            Column {
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x12000000)))
                Spacer(Modifier.height(10.dp))
                content()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditableChipFlow(items: List<String>, onAdd: () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.padding(bottom = 2.dp),
    ) {
        items.forEach { name ->
            // Creatives/media are manager-managed (web catalog) — field users can add, not remove/edit.
            Row(
                Modifier.background(NeutralSurfaceV, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = NeutralText)
            }
        }
        Box(
            Modifier.background(YellowContainer, RoundedCornerShape(8.dp))
                .clickable { onAdd() }
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(RgsIcons.Add, null, tint = YellowOnContainer, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(2.dp))
                Text("Add", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = YellowOnContainer)
            }
        }
    }
}

// ── Shops work-list — Pending / Done segments, search, rows, + Add shop ──
@Composable
private fun ShopsWorkList(shops: List<Shop>, onAddShop: () -> Unit, onImport: () -> Unit, onStartShop: (Shop) -> Unit, onEditShop: (Shop) -> Unit, onSetStatus: (Shop, String) -> Unit) {
    var seg by remember { mutableStateOf(0) }      // 0 = Pending, 1 = Done
    var query by remember { mutableStateOf("") }
    val pending = shops.filter { it.status == "Pending" }.sortedBy { it.name.lowercase() }
    val done = shops.filter { it.status != "Pending" }.sortedBy { it.name.lowercase() }
    val base = if (seg == 0) pending else done
    val list = if (query.isBlank()) base
        else base.filter { it.name.contains(query, ignoreCase = true) || it.city.contains(query, ignoreCase = true) }

    RgsCard {
        // Survey progress — surveyed shops out of actionable ones (Skipped excluded from both).
        val surveyed = shops.count { it.status != "Pending" && it.status != "Skipped" }
        val actionable = shops.count { it.status != "Skipped" }   // pending + surveyed
        if (actionable > 0) {
            val pct = surveyed * 100 / actionable
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$surveyed / $actionable shops done", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NeutralText, modifier = Modifier.weight(1f))
                Text("$pct%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppYellowDark)
            }
            Spacer(Modifier.height(5.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(NeutralSurfaceV)) {
                Box(Modifier.fillMaxWidth(surveyed.toFloat() / actionable).height(6.dp).clip(RoundedCornerShape(50)).background(AppYellow))
            }
            Spacer(Modifier.height(10.dp))
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(NeutralSurfaceV).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            SegTab("Pending · ${pending.size}", seg == 0, Modifier.weight(1f)) { seg = 0 }
            SegTab("Done · ${done.size}", seg == 1, Modifier.weight(1f)) { seg = 1 }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                .border(0.5.dp, NeutralOutline, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(RgsIcons.Search, null, tint = NeutralTextSoft, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
                if (query.isEmpty()) Text("Search shop or city", fontSize = 13.sp, color = NeutralTextSoft)
                BasicTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = TextStyle(fontSize = 13.sp, color = NeutralText),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                Icon(RgsIcons.Close, null, tint = NeutralTextSoft, modifier = Modifier.size(16.dp).clickable { query = "" })
            }
        }
        Spacer(Modifier.height(4.dp))
        if (list.isEmpty()) {
            Text(
                when {
                    shops.isEmpty() -> "No shops yet. Add shops to build your visit list."
                    seg == 0 -> "No pending shops — all surveyed."
                    else -> "No surveyed shops yet."
                },
                fontSize = 12.5.sp, color = NeutralTextSoft, modifier = Modifier.padding(vertical = 14.dp),
            )
        } else {
            // City-wise grouping — work one city at a time. Cities A→Z; shops name-sorted within.
            val grouped = list.groupBy { it.city.trim().ifBlank { "No city" } }.toSortedMap(compareBy { it.lowercase() })
            val showCityHeaders = grouped.size > 1
            grouped.forEach { (city, cityShops) ->
                if (showCityHeaders) {
                    Text(
                        city.uppercase(), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp,
                        color = AppYellowDark, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    )
                }
                cityShops.forEachIndexed { i, s ->
                    ShopRow(s, pending = seg == 0, onStart = { onStartShop(s) }, onEdit = { onEditShop(s) }, onSetStatus = { st -> onSetStatus(s, st) })
                    if (i < cityShops.size - 1) Box(Modifier.fillMaxWidth().height(0.5.dp).background(NeutralOutline.copy(alpha = 0.5f)))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WorklistButton("Add shop", RgsIcons.Add, SoftChipGradient, Modifier.weight(1f), onAddShop)
            WorklistButton("Import list", RgsIcons.Download, SolidColor(NeutralSurfaceV), Modifier.weight(1f), onImport)
        }
    }
}

// ── Installation work-list — SHOP-wise. Tap a shop → its install screen with all sizes. ──
@Composable
private fun InstallWorkList(installs: List<InstallEntry>, onOpenShop: (String) -> Unit) {
    var seg by remember { mutableStateOf(0) }   // 0 = to do, 1 = done
    val byShop = installs.groupBy { it.shopId.ifBlank { it.shopName } }
    val isDone: (List<InstallEntry>) -> Boolean = { es -> es.all { it.status == "Installed" || it.status == "NotDone" } }
    val todo = byShop.keys.filter { !isDone(byShop.getValue(it)) }.sortedBy { byShop.getValue(it).first().shopName.lowercase() }
    val done = byShop.keys.filter { isDone(byShop.getValue(it)) }.sortedBy { byShop.getValue(it).first().shopName.lowercase() }
    val list = if (seg == 0) todo else done
    RgsCard {
        Text(
            "${byShop.size} shop${if (byShop.size != 1) "s" else ""} · ${installs.size} size${if (installs.size != 1) "s" else ""} · ${installs.count { it.status == "Installed" }} installed",
            fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = NeutralText, modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(NeutralSurfaceV).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            SegTab("To do · ${todo.size}", seg == 0, Modifier.weight(1f)) { seg = 0 }
            SegTab("Done · ${done.size}", seg == 1, Modifier.weight(1f)) { seg = 1 }
        }
        Spacer(Modifier.height(8.dp))
        if (installs.isEmpty()) {
            Text(
                "No approved shops yet. Shops appear here after the admin approves them on the web.",
                fontSize = 12.5.sp, color = NeutralTextSoft, modifier = Modifier.padding(vertical = 14.dp),
            )
        } else if (list.isEmpty()) {
            Text(
                if (seg == 0) "Nothing to install." else "Nothing done yet.",
                fontSize = 12.5.sp, color = NeutralTextSoft, modifier = Modifier.padding(vertical = 14.dp),
            )
        } else {
            list.forEachIndexed { i, key ->
                val es = byShop.getValue(key)
                ShopInstallRow(es.first().shopName, es.first().city, es.size, es.count { it.status == "Installed" }) { onOpenShop(key) }
                if (i < list.size - 1) Box(Modifier.fillMaxWidth().height(0.5.dp).background(NeutralOutline.copy(alpha = 0.5f)))
            }
        }
    }
}

// One shop row — tap to open all its sizes.
@Composable
private fun ShopInstallRow(name: String, city: String, total: Int, installed: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name.ifBlank { "Shop" }, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NeutralText)
            val sub = listOf(city, "$total size${if (total > 1) "s" else ""} · $installed installed").filter { it.isNotBlank() }.joinToString(" · ")
            Text(sub, fontSize = 11.5.sp, color = NeutralTextSoft)
        }
        Text("›", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AppYellowDark)
    }
}

@Composable
private fun WorklistButton(label: String, icon: ImageVector, bg: androidx.compose.ui.graphics.Brush, mod: Modifier, onClick: () -> Unit) {
    Row(
        mod.clip(RoundedCornerShape(10.dp)).background(bg).clickable { onClick() }.padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = AppYellowDark, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = AppYellowDark)
    }
}

@Composable
private fun ImportShopsDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) runCatching {
            val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            text = if (text.isBlank()) content else text.trimEnd() + "\n" + content
        }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import shops", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Ek shop per line. Comma se optional: Name, City, Contact", fontSize = 11.5.sp, color = NeutralTextSoft)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    placeholder = { Text("Ramesh Store, Jalgaon\nShree Garments, Bhusawal, 9876543210", fontSize = 12.sp, color = NeutralTextSoft) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 220.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppYellowDark, unfocusedBorderColor = NeutralOutline, cursorColor = AppYellowDark,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(NeutralSurfaceV)
                        .clickable { picker.launch("*/*") }.padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(RgsIcons.Download, null, tint = AppYellowDark, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Pick a CSV / text file", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = AppYellowDark)
                }
            }
        },
        confirmButton = {
            val n = text.lineSequence().count { it.isNotBlank() }
            Text(
                if (n > 0) "Import $n" else "Import",
                color = if (n > 0) AppYellowDark else NeutralTextSoft, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(enabled = n > 0) { onImport(text) }.padding(8.dp),
            )
        },
        dismissButton = { Text("Cancel", color = NeutralTextSoft, modifier = Modifier.clickable { onDismiss() }.padding(8.dp)) },
    )
}

@Composable
private fun SegTab(label: String, sel: Boolean, mod: Modifier, onClick: () -> Unit) {
    Box(
        mod.clip(RoundedCornerShape(8.dp)).background(if (sel) NeutralSurface else Color.Transparent)
            .clickable { onClick() }.padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = if (sel) NeutralText else NeutralTextSoft)
    }
}

@Composable
private fun ShopRow(shop: Shop, pending: Boolean, onStart: () -> Unit, onEdit: () -> Unit, onSetStatus: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(SoftChipGradient), contentAlignment = Alignment.Center) {
            Icon(RgsIcons.Store, null, tint = AppYellowDark, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(shop.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = NeutralText, maxLines = 1)
            val sub = listOfNotNull(shop.city.ifBlank { null }, if (pending) "not visited" else shop.status).joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, fontSize = 11.5.sp, color = NeutralTextSoft, maxLines = 1)
        }
        if (pending) {
            // Edit — fix the shop's name / city / contact.
            Text(
                "Edit", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = NeutralTextSoft,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onEdit() }.padding(horizontal = 8.dp, vertical = 6.dp),
            )
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier.clip(RoundedCornerShape(8.dp)).background(BrandGradient).clickable { onStart() }
                    .padding(horizontal = 13.dp, vertical = 6.dp),
            ) {
                Text("Start ›", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
        } else {
            // Revisit — send a done/skipped shop back to the pending list.
            Text(
                "Revisit", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = AppYellowDark,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onSetStatus("Pending") }.padding(horizontal = 8.dp, vertical = 6.dp),
            )
            if (shop.status != "Skipped") {
                Spacer(Modifier.width(4.dp))
                Icon(RgsIcons.Check, null, tint = Color(0xFF1B873F), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun AddShopDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
    title: String = "Add shop",
    confirmLabel: String = "Add",
    initialName: String = "",
    initialCity: String = "",
    initialContact: String = "",
) {
    var name by remember { mutableStateOf(initialName) }
    var city by remember { mutableStateOf(initialCity) }
    var contact by remember { mutableStateOf(initialContact) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                DialogField("Shop name", name) { name = it }
                Spacer(Modifier.height(8.dp))
                DialogField("City", city) { city = it }
                Spacer(Modifier.height(8.dp))
                DialogField("Contact no.", contact, KeyboardType.Number) { contact = it.filter { c -> c.isDigit() }.take(10) }
            }
        },
        confirmButton = {
            Text(
                confirmLabel, color = if (name.isNotBlank()) AppYellowDark else NeutralTextSoft, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(enabled = name.isNotBlank()) { onSave(name.trim(), city.trim(), contact.trim()) }.padding(8.dp),
            )
        },
        dismissButton = {
            Text("Cancel", color = NeutralTextSoft, modifier = Modifier.clickable { onDismiss() }.padding(8.dp))
        },
    )
}

@Composable
private fun DialogField(label: String, value: String, keyboardType: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AppYellowDark, focusedLabelColor = AppYellowDark, cursorColor = AppYellowDark,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}


