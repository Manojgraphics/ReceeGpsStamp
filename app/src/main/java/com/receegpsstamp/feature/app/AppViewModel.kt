package com.receegpsstamp.feature.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import android.content.Context
import android.net.Uri
import android.util.Log
import com.receegpsstamp.data.auth.AuthRepository
import com.receegpsstamp.data.local.LocalStore
import com.receegpsstamp.data.location.LocationProvider
import com.receegpsstamp.data.model.Company
import com.receegpsstamp.data.model.Distributor
import com.receegpsstamp.data.model.Expense
import com.receegpsstamp.data.model.InstallEntry
import com.receegpsstamp.data.model.MediaItem
import com.receegpsstamp.data.model.RecceDraft
import com.receegpsstamp.data.model.RecceEntry
import com.receegpsstamp.data.model.Shop
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class AppUiState(
    val user: FirebaseUser? = null,
    val companies: List<Company> = emptyList(),
    val distributors: List<Distributor> = emptyList(),
    val selectedDistributor: Distributor? = null,
    val selectedCompany: Company? = null,
    val shops: List<Shop> = emptyList(),
    val recceEntries: List<RecceEntry> = emptyList(),
    // In-progress drafts for the selected project.
    val drafts: List<RecceDraft> = emptyList(),
    // All expenses (Expense Manager) — the screen filters by project itself.
    val expenses: List<Expense> = emptyList(),
    // Installation entries (recce → approval → install).
    val installs: List<InstallEntry> = emptyList(),
    // Unscoped — every distributor's data, for cross-project browsing (Gallery).
    val allShops: List<Shop> = emptyList(),
    val allRecces: List<RecceEntry> = emptyList(),
    // Signed-in surveyor's profile (collected at sign-in).
    val userName: String = "",
    val userMobile: String = "",
)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val localStore: LocalStore,
    private val locationProvider: LocationProvider,
    private val reportExporter: com.receegpsstamp.data.export.ReportExporter,
    private val photoBuffer: com.receegpsstamp.data.capture.PhotoCaptureBuffer,
    private val photoNamer: com.receegpsstamp.data.capture.PhotoNamer,
    private val selectionStore: com.receegpsstamp.data.session.SelectionStore,
    private val settingsStore: com.receegpsstamp.data.local.SettingsStore,
    private val profileStore: com.receegpsstamp.data.local.ProfileStore,
    private val projectTransfer: com.receegpsstamp.data.transfer.ProjectTransfer,
    // Injected only so its cloud auto-sync (auto-backup on change + auto-restore on sign-in) starts
    // at app launch — it has no manual API to call.
    @Suppress("unused") private val firestoreSync: com.receegpsstamp.data.sync.FirestoreSync,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    // Selection lives in a @Singleton so every AppViewModel instance (one per nav destination)
    // agrees on the same company/distributor — like PhotoCaptureBuffer does for photos.
    private val _selectedCompanyId get() = selectionStore.companyId
    private val _selectedDistributorId get() = selectionStore.distributorId

    // Backed by a @Singleton so the camera screen and the recce screen — which each get a
    // distinct AppViewModel instance from hiltViewModel() — share the same buffer.
    val capturedPhotos get() = photoBuffer.photos

    /** Live app settings — screens read WhatsApp-share & other prefs from here. */
    val settings = settingsStore.settings

    // Single offline source of truth: the local store + the current selection.
    val state: StateFlow<AppUiState> = combine(
        authRepo.authState,
        localStore.db,
        _selectedCompanyId,
        _selectedDistributorId,
        profileStore.profile,
    ) { user, db, selCompId, selDistId, profile ->
        val compList = db.companies
        val dists = db.distributors

        val company = if (selCompId != null) compList.find { it.id == selCompId }
            else if (!selectionStore.hasAutoSelectedCompany && compList.isNotEmpty()) {
                selectionStore.hasAutoSelectedCompany = true
                compList.first().also { _selectedCompanyId.value = it.id }
            } else null

        val companyDists = if (company != null) dists.filter { it.companyId == company.id } else dists
        val selected = if (selDistId != null) companyDists.find { it.id == selDistId }
            else if (!selectionStore.hasAutoSelectedDistributor && companyDists.isNotEmpty()) {
                selectionStore.hasAutoSelectedDistributor = true
                companyDists.first().also { _selectedDistributorId.value = it.id }
            } else null

        val shopList = if (selected != null) db.shops.filter { it.distributorId == selected.id } else emptyList()
        val recceList = if (selected != null) db.recces.filter { it.distributorId == selected.id } else emptyList()
        val draftList = if (selected != null) db.drafts.filter { it.distributorId == selected.id } else emptyList()

        AppUiState(
            user = user,
            companies = compList,
            distributors = dists,
            selectedDistributor = selected,
            selectedCompany = company,
            shops = shopList,
            recceEntries = recceList,
            drafts = draftList,
            allShops = db.shops,
            allRecces = db.recces,
            expenses = db.expenses,
            installs = db.installs,
            userName = profile.fullName.ifBlank { user?.displayName ?: "" },
            userMobile = profile.mobile,
        )
    }.catch { emit(AppUiState()) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppUiState())

    // Fleet maintenance moved to FleetViewModel; Expense Manager moved to ExpenseViewModel.

    // ── Installation ──
    fun markInstallStarted(id: String) {
        state.value.installs.find { it.id == id }?.let { localStore.updateInstall(it.copy(status = "InProgress", startedAt = System.currentTimeMillis())) }
    }
    fun markInstalled(id: String) {
        state.value.installs.find { it.id == id }?.let { localStore.updateInstall(it.copy(status = "Installed", installedAt = System.currentTimeMillis())) }
        // Stamp where it was installed (web map shows install points) — current GPS.
        viewModelScope.launch {
            val gps = runCatching { locationProvider.getCurrentLocation() }.getOrNull() ?: return@launch
            localStore.db.value.installs.find { it.id == id }?.let { localStore.updateInstall(it.copy(lat = gps.lat, lng = gps.lng, address = gps.address)) }
        }
    }
    fun markInstallNotDone(id: String, reason: String) {
        state.value.installs.find { it.id == id }?.let { localStore.updateInstall(it.copy(status = "NotDone", reason = reason)) }
    }
    /** Reopen an Installed/NotDone size to fix a mistake — keeps photos, clears the reason. */
    fun reopenInstall(id: String) {
        state.value.installs.find { it.id == id }?.let {
            localStore.updateInstall(it.copy(status = if (it.afterPhotos.isNotEmpty()) "InProgress" else "Pending", reason = ""))
        }
    }
    fun addInstallAfterPhoto(id: String, path: String) {
        state.value.installs.find { it.id == id }?.let { localStore.updateInstall(it.copy(afterPhotos = it.afterPhotos + path)) }
    }
    fun addInstallBeforePhoto(id: String, path: String) {
        state.value.installs.find { it.id == id }?.let { localStore.updateInstall(it.copy(beforePhotos = it.beforePhotos + path)) }
    }
    fun addInstallFrontPhoto(id: String, path: String) {
        state.value.installs.find { it.id == id }?.let { localStore.updateInstall(it.copy(frontPhotos = it.frontPhotos + path)) }
    }
    /** Re-pull admin approvals/assignments (app resume or manual refresh) so web approvals arrive live. */
    fun syncNow() { firestoreSync.refresh() }

    fun selectCompany(id: String) {
        _selectedCompanyId.value = id
        _selectedDistributorId.value = null
        selectionStore.hasAutoSelectedDistributor = false
    }

    fun selectDistributor(id: String) {
        _selectedDistributorId.value = id
    }

    /** Open a specific project (distributor) — sets both its company and itself as the selection. */
    fun openProject(companyId: String, distributorId: String) {
        _selectedCompanyId.value = companyId
        selectionStore.hasAutoSelectedCompany = true
        _selectedDistributorId.value = distributorId
        selectionStore.hasAutoSelectedDistributor = true
    }

    fun addCompany(name: String) {
        localStore.addCompany(Company(name = name, userId = userId))
    }

    fun addDistributor(name: String, city: String, companyId: String, companyName: String, contact: String) {
        val id = localStore.addDistributor(
            Distributor(name = name, city = city, contact = contact, companyId = companyId, companyName = companyName, userId = userId),
        )
        _selectedDistributorId.value = id
    }

    fun updateCompany(company: Company) = localStore.updateCompany(company)
    fun updateDistributor(distributor: Distributor) = localStore.updateDistributor(distributor)

    /** Remembers the last-picked creative as this project's sticky default (auto-fills new media). */
    fun setProjectCreative(creative: String) {
        val d = state.value.selectedDistributor ?: return
        if (d.defaultCreative == creative) return
        localStore.updateDistributor(d.copy(defaultCreative = creative))
    }

    fun addCreative(name: String) {
        val c = state.value.selectedCompany ?: return
        localStore.updateCompany(c.copy(creatives = c.creatives + name))
    }

    fun removeCreative(name: String) {
        val c = state.value.selectedCompany ?: return
        localStore.updateCompany(c.copy(creatives = c.creatives - name))
    }

    fun addMediaType(name: String) {
        val c = state.value.selectedCompany ?: return
        localStore.updateCompany(c.copy(mediaTypes = c.mediaTypes + name))
    }

    fun removeMediaType(name: String) {
        val c = state.value.selectedCompany ?: return
        localStore.updateCompany(c.copy(mediaTypes = c.mediaTypes - name))
    }

    fun addCapturedPhoto(path: String) {
        capturedPhotos.add(path)
    }

    /** Removes a just-captured photo from the buffer and deletes its file (thumbnail delete in the form). */
    fun removeCapturedPhoto(path: String) {
        capturedPhotos.remove(path)
        try { java.io.File(path).delete() } catch (_: Throwable) { /* best-effort */ }
    }

    fun clearCapturedPhotos() {
        capturedPhotos.clear()
    }

    /** Restores a draft's photos into the capture buffer when a draft is reopened. */
    fun loadCapturedPhotos(paths: List<String>) {
        capturedPhotos.clear()
        capturedPhotos.addAll(paths)
    }

    // ── Drafts — save an in-progress recce to continue later ──

    fun saveDraft(draft: RecceDraft) {
        if (distId.isEmpty()) return  // silent — auto-save on background; the UI confirms a manual save
        localStore.saveDraft(draft.copy(distributorId = distId))
    }

    /** Removes a draft. [deletePhotos] = true on discard (frees storage); false after finalising. */
    fun deleteDraft(id: String, deletePhotos: Boolean) {
        if (deletePhotos) {
            val d = state.value.drafts.firstOrNull { it.id == id }
            d?.photos?.distinct()?.forEach { try { java.io.File(it).delete() } catch (_: Throwable) {} }
        }
        localStore.deleteDraft(id)
    }

    private val userId: String get() = state.value.user?.uid ?: "local"
    private val distId: String get() = state.value.selectedDistributor?.id ?: ""

    // Transient one-off messages surfaced as a toast by the UI.
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()
    private fun notify(msg: String) { _messages.tryEmit(msg) }

    private val safe = kotlinx.coroutines.CoroutineExceptionHandler { _, e ->
        Log.e("AppViewModel", "action failed", e)
        notify("Something went wrong")
    }

    /** Next permanent shop code for the current distributor, e.g. "PE-ME-003". */
    private fun nextShopCode(): String {
        val s = state.value
        val cc = com.receegpsstamp.data.util.code2(s.selectedCompany?.name ?: "")
        val dc = com.receegpsstamp.data.util.code2(s.selectedDistributor?.name ?: "")
        val coded = s.shops.count { it.code.isNotBlank() }
        return "%s-%s-%03d".format(cc, dc, coded + 1)
    }

    /** Manually add a shop to the current project as a Pending (to-visit) entry — for the work-list. */
    fun addShop(name: String, city: String, contact: String) {
        if (distId.isEmpty()) { notify("Select a distributor first"); return }
        val n = name.trim()
        if (n.isEmpty()) { notify("Enter a shop name"); return }
        if (state.value.shops.any { it.name.trim().equals(n, ignoreCase = true) }) {
            notify("\"$n\" already in this project"); return
        }
        localStore.addShop(Shop(name = n, city = city.trim(), contact = contact.trim(), status = "Pending", distributorId = distId, userId = userId))
        notify("Shop added")
    }

    /** Work-list quick action — mark a shop "Skipped" (out of pending) or back to "Pending" (revisit). */
    fun setShopStatus(shop: Shop, status: String) = localStore.updateShop(shop.copy(status = status))

    /** Bulk-import shops from pasted/CSV text — one per line, comma: Name[, City[, Contact]]. Dedupes by name. */
    fun importShops(raw: String) {
        if (distId.isEmpty()) { notify("Select a distributor first"); return }
        val existing = state.value.shops.map { it.name.trim().lowercase() }.toMutableSet()
        val toAdd = mutableListOf<Shop>()
        raw.lineSequence().forEachIndexed { i, line ->
            val parts = line.split(",").map { it.trim() }
            val name = parts.getOrNull(0).orEmpty()
            if (name.isEmpty()) return@forEachIndexed
            // Skip a header row like "Name, City, Contact".
            if (i == 0 && name.equals("name", ignoreCase = true)) return@forEachIndexed
            val key = name.lowercase()
            if (key in existing) return@forEachIndexed
            existing.add(key)
            toAdd.add(Shop(
                name = name,
                city = parts.getOrNull(1).orEmpty(),
                contact = parts.getOrNull(2)?.filter { it.isDigit() }?.take(10).orEmpty(),
                status = "Pending", distributorId = distId, userId = userId,
            ))
        }
        if (toAdd.isEmpty()) { notify("No new shops to import"); return }
        localStore.addShops(toAdd)
        notify("Imported ${toAdd.size} shop${if (toAdd.size > 1) "s" else ""}")
    }

    fun saveRecceWithNewShop(
        shopName: String, shopCity: String, shopContact: String,
        status: String, remark: String, media: List<MediaItem>,
    ) {
        if (distId.isEmpty()) { notify("Select a distributor first"); return }
        // Snapshot the captured photos NOW — the UI's resetForm() clears the buffer the moment this
        // call returns, so reading it after the coroutine suspends (location await) would lose them.
        val photos = capturedPhotos.toList()
        viewModelScope.launch(safe) {
            val fix = locationProvider.lastFix()
            // Reuse an existing master shop with the same name instead of creating a duplicate.
            val existing = state.value.shops.firstOrNull {
                it.name.trim().equals(shopName.trim(), ignoreCase = true)
            }
            val code = existing?.code?.ifBlank { nextShopCode() } ?: nextShopCode()
            val shopId = if (existing != null) {
                localStore.updateShop(existing.copy(
                    status = status, code = code,
                    city = shopCity.ifBlank { existing.city },
                    contact = shopContact.ifBlank { existing.contact },
                ))
                existing.id
            } else {
                localStore.addShop(Shop(
                    name = shopName, city = shopCity, contact = shopContact,
                    status = status, code = code, distributorId = distId, userId = userId,
                ))
            }
            // Rename photos to <shopCode>_<shopName>_<n>.jpg now that the code is known.
            val nameMap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { photoNamer.rename(photos, code, shopName) }
            val newPhotos = photos.map { nameMap[it] ?: it }
            val newMedia = media.map { m -> m.copy(photos = m.photos.map { nameMap[it] ?: it }) }
            localStore.addRecce(RecceEntry(
                shopId = shopId, status = status, remark = remark, media = newMedia,
                distributorId = distId, userId = userId, shopPhotos = newPhotos,
                lat = fix?.first ?: 0.0, lng = fix?.second ?: 0.0, address = fix?.third ?: "",
            ))
            capturedPhotos.clear()
        }
    }

    fun saveRecce(entry: RecceEntry) {
        if (distId.isEmpty()) { notify("Select a distributor first"); return }
        // Snapshot photos before the coroutine suspends — see saveRecceWithNewShop.
        val photos = capturedPhotos.toList()
        viewModelScope.launch(safe) {
            val fix = locationProvider.lastFix()
            // Assign the shop code first, then rename photos to <shopCode>_<shopName>_<n>.jpg.
            val shop = state.value.shops.firstOrNull { it.id == entry.shopId }
            val code = shop?.code?.ifBlank { nextShopCode() } ?: nextShopCode()
            val nameMap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { photoNamer.rename(photos, code, shop?.name ?: "") }
            val newPhotos = photos.map { nameMap[it] ?: it }
            val newMedia = entry.media.map { m -> m.copy(photos = m.photos.map { nameMap[it] ?: it }) }
            localStore.addRecce(entry.copy(
                distributorId = distId, userId = userId, shopPhotos = newPhotos, media = newMedia,
                lat = fix?.first ?: 0.0, lng = fix?.second ?: 0.0, address = fix?.third ?: "",
            ))
            // Master record is the source of truth: set outcome status + assign the code once.
            if (shop != null) {
                localStore.updateShop(shop.copy(status = entry.status, code = code))
            }
            capturedPhotos.clear()
        }
    }

    // ── Edit an existing recce — "Update Store Details" reopens it in the Start Work form ──

    /** Cross-screen edit request id; the Start Work form watches this and loads the recce. */
    val editRecceId: StateFlow<String?> = selectionStore.editRecceId

    /** Photo path the Annotate screen is opening. */
    val annotatePhoto: StateFlow<String?> = selectionStore.annotatePhoto
    fun startAnnotate(path: String) { selectionStore.annotatePhoto.value = path }
    fun consumeAnnotate() { selectionStore.annotatePhoto.value = null }

    /** Begins editing a recce from the Dashboard: makes its project active + loads its photos. */
    fun startEditRecce(recce: RecceEntry) {
        val dist = state.value.distributors.find { it.id == recce.distributorId } ?: return
        _selectedCompanyId.value = dist.companyId
        _selectedDistributorId.value = dist.id
        selectionStore.hasAutoSelectedCompany = true
        selectionStore.hasAutoSelectedDistributor = true
        loadCapturedPhotos((recce.shopPhotos + recce.media.flatMap { it.photos }).distinct())
        selectionStore.editRecceId.value = recce.id
    }

    /** Clears the edit request once the form has consumed it. */
    fun consumeEditRecce() { selectionStore.editRecceId.value = null }

    /** Saves changes to an existing recce (from the edit flow) instead of creating a new one. */
    fun updateRecce(recceId: String, status: String, remark: String, media: List<MediaItem>) {
        val photos = capturedPhotos.toList()
        viewModelScope.launch(safe) {
            val existing = state.value.allRecces.find { it.id == recceId } ?: run { notify("Store not found"); return@launch }
            localStore.updateRecce(existing.copy(status = status, remark = remark, media = media, shopPhotos = photos))
            state.value.allShops.firstOrNull { it.id == existing.shopId }?.let {
                localStore.updateShop(it.copy(status = status))
            }
            capturedPhotos.clear()
            selectionStore.editRecceId.value = null
            notify("Store updated")
        }
    }

    // Expense Manager moved to ExpenseViewModel (see feature/expense/ExpenseViewModel.kt).
    // AppUiState keeps `expenses` only because the Dashboard summary still reads it.

    // ── Share / export individual stores (from the Retail Stores list) ──

    /** A share request the UI turns into an intent (it owns the Context + FileProvider). */
    data class ShareReq(val text: String, val photoPaths: List<String>)
    // Channel (not SharedFlow) so a request fired while the screen is recreating is buffered and still
    // delivered to the next collector — SharedFlow with replay=0 silently drops it.
    private val _shareReq = Channel<ShareReq>(Channel.BUFFERED)
    val shareReq: Flow<ShareReq> = _shareReq.receiveAsFlow()

    /** Share one store's details — text + its photos. */
    fun shareStore(recceId: String) {
        val r = state.value.allRecces.find { it.id == recceId } ?: return
        _shareReq.trySend(ShareReq(buildStoreText(listOf(r)), (r.shopPhotos + r.media.flatMap { it.photos }).distinct()))
    }

    /** Share a group of stores as ONE WhatsApp message — combined details + ALL their photos. */
    fun shareStoresGroup(recceIds: List<String>) {
        val recces = state.value.allRecces.filter { it.id in recceIds }
        if (recces.isEmpty()) { notify("Nothing selected"); return }
        val photos = recces.flatMap { it.shopPhotos + it.media.flatMap { m -> m.photos } }.distinct()
        _shareReq.trySend(ShareReq(buildStoreText(recces), photos))
    }

    /** Share several stores' details together — text only (too many photos to attach). */
    fun shareStores(recceIds: List<String>) {
        val recces = state.value.allRecces.filter { it.id in recceIds }
        if (recces.isEmpty()) { notify("Nothing selected"); return }
        _shareReq.trySend(ShareReq(buildStoreText(recces), emptyList()))
    }

    /** Share one shop's installation proof — text (per-size status) + before/after photos (like a recce share). */
    fun shareInstallShop(shopKey: String) {
        val s = state.value
        val entries = s.installs.filter { (it.shopId.ifBlank { it.shopName }) == shopKey }.sortedBy { it.mediaIndex }
        if (entries.isEmpty()) { notify("Nothing to share"); return }
        val front = entries.firstOrNull()?.frontPhotos.orEmpty()
        val photos = (front + entries.flatMap { e ->
            val recce = s.allRecces.find { it.id == e.recceId }
            val before = recce?.media?.getOrNull(e.mediaIndex)?.photos.orEmpty().ifEmpty { recce?.shopPhotos.orEmpty() }
            before + e.beforePhotos + e.afterPhotos
        }).filter { it.isNotBlank() }.distinct()
        _shareReq.trySend(ShareReq(buildInstallText(entries), photos))
    }

    /** WhatsApp-style text summary for one shop's installation (all its sizes). */
    private fun buildInstallText(entries: List<InstallEntry>): String {
        val s = state.value
        val head = entries.first()
        val dist = s.distributors.find { it.id == head.distributorId }
        val co = s.companies.find { it.id == dist?.companyId }?.name ?: dist?.companyName ?: head.project
        return buildString {
            val coDist = listOf(co, dist?.name ?: head.project).filter { it.isNotBlank() }.distinct().joinToString(" · ")
            if (coDist.isNotBlank()) appendLine(coDist)
            appendLine("Installation — ${head.shopName.ifBlank { "Shop" }}")
            if (head.city.isNotBlank()) appendLine("City: ${head.city}")
            appendLine()
            entries.forEach { e ->
                val size = listOf(e.mediaType, e.size).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Media" }
                val status = when (e.status) {
                    "Installed" -> "Installed ✅"
                    "NotDone" -> "Not done — ${e.reason.ifBlank { "—" }}"
                    "InProgress" -> "In progress"
                    else -> "Pending"
                }
                appendLine("• $size — $status")
            }
        }
    }

    private val PPTX_MIME = "application/vnd.openxmlformats-officedocument.presentationml.presentation"

    fun exportStoresPdf(recceIds: List<String>) =
        exportStores(recceIds, "application/pdf") { co, dist, city, shops, recces -> reportExporter.exportPdf(co, dist, city, nextExportSerial(), shops, recces) }

    fun exportStoresExcel(recceIds: List<String>) =
        exportStores(recceIds, "text/csv") { co, dist, city, shops, recces -> reportExporter.exportCsv(co, dist, city, nextExportSerial(), shops, recces) }

    fun exportStoresPptx(recceIds: List<String>) =
        exportStores(recceIds, PPTX_MIME) { co, dist, city, shops, recces -> reportExporter.exportPptx(co, dist, city, nextExportSerial(), shops, recces) }

    /** Next running serial for an Excel report filename — bumped & persisted on every export. */
    private fun nextExportSerial(): Int {
        val n = settingsStore.settings.value.exportSerial + 1
        settingsStore.update { it.copy(exportSerial = n) }
        return n
    }

    private fun exportStores(
        recceIds: List<String>,
        mime: String,
        block: (company: String, distributor: String, city: String, shops: List<Shop>, recces: List<RecceEntry>) -> Uri,
    ) {
        val snap = state.value
        val recces = snap.allRecces.filter { it.id in recceIds }
        if (recces.isEmpty()) { notify("Nothing selected"); return }
        val dist = snap.distributors.find { it.id == recces.first().distributorId }
        val shops = snap.allShops.filter { s -> recces.any { it.shopId == s.id } }
        val coName = snap.companies.find { it.id == dist?.companyId }?.name ?: dist?.companyName ?: ""
        notify("Generating report…")
        viewModelScope.launch(safe) {
            val uri = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { block(coName, dist?.name ?: "", dist?.city ?: "", shops, recces) }
            _shareFile.trySend(uri to mime)
        }
    }

    private fun dim(d: Double): String = if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()

    /** WhatsApp-style text summary for one or more stores. */
    private fun buildStoreText(recces: List<RecceEntry>): String {
        val s = state.value
        return buildString {
            recces.forEachIndexed { i, r ->
                val shop = s.allShops.find { it.id == r.shopId }
                val dist = s.distributors.find { it.id == r.distributorId }
                val co = s.companies.find { it.id == dist?.companyId }?.name ?: dist?.companyName ?: ""
                if (i > 0) appendLine()
                val coDist = listOf(co, dist?.name ?: "").filter { it.isNotBlank() }.joinToString(" · ")
                if (coDist.isNotBlank()) appendLine(coDist)
                appendLine("Shop: ${shop?.name ?: "Shop"}")
                if (!shop?.contact.isNullOrBlank()) appendLine("Contact: ${shop?.contact}")
                if (!shop?.city.isNullOrBlank()) appendLine("City: ${shop?.city}")
                appendLine("Status: ${r.status}")
                r.media.forEachIndexed { idx, m ->
                    val label = if (r.media.size > 1) "Media ${idx + 1}" else "Media"
                    val sizeStr = if (m.width > 0 || m.height > 0) "${dim(m.width)} x ${dim(m.height)} ${m.unit}" else ""
                    val parts = listOf(
                        m.type, m.creative, sizeStr, if (m.qty > 0) "Qty ${m.qty}" else "",
                        m.remark, if (m.photos.isNotEmpty()) "Photos ${m.photos.size}" else "",
                    ).filter { it.isNotBlank() }
                    appendLine("$label: ${parts.joinToString(" · ")}")
                }
                if (r.remark.isNotBlank()) appendLine("Remark: ${r.remark}")
                if (r.lat != 0.0 || r.lng != 0.0) {
                    // Latitude, longitude & the tappable Google Maps link — matches the Recce-form share.
                    appendLine("Location: ${"%.6f".format(r.lat)}, ${"%.6f".format(r.lng)} · https://maps.google.com/?q=${"%.6f".format(r.lat)},${"%.6f".format(r.lng)}")
                }
                appendLine("Photos: ${r.shopPhotos.size + r.media.sumOf { it.photos.size }}")
            }
        }.trim()
    }

    // ── Per-project actions — driven from the multi-project Dashboard, scoped by distributorId ──

    /** Toggle a project's completion (done ⇄ reopen) without needing it to be the selected one. */
    /** Advance/reset a project's lifecycle stage — "" (Active) | "RecceDone" | "InstallDone". */
    fun setProjectStage(distributorId: String, stage: String) {
        val d = state.value.distributors.find { it.id == distributorId } ?: return
        localStore.updateDistributor(d.copy(stage = stage, completedAt = if (stage == "InstallDone") System.currentTimeMillis() else 0L))
        notify(when (stage) { "RecceDone" -> "Recce marked done"; "InstallDone" -> "Installation done — project complete"; else -> "Project reopened" })
    }

    /** Deletes the photo files of a project's recces & clears the paths — frees storage, keeps records. */
    fun removeProjectPhotos(distributorId: String) {
        val recces = state.value.allRecces.filter { it.distributorId == distributorId }
        viewModelScope.launch(safe) {
            val deleted = recces.flatMap { it.shopPhotos + it.media.flatMap { m -> m.photos } }.distinct().count {
                try { java.io.File(it).delete() } catch (_: Throwable) { false }
            }
            localStore.clearDistributorPhotos(distributorId)
            notify("$deleted photos freed · records kept")
        }
    }

    /** Permanently removes an entire project — distributor, shops, recces and all photo files. */
    fun deleteProject(distributorId: String) {
        val s = state.value
        val recces = s.allRecces.filter { it.distributorId == distributorId }
        viewModelScope.launch(safe) {
            recces.flatMap { it.shopPhotos + it.media.flatMap { m -> m.photos } }.distinct().forEach {
                try { java.io.File(it).delete() } catch (_: Throwable) { /* best-effort */ }
            }
            // Drop the selection if we're deleting the active project, so the UI doesn't dangle.
            if (_selectedDistributorId.value == distributorId) {
                _selectedDistributorId.value = null
                selectionStore.hasAutoSelectedDistributor = false
            }
            localStore.deleteDistributor(distributorId)
            notify("Project deleted")
        }
    }

    /** Permanently removes a company plus every project (distributor), shop, recce & photo under it. */
    fun deleteCompany(companyId: String) {
        val s = state.value
        val distIds = s.distributors.filter { it.companyId == companyId }.map { it.id }.toSet()
        viewModelScope.launch(safe) {
            s.allRecces.filter { it.distributorId in distIds }
                .flatMap { it.shopPhotos + it.media.flatMap { m -> m.photos } }.distinct()
                .forEach { try { java.io.File(it).delete() } catch (_: Throwable) { /* best-effort */ } }
            if (_selectedCompanyId.value == companyId) {
                _selectedCompanyId.value = null
                selectionStore.hasAutoSelectedCompany = false
                _selectedDistributorId.value = null
                selectionStore.hasAutoSelectedDistributor = false
            }
            localStore.deleteCompany(companyId)
            notify("Company deleted")
        }
    }

    fun exportProjectPdf(distributorId: String) =
        exportProject(distributorId, "application/pdf") { co, dist, city, shops, recces ->
            reportExporter.exportPdf(co, dist, city, nextExportSerial(), shops, recces)
        }

    fun exportProjectExcel(distributorId: String) =
        exportProject(distributorId, "text/csv") { co, dist, city, shops, recces ->
            reportExporter.exportCsv(co, dist, city, nextExportSerial(), shops, recces)
        }

    fun exportProjectPptx(distributorId: String) =
        exportProject(distributorId, PPTX_MIME) { co, dist, city, shops, recces ->
            reportExporter.exportPptx(co, dist, city, nextExportSerial(), shops, recces)
        }

    private fun exportProject(
        distributorId: String,
        mime: String,
        block: (company: String, distributor: String, city: String, shops: List<Shop>, recces: List<RecceEntry>) -> Uri,
    ) {
        val snap = state.value
        val dist = snap.distributors.find { it.id == distributorId } ?: run { notify("Project not found"); return }
        val shops = snap.allShops.filter { it.distributorId == distributorId }
        val recces = snap.allRecces.filter { it.distributorId == distributorId }
        if (recces.isEmpty()) { notify("Nothing to export yet"); return }
        val coName = snap.companies.find { it.id == dist.companyId }?.name ?: dist.companyName
        notify("Generating report…")
        viewModelScope.launch(safe) {
            val uri = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { block(coName, dist.name, dist.city, shops, recces) }
            _shareFile.trySend(uri to mime)
        }
    }

    /** Imports a .rgsproj file picked from another device — recreates the whole project locally. */
    fun importProjectFile(uri: Uri) {
        notify("Importing project…")
        viewModelScope.launch(safe) {
            val name = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { projectTransfer.importProject(uri) }
            notify(if (name != null) "Imported project: $name" else "Import failed — invalid file")
        }
    }

    fun fetchLocation(onResult: (city: String, lat: Double, lng: Double) -> Unit) {
        viewModelScope.launch(safe) {
            val gps = locationProvider.getCurrentLocation()
            if (gps != null) {
                val city = gps.city.ifEmpty { "${"%.4f".format(gps.lat)}, ${"%.4f".format(gps.lng)}" }
                onResult(city, gps.lat, gps.lng)
            }
        }
    }

    // ── Professional exports — generated on a background thread (heavy: photo decode + PDF) ──
    // Emits (file Uri, mime) when a report is ready; the UI shows the share sheet. Channel (not
    // SharedFlow) so the event survives a screen recreation between generation and collection.
    private val _shareFile = Channel<Pair<Uri, String>>(Channel.BUFFERED)
    val shareFile: Flow<Pair<Uri, String>> = _shareFile.receiveAsFlow()
}
