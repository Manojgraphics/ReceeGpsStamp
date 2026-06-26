package com.receegpsstamp.feature.gallery

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.receegpsstamp.data.model.Distributor
import com.receegpsstamp.data.model.RecceEntry
import com.receegpsstamp.data.model.Shop
import com.receegpsstamp.ui.components.RgsTopBar
import com.receegpsstamp.ui.theme.*
import java.io.File
import kotlinx.coroutines.launch

data class GalleryPhoto(
    val path: String,
    val shopName: String,
    val city: String = "",
    val status: String = "Recce",
    val createdAt: Long = 0L,
    val distributorId: String = "",
)

private fun statusColor(status: String): Color = when (status) {
    "Interested" -> StatusInterested
    "Not Interested" -> StatusError
    "Closed" -> NeutralTextSoft
    else -> AppYellow
}

@Composable
fun GalleryScreen(
    onBack: () -> Unit,
    shops: List<Shop> = emptyList(),
    recceEntries: List<RecceEntry> = emptyList(),
    distributorName: String = "",
    distributors: List<Distributor> = emptyList(),
    selectedDistributorId: String = "",
    onMenuClick: (() -> Unit)? = null,
    onAnnotate: (path: String) -> Unit = {},
) {
    var lightboxPhoto by remember { mutableStateOf<GalleryPhoto?>(null) }
    val context = LocalContext.current

    val shopMap = shops.associateBy { it.id }
    // "" = All distributors; otherwise a specific distributor id. Defaults to the current one.
    var distFilter by remember(selectedDistributorId) { mutableStateOf(selectedDistributorId) }
    val activeDistName = if (distFilter.isBlank()) "All distributors"
        else distributors.firstOrNull { it.id == distFilter }?.name ?: distributorName

    val allPhotos = remember(recceEntries, shops, distFilter) {
        recceEntries.filter { distFilter.isBlank() || it.distributorId == distFilter }.flatMap { entry ->
            val shop = shopMap[entry.shopId]
            entry.shopPhotos.map { GalleryPhoto(it, shop?.name ?: "Unknown", shop?.city ?: "", entry.status, entry.createdAt, entry.distributorId) }
        }.distinctBy { it.path }   // unique paths — duplicate keys crash the LazyVerticalGrid (white screen)
            .sortedByDescending { it.createdAt }
    }
    // For Albums section (only shown when filter = All) — every distributor with their photo count + cover.
    val albums = remember(recceEntries, shops, distributors) {
        distributors.map { d ->
            val ps = recceEntries.filter { it.distributorId == d.id }.flatMap { e ->
                val shop = shopMap[e.shopId]
                e.shopPhotos.map { GalleryPhoto(it, shop?.name ?: "Unknown", shop?.city ?: "", e.status, e.createdAt, e.distributorId) }
            }.distinctBy { it.path }.sortedByDescending { it.createdAt }
            Triple(d, ps.size, ps.firstOrNull()?.path)
        }.filter { it.second > 0 }
    }
    var query by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("All") }
    val statuses = listOf("All", "Interested", "Not Interested", "Closed")
    val displayPhotos = allPhotos.filter { p ->
        (statusFilter == "All" || p.status == statusFilter) &&
            (query.isBlank() || p.shopName.contains(query, true) || p.city.contains(query, true))
    }

    Column(Modifier.fillMaxSize().background(NeutralBg).navigationBarsPadding()) {
        RgsTopBar(
            "Gallery",
            subtitle = "$activeDistName · ${displayPhotos.size} of ${allPhotos.size} photos",
            showBack = onMenuClick == null,
            onNav = onMenuClick ?: onBack,
        )

        // Fixed filter header (kept out of the lazy grid so it never re-lays-out the photos).
        Column(Modifier.padding(10.dp)) {
            // ── ALBUMS — show distributor album cards when no specific distributor is selected ──
            if (distFilter.isBlank() && albums.size > 1) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("ALBUMS", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp, color = NeutralTextSoft, modifier = Modifier.weight(1f))
                    Text("${albums.size} distributors", fontSize = 10.sp, color = NeutralTextSoft)
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    albums.forEach { (d, count, cover) ->
                        AlbumCard(name = d.name, count = count, cover = cover) { distFilter = d.id }
                    }
                }
                Spacer(Modifier.height(10.dp))
            } else if (distFilter.isNotBlank() && distributors.size > 1) {
                // When viewing one album, show a quick "All / <other distributors>" chip strip to switch.
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    DistChip("All", false) { distFilter = "" }
                    distributors.forEach { d -> DistChip(d.name, distFilter == d.id) { distFilter = d.id } }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (allPhotos.isNotEmpty()) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("Search shop or city", fontSize = 13.sp, color = NeutralTextSoft) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    statuses.forEach { st ->
                        val sel = statusFilter == st
                        Box(
                            Modifier.clip(RoundedCornerShape(8.dp))
                                .background(if (sel) AppYellow else NeutralSurfaceV)
                                .clickable { statusFilter = st }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(st, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = if (sel) Color.Black else NeutralTextSoft)
                        }
                    }
                }
            }
        }

        when {
            allPhotos.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                com.receegpsstamp.ui.components.EmptyState(
                    icon = RgsIcons.Gallery,
                    title = "No photos yet",
                    subtitle = "Photos you capture in $activeDistName will show up here.",
                )
            }
            displayPhotos.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No photos match this filter", fontSize = 13.sp, color = NeutralTextSoft)
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                contentPadding = PaddingValues(bottom = 10.dp),
            ) {
                items(displayPhotos, key = { it.path }) { photo ->
                    PhotoCell(photo) { lightboxPhoto = it }
                }
            }
        }
    }

    // Lightbox
    if (lightboxPhoto != null) {
        val startIndex = displayPhotos.indexOfFirst { it.path == lightboxPhoto!!.path }.coerceAtLeast(0)
        val pagerState = rememberPagerState(initialPage = startIndex) { displayPhotos.size }
        // Current page drives the top bar + actions; swipe left/right to move between photos.
        val photo = displayPhotos.getOrNull(pagerState.currentPage) ?: lightboxPhoto!!
        // Pinch-to-zoom + pan state — reset whenever the page changes.
        var scale by remember(pagerState.currentPage) { mutableStateOf(1f) }
        var offset by remember(pagerState.currentPage) { mutableStateOf(Offset.Zero) }
        // Apple Photos / OPPO Gallery style: single tap fades the bars (their space stays reserved
        // so the photo never shifts), doesn't close.
        var controlsVisible by remember { mutableStateOf(true) }
        val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
        val dateFmt = remember { java.text.SimpleDateFormat("dd MMM yyyy · HH:mm", java.util.Locale.getDefault()) }
        Dialog(
            onDismissRequest = { lightboxPhoto = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            // Make the dialog window truly edge-to-edge so navigationBarsPadding() / statusBarsPadding()
            // receive real system insets — otherwise the action bar slides under the system nav buttons.
            val view = LocalView.current
            SideEffect {
                val parent = view.parent
                if (parent is androidx.compose.ui.window.DialogWindowProvider) {
                    val window = parent.window
                    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
                    window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                    // Make the system status + nav bar areas pure black so the phone's nav icons sit
                    // cleanly against the lightbox black background instead of "interrupting" it.
                    @Suppress("DEPRECATION")
                    run {
                        window.statusBarColor = android.graphics.Color.BLACK
                        window.navigationBarColor = android.graphics.Color.BLACK
                    }
                    androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = false
                        isAppearanceLightNavigationBars = false
                    }
                }
            }
            // Column layout: [top bar] [photo — weight(1f)] [bottom bar]. The bars always reserve
            // their space (only their *contents* fade on tap), so the photo sits in a fixed area and
            // is perfectly centred between the bars — and never shifts when the bars toggle.
            val controlsAlpha by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (controlsVisible) 1f else 0f, label = "controls",
            )
            Column(Modifier.fillMaxSize().background(Color.Black)) {
                // ── Top bar (fixed height; content fades on tap) ──
                Row(
                    Modifier.fillMaxWidth()
                        .background(Color.Black.copy(0.7f * controlsAlpha))
                        .statusBarsPadding()
                        .padding(8.dp)
                        .graphicsLayer(alpha = controlsAlpha),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(RgsIcons.Close, null, tint = Color.White, modifier = Modifier.size(24.dp).clickable { lightboxPhoto = null })
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(photo.shopName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        val sub = buildList {
                            if (photo.createdAt > 0L) add(dateFmt.format(java.util.Date(photo.createdAt)))
                            if (photo.status.isNotBlank()) add(photo.status)
                            if (photo.city.isNotBlank()) add(photo.city)
                        }.joinToString(" · ")
                        if (sub.isNotEmpty()) Text(sub, fontSize = 11.sp, color = Color.White.copy(0.7f))
                    }
                    Text("${pagerState.currentPage + 1} / ${displayPhotos.size}", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(0.7f))
                }

                HorizontalPager(
                    state = pagerState,
                    // Photo fills the space between the bars and centres in it.
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    // Standard gallery: swipe changes photo when not zoomed; when zoomed in, dragging pans
                    // the image instead so the user can explore the zoomed view.
                    userScrollEnabled = scale == 1f,
                ) { page ->
                    val pageFile = File(displayPhotos[page].path)
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (pageFile.exists()) {
                            AsyncImage(
                                model = pageFile,
                                contentDescription = displayPhotos[page].shopName,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                                    // Double-tap toggles zoom; single tap (at scale 1) closes the lightbox.
                                    .pointerInput(page) {
                                        detectTapGestures(
                                            onDoubleTap = {
                                                if (scale > 1f) { scale = 1f; offset = Offset.Zero }
                                                else { scale = 2.5f; offset = Offset.Zero }
                                            },
                                            // Single tap toggles the overlay (top + bottom bars) — never closes.
                                            // Lightbox closes via the X button or system back only (Apple/OPPO style).
                                            onTap = { if (scale == 1f) controlsVisible = !controlsVisible },
                                        )
                                    }
                                    // Pinch zoom + drag pan — custom so single-finger drag at scale=1
                                    // isn't consumed (lets HorizontalPager swipe to the next photo).
                                    .pointerInput(page) {
                                        awaitEachGesture {
                                            awaitFirstDown(requireUnconsumed = false)
                                            do {
                                                val event = awaitPointerEvent()
                                                val pointers = event.changes.count { it.pressed }
                                                if (pointers >= 2) {
                                                    val zoom = event.calculateZoom()
                                                    val pan = event.calculatePan()
                                                    if (zoom != 1f || pan != Offset.Zero) {
                                                        val newScale = (scale * zoom).coerceIn(1f, 5f)
                                                        offset = if (newScale == 1f) Offset.Zero else offset + pan
                                                        scale = newScale
                                                        event.changes.forEach { it.consume() }
                                                    }
                                                } else if (scale > 1f && pointers == 1) {
                                                    val pan = event.calculatePan()
                                                    if (pan != Offset.Zero) {
                                                        offset += pan
                                                        event.changes.forEach { it.consume() }
                                                    }
                                                }
                                                // scale == 1 + single finger: don't consume → pager swipes.
                                            } while (event.changes.any { it.pressed })
                                        }
                                    }
                                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
                            )
                        }
                    }
                }

                // ── Bottom bar (fixed height; content fades on tap) ──
                Column(
                    Modifier.fillMaxWidth()
                        .background(Color.Black.copy(0.7f * controlsAlpha))
                        .navigationBarsPadding()
                        .padding(bottom = 100.dp)
                        .graphicsLayer(alpha = controlsAlpha),
                ) {
                    // ── Filmstrip — horizontal scroll of thumbnails, tap to jump to that photo ──
                    if (displayPhotos.size > 1) {
                        val listState = rememberLazyListState()
                        // Keep the current thumb in view as the user swipes.
                        androidx.compose.runtime.LaunchedEffect(pagerState.currentPage) {
                            listState.animateScrollToItem(pagerState.currentPage.coerceAtMost(displayPhotos.size - 1))
                        }
                        LazyRow(
                            state = listState,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) {
                            itemsIndexed(items = displayPhotos, key = { _, p -> p.path }) { idx, p ->
                                val sel = idx == pagerState.currentPage
                                val f = File(p.path)
                                Box(
                                    Modifier.size(width = 48.dp, height = 48.dp).clip(RoundedCornerShape(6.dp))
                                        .background(NeutralOutline)
                                        .border(if (sel) 2.dp else 0.dp, AppYellow, RoundedCornerShape(6.dp))
                                        .clickable { coroutineScope.launch { pagerState.animateScrollToPage(idx) } },
                                ) {
                                    if (f.exists()) AsyncImage(model = f, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                }
                            }
                        }
                    }
                Row(
                    Modifier.fillMaxWidth()
                        .background(Color.Black.copy(0.85f))
                        .padding(vertical = 14.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        Modifier.clickable {
                            val f = File(photo.path)
                            if (f.exists()) {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/*"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share"))
                            }
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(RgsIcons.Share, null, tint = Color.White, modifier = Modifier.size(26.dp))
                        Text("Share", fontSize = 10.sp, color = Color.White)
                    }
                    Column(
                        Modifier.clickable { lightboxPhoto = null; onAnnotate(photo.path) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(RgsIcons.Edit, null, tint = Color.White, modifier = Modifier.size(26.dp))
                        Text("Edit", fontSize = 10.sp, color = Color.White)
                    }
                    Column(
                        Modifier.clickable {
                            val f = File(photo.path)
                            if (f.exists()) {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/*"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                intent.setPackage("com.whatsapp")
                                try { context.startActivity(intent) } catch (_: Exception) {
                                    context.startActivity(Intent.createChooser(intent.apply { setPackage(null) }, "Share"))
                                }
                            }
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(RgsIcons.Download, null, tint = Color.White, modifier = Modifier.size(26.dp))
                        Text("WhatsApp", fontSize = 10.sp, color = Color.White)
                    }
                }
                }   // bottom bar Column (filmstrip + actions)
            }   // root Column
        }
    }
}

@Composable
private fun AlbumCard(name: String, count: Int, cover: String?, onClick: () -> Unit) {
    Column(
        Modifier.width(110.dp).clip(RoundedCornerShape(10.dp))
            .background(NeutralSurfaceV).clickable { onClick() }.padding(4.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().height(85.dp).clip(RoundedCornerShape(8.dp)).background(NeutralOutline),
            contentAlignment = Alignment.BottomEnd,
        ) {
            if (cover != null) {
                AsyncImage(model = java.io.File(cover), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(RgsIcons.Gallery, null, tint = NeutralTextSoft, modifier = Modifier.size(28.dp).align(Alignment.Center))
            }
            Box(
                Modifier.padding(4.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(0.55f)).padding(horizontal = 5.dp, vertical = 1.dp),
            ) { Text("$count", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color.White) }
        }
        Spacer(Modifier.height(5.dp))
        Text(name, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = NeutralText, maxLines = 1, modifier = Modifier.padding(horizontal = 2.dp))
    }
}

@Composable
private fun DistChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (selected) AppYellow else NeutralSurfaceV)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.Black else NeutralTextSoft)
    }
}


@Composable
private fun PhotoCell(photo: GalleryPhoto, onPhotoClick: (GalleryPhoto) -> Unit) {
    val file = File(photo.path)
    Box(
        Modifier.fillMaxWidth().height(104.dp).clip(RoundedCornerShape(9.dp))
            .background(NeutralSurfaceV).clickable { onPhotoClick(photo) },
    ) {
        if (file.exists()) {
            AsyncImage(
                model = file,
                contentDescription = photo.shopName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            photo.status, fontSize = 8.5.sp, fontWeight = FontWeight.ExtraBold,
            color = if (photo.status == "Recce") Color.Black else Color.White,
            modifier = Modifier.align(Alignment.TopStart)
                .background(statusColor(photo.status), RoundedCornerShape(bottomEnd = 6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        Text(
            listOf(photo.shopName, photo.city).filter { it.isNotBlank() }.joinToString(" · "),
            fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.BottomStart)
                .background(Color.Black.copy(0.55f)).fillMaxWidth()
                .padding(horizontal = 5.dp, vertical = 4.dp),
            maxLines = 1,
        )
    }
}
