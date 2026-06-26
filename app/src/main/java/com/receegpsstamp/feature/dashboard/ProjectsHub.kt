package com.receegpsstamp.feature.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.receegpsstamp.ui.theme.NeutralBg
import coil.compose.AsyncImage
import com.receegpsstamp.data.model.Company
import com.receegpsstamp.data.model.Distributor
import com.receegpsstamp.data.model.RecceEntry
import com.receegpsstamp.data.model.Shop
import com.receegpsstamp.ui.components.RgsCard
import com.receegpsstamp.ui.theme.AppYellow
import com.receegpsstamp.ui.theme.AppYellowDark
import com.receegpsstamp.ui.theme.BrandGrey
import com.receegpsstamp.ui.theme.NeutralSurface
import com.receegpsstamp.ui.theme.NeutralSurfaceV
import com.receegpsstamp.ui.theme.NeutralText
import com.receegpsstamp.ui.theme.NeutralTextSoft
import com.receegpsstamp.ui.theme.RgsIcons
import com.receegpsstamp.ui.theme.StatusError
import com.receegpsstamp.ui.theme.StatusInterested
import com.receegpsstamp.ui.theme.YellowContainer

/**
 * Multi-project hub for the Dashboard — lists every project (distributor) as an expandable card.
 * Each card exposes per-project actions: mark done, export report, remove photos, delete, show shops.
 */
@Composable
fun ProjectsHub(
    distributors: List<Distributor>,
    companies: List<Company>,
    allShops: List<Shop>,
    allRecces: List<RecceEntry>,
    currentDistributorId: String = "",
    onSetStage: (String, String) -> Unit,
    onExportPdf: (String) -> Unit,
    onExportExcel: (String) -> Unit,
    onExportPptx: (String) -> Unit,
    onRemovePhotos: (String) -> Unit,
    onDeleteProject: (String) -> Unit,
    onImport: () -> Unit,
    onEditStore: (RecceEntry) -> Unit,
    onShareStore: (RecceEntry) -> Unit,
    onShareStores: (List<String>) -> Unit,
    onExportStoresPdf: (List<String>) -> Unit,
    onExportStoresExcel: (List<String>) -> Unit,
    onExportStoresPptx: (List<String>) -> Unit,
) {
    if (distributors.isEmpty()) {
        com.receegpsstamp.ui.components.EmptyState(
            icon = RgsIcons.Project,
            title = "No projects yet",
            subtitle = "Create a project in the Project tab to get started.",
        )
        return
    }

    // One card renderer reused for both the pinned current project and the rest of the list.
    val sectionLabel: @Composable (String) -> Unit = { label ->
        Text(
            label, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp,
            color = NeutralTextSoft, modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
    val cardFor: @Composable (Distributor) -> Unit = { dist ->
        key(dist.id) {
            ProjectCard(
                dist = dist,
                companyName = companies.find { it.id == dist.companyId }?.name ?: dist.companyName,
                shops = allShops.filter { it.distributorId == dist.id },
                recces = allRecces.filter { it.distributorId == dist.id },
                onSetStage = { onSetStage(dist.id, it) },
                onExportPdf = { onExportPdf(dist.id) },
                onExportExcel = { onExportExcel(dist.id) },
                onExportPptx = { onExportPptx(dist.id) },
                onRemovePhotos = { onRemovePhotos(dist.id) },
                onDeleteProject = { onDeleteProject(dist.id) },
                onEditStore = onEditStore,
                onShareStore = onShareStore,
                onShareStores = onShareStores,
                onExportStoresPdf = onExportStoresPdf,
                onExportStoresExcel = onExportStoresExcel,
                onExportStoresPptx = onExportStoresPptx,
            )
        }
    }

    // Pin the current project on top as a regular card; the rest follow under PROJECTS (no duplicate).
    val current = distributors.find { it.id == currentDistributorId }
    if (current != null) {
        sectionLabel("CURRENT PROJECT")
        cardFor(current)
    }
    val rest = distributors.filter { it.id != currentDistributorId }
    if (rest.isNotEmpty()) {
        sectionLabel("PROJECTS")
        rest.forEach { cardFor(it) }
    }
}

private enum class Confirm { RemovePhotos, Delete }

@Composable
private fun ProjectCard(
    dist: Distributor,
    companyName: String,
    shops: List<Shop>,
    recces: List<RecceEntry>,
    onSetStage: (String) -> Unit,
    onExportPdf: () -> Unit,
    onExportExcel: () -> Unit,
    onExportPptx: () -> Unit,
    onRemovePhotos: () -> Unit,
    onDeleteProject: () -> Unit,
    onEditStore: (RecceEntry) -> Unit,
    onShareStore: (RecceEntry) -> Unit,
    onShareStores: (List<String>) -> Unit,
    onExportStoresPdf: (List<String>) -> Unit,
    onExportStoresExcel: (List<String>) -> Unit,
    onExportStoresPptx: (List<String>) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var storesOpen by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    var exportChoice by remember { mutableStateOf(false) }
    // Multi-select of stores within this card (share / export several at once).
    var selectMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    // Stores-screen search & filters.
    var query by remember { mutableStateOf("") }
    var cityFilter by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("") }

    val stage = dist.stage
    val done = stage == "InstallDone"
    val shopCount = shops.size
    val recceCount = recces.size
    val shopById = shops.associateBy { it.id }

    if (confirm != null) {
        val isDelete = confirm == Confirm.Delete
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (isDelete) "Delete whole project?" else "Remove photo gallery?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (isDelete)
                        "Permanently deletes \"${dist.name}\" — all shops, recces & photos. Can't be undone. Export the report first if you need it."
                    else
                        "Deletes the photo files for \"${dist.name}\" to free storage. All shop & recce records are kept.",
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                Text(
                    if (isDelete) "Delete project" else "Remove photos", color = StatusError, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        if (isDelete) onDeleteProject() else onRemovePhotos()
                        confirm = null
                    }.padding(8.dp),
                )
            },
            dismissButton = {
                Text("Cancel", color = NeutralTextSoft, modifier = Modifier.clickable { confirm = null }.padding(8.dp))
            },
        )
    }

    if (exportChoice) {
        AlertDialog(
            onDismissRequest = { exportChoice = false },
            title = { Text("Export report", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Choose a format for \"${dist.name}\".", fontSize = 13.sp, color = NeutralTextSoft)
                    Spacer(Modifier.height(6.dp))
                    Text("PDF", color = AppYellowDark, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().clickable { onExportPdf(); exportChoice = false }.padding(vertical = 10.dp))
                    Text("PowerPoint (PPT)", color = AppYellowDark, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().clickable { onExportPptx(); exportChoice = false }.padding(vertical = 10.dp))
                    Text("Excel / CSV", color = AppYellowDark, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().clickable { onExportExcel(); exportChoice = false }.padding(vertical = 10.dp))
                }
            },
            confirmButton = {
                Text(
                    "Cancel", color = NeutralTextSoft,
                    modifier = Modifier.clickable { exportChoice = false }.padding(8.dp),
                )
            },
        )
    }

    RgsCard {
        // Header — tap opens a popup sub-menu of actions (no big inline expand).
        // Header — body tap opens the stores screen; the ⋮ opens the actions menu.
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { storesOpen = true }.padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (done) StatusInterested.copy(alpha = 0.16f) else YellowContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (done) RgsIcons.Check else RgsIcons.Project, null, tint = if (done) StatusInterested else AppYellowDark, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(dist.name, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = NeutralText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$companyName · $shopCount shops · $recceCount recce", fontSize = 11.sp, color = NeutralTextSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            when (stage) {
                "InstallDone" -> Row(
                    Modifier.clip(RoundedCornerShape(7.dp)).background(StatusInterested).padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(RgsIcons.Check, null, tint = Color.White, modifier = Modifier.size(11.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Installation Done", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                "RecceDone" -> Box(
                    Modifier.clip(RoundedCornerShape(7.dp)).background(AppYellowDark.copy(alpha = 0.16f)).padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text("Recce Done", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AppYellowDark)
                }
                else -> Box(
                    Modifier.clip(RoundedCornerShape(7.dp)).background(NeutralSurfaceV).padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text("Active", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeutralTextSoft)
                }
            }
            // ⋮ more — opens the actions popup (anchored here).
            Box {
                Icon(
                    RgsIcons.More, "Project actions", tint = NeutralTextSoft,
                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(50)).clickable { menuOpen = true }.padding(7.dp),
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    MenuRow(RgsIcons.Download, "Export Report") { menuOpen = false; exportChoice = true }
                    when (stage) {
                        "" -> MenuRow(RgsIcons.Check, "Mark Recce Done", StatusInterested) { menuOpen = false; onSetStage("RecceDone") }
                        "RecceDone" -> {
                            MenuRow(RgsIcons.Check, "Mark Installation Done", StatusInterested) { menuOpen = false; onSetStage("InstallDone") }
                            MenuRow(RgsIcons.Refresh, "Reopen (Active)") { menuOpen = false; onSetStage("") }
                        }
                        else -> MenuRow(RgsIcons.Refresh, "Reopen project") { menuOpen = false; onSetStage("") }
                    }
                    MenuRow(RgsIcons.Gallery, "Remove Photo Gallery", AppYellowDark) { menuOpen = false; confirm = Confirm.RemovePhotos }
                    MenuRow(RgsIcons.Delete, "Delete whole Project", StatusError) { menuOpen = false; confirm = Confirm.Delete }
                }
            }
        }
    }

    // ── Stores screen — full-screen list of this project's shops ──
    if (storesOpen) {
        Dialog(onDismissRequest = { storesOpen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(Modifier.fillMaxSize().background(NeutralBg).navigationBarsPadding()) {
                // Dark top bar (same as the Dashboard).
                Row(
                    Modifier.fillMaxWidth().background(BrandGrey).statusBarsPadding().padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(RgsIcons.Back, "Back", tint = Color.White, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(50)).clickable { storesOpen = false }.padding(8.dp))
                    Spacer(Modifier.width(6.dp))
                    Column(Modifier.weight(1f)) {
                        Text(dist.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("$recceCount stores · $companyName", fontSize = 11.sp, color = Color.White.copy(0.7f))
                    }
                    if (recces.isNotEmpty()) {
                        if (selectMode) {
                            Text(
                                "Done", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = AppYellow,
                                modifier = Modifier.clip(RoundedCornerShape(7.dp)).clickable { selectMode = false; selectedIds = emptySet() }.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        } else {
                            Row(
                                Modifier.clip(RoundedCornerShape(7.dp)).background(AppYellow).clickable { selectMode = true }.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(RgsIcons.Check, null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Select", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                        }
                    }
                }
                // ── Search + City + Status filters (one row) ──
                val cities = recces.mapNotNull { shopById[it.shopId]?.city?.takeIf { c -> c.isNotBlank() } }.distinct().sorted()
                val statuses = listOf("Interested", "Not Interested", "Revisit", "Closed").filter { st -> recces.any { it.status == st } }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        placeholder = { Text("Search shop", fontSize = 12.5.sp, color = NeutralTextSoft) },
                        singleLine = true,
                        leadingIcon = { Icon(RgsIcons.Store, null, tint = NeutralTextSoft, modifier = Modifier.size(16.dp)) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = NeutralText),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppYellow, unfocusedBorderColor = NeutralSurfaceV),
                        modifier = Modifier.weight(1f).height(50.dp),
                    )
                    FilterDropdown("City", cityFilter, cities) { cityFilter = it }
                    FilterDropdown("Status", statusFilter, statuses) { statusFilter = it }
                }
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color(0x14000000)))
                // Apply filters.
                val filtered = recces.filter { r ->
                    val shop = shopById[r.shopId]
                    (cityFilter.isBlank() || shop?.city == cityFilter) &&
                        (statusFilter.isBlank() || r.status == statusFilter) &&
                        (query.isBlank() || (shop?.name ?: "").contains(query, true) || (shop?.city ?: "").contains(query, true))
                }
                // Select-all of the CURRENT filtered list — lets you send a whole filter (city/status) at once.
                if (selectMode && filtered.isNotEmpty()) {
                    val allSel = filtered.all { it.id in selectedIds }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            Modifier.clip(RoundedCornerShape(6.dp)).clickable {
                                val ids = filtered.map { it.id }.toSet()
                                selectedIds = if (allSel) selectedIds - ids else selectedIds + ids
                            }.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(RgsIcons.Check, null, tint = AppYellowDark, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(if (allSel) "Clear all" else "Select all (${filtered.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppYellowDark)
                        }
                        Spacer(Modifier.weight(1f))
                        Text("${selectedIds.size} selected", fontSize = 11.5.sp, color = NeutralTextSoft)
                    }
                }
                // Action bar — sits directly under the select summary so it is ALWAYS visible.
                // (A bottom bar gets hidden behind the system nav bar inside this full-screen Dialog.)
                if (selectMode && selectedIds.isNotEmpty()) {
                    val ids = selectedIds.toList()
                    Row(
                        Modifier.fillMaxWidth().background(NeutralSurface).padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SelectAction(RgsIcons.Share, "WhatsApp") { storesOpen = false; onShareStores(ids) }
                        SelectAction(RgsIcons.Download, "PDF") { storesOpen = false; onExportStoresPdf(ids) }
                        SelectAction(RgsIcons.Download, "PPT") { storesOpen = false; onExportStoresPptx(ids) }
                        SelectAction(RgsIcons.Download, "Excel") { storesOpen = false; onExportStoresExcel(ids) }
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(10.dp)) {
                    if (filtered.isEmpty()) {
                        com.receegpsstamp.ui.components.EmptyState(
                            icon = RgsIcons.Store,
                            title = if (recces.isEmpty()) "No stores yet" else "No stores match",
                            subtitle = if (recces.isEmpty()) "Stores you survey for ${dist.name} will appear here." else "Try a different search or filter.",
                        )
                    } else {
                        filtered.forEach { r ->
                            key(r.id) {
                                ShopDataItem(
                                    shop = shopById[r.shopId], recce = r,
                                    selectMode = selectMode,
                                    selected = r.id in selectedIds,
                                    onToggleSelect = { selectedIds = if (r.id in selectedIds) selectedIds - r.id else selectedIds + r.id },
                                    onEdit = { storesOpen = false; onEditStore(r) },
                                    onShare = { storesOpen = false; onShareStore(r) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// City / Status filter — a compact dropdown button.
@Composable
private fun FilterDropdown(label: String, selected: String, options: List<String>, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val active = selected.isNotBlank()
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(9.dp)).background(if (active) YellowContainer else NeutralSurfaceV)
                .clickable { open = true }.padding(start = 10.dp, end = 6.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(selected.ifBlank { label }, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = if (active) AppYellowDark else NeutralTextSoft, maxLines = 1)
            Icon(RgsIcons.DropDown, null, tint = if (active) AppYellowDark else NeutralTextSoft, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("All ${label.lowercase()}", fontSize = 13.sp, fontWeight = if (!active) FontWeight.Bold else FontWeight.Normal) }, onClick = { onSelect(""); open = false })
            options.forEach { opt ->
                DropdownMenuItem(text = { Text(opt, fontSize = 13.sp, fontWeight = if (opt == selected) FontWeight.Bold else FontWeight.Normal, color = if (opt == selected) AppYellowDark else NeutralText) }, onClick = { onSelect(opt); open = false })
            }
        }
    }
}

// One row inside the project popup sub-menu.
@Composable
private fun MenuRow(icon: ImageVector, label: String, tint: Color = NeutralText, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = tint) },
        leadingIcon = { Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp)) },
        onClick = onClick,
    )
}


@Composable
private fun RowScope.SelectAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(YellowContainer).clickable { onClick() }.padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = AppYellowDark, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = AppYellowDark)
    }
}

@Composable
private fun ShopDataItem(
    shop: Shop?,
    recce: RecceEntry,
    selectMode: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val photos = recce.shopPhotos.size + recce.media.sumOf { it.photos.size }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                .clickable { if (selectMode) onToggleSelect() else open = !open }
                .padding(vertical = 6.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectMode) {
                Checkbox(checked = selected, onCheckedChange = { onToggleSelect() }, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(11.dp))
            } else {
                Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(statusColor(recce.status)))
                Spacer(Modifier.width(9.dp))
            }
            Column(Modifier.weight(1f)) {
                val city = shop?.city?.takeIf { it.isNotBlank() }
                Text(
                    if (city != null) "${shop?.name ?: "Shop"} · $city" else (shop?.name ?: "Shop"),
                    fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = NeutralText, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text("${recce.status} · ${recce.media.size} media · $photos photos", fontSize = 10.sp, color = NeutralTextSoft)
            }
            if (!selectMode) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(28.dp).clip(RoundedCornerShape(50)).clickable { menuOpen = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(RgsIcons.Edit, null, tint = AppYellowDark, modifier = Modifier.size(17.dp))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Update Store Details", fontSize = 13.sp) },
                            leadingIcon = { Icon(RgsIcons.Edit, null, modifier = Modifier.size(18.dp)) },
                            onClick = { menuOpen = false; onEdit() },
                        )
                    }
                }
                Icon(if (open) RgsIcons.DropUp else RgsIcons.DropDown, null, tint = NeutralTextSoft, modifier = Modifier.size(15.dp))
            }
        }
        AnimatedVisibility(open && !selectMode, enter = expandVertically(), exit = shrinkVertically()) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // Photos (shop front + media) — actual images so the front photo is visible here too.
                val photoPaths = (recce.shopPhotos + recce.media.flatMap { it.photos }).distinct()
                if (photoPaths.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        photoPaths.forEach { p -> StoreThumb(p) }
                    }
                    Spacer(Modifier.height(2.dp))
                }
                val meta = listOfNotNull(shop?.code?.ifBlank { null }, shop?.city?.ifBlank { null }, shop?.contact?.ifBlank { null }).joinToString(" · ")
                if (meta.isNotBlank()) DetailLine(meta)
                if (recce.media.isEmpty()) {
                    DetailLine("No media added")
                } else {
                    recce.media.forEachIndexed { idx, m ->
                        val size = if (m.width > 0.0 || m.height > 0.0) "${dimStr(m.width)} × ${dimStr(m.height)} ${m.unit}" else ""
                        val parts = listOfNotNull(
                            m.type.ifBlank { null }, m.creative.ifBlank { null },
                            size.ifBlank { null }, if (m.qty > 0) "Qty ${m.qty}" else null,
                            m.remark.ifBlank { null },
                            if (m.photos.isNotEmpty()) "${m.photos.size} photo" else null,
                        )
                        DetailLine("${idx + 1}. " + parts.joinToString(" · "))
                    }
                }
                if (recce.remark.isNotBlank()) DetailLine("Remark: ${recce.remark}")
                if (recce.lat != 0.0 || recce.lng != 0.0) DetailLine("Location: ${"%.5f".format(recce.lat)}, ${"%.5f".format(recce.lng)}")
                Spacer(Modifier.height(6.dp))
                // Share this store's details on WhatsApp.
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(YellowContainer).clickable { onShare() }.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(RgsIcons.Share, null, tint = AppYellowDark, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Share on WhatsApp", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppYellowDark)
                }
            }
        }
    }
}

@Composable
private fun DetailLine(text: String) {
    Text(text, fontSize = 11.sp, color = NeutralTextSoft, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun StoreThumb(path: String, onClick: () -> Unit = {}) {
    AsyncImage(
        model = java.io.File(path),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(58.dp).clip(RoundedCornerShape(8.dp)).background(NeutralSurfaceV).clickable { onClick() },
    )
}

private fun dimStr(d: Double): String = if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()

private fun statusColor(status: String): Color = when (status) {
    "Interested" -> StatusInterested
    "Not Interested" -> StatusError
    "Revisit" -> AppYellowDark
    else -> NeutralTextSoft
}
