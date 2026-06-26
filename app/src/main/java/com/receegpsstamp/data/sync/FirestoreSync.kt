package com.receegpsstamp.data.sync

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.receegpsstamp.data.image.ImageCompressor
import com.receegpsstamp.data.local.AppProfile
import com.receegpsstamp.data.local.LocalStore
import com.receegpsstamp.data.local.ProfileStore
import com.receegpsstamp.data.model.Company
import com.receegpsstamp.data.model.Distributor
import com.receegpsstamp.data.model.Expense
import com.receegpsstamp.data.model.FuelLog
import com.receegpsstamp.data.model.InstallEntry
import com.receegpsstamp.data.model.MaintItem
import com.receegpsstamp.data.model.RecceDraft
import com.receegpsstamp.data.model.RecceEntry
import com.receegpsstamp.data.model.ServiceLog
import com.receegpsstamp.data.model.Shop
import com.receegpsstamp.data.model.Vehicle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cloud sync: recce data + profile → Firestore, photos → Firebase Storage.
 *
 * Data model: the whole local [LocalStore.Db] (+ the [ProfileStore] profile) is mirrored to
 * `users/{uid}/backup/<collection>`, one document per collection holding that list as a JSON string.
 * Splitting per collection (rather than one blob) keeps each doc well under Firestore's 1 MiB limit.
 * Overwriting the arrays each time naturally carries adds, edits AND deletes — no per-record diffing.
 *
 * Photos: the JSON only stores file paths. The actual image files are uploaded to
 * `users/{uid}/files/<relativePath>` (the path under getExternalFilesDir, which is reproducible on
 * any device for this package), tracked so each uploads once. On restore they're pulled back down
 * and the paths re-pointed to this device.
 *
 * Auto: the [init] observer pushes (debounced) on every data/profile change while signed in; an auth
 * listener auto-syncs on sign-in ([syncOnSignIn]) — a fresh device pulls everything (data + photos),
 * a device with local work pushes. Firestore offline persistence queues writes and flushes them.
 */
@Singleton
class FirestoreSync @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val localStore: LocalStore,
    private val profileStore: ProfileStore,
    private val imageCompressor: ImageCompressor,
    @ApplicationContext private val context: Context,
) {
    data class Result(val ok: Boolean, val message: String)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()

    init {
        // Any local data OR profile change (while signed in) → debounced push, so edits coalesce.
        scope.launch {
            merge(
                localStore.db.drop(1).map { },
                profileStore.profile.drop(1).map { },
            ).debounce(DEBOUNCE_MS).collect {
                if (auth.currentUser != null) { runCatching { backup() }; runCatching { pushFleet() }; runCatching { pushCatalog() } }
            }
        }
        // Sign-in (or app start while already signed in) → auto-sync this account's data.
        auth.addAuthStateListener { fb ->
            if (fb.currentUser != null) scope.launch { runCatching { syncOnSignIn() } }
        }
    }

    /**
     * Automatic sign-in sync — no user action, no buttons. A device that has no recce data yet
     * PULLS the cloud backup, so the signed-in user's data shows up on any phone. A device that
     * already has local work KEEPS it and pushes instead, so anything captured offline before
     * signing in is never overwritten.
     */
    private suspend fun syncOnSignIn() {
        val local = localStore.db.value
        val hasUserData = local.distributors.isNotEmpty() || local.shops.isNotEmpty() ||
            local.recces.isNotEmpty() || local.drafts.isNotEmpty() || local.expenses.isNotEmpty()
        if (hasUserData) backup() else restore()
        auth.currentUser?.uid?.let { pullAssignments(it); pullApprovals(it) }
        runCatching { pullFleet() }
        runCatching { pullCatalog() }
    }

    /**
     * On-demand re-pull of admin assignments + approvals (called on app resume / manual refresh) so a
     * web approval — e.g. the Installation list — reaches this phone without a cold restart. Cheap: the
     * assignment pull is gated by the doc's updatedAt, so an unchanged doc is a no-op.
     */
    fun refresh() {
        val uid = auth.currentUser?.uid ?: return
        scope.launch {
            runCatching { pullAssignments(uid) }
            runCatching { pullApprovals(uid) }
            runCatching { pullFleet() }
        }
    }

    // ── Shared project catalog (companies + distributors w/ creatives & media) — admin-managed,
    // shared across all users. MERGE (never wipes): local + shared union, shared wins on same id. ──
    @Volatile private var lastCatalogHash = 0
    private fun pushCatalog() {
        val db = localStore.db.value
        val hash = db.companies.hashCode() * 31 + db.distributors.hashCode()
        if (hash == lastCatalogHash) return
        lastCatalogHash = hash
        val col = firestore.collection("catalog")
        val now = System.currentTimeMillis()
        runCatching { col.document("companies").set(mapOf("json" to gson.toJson(db.companies), "updatedAt" to now)) }
        runCatching { col.document("distributors").set(mapOf("json" to gson.toJson(db.distributors), "updatedAt" to now)) }
    }
    private suspend fun pullCatalog() {
        try {
            val col = firestore.collection("catalog")
            val cJson = col.document("companies").get().await().getString("json")
            val dJson = col.document("distributors").get().await().getString("json")
            val sharedComp: List<Company> = parse(cJson, object : TypeToken<List<Company>>() {}.type)
            val sharedDist: List<Distributor> = parse(dJson, object : TypeToken<List<Distributor>>() {}.type)
            if (sharedComp.isEmpty() && sharedDist.isEmpty()) return
            val local = localStore.db.value
            val compMap = LinkedHashMap<String, Company>(); local.companies.forEach { compMap[it.id] = it }; sharedComp.forEach { compMap[it.id] = it }
            val distMap = LinkedHashMap<String, Distributor>(); local.distributors.forEach { distMap[it.id] = it }; sharedDist.forEach { distMap[it.id] = it }
            val comps = compMap.values.toList(); val dists = distMap.values.toList()
            localStore.setCatalog(comps, dists)
            lastCatalogHash = comps.hashCode() * 31 + dists.hashCode()
        } catch (_: Exception) { /* retried next sign-in */ }
    }

    // ── Shared company fleet (top-level `fleet` collection) — managed on web + app, one source. ──
    @Volatile private var lastFleetHash = 0
    /** Upserts every local vehicle (with its fuel/service logs embedded) to the shared `fleet` collection. */
    private fun pushFleet() {
        val db = localStore.db.value
        val hash = (db.vehicles.hashCode() * 31 + db.fuelLogs.hashCode()) * 31 + db.serviceLogs.hashCode()
        if (hash == lastFleetHash) return
        lastFleetHash = hash
        val col = firestore.collection("fleet")
        val now = System.currentTimeMillis()
        for (v in db.vehicles) {
            val doc = mapOf(
                "number" to v.number, "name" to v.name, "model" to v.model, "type" to v.type, "currentKm" to v.currentKm,
                "maintItems" to v.maintItems.map { mapOf("name" to it.name, "intervalKm" to it.intervalKm, "lastKm" to it.lastKm) },
                "insuranceExpiry" to v.insuranceExpiry, "pucExpiry" to v.pucExpiry, "fitnessExpiry" to v.fitnessExpiry,
                "make" to v.make, "year" to v.year, "chassis" to v.chassis, "engine" to v.engine, "lat" to v.lat, "lng" to v.lng,
                "fuelLogs" to db.fuelLogs.filter { it.vehicleId == v.id }.map { mapOf("id" to it.id, "date" to it.date, "odometer" to it.odometer, "litres" to it.litres, "amount" to it.amount, "note" to it.note) },
                "serviceLogs" to db.serviceLogs.filter { it.vehicleId == v.id }.map { mapOf("id" to it.id, "date" to it.date, "odometer" to it.odometer, "type" to it.type, "amount" to it.amount, "note" to it.note) },
                "updatedAt" to now,
            )
            runCatching { col.document(v.id).set(doc) }
        }
    }
    /** Pulls the shared fleet → replaces the local fleet so app + web stay in sync. */
    private suspend fun pullFleet() {
        try {
            val snap = firestore.collection("fleet").get().await()
            val vehicles = ArrayList<Vehicle>(); val fuel = ArrayList<FuelLog>(); val service = ArrayList<ServiceLog>()
            for (d in snap.documents) {
                val id = d.id
                @Suppress("UNCHECKED_CAST")
                val mi = (d.get("maintItems") as? List<Map<String, Any?>>)?.map { m ->
                    MaintItem((m["name"] as? String).orEmpty(), (m["intervalKm"] as? Number)?.toInt() ?: 0, (m["lastKm"] as? Number)?.toInt() ?: 0)
                } ?: emptyList()
                vehicles.add(
                    Vehicle(
                        id = id, number = d.getString("number").orEmpty(), name = d.getString("name").orEmpty(),
                        model = d.getString("model").orEmpty(), type = d.getString("type") ?: "Bike",
                        currentKm = (d.get("currentKm") as? Number)?.toInt() ?: 0, maintItems = mi,
                        insuranceExpiry = d.getLong("insuranceExpiry") ?: 0L, pucExpiry = d.getLong("pucExpiry") ?: 0L, fitnessExpiry = d.getLong("fitnessExpiry") ?: 0L,
                        make = d.getString("make").orEmpty(), year = (d.get("year") as? Number)?.toInt() ?: 0,
                        chassis = d.getString("chassis").orEmpty(), engine = d.getString("engine").orEmpty(),
                        lat = d.getDouble("lat") ?: 0.0, lng = d.getDouble("lng") ?: 0.0,
                    ),
                )
                @Suppress("UNCHECKED_CAST")
                (d.get("fuelLogs") as? List<Map<String, Any?>>)?.forEach { m ->
                    fuel.add(FuelLog(id = (m["id"] as? String).orEmpty(), vehicleId = id, date = (m["date"] as? Number)?.toLong() ?: 0L, odometer = (m["odometer"] as? Number)?.toInt() ?: 0, litres = (m["litres"] as? Number)?.toDouble() ?: 0.0, amount = (m["amount"] as? Number)?.toDouble() ?: 0.0, note = (m["note"] as? String).orEmpty()))
                }
                @Suppress("UNCHECKED_CAST")
                (d.get("serviceLogs") as? List<Map<String, Any?>>)?.forEach { m ->
                    service.add(ServiceLog(id = (m["id"] as? String).orEmpty(), vehicleId = id, date = (m["date"] as? Number)?.toLong() ?: 0L, odometer = (m["odometer"] as? Number)?.toInt() ?: 0, type = (m["type"] as? String) ?: "Service", amount = (m["amount"] as? Number)?.toDouble() ?: 0.0, note = (m["note"] as? String).orEmpty()))
                }
            }
            localStore.setFleet(vehicles, fuel, service)
            lastFleetHash = (vehicles.hashCode() * 31 + fuel.hashCode()) * 31 + service.hashCode()
        } catch (_: Exception) { /* retried next sign-in */ }
    }
    /** Removes a vehicle from the shared fleet (called when deleted in the app). */
    fun deleteSharedVehicle(id: String) { scope.launch { runCatching { firestore.collection("fleet").document(id).delete().await() } } }

    private fun backupCollection(uid: String) =
        firestore.collection("users").document(uid).collection("backup")

    /** Mirrors the whole local DB to Firestore as per-collection JSON docs. No-op when signed out. */
    suspend fun backup(): Result = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext Result(false, "Not signed in")
        try {
            val db = localStore.db.value
            val now = System.currentTimeMillis()
            val col = backupCollection(uid)
            firestore.runBatch { b ->
                b.set(col.document("companies"), doc(gson.toJson(db.companies), now))
                b.set(col.document("distributors"), doc(gson.toJson(db.distributors), now))
                b.set(col.document("shops"), doc(gson.toJson(db.shops), now))
                b.set(col.document("recces"), doc(gson.toJson(db.recces), now))
                b.set(col.document("drafts"), doc(gson.toJson(db.drafts), now))
                b.set(col.document("expenses"), doc(gson.toJson(db.expenses), now))
                b.set(col.document("installs"), doc(gson.toJson(db.installs), now))
                b.set(col.document("vehicles"), doc(gson.toJson(db.vehicles), now))
                b.set(col.document("fuelLogs"), doc(gson.toJson(db.fuelLogs), now))
                b.set(col.document("serviceLogs"), doc(gson.toJson(db.serviceLogs), now))
                b.set(col.document("profile"), doc(gson.toJson(profileStore.profile.value), now))
            }.await()
            backupPhotos(uid)
            Result(true, "Backed up to cloud")
        } catch (e: Exception) {
            Result(false, "Cloud backup failed: ${e.message ?: "unknown error"}")
        }
    }

    /** Pulls the cloud backup and REPLACES all local data. Returns a user-facing message. */
    suspend fun restore(): Result = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext Result(false, "Sign in to restore")
        try {
            val snap = backupCollection(uid).get().await()
            if (snap.isEmpty) return@withContext Result(false, "No cloud backup found")
            fun jsonOf(name: String): String? = snap.documents.firstOrNull { it.id == name }?.getString("json")
            val db = LocalStore.Db(
                companies = parse(jsonOf("companies"), object : TypeToken<List<Company>>() {}.type),
                distributors = parse(jsonOf("distributors"), object : TypeToken<List<Distributor>>() {}.type),
                shops = parse(jsonOf("shops"), object : TypeToken<List<Shop>>() {}.type),
                recces = parse(jsonOf("recces"), object : TypeToken<List<RecceEntry>>() {}.type),
                drafts = parse(jsonOf("drafts"), object : TypeToken<List<RecceDraft>>() {}.type),
                expenses = parse(jsonOf("expenses"), object : TypeToken<List<Expense>>() {}.type),
                installs = parse(jsonOf("installs"), object : TypeToken<List<InstallEntry>>() {}.type),
                vehicles = parse(jsonOf("vehicles"), object : TypeToken<List<Vehicle>>() {}.type),
                fuelLogs = parse(jsonOf("fuelLogs"), object : TypeToken<List<FuelLog>>() {}.type),
                serviceLogs = parse(jsonOf("serviceLogs"), object : TypeToken<List<ServiceLog>>() {}.type),
            )
            // Profile (name/mobile/email/city/state/gender) rides along in its own doc.
            jsonOf("profile")?.let { pj ->
                runCatching { gson.fromJson(pj, AppProfile::class.java) }.getOrNull()?.let { p ->
                    profileStore.update { p }
                }
            }
            // Pull each recce/draft photo back from Storage and re-point paths to this device.
            val withPhotos = restorePhotos(uid, db)
            val ok = localStore.importJson(gson.toJson(withPhotos))
            if (ok) Result(true, "Restored from cloud") else Result(false, "Restore failed — data unreadable")
        } catch (e: Exception) {
            Result(false, "Restore failed: ${e.message ?: "unknown error"}")
        }
    }

    // ── Photos → Firebase Storage (the JSON only stores paths; the files ride in Storage) ──
    private val syncedFile by lazy { File(context.filesDir, "storage_synced.json") }

    private fun loadUploaded(): MutableSet<String> = try {
        if (syncedFile.exists())
            gson.fromJson(syncedFile.readText(), Array<String>::class.java)?.toMutableSet() ?: mutableSetOf()
        else mutableSetOf()
    } catch (_: Throwable) { mutableSetOf() }

    private fun saveUploaded(set: Set<String>) {
        try { syncedFile.writeText(gson.toJson(set.toList())) } catch (_: Throwable) {}
    }

    /** A photo's path relative to getExternalFilesDir (e.g. "Photos/General/PE-ME-001_1.jpg"). */
    private fun relPath(absPath: String): String? = when {
        absPath.isBlank() -> null
        absPath.contains("/files/") -> absPath.substringAfter("/files/")
        else -> context.getExternalFilesDir(null)?.absolutePath?.let { root ->
            if (absPath.startsWith("$root/")) absPath.removePrefix("$root/") else null
        }
    }

    private fun allPhotoPaths(db: LocalStore.Db): List<String> =
        (db.recces.flatMap { it.shopPhotos + it.media.flatMap { m -> m.photos } } +
            db.drafts.flatMap { it.photos } +
            db.expenses.flatMap { it.billPhotos } +
            db.installs.flatMap { it.afterPhotos + it.beforePhotos + it.frontPhotos }).filter { it.isNotBlank() }.distinct()

    /** Uploads any not-yet-synced photo files; the synced set means each photo uploads only once. */
    private suspend fun backupPhotos(uid: String) {
        val uploaded = loadUploaded()
        var changed = false
        for (path in allPhotoPaths(localStore.db.value)) {
            val rel = relPath(path) ?: continue
            if (rel in uploaded) continue
            val file = File(path)
            if (!file.exists()) continue
            try {
                // Already-small files (bill photos ~200 KB, UPI screenshots) upload as-is; larger
                // recce photos get re-compressed (max 2560px, Q85) to keep cloud storage small.
                val upload = if (file.length() <= 500_000L) file else (compressForUpload(file) ?: file)
                storage.reference.child("users/$uid/files/$rel").putFile(Uri.fromFile(upload)).await()
                if (upload !== file) upload.delete()
                uploaded.add(rel); changed = true
            } catch (_: Exception) { /* leave untracked → retried on the next backup */ }
        }
        if (changed) saveUploaded(uploaded)
    }

    /** Re-encodes a photo to a smaller upright JPEG (max 2560px, Q85) for upload; null on failure. */
    private fun compressForUpload(file: File): File? {
        val bmp = imageCompressor.prepareImageForPdf(file, MAX_UPLOAD_PX).getOrNull() ?: return null
        val tmp = File(context.cacheDir, "up_${file.name}")
        return try {
            tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, UPLOAD_QUALITY, it) }
            tmp
        } catch (_: Throwable) {
            null
        } finally {
            bmp.recycle()
        }
    }

    /** Downloads any missing photos for the restored DB and rewrites paths to this device. */
    private suspend fun restorePhotos(uid: String, db: LocalStore.Db): LocalStore.Db {
        val root = context.getExternalFilesDir(null) ?: return db
        val seen = HashMap<String, String>()
        suspend fun fix(path: String): String {
            if (path.isBlank()) return path
            seen[path]?.let { return it }
            val rel = relPath(path) ?: return path
            val target = File(root, rel)
            if (!target.exists()) {
                try {
                    target.parentFile?.mkdirs()
                    storage.reference.child("users/$uid/files/$rel").getFile(target).await()
                } catch (_: Exception) { seen[path] = path; return path }
            }
            return target.absolutePath.also { seen[path] = it }
        }
        val recces = db.recces.map { r ->
            r.copy(
                shopPhotos = r.shopPhotos.map { fix(it) },
                media = r.media.map { m -> m.copy(photos = m.photos.map { fix(it) }) },
            )
        }
        val drafts = db.drafts.map { d -> d.copy(photos = d.photos.map { fix(it) }) }
        val expenses = db.expenses.map { e ->
            if (e.billPhotos.isEmpty()) e else e.copy(billPhotos = e.billPhotos.map { fix(it) })
        }
        val installs = db.installs.map { i ->
            if (i.afterPhotos.isEmpty() && i.beforePhotos.isEmpty() && i.frontPhotos.isEmpty()) i
            else i.copy(afterPhotos = i.afterPhotos.map { fix(it) }, beforePhotos = i.beforePhotos.map { fix(it) }, frontPhotos = i.frontPhotos.map { fix(it) })
        }
        return db.copy(recces = recces, drafts = drafts, expenses = expenses, installs = installs)
    }

    private fun doc(json: String, updatedAt: Long) = mapOf("json" to json, "updatedAt" to updatedAt)

    private fun <T> parse(json: String?, type: java.lang.reflect.Type): List<T> {
        if (json.isNullOrBlank()) return emptyList()
        return try { gson.fromJson(json, type) ?: emptyList() } catch (_: Exception) { emptyList() }
    }

    // ── Assignments — admin-assigned shops from the web dashboard, pulled & merged once per update ──
    private val assignFile by lazy { File(context.filesDir, "assignments_applied.txt") }
    private fun lastAssignmentApplied(): Long =
        try { if (assignFile.exists()) assignFile.readText().trim().toLongOrNull() ?: 0L else 0L } catch (_: Throwable) { 0L }
    private fun setLastAssignmentApplied(v: Long) { try { assignFile.writeText(v.toString()) } catch (_: Throwable) {} }

    /** Pulls admin-pushed project setups + assigned shops (web dashboard) into local data — once per update. */
    private suspend fun pullAssignments(uid: String) {
        try {
            val snap = firestore.collection("assignments").document(uid).get().await()
            if (!snap.exists()) return
            val updatedAt = snap.getLong("updatedAt") ?: 0L
            if (updatedAt <= lastAssignmentApplied()) return

            // Project setups — company (+ creatives/media) + distributor (city/contact).
            @Suppress("UNCHECKED_CAST")
            val rawSetups = snap.get("projects") as? List<Map<String, Any?>> ?: emptyList()
            val setups = rawSetups.mapNotNull { m ->
                val company = (m["company"] as? String)?.trim().orEmpty()
                if (company.isEmpty()) null
                else LocalStore.ProjectSetup(
                    company = company,
                    creatives = (m["creatives"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                    mediaTypes = (m["mediaTypes"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                    name = (m["name"] as? String).orEmpty(),
                    city = (m["city"] as? String).orEmpty(),
                    contact = (m["contact"] as? String).orEmpty(),
                )
            }
            if (setups.isNotEmpty()) localStore.applyProjectSetups(setups, uid)

            // Assigned shops → Pending.
            @Suppress("UNCHECKED_CAST")
            val raw = snap.get("shops") as? List<Map<String, Any?>> ?: emptyList()
            val assigned = raw.mapNotNull { m ->
                val name = (m["name"] as? String)?.trim().orEmpty()
                if (name.isEmpty()) null
                else LocalStore.AssignedShop(
                    name = name,
                    city = (m["city"] as? String).orEmpty(),
                    contact = (m["contact"] as? String).orEmpty(),
                    company = (m["company"] as? String).orEmpty(),
                    project = (m["project"] as? String).orEmpty(),
                )
            }
            if (assigned.isNotEmpty()) localStore.applyAssignments(assigned, uid)

            // Admin-assigned vehicle (web fleet registry).
            (snap.get("vehicle") as? Map<*, *>)?.let { v ->
                localStore.setVehicle((v["number"] as? String).orEmpty(), (v["type"] as? String).orEmpty())
            }

            // Installation list — admin-approved shops → a Pending InstallEntry to install.
            @Suppress("UNCHECKED_CAST")
            val rawInstall = snap.get("installList") as? List<Map<String, Any?>> ?: emptyList()
            val installs = rawInstall.mapNotNull { m ->
                val shopId = (m["shopId"] as? String).orEmpty()
                val recceId = (m["recceId"] as? String).orEmpty()
                if (shopId.isBlank() && recceId.isBlank()) null
                else InstallEntry(
                    recceId = recceId,
                    mediaIndex = (m["mediaIndex"] as? Number)?.toInt() ?: 0,
                    shopId = shopId,
                    distributorId = (m["distributorId"] as? String).orEmpty(),
                    project = (m["project"] as? String).orEmpty(),
                    shopName = (m["shopName"] as? String).orEmpty(),
                    city = (m["city"] as? String).orEmpty(),
                    mediaType = (m["mediaType"] as? String).orEmpty(),
                    size = (m["size"] as? String).orEmpty(),
                    status = "Pending",
                    assignedUid = (m["assignedUid"] as? String).orEmpty(),
                )
            }
            if (installs.isNotEmpty()) localStore.addInstalls(installs, uid)

            setLastAssignmentApplied(updatedAt)
        } catch (_: Exception) { /* retried on next sync */ }
    }

    /** Pulls manager-set expense approvals (web dashboard) so the surveyor sees Approved/Rejected status. */
    private suspend fun pullApprovals(uid: String) {
        try {
            val snap = firestore.collection("approvals").document(uid).get().await()
            if (!snap.exists()) { localStore.setApprovals(emptyMap()); return }
            @Suppress("UNCHECKED_CAST")
            val statuses = snap.get("statuses") as? Map<String, Any?> ?: emptyMap()
            val map = HashMap<String, String>()
            for ((eid, v) in statuses) {
                val state = (v as? Map<*, *>)?.get("state") as? String
                if (!state.isNullOrBlank()) map[eid] = state
            }
            localStore.setApprovals(map)
        } catch (_: Exception) { /* retried on next sign-in */ }
    }

    private companion object {
        const val DEBOUNCE_MS = 2000L
        const val MAX_UPLOAD_PX = 2560   // cap the longest side of uploaded photos
        const val UPLOAD_QUALITY = 85    // JPEG quality for cloud uploads
    }
}
