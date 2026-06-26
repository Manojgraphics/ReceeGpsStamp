@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.receegpsstamp.feature.expense

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Engineering
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalPolice
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.receegpsstamp.data.model.Distributor
import com.receegpsstamp.data.model.Expense
import com.receegpsstamp.data.model.Vehicle
import com.receegpsstamp.ui.components.RgsCard
import com.receegpsstamp.ui.components.RgsTopBar
import com.receegpsstamp.ui.theme.AppYellow
import com.receegpsstamp.ui.theme.AppYellowDark
import com.receegpsstamp.ui.theme.BrandGradient
import com.receegpsstamp.ui.theme.BrandGradientH
import com.receegpsstamp.ui.theme.SoftChipGradient
import com.receegpsstamp.ui.theme.NeutralBg
import com.receegpsstamp.ui.theme.NeutralOutline
import com.receegpsstamp.ui.theme.NeutralSurfaceV
import com.receegpsstamp.ui.theme.NeutralText
import com.receegpsstamp.ui.theme.NeutralTextSoft
import com.receegpsstamp.ui.theme.StatusError
import com.receegpsstamp.ui.theme.YellowContainer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

// ── Categories ──
data class ExpenseCat(val name: String, val icon: ImageVector)

val EXPENSE_CATS = listOf(
    ExpenseCat("Tea / Snacks", Icons.Rounded.LocalCafe),
    ExpenseCat("Dinner / Lunch", Icons.Rounded.Restaurant),
    ExpenseCat("Lodge", Icons.Rounded.Hotel),
    ExpenseCat("Fuel", Icons.Rounded.LocalGasStation),
    ExpenseCat("Travel", Icons.Rounded.DirectionsBus),
    ExpenseCat("Material", Icons.Rounded.Inventory2),
    ExpenseCat("Daily wages", Icons.Rounded.Engineering),
    ExpenseCat("Driver", Icons.Rounded.DirectionsCar),
    ExpenseCat("Police fine", Icons.Rounded.LocalPolice),
    ExpenseCat("Other Expenses", Icons.Rounded.MoreHoriz),
)

fun iconFor(category: String): ImageVector =
    EXPENSE_CATS.find { it.name == category }?.icon
        ?: if (category == "Advance") Icons.Rounded.AccountBalanceWallet else Icons.Rounded.Payments

private val AdvanceGreen = Color(0xFF1B873F)
private fun money(v: Double): String = "₹" + "%,.0f".format(v)
private val dayFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

@Composable
fun ExpenseScreen(
    expenses: List<Expense>,
    approvals: Map<String, String> = emptyMap(),
    vehicleNumber: String = "",
    vehicleType: String = "",
    vehicles: List<Vehicle> = emptyList(),
    projects: List<Distributor>,
    compressBill: suspend (Uri) -> String?,
    onAdd: (Expense) -> Unit,
    onDelete: (String) -> Unit,
    onExportPdf: (List<Expense>, String) -> Unit,
    onExportExcel: (List<Expense>, String) -> Unit,
    onBack: () -> Unit,
) {
    var adding by remember { mutableStateOf<String?>(null) }      // null=list, "EXPENSE"/"ADVANCE"=form
    if (adding != null) {
        AddExpenseForm(
            initialKind = adding!!,
            projects = projects,
            vehicles = vehicles,
            compressBill = compressBill,
            onCancel = { adding = null },
            onSave = { onAdd(it); adding = null },
        )
        return
    }

    var scope by remember { mutableStateOf("") }                  // "" = All
    var toDelete by remember { mutableStateOf<Expense?>(null) }
    val scopeNames = expenses.map { if (it.projectName.isBlank()) "General" else it.projectName }.distinct().sorted()
    val filtered = when (scope) {
        "" -> expenses
        "General" -> expenses.filter { it.projectName.isBlank() }
        else -> expenses.filter { it.projectName == scope }
    }
    val totals = expenseTotalsOf(filtered)

    Column(Modifier.fillMaxSize().background(NeutralBg).navigationBarsPadding()) {
        RgsTopBar("Expenses", onNav = onBack)
        BalanceCard(totals.advance, totals.spent, totals.companyPaid)
        FuelSummary(filtered)
        if (vehicleNumber.isNotBlank()) Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.clip(RoundedCornerShape(9.dp)).background(SoftChipGradient).padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    "🚗 " + vehicleNumber + (if (vehicleType.isNotBlank()) " · " + vehicleType else ""),
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppYellowDark,
                )
            }
        }
        val scopeLabel = if (scope.isBlank()) "All" else scope
        if (filtered.isNotEmpty()) ExportRow({ onExportPdf(filtered, scopeLabel) }, { onExportExcel(filtered, scopeLabel) })
        if (scopeNames.size > 1) ScopeChips(scope, scopeNames) { scope = it }

        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp)) {
            if (filtered.isEmpty()) {
                item {
                    Text(
                        "No entries yet. Add an advance you took, then log expenses as you spend.",
                        fontSize = 12.5.sp, color = NeutralTextSoft,
                        modifier = Modifier.fillMaxWidth().padding(28.dp),
                    )
                }
            }
            val groups = filtered.sortedByDescending { it.date }.groupBy { dayFmt.format(Date(it.date)) }
            groups.forEach { (day, items) ->
                item {
                    Text(
                        day, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = NeutralTextSoft,
                        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp),
                    )
                }
                items(items, key = { it.id }) { e -> ExpenseRow(e, approvals[e.id]) { toDelete = e } }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AddButton("+ Advance", Modifier.weight(1f), filled = false) { adding = "ADVANCE" }
            AddButton("+ Expense", Modifier.weight(1f), filled = true) { adding = "EXPENSE" }
        }
    }

    toDelete?.let { e ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            confirmButton = { TextButton(onClick = { onDelete(e.id); toDelete = null }) { Text("Delete", color = StatusError, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel", color = NeutralTextSoft) } },
            title = { Text("${e.category} · ${money(e.amount)}", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(dayFmt.format(Date(e.date)) + if (e.projectName.isNotBlank()) " · ${e.projectName}" else " · General", fontSize = 12.sp, color = NeutralTextSoft)
                    if (e.note.isNotBlank()) Text(e.note, fontSize = 13.sp, color = NeutralText, modifier = Modifier.padding(top = 6.dp))
                    if (e.billPhotos.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            e.billPhotos.take(4).forEach { p ->
                                AsyncImage(model = File(p), contentDescription = null, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)).background(NeutralSurfaceV))
                            }
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun BalanceCard(advance: Double, spent: Double, companyPaid: Double) {
    val balance = advance - spent
    Box(
        Modifier.fillMaxWidth().padding(10.dp).clip(RoundedCornerShape(18.dp))
            .background(BrandGradient).padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.AccountBalanceWallet, null, tint = AppYellowDark, modifier = Modifier.size(15.dp)) }
                Spacer(Modifier.width(8.dp))
                Text("Balance overview", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NeutralText.copy(alpha = 0.85f))
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GStat("Advance", money(advance), NeutralText, Modifier.weight(1f))
                GDivider()
                GStat("Spent", money(spent), NeutralText, Modifier.weight(1f))
                GDivider()
                GStat(
                    if (balance >= 0) "To return" else "Overspent",
                    money(kotlin.math.abs(balance)),
                    if (balance >= 0) NeutralText else StatusError,
                    Modifier.weight(1f), emphasize = true,
                )
            }
            if (companyPaid > 0) {
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.4f))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Paid by company (not in your balance)", fontSize = 10.5.sp, color = NeutralText.copy(alpha = 0.75f), modifier = Modifier.weight(1f))
                    Text(money(companyPaid), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = NeutralText)
                }
            }
        }
    }
}

@Composable
private fun GStat(label: String, value: String, valueColor: Color, modifier: Modifier, emphasize: Boolean = false) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = if (emphasize) 18.sp else 16.sp, fontWeight = FontWeight.ExtraBold, color = valueColor)
        Text(label, fontSize = 10.5.sp, fontWeight = FontWeight.Medium, color = NeutralText.copy(alpha = 0.7f))
    }
}

@Composable
private fun GDivider() {
    Box(Modifier.height(28.dp).width(1.dp).background(Color.White.copy(alpha = 0.5f)))
}

@Composable
private fun StatCol(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = color)
        Text(label, fontSize = 11.sp, color = NeutralTextSoft)
    }
}

@Composable
private fun ScopeChips(scope: String, names: List<String>, onScope: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Chip("All", scope == "") { onScope("") }
        names.forEach { n -> Chip(n, scope == n) { onScope(n) } }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(20.dp))
            .background(if (selected) BrandGradient else SolidColor(NeutralSurfaceV))
            .clickable { onClick() }.padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (selected) NeutralText else NeutralTextSoft)
    }
}

@Composable
private fun ExpenseRow(e: Expense, approval: String?, onClick: () -> Unit) {
    val isIn = e.kind == "ADVANCE"
    RgsCard(Modifier.padding(vertical = 3.dp).clickable { onClick() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(11.dp))
                    .background(if (isIn) SolidColor(AdvanceGreen.copy(alpha = 0.16f)) else SoftChipGradient),
                contentAlignment = Alignment.Center,
            ) {
                Icon(iconFor(e.category), null, tint = if (isIn) AdvanceGreen else AppYellowDark, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(e.category, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NeutralText)
                val sub = listOf(
                    if (e.projectName.isNotBlank()) e.projectName else "General",
                    if (e.paymentMode == "Company") "Company-paid" else "",
                    e.note.takeIf { it.isNotBlank() } ?: "",
                ).filter { it.isNotBlank() }.joinToString(" · ")
                if (sub.isNotBlank()) Text(sub, fontSize = 11.5.sp, color = NeutralTextSoft)
                if (e.kind == "EXPENSE" && e.paymentMode != "Company") {
                    val badge = when (approval) {
                        "approved" -> "✓ Approved" to AdvanceGreen
                        "rejected" -> "✗ Rejected" to StatusError
                        else -> null
                    }
                    if (badge != null) Text(
                        badge.first, fontSize = 10.5.sp, fontWeight = FontWeight.Bold,
                        color = badge.second, modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
            Text(
                (if (isIn) "+ " else "− ") + money(e.amount),
                fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                color = if (isIn) AdvanceGreen else StatusError,
            )
        }
    }
}

@Composable
private fun AddButton(label: String, modifier: Modifier, filled: Boolean, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(14.dp))
            .background(if (filled) BrandGradient else SolidColor(YellowContainer))
            .clickable { onClick() }.padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Add, null, tint = if (filled) Color.Black else AppYellowDark, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(5.dp))
            Text(label.removePrefix("+ "), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (filled) Color.Black else AppYellowDark)
        }
    }
}

// ── Add / edit form ──
@Composable
private fun AddExpenseForm(
    initialKind: String,
    projects: List<Distributor>,
    vehicles: List<Vehicle>,
    compressBill: suspend (Uri) -> String?,
    onCancel: () -> Unit,
    onSave: (Expense) -> Unit,
) {
    var kind by remember { mutableStateOf(initialKind) }
    var category by remember { mutableStateOf(if (initialKind == "ADVANCE") "Advance" else "") }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(System.currentTimeMillis()) }
    var projectId by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var payment by remember { mutableStateOf("Cash") }
    var showDate by remember { mutableStateOf(false) }
    var odometer by remember { mutableStateOf("") }
    var litres by remember { mutableStateOf("") }
    var vehicleId by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    var days by remember { mutableStateOf("") }
    var nights by remember { mutableStateOf("") }
    var dayRate by remember { mutableStateOf("") }
    var billPhotos by remember { mutableStateOf<List<String>>(emptyList()) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var camUri by remember { mutableStateOf<Uri?>(null) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch { compressBill(uri)?.let { billPhotos = billPhotos + it } }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) camUri?.let { u -> scope.launch { compressBill(u)?.let { billPhotos = billPhotos + it } } }
    }
    // Fuel: amount = litres × rate (auto-filled).
    androidx.compose.runtime.LaunchedEffect(litres, rate, category) {
        if (category == "Fuel") {
            val f = (litres.toDoubleOrNull() ?: 0.0) * (rate.toDoubleOrNull() ?: 0.0)
            if (f > 0) amount = if (f % 1.0 == 0.0) f.toLong().toString() else "%.2f".format(f)
        }
    }
    // Daily wages / Driver: amount = (days + nights) × rate/day (auto-filled).
    androidx.compose.runtime.LaunchedEffect(days, nights, dayRate, category) {
        if (category == "Daily wages" || category == "Driver") {
            val units = (days.toDoubleOrNull() ?: 0.0) + (nights.toDoubleOrNull() ?: 0.0)
            val a = units * (dayRate.toDoubleOrNull() ?: 0.0)
            if (a > 0) amount = if (a % 1.0 == 0.0) a.toLong().toString() else "%.2f".format(a)
        }
    }

    Column(Modifier.fillMaxSize().background(NeutralBg).navigationBarsPadding()) {
        RgsTopBar(if (kind == "ADVANCE") "Add Advance" else "Add Expense", onNav = onCancel)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp)) {
            // kind toggle
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Expense", kind == "EXPENSE") { kind = "EXPENSE"; if (category == "Advance") category = "" }
                Chip("Advance", kind == "ADVANCE") { kind = "ADVANCE"; category = "Advance" }
            }
            Spacer(Modifier.height(14.dp))

            if (kind == "EXPENSE") {
                Label("Category")
                CategoryDropdown(category) { category = it }
                val hasCalc = category == "Fuel" || category == "Daily wages" || category == "Driver"
                Spacer(Modifier.height(if (hasCalc) 10.dp else 14.dp))

                if (category == "Fuel") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f)) { Field("Odometer (km)", odometer, KeyboardType.Number) { odometer = it.filter { c -> c.isDigit() || c == '.' } } }
                        Box(Modifier.weight(1f)) { Field("Litres", litres, KeyboardType.Number) { litres = it.filter { c -> c.isDigit() || c == '.' } } }
                    }
                    Spacer(Modifier.height(10.dp))
                    Field("Rate (₹/L)", rate, KeyboardType.Number) { rate = it.filter { c -> c.isDigit() || c == '.' } }
                    Spacer(Modifier.height(14.dp))
                    if (vehicles.isNotEmpty()) {
                        Label("Company vehicle (optional) — also logs this fuel to Fleet")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Chip("None", vehicleId.isBlank()) { vehicleId = "" }
                            vehicles.forEach { veh -> Chip(veh.name.ifBlank { veh.number }, vehicleId == veh.id) { vehicleId = veh.id } }
                        }
                        Spacer(Modifier.height(14.dp))
                    }
                }

                if (category == "Daily wages" || category == "Driver") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f)) { Field("No. of days", days, KeyboardType.Number) { days = it.filter { c -> c.isDigit() || c == '.' } } }
                        Box(Modifier.weight(1f)) { Field("No. of nights", nights, KeyboardType.Number) { nights = it.filter { c -> c.isDigit() || c == '.' } } }
                    }
                    Spacer(Modifier.height(10.dp))
                    Field("Rate / day (₹)", dayRate, KeyboardType.Number) { dayRate = it.filter { c -> c.isDigit() || c == '.' } }
                    Text(
                        "Amount auto-fills = (days + nights) × rate/day. Use the note for the worker name.",
                        fontSize = 10.5.sp, color = NeutralTextSoft, modifier = Modifier.padding(top = 5.dp),
                    )
                    Spacer(Modifier.height(14.dp))
                }
            }

            Field("Amount (₹)", amount, KeyboardType.Number) { v -> amount = v.filter { it.isDigit() || it == '.' } }
            Spacer(Modifier.height(10.dp))

            // Date
            Label("Date")
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(NeutralSurfaceV)
                    .clickable { showDate = true }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.CalendarMonth, null, tint = AppYellowDark, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(dayFmt.format(Date(date)), fontSize = 14.sp, color = NeutralText)
            }
            Spacer(Modifier.height(10.dp))

            // Project
            Label("Project")
            ProjectDropdown(projects, projectId) { projectId = it }
            Spacer(Modifier.height(10.dp))

            // Note
            Field(if (category == "Material") "Material details — what / qty / rate" else "Note (optional)", note, KeyboardType.Text) { note = it }
            Spacer(Modifier.height(10.dp))

            // Payment mode
            Label("Paid by")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Cash", "UPI", "Card", "Company").forEach { p -> Chip(p, payment == p) { payment = p } }
            }
            if (payment == "Company") {
                Spacer(Modifier.height(4.dp))
                Text("Paid directly by the company — kept out of your advance balance.", fontSize = 11.sp, color = NeutralTextSoft)
            }
            Spacer(Modifier.height(14.dp))

            Label("Bill photos — fuel bill, odometer, UPI screenshot…")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                billPhotos.forEach { p ->
                    Box(Modifier.size(72.dp)) {
                        AsyncImage(model = File(p), contentDescription = null, modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)).background(NeutralSurfaceV))
                        Box(
                            Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).clickable { billPhotos = billPhotos - p },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Close, null, tint = Color.White, modifier = Modifier.size(13.dp)) }
                    }
                }
                PhotoTile(Icons.Rounded.PhotoCamera, "Camera") {
                    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                    val f = File(dir, "billcam_${System.currentTimeMillis()}.jpg")
                    val u = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                    camUri = u; camera.launch(u)
                }
                PhotoTile(Icons.Rounded.Image, "Gallery") { gallery.launch("image/*") }
            }
            Spacer(Modifier.height(20.dp))

            val amt = amount.toDoubleOrNull() ?: 0.0
            val valid = amt > 0 && (kind == "ADVANCE" || category.isNotBlank())
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(if (valid) AppYellow else AppYellow.copy(alpha = 0.3f))
                    .clickable(enabled = valid) {
                        onSave(
                            Expense(
                                kind = kind,
                                category = if (kind == "ADVANCE") "Advance" else category,
                                amount = amt, date = date, projectId = projectId,
                                note = note.trim(), paymentMode = payment, billPhotos = billPhotos,
                                odometer = odometer.toDoubleOrNull() ?: 0.0,
                                litres = litres.toDoubleOrNull() ?: 0.0,
                                ratePerLitre = rate.toDoubleOrNull() ?: 0.0,
                                vehicleId = vehicleId,
                                days = days.toDoubleOrNull() ?: 0.0,
                                nights = nights.toDoubleOrNull() ?: 0.0,
                                ratePerDay = dayRate.toDoubleOrNull() ?: 0.0,
                            ),
                        )
                    }.padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (kind == "ADVANCE") "Save advance" else "Save expense", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }
    }

    if (showDate) {
        val st = rememberDatePickerState(initialSelectedDateMillis = date)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = { TextButton(onClick = { st.selectedDateMillis?.let { date = it }; showDate = false }) { Text("OK", color = AppYellowDark, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel", color = NeutralTextSoft) } },
        ) { DatePicker(state = st) }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeutralTextSoft, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun Field(label: String, value: String, keyboardType: KeyboardType, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = !label.startsWith("Note") && !label.startsWith("Material"),
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
private fun ProjectDropdown(projects: List<Distributor>, selectedId: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = if (selectedId.isBlank()) "General (no project)" else projects.find { it.id == selectedId }?.name ?: "General (no project)"
    Box {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(NeutralSurfaceV)
                .clickable { expanded = true }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontSize = 14.sp, color = NeutralText, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.KeyboardArrowDown, null, tint = NeutralTextSoft, modifier = Modifier.size(22.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("General (no project)") }, onClick = { onSelect(""); expanded = false })
            projects.forEach { d ->
                DropdownMenuItem(text = { Text(d.name) }, onClick = { onSelect(d.id); expanded = false })
            }
        }
    }
}

@Composable
private fun PhotoTile(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)).background(YellowContainer).clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = AppYellowDark, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = AppYellowDark)
    }
}

@Composable
private fun CategoryDropdown(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(if (selected.isBlank()) SolidColor(NeutralSurfaceV) else BrandGradient)
                .clickable { expanded = true }.padding(horizontal = 12.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected.isNotBlank()) {
                Box(
                    Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(Color.White),
                    contentAlignment = Alignment.Center,
                ) { Icon(iconFor(selected), null, tint = AppYellowDark, modifier = Modifier.size(19.dp)) }
                Spacer(Modifier.width(10.dp))
            }
            Text(
                selected.ifBlank { "Select category" }, fontSize = 14.5.sp,
                fontWeight = if (selected.isBlank()) FontWeight.Normal else FontWeight.Bold,
                color = if (selected.isBlank()) NeutralTextSoft else NeutralText, modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Rounded.KeyboardArrowDown, null,
                tint = if (selected.isBlank()) NeutralTextSoft else AppYellowDark, modifier = Modifier.size(24.dp),
            )
        }
        DropdownMenu(
            expanded = expanded, onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 600.dp).background(Color.White),
        ) {
            Row(
                Modifier.fillMaxWidth().background(BrandGradientH).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Category, null, tint = NeutralText, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text("Select a category", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = NeutralText)
            }
            EXPENSE_CATS.forEach { c ->
                val sel = c.name == selected
                DropdownMenuItem(
                    modifier = Modifier.background(if (sel) YellowContainer.copy(alpha = 0.5f) else Color.Transparent),
                    text = {
                        Row(modifier = Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(SoftChipGradient),
                                contentAlignment = Alignment.Center,
                            ) { Icon(c.icon, null, tint = AppYellowDark, modifier = Modifier.size(18.dp)) }
                            Spacer(Modifier.width(14.dp))
                            Text(
                                c.name, fontSize = 14.5.sp,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                color = if (sel) AppYellowDark else NeutralText,
                            )
                        }
                    },
                    onClick = { onSelect(c.name); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun ExportRow(onPdf: () -> Unit, onExcel: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ExportChip("Export PDF", Icons.Rounded.PictureAsPdf, Modifier.weight(1f), onPdf)
        ExportChip("Export Excel", Icons.Rounded.GridOn, Modifier.weight(1f), onExcel)
    }
}

@Composable
private fun ExportChip(label: String, icon: ImageVector, mod: Modifier, onClick: () -> Unit) {
    Row(
        mod.clip(RoundedCornerShape(10.dp)).background(YellowContainer).clickable { onClick() }.padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = AppYellowDark, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = AppYellowDark)
    }
}

@Composable
private fun FuelSummary(expenses: List<Expense>) {
    val stats = fuelStatsOf(expenses) ?: return
    val dist = stats.distance
    val avg = stats.avgMileage
    val perKm = stats.costPerKm
    RgsCard(Modifier.padding(horizontal = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.LocalGasStation, null, tint = AppYellowDark, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Fuel & Mileage", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NeutralText)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            StatCol("Distance", "%.0f km".format(dist), NeutralText, Modifier.weight(1f))
            StatCol("Avg mileage", "%.1f km/L".format(avg), AdvanceGreen, Modifier.weight(1f))
            StatCol("Cost / km", "₹%.1f".format(perKm), AppYellowDark, Modifier.weight(1f))
        }
    }
}
