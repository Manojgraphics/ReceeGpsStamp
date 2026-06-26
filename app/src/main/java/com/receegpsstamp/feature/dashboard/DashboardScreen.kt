package com.receegpsstamp.feature.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.receegpsstamp.data.model.Company
import com.receegpsstamp.data.model.Distributor
import com.receegpsstamp.data.model.Expense
import com.receegpsstamp.data.model.RecceEntry
import com.receegpsstamp.data.model.Shop
import com.receegpsstamp.ui.components.RgsCard
import com.receegpsstamp.ui.components.RgsTopBar
import com.receegpsstamp.ui.theme.*

@Composable
fun DashboardScreen(
    onBack: () -> Unit = {},
    onMenuClick: (() -> Unit)? = null,
    distributorCount: Int = 0,
    distributorName: String = "",
    selectedDistributorId: String = "",
    distributors: List<Distributor> = emptyList(),
    companies: List<Company> = emptyList(),
    allShops: List<Shop> = emptyList(),
    allRecces: List<RecceEntry> = emptyList(),
    expenses: List<Expense> = emptyList(),
    onOpenExpenses: () -> Unit = {},
    onSetProjectStage: (String, String) -> Unit = { _, _ -> },
    onProjectExportPdf: (String) -> Unit = {},
    onProjectExportExcel: (String) -> Unit = {},
    onProjectExportPptx: (String) -> Unit = {},
    onRemoveProjectPhotos: (String) -> Unit = {},
    onDeleteProject: (String) -> Unit = {},
    onImportProject: (android.net.Uri) -> Unit = {},
    onEditStore: (RecceEntry) -> Unit = {},
    onShareStore: (RecceEntry) -> Unit = {},
    onShareStores: (List<String>) -> Unit = {},
    onExportStoresPdf: (List<String>) -> Unit = {},
    onExportStoresExcel: (List<String>) -> Unit = {},
    onExportStoresPptx: (List<String>) -> Unit = {},
) {
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) onImportProject(uri) }
    // KPI drill-down: which tile was tapped ("stores" / "surveyed" / "interested"); null = closed.
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(NeutralBg).navigationBarsPadding()) {
        RgsTopBar(
            if (distributorName.isNotBlank()) "Dashboard · $distributorName" else "Dashboard",
            showBack = onMenuClick == null, onNav = onMenuClick ?: onBack,
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // ── Enterprise overview: global KPIs across every project ──
            val gInterested = allRecces.count { it.status == "Interested" }
            val gSurveyed = allRecces.size
            val gStores = allShops.size
            val gConversion = if (gSurveyed > 0) gInterested * 100 / gSurveyed else 0
            val gCoverage = if (gStores > 0) gSurveyed * 100 / gStores else 0
            OverviewHero(
                projects = distributorCount,
                stores = gStores,
                surveyed = gSurveyed,
                conversion = gConversion,
                coverage = gCoverage,
                interested = gInterested,
            )

            // Expenses balance — tap to open the Expense Manager.
            ExpensesCard(expenses, onOpenExpenses)

            // ── Recent activity — last few recces, tap to open/edit ──
            val recent = allRecces.sortedByDescending { it.createdAt }.take(6)
            if (recent.isNotEmpty()) {
                Text("RECENT ACTIVITY", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp, color = NeutralTextSoft, modifier = Modifier.padding(horizontal = 2.dp))
                RgsCard {
                    val byId = allShops.associateBy { it.id }
                    recent.forEachIndexed { i, r ->
                        RecceRow(byId[r.shopId], r) { onEditStore(r) }
                        if (i < recent.size - 1) Box(Modifier.fillMaxWidth().height(0.5.dp).background(NeutralOutline))
                    }
                }
            }

            // Multi-project hub — current project pinned on top, then the rest. Each is a regular card.
            ProjectsHub(
                distributors = distributors,
                companies = companies,
                allShops = allShops,
                allRecces = allRecces,
                currentDistributorId = selectedDistributorId,
                onSetStage = onSetProjectStage,
                onExportPdf = onProjectExportPdf,
                onExportExcel = onProjectExportExcel,
                onExportPptx = onProjectExportPptx,
                onRemovePhotos = onRemoveProjectPhotos,
                onDeleteProject = onDeleteProject,
                onImport = { importLauncher.launch(arrayOf("*/*")) },
                onEditStore = onEditStore,
                onShareStore = onShareStore,
                onShareStores = onShareStores,
                onExportStoresPdf = onExportStoresPdf,
                onExportStoresExcel = onExportStoresExcel,
                onExportStoresPptx = onExportStoresPptx,
            )

        }
    }
    }
}

// Enterprise overview header — animated, tappable KPI tiles + a conversion donut.
@Composable
private fun OverviewHero(
    projects: Int, stores: Int, surveyed: Int, conversion: Int, coverage: Int, interested: Int,
) {
    // Count-up animations so the numbers feel alive on load.
    val aProjects by animateIntAsState(projects, tween(700), label = "p")
    val aStores by animateIntAsState(stores, tween(700), label = "s")
    val aSurveyed by animateIntAsState(surveyed, tween(700), label = "sv")
    val aInterested by animateIntAsState(interested, tween(700), label = "i")
    val aCoverage by animateFloatAsState(coverage / 100f, tween(800), label = "cov")
    Column(
        Modifier.fillMaxWidth().shadow(3.dp, RoundedCornerShape(16.dp))
            .background(Color(0xFF1C1C1E), RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).background(AppYellow.copy(0.18f), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                Icon(RgsIcons.Dashboard, null, tint = AppYellow, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text("Overview", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("All projects · live", fontSize = 10.5.sp, color = Color.White.copy(0.55f))
            }
            DonutRing(conversion, label = "Convert")
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KpiTile(RgsIcons.Project, "$aProjects", "Projects", AppYellow, Modifier.weight(1f))
            KpiTile(RgsIcons.Store, "$aStores", "Stores", Color(0xFF5DA9E9), Modifier.weight(1f))
            KpiTile(RgsIcons.Check, "$aSurveyed", "Surveyed", Color(0xFF59C28A), Modifier.weight(1f))
            KpiTile(RgsIcons.Camera, "$aInterested", "Interested", Color(0xFFE0A93B), Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        // Coverage bar — how much of all stores have been surveyed.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Coverage", fontSize = 10.5.sp, color = Color.White.copy(0.6f), modifier = Modifier.width(64.dp))
            Box(Modifier.weight(1f).height(7.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(0.12f))) {
                Box(Modifier.fillMaxWidth(aCoverage).height(7.dp).clip(RoundedCornerShape(50)).background(AppYellow))
            }
            Spacer(Modifier.width(8.dp))
            Text("$coverage%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppYellow)
        }
    }
}

@Composable
private fun KpiTile(icon: ImageVector, value: String, label: String, accent: Color, mod: Modifier = Modifier) {
    Column(
        mod.clip(RoundedCornerShape(11.dp)).background(Color.White.copy(0.06f)).padding(vertical = 9.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(15.dp))
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Text(label, fontSize = 8.8.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(0.55f), maxLines = 1)
    }
}

@Composable
private fun DonutRing(pct: Int, label: String) {
    val aPct by animateFloatAsState(pct / 100f, tween(800), label = "donut")
    Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.size(52.dp)) {
            val stroke = 6.dp.toPx()
            drawArc(Color.White.copy(0.12f), 0f, 360f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            drawArc(AppYellow, -90f, 360f * aPct, false, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$pct%", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text(label, fontSize = 6.5.sp, color = Color.White.copy(0.55f))
        }
    }
}

// A tappable recce row — used by the Recent Activity feed and the KPI drill-down dialog.
@Composable
private fun RecceRow(shop: Shop?, recce: RecceEntry, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp)).clickable { onClick() }.padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(statusColor(recce.status)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            val city = shop?.city?.takeIf { it.isNotBlank() }
            Text(
                if (city != null) "${shop?.name ?: "Shop"} · $city" else (shop?.name ?: "Shop"),
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = NeutralText,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text("${recce.status} · ${timeAgo(recce.createdAt)}", fontSize = 10.5.sp, color = NeutralTextSoft)
        }
        Icon(RgsIcons.Edit, null, tint = AppYellowDark, modifier = Modifier.size(15.dp))
    }
}

private fun statusColor(s: String): Color = when (s) {
    "Interested" -> AppYellowDark
    "Not Interested" -> Color(0xFFC0605F)
    "Revisit" -> Color(0xFFC98A2E)
    else -> NeutralTextSoft
}

private fun timeAgo(ts: Long): String {
    if (ts <= 0) return "—"
    val d = System.currentTimeMillis() - ts
    return when {
        d < 60_000 -> "just now"
        d < 3_600_000 -> "${d / 60_000}m ago"
        d < 86_400_000 -> "${d / 3_600_000}h ago"
        d < 7 * 86_400_000L -> "${d / 86_400_000}d ago"
        else -> java.text.SimpleDateFormat("dd MMM", java.util.Locale.getDefault()).format(java.util.Date(ts))
    }
}

@Composable
private fun ExpensesCard(expenses: List<Expense>, onOpen: () -> Unit) {
    val advance = expenses.filter { it.kind == "ADVANCE" }.sumOf { it.amount }
    val spent = expenses.filter { it.kind == "EXPENSE" && it.paymentMode != "Company" }.sumOf { it.amount }
    val balance = advance - spent
    Box(
        Modifier.fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp))
            .background(BrandGradient, RoundedCornerShape(16.dp))
            .clickable { onOpen() }
            .padding(14.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).background(Color.White.copy(0.55f), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                    Icon(RgsIcons.Wallet, null, tint = AppYellowDark, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(9.dp))
                Text("Expenses", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NeutralText, modifier = Modifier.weight(1f))
                Text("View ›", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = NeutralText)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                ExpStat("Advance", "₹%,.0f".format(advance), NeutralText, Modifier.weight(1f))
                ExpStat("Spent", "₹%,.0f".format(spent), NeutralText, Modifier.weight(1f))
                ExpStat(
                    if (balance >= 0) "To return" else "Overspent",
                    "₹%,.0f".format(kotlin.math.abs(balance)),
                    if (balance >= 0) NeutralText else StatusError, Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ExpStat(label: String, value: String, color: Color, mod: Modifier) {
    Column(mod, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = color)
        Text(label, fontSize = 10.sp, color = NeutralText.copy(alpha = 0.72f))
    }
}



