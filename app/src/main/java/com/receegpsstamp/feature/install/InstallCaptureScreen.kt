@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.receegpsstamp.feature.install

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.receegpsstamp.data.model.InstallEntry
import com.receegpsstamp.ui.components.RgsCard
import com.receegpsstamp.ui.components.RgsTopBar
import com.receegpsstamp.ui.theme.AppYellowDark
import com.receegpsstamp.ui.theme.BrandGradient
import com.receegpsstamp.ui.theme.NeutralBg
import com.receegpsstamp.ui.theme.NeutralSurfaceV
import com.receegpsstamp.ui.theme.NeutralText
import com.receegpsstamp.ui.theme.NeutralTextSoft
import com.receegpsstamp.ui.theme.StatusError
import com.receegpsstamp.ui.theme.YellowContainer
import java.io.File

private val REASONS = listOf("Print missing", "No frame", "Shop moved / closed", "Owner refused", "Other")
private val OkGreen = Color(0xFF1B873F)

private fun statusOf(e: InstallEntry): Pair<String, Color> = when (e.status) {
    "Installed" -> "✓ Installed" to OkGreen
    "InProgress" -> "● In progress" to AppYellowDark
    "NotDone" -> "✗ ${e.reason.ifBlank { "Not done" }}" to StatusError
    else -> "To install" to NeutralTextSoft
}

/**
 * Installation for ONE shop — lists every approved media-size of the shop as a card. Before = recce
 * photos (+ any added here); the installer captures the After per size and marks Installed / reason.
 */
@Composable
fun InstallShopScreen(
    shopName: String,
    city: String,
    entries: List<InstallEntry>,
    frontPhotos: List<String>,
    recceBefore: (InstallEntry) -> List<String>,
    onStart: (String) -> Unit,
    onAddFront: () -> Unit,
    onAddBefore: (String) -> Unit,
    onAddAfter: (String) -> Unit,
    onDone: (String) -> Unit,
    onNotDone: (String, String) -> Unit,
    onReopen: (String) -> Unit,
    onShareWhatsApp: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(NeutralBg).navigationBarsPadding()) {
        RgsTopBar(shopName.ifBlank { "Installation" }, showBack = true, onNav = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(12.dp)) {
            val doneCount = entries.count { it.status == "Installed" }
            Text(
                listOf(city, "${entries.size} size${if (entries.size > 1) "s" else ""} · $doneCount installed")
                    .filter { it.isNotBlank() }.joinToString(" · "),
                fontSize = 12.5.sp, color = NeutralTextSoft, modifier = Modifier.padding(bottom = 10.dp),
            )
            // Shop-front (storefront) photo — one set for the whole shop.
            RgsCard(Modifier.fillMaxWidth()) {
                Label("SHOP FRONT")
                Photos(frontPhotos, empty = "No shop-front photo.")
                Spacer(Modifier.height(6.dp))
                SmallBtn("📷 Add front shop photo", onAddFront)
            }
            Spacer(Modifier.height(10.dp))
            entries.forEachIndexed { i, e ->
                SizeCard(
                    e = e,
                    recceBefore = recceBefore(e),
                    onStart = { onStart(e.id) },
                    onAddBefore = { onAddBefore(e.id) },
                    onAddAfter = { onAddAfter(e.id) },
                    onDone = { onDone(e.id) },
                    onNotDone = { reason -> onNotDone(e.id, reason) },
                    onReopen = { onReopen(e.id) },
                )
                if (i < entries.size - 1) Spacer(Modifier.height(10.dp))
            }
            val allResolved = entries.all { it.status == "Installed" || it.status == "NotDone" }
            Spacer(Modifier.height(16.dp))
            if (allResolved) {
                BigBtn("📤 Save & Share on WhatsApp", filled = true, onClick = onShareWhatsApp)
            } else {
                Text(
                    "Finish all sizes (install or mark a reason) to Save & Share.",
                    fontSize = 12.sp, color = NeutralTextSoft,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SizeCard(
    e: InstallEntry,
    recceBefore: List<String>,
    onStart: () -> Unit,
    onAddBefore: () -> Unit,
    onAddAfter: () -> Unit,
    onDone: () -> Unit,
    onNotDone: (String) -> Unit,
    onReopen: () -> Unit,
) {
    RgsCard(Modifier.fillMaxWidth()) {
        val label = listOf(e.mediaType, e.size).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Install" }
        val (st, sc) = statusOf(e)
        Box(Modifier.fillMaxWidth()) {
            Text(label, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = NeutralText, modifier = Modifier.padding(end = 90.dp))
            Text(st, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = sc, modifier = Modifier.align(Alignment.TopEnd))
        }

        Spacer(Modifier.height(10.dp))
        Label("BEFORE")
        Photos(recceBefore + e.beforePhotos, empty = "No before photo.")
        Spacer(Modifier.height(6.dp))
        SmallBtn("📷 Add before", onAddBefore)

        Spacer(Modifier.height(12.dp))
        Label("AFTER")
        Photos(e.afterPhotos, empty = "No after photo yet.")
        Spacer(Modifier.height(6.dp))
        SmallBtn("📷 Add after", onAddAfter)

        if (e.status == "Pending" || e.status == "InProgress") {
            Spacer(Modifier.height(14.dp))
            if (e.status == "Pending") {
                BigBtn("▶ Start installation", filled = true, onClick = onStart)
                Spacer(Modifier.height(8.dp))
            }
            if (e.afterPhotos.isNotEmpty()) {
                BigBtn("✅ Mark Installed", filled = true, onClick = onDone)
            } else {
                Text("Add an after photo to mark Installed.", fontSize = 12.sp, color = NeutralTextSoft)
            }
            Spacer(Modifier.height(14.dp))
            Label("Not done — pick a reason")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                REASONS.forEach { r ->
                    Box(
                        Modifier.clip(RoundedCornerShape(20.dp)).background(SolidColor(NeutralSurfaceV)).clickable { onNotDone(r) }.padding(horizontal = 13.dp, vertical = 9.dp),
                    ) { Text(r, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = StatusError) }
                }
            }
        } else {
            Spacer(Modifier.height(12.dp))
            SmallBtn("↺ Reopen to edit", onReopen)
        }
    }
}

@Composable
private fun Photos(paths: List<String>, empty: String) {
    val files = paths.map { File(it) }.filter { it.exists() }
    if (files.isEmpty()) {
        Text(empty, fontSize = 12.5.sp, color = NeutralTextSoft)
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            files.forEach { f ->
                AsyncImage(
                    model = f, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(92.dp).clip(RoundedCornerShape(10.dp)).background(NeutralSurfaceV),
                )
            }
        }
    }
}

@Composable
private fun Label(t: String) =
    Text(t, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeutralTextSoft, modifier = Modifier.padding(bottom = 7.dp))

@Composable
private fun SmallBtn(label: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(10.dp)).background(SolidColor(YellowContainer)).clickable { onClick() }.padding(horizontal = 14.dp, vertical = 9.dp),
    ) { Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = AppYellowDark) }
}

@Composable
private fun BigBtn(label: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(if (filled) BrandGradient else SolidColor(YellowContainer)).clickable { onClick() }.padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (filled) Color.Black else AppYellowDark) }
}
