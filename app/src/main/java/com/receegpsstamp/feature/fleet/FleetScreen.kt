@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.receegpsstamp.feature.fleet

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.receegpsstamp.data.model.FuelLog
import com.receegpsstamp.data.model.ServiceLog
import com.receegpsstamp.data.model.Vehicle
import com.receegpsstamp.ui.components.RgsCard
import com.receegpsstamp.ui.components.RgsTopBar
import com.receegpsstamp.ui.theme.AppYellowDark
import com.receegpsstamp.ui.theme.BrandGradient
import com.receegpsstamp.ui.theme.NeutralBg
import com.receegpsstamp.ui.theme.NeutralSurfaceV
import com.receegpsstamp.ui.theme.NeutralText
import com.receegpsstamp.ui.theme.NeutralTextSoft
import com.receegpsstamp.ui.theme.SoftChipGradient
import com.receegpsstamp.ui.theme.StatusError
import com.receegpsstamp.ui.theme.YellowContainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
private fun money(v: Double) = "₹" + "%,.0f".format(v)
private val WarnAmber = Color(0xFF8A6D00)

// Status / economy / alert math lives in FleetMath.kt (extracted for unit testing — see FleetMathTest).

@Composable
fun FleetScreen(
    vehicles: List<Vehicle>,
    fuelLogs: List<FuelLog>,
    serviceLogs: List<ServiceLog>,
    onAddFuel: (FuelLog) -> Unit,
    onDeleteFuel: (String) -> Unit,
    onAddService: (ServiceLog) -> Unit,
    onDeleteService: (String) -> Unit,
    onBack: () -> Unit,
) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = vehicles.find { it.id == selectedId }

    var fuelFor by remember { mutableStateOf<Vehicle?>(null) }
    var svcFor by remember { mutableStateOf<Vehicle?>(null) }

    BackHandler(enabled = selected != null) { selectedId = null }

    Column(Modifier.fillMaxSize().background(NeutralBg).navigationBarsPadding()) {
        RgsTopBar(
            if (selected != null) selected.name.ifBlank { selected.number }.ifBlank { "Vehicle" } else "Fleet Manage",
            onNav = { if (selected != null) selectedId = null else onBack() },
        )
        if (selected == null) {
            FleetList(vehicles, fuelLogs, onOpen = { selectedId = it.id })
        } else {
            VehicleDetail(
                selected,
                fuelLogs.filter { it.vehicleId == selected.id },
                serviceLogs.filter { it.vehicleId == selected.id },
                onFuel = { fuelFor = selected }, onService = { svcFor = selected },
                onDeleteFuel = onDeleteFuel, onDeleteService = onDeleteService,
            )
        }
    }

    fuelFor?.let { v -> FuelDialog(v, { fuelFor = null }) { onAddFuel(it); fuelFor = null } }
    svcFor?.let { v -> ServiceDialog(v, { svcFor = null }) { onAddService(it); svcFor = null } }
}

// ── List ──
@Composable
private fun FleetList(vehicles: List<Vehicle>, fuelLogs: List<FuelLog>, onOpen: (Vehicle) -> Unit) {
    val allAlerts = vehicles.flatMap { v -> alertsOf(v, statusOf(v, fuelLogs.filter { it.vehicleId == v.id })) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        if (allAlerts.isNotEmpty()) {
            item {
                RgsCard(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Text("⚠ ALERTS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StatusError)
                    Spacer(Modifier.height(6.dp))
                    allAlerts.forEach { Text("• $it", fontSize = 12.5.sp, color = NeutralText, modifier = Modifier.padding(vertical = 2.dp)) }
                }
            }
        }
        if (vehicles.isEmpty()) {
            item {
                Text(
                    "No vehicles yet. Vehicles are added on the web dashboard — they appear here automatically. Then log each fuel refill (with odometer) to track mileage & alerts.",
                    fontSize = 12.5.sp, color = NeutralTextSoft, modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            }
        }
        items(vehicles, key = { it.id }) { v -> VehicleCard(v, fuelLogs.filter { it.vehicleId == v.id }) { onOpen(v) } }
    }
}

@Composable
private fun VehicleCard(v: Vehicle, fuel: List<FuelLog>, onClick: () -> Unit) {
    val st = statusOf(v, fuel)
    val alerts = alertsOf(v, st)
    RgsCard(Modifier.fillMaxWidth().padding(top = 10.dp).clickable { onClick() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(SoftChipGradient), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.DirectionsCar, null, tint = AppYellowDark, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(v.name.ifBlank { v.number }, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = NeutralText)
                Text(
                    listOf(v.number.takeIf { it.isNotBlank() && v.name.isNotBlank() } ?: "", v.model, v.type, "${v.currentKm} km").filter { it.isNotBlank() }.joinToString(" · "),
                    fontSize = 11.5.sp, color = NeutralTextSoft,
                )
            }
            if (st.economy > 0) Text("%.1f km/l".format(st.economy), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppYellowDark)
        }
        if (alerts.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            alerts.take(2).forEach { Text("⚠ $it", fontSize = 11.5.sp, color = StatusError) }
        }
    }
}

// ── Detail ──
@Composable
private fun VehicleDetail(
    v: Vehicle, fuel: List<FuelLog>, service: List<ServiceLog>,
    onFuel: () -> Unit, onService: () -> Unit,
    onDeleteFuel: (String) -> Unit, onDeleteService: (String) -> Unit,
) {
    val st = statusOf(v, fuel)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        RgsCard(Modifier.fillMaxWidth()) {
            Row {
                Stat("Odometer", "${v.currentKm} km", Modifier.weight(1f))
                Stat("Mileage", if (st.economy > 0) "%.1f km/l".format(st.economy) else "—", Modifier.weight(1f))
                Stat("Type", v.type, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(10.dp))
        SecLabel("MAINTENANCE")
        st.items.forEach { DueRow(it) }

        Spacer(Modifier.height(10.dp))
        SecLabel("DOCUMENTS")
        DocRow("Insurance", st.insDays)
        DocRow("PUC", st.pucDays)
        DocRow("Fitness", st.fitDays)

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FleetBtn("⛽ Add fuel", Modifier.weight(1f), filled = true, onFuel)
            FleetBtn("🔧 Add service", Modifier.weight(1f), filled = false, onService)
        }

        if (fuel.isNotEmpty()) {
            Spacer(Modifier.height(14.dp)); SecLabel("FUEL HISTORY")
            fuel.sortedByDescending { it.date }.forEach { f ->
                LogRow("⛽ ${f.odometer} km · %.1f L".format(f.litres), money(f.amount), dateFmt.format(Date(f.date))) { onDeleteFuel(f.id) }
            }
        }
        if (service.isNotEmpty()) {
            Spacer(Modifier.height(14.dp)); SecLabel("SERVICE HISTORY")
            service.sortedByDescending { it.date }.forEach { s ->
                LogRow("${s.type} · ${s.odometer} km", money(s.amount), dateFmt.format(Date(s.date))) { onDeleteService(s.id) }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = NeutralText)
        Text(label, fontSize = 11.sp, color = NeutralTextSoft)
    }
}

@Composable
private fun SecLabel(t: String) = Text(t, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeutralTextSoft, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))

@Composable
private fun DueRow(d: ItemDue) {
    RgsCard(Modifier.fillMaxWidth().padding(bottom = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(d.name, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = NeutralText, modifier = Modifier.weight(1f))
            val (txt, col) = when {
                !d.recorded -> "not recorded" to NeutralTextSoft
                d.remainingKm <= 0 -> "OVERDUE by ${-d.remainingKm} km" to StatusError
                d.remainingKm <= 500 -> "due in ${d.remainingKm} km" to WarnAmber
                else -> "in ${d.remainingKm} km" to NeutralTextSoft
            }
            Text(txt, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = col)
        }
        if (d.recorded) {
            Spacer(Modifier.height(6.dp))
            val used = (d.intervalKm - d.remainingKm).coerceIn(0, d.intervalKm).toFloat() / d.intervalKm.coerceAtLeast(1)
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(YellowContainer)) {
                Box(Modifier.fillMaxWidth(used).height(6.dp).clip(RoundedCornerShape(50)).background(if (d.remainingKm <= 0) SolidColor(StatusError) else BrandGradient))
            }
        }
    }
}

@Composable
private fun DocRow(label: String, days: Long?) {
    RgsCard(Modifier.fillMaxWidth().padding(bottom = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = NeutralText, modifier = Modifier.weight(1f))
            val (txt, col) = when {
                days == null -> "not set" to NeutralTextSoft
                days < 0 -> "EXPIRED ${-days}d ago" to StatusError
                days <= 15 -> "expires in ${days}d" to WarnAmber
                else -> "in ${days}d" to NeutralTextSoft
            }
            Text(txt, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = col)
        }
    }
}

@Composable
private fun LogRow(title: String, amount: String, date: String, onDelete: () -> Unit) {
    RgsCard(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NeutralText)
                Text(date, fontSize = 11.sp, color = NeutralTextSoft)
            }
            Text(amount, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NeutralText)
            Spacer(Modifier.width(10.dp))
            Text("✕", fontSize = 14.sp, color = NeutralTextSoft, modifier = Modifier.clickable { onDelete() }.padding(4.dp))
        }
    }
}

@Composable
private fun FleetBtn(label: String, modifier: Modifier, filled: Boolean, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(13.dp)).background(if (filled) BrandGradient else SolidColor(YellowContainer)).clickable { onClick() }.padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = if (filled) Color.Black else AppYellowDark)
    }
}

// ── Dialogs ──
@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, numeric: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label) }, singleLine = true,
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

@Composable
private fun TypePicker(options: List<String>, selected: String, onPick: (String) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        options.forEach { o ->
            val on = o == selected
            Box(
                Modifier.clip(RoundedCornerShape(9.dp)).background(if (on) BrandGradient else SolidColor(NeutralSurfaceV)).clickable { onPick(o) }.padding(horizontal = 12.dp, vertical = 8.dp),
            ) { Text(o, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (on) Color.Black else NeutralTextSoft) }
        }
    }
}

@Composable
private fun FuelDialog(v: Vehicle, onDismiss: () -> Unit, onSave: (FuelLog) -> Unit) {
    var odo by remember { mutableStateOf(v.currentKm.takeIf { it > 0 }?.toString() ?: "") }
    var litres by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add fuel — ${v.name.ifBlank { v.number }}") },
        text = {
            Column {
                Text("Enter the odometer reading at fill-up — this updates km & mileage.", fontSize = 12.sp, color = NeutralTextSoft, modifier = Modifier.padding(bottom = 4.dp))
                Field("Odometer (km)", odo, { odo = it }, numeric = true)
                Field("Litres", litres, { litres = it }, numeric = true)
                Field("Amount (₹)", amount, { amount = it }, numeric = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(FuelLog(vehicleId = v.id, date = System.currentTimeMillis(), odometer = odo.toIntOrNull() ?: 0, litres = litres.toDoubleOrNull() ?: 0.0, amount = amount.toDoubleOrNull() ?: 0.0))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ServiceDialog(v: Vehicle, onDismiss: () -> Unit, onSave: (ServiceLog) -> Unit) {
    var type by remember { mutableStateOf(v.maintItems.firstOrNull()?.name ?: "Oil change") }
    var odo by remember { mutableStateOf(v.currentKm.takeIf { it > 0 }?.toString() ?: "") }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add service — ${v.name.ifBlank { v.number }}") },
        text = {
            Column {
                Text("Type", fontSize = 11.sp, color = NeutralTextSoft)
                TypePicker(v.maintItems.map { it.name } + "Other", type) { type = it }
                Field("Odometer (km)", odo, { odo = it }, numeric = true)
                Field("Amount (₹)", amount, { amount = it }, numeric = true)
                Field("Note", note, { note = it })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(ServiceLog(vehicleId = v.id, date = System.currentTimeMillis(), odometer = odo.toIntOrNull() ?: 0, type = type, amount = amount.toDoubleOrNull() ?: 0.0, note = note.trim()))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
