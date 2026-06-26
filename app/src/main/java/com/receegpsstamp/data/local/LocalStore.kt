package com.receegpsstamp.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.receegpsstamp.data.model.Company
import com.receegpsstamp.data.model.Distributor
import com.receegpsstamp.data.model.Expense
import com.receegpsstamp.data.model.FuelLog
import com.receegpsstamp.data.model.InstallEntry
import com.receegpsstamp.data.model.RecceDraft
import com.receegpsstamp.data.model.RecceEntry
import com.receegpsstamp.data.model.ServiceLog
import com.receegpsstamp.data.model.Shop
import com.receegpsstamp.data.model.Vehicle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Bump when the on-disk JSON shape changes; [LocalStore] then backs up + migrates older files on load. */
private const val SCHEMA_VERSION = 1

/**
 * Offline-first local data store. All app data lives in one JSON file on the device — no
 * network, no Firebase. Exposed as a single reactive [StateFlow]; every mutation persists
 * atomically (write temp + rename) so a crash mid-write can't corrupt the file.
 */
@Singleton
class LocalStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class Db(
        val companies: List<Company> = emptyList(),
        val distributors: List<Distributor> = emptyList(),
        val shops: List<Shop> = emptyList(),
        val recces: List<RecceEntry> = emptyList(),
        val drafts: List<RecceDraft> = emptyList(),
        val expenses: List<Expense> = emptyList(),
        // Installation entries — field installs of approved media (recce = before, install = after).
        val installs: List<InstallEntry> = emptyList(),
        // Fleet maintenance — company vehicles + their fuel/service logs (backed up & synced).
        val vehicles: List<Vehicle> = emptyList(),
        val fuelLogs: List<FuelLog> = emptyList(),
        val serviceLogs: List<ServiceLog> = emptyList(),
        // Manager-set expense approval status from the web dashboard (expenseId → "approved"/"rejected").
        // Pulled by FirestoreSync; NOT backed up (manager owns it).
        val approvals: Map<String, String> = emptyMap(),
        // Admin-assigned vehicle from the web fleet registry. Pulled by FirestoreSync; NOT backed up.
        val vehicleNumber: String = "",
        val vehicleType: String = "",
        // On-disk schema version (see [SCHEMA_VERSION]) — lets load() detect & migrate older files.
        val schemaVersion: Int = SCHEMA_VERSION,
    )

    /** A shop assigned to this surveyor from the web admin dashboard (pulled by FirestoreSync). */
    data class AssignedShop(
        val name: String, val city: String = "", val contact: String = "",
        val company: String = "", val project: String = "",
    )

    /** A project setup pushed from the web admin dashboard — company config + distributor details. */
    data class ProjectSetup(
        val company: String,
        val creatives: List<String> = emptyList(),
        val mediaTypes: List<String> = emptyList(),
        val name: String = "", val city: String = "", val contact: String = "",
    )

    private val file = File(context.filesDir, "rgs_data.json")
    private val gson = Gson()
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    // Seeded companies with their default creatives (brand options).
    // NOTE: declared before _db so it's initialised before load() runs.
    private val seedCreatives = mapOf(
        "Dollar" to listOf("BigBoss", "Missy", "Laher", "NXT"),
        "LUX ONN" to listOf("LUX", "ONN", "PYNK"),
        "SHEETAL" to listOf("SHEETAL", "JADOR"),
        "KHUSHBOO" to listOf("KHUSHBOO"),
        "OPPO" to listOf("F Series", "Reno Series", "FindX Series"),
    )

    // Default media types per company.
    private val seedMediaTypes = mapOf(
        "Dollar" to listOf("Frontlite", "Glow sign", "Standee", "Vinyl Sunboard", "Onway", "Only vinyl"),
        "SHEETAL" to listOf("Frontlite", "Glowsign", "Standee", "Only vinyl", "Vinyl Sunboard"),
        "OPPO" to listOf(
            "Vinyl Sunboard", "Onway", "Only Vinyl", "Tranlite", "Only Febric",
            "Cutout Standee", "Nonlite", "Fabric Board", "ClipOnn",
        ),
    )

    private val _db = MutableStateFlow(load())
    val db: StateFlow<Db> = _db.asStateFlow()

    /**
     * Loads the local DB. A corrupt/unreadable file is quarantined (NOT silently wiped) and a fresh
     * seeded DB is returned instead; on a schema upgrade the previous file is copied to rgs_data.bak
     * before the migrated version is written. The result is always re-stamped with [SCHEMA_VERSION].
     */
    private fun load(): Db {
        val raw = if (file.exists()) runCatching { file.readText() }.getOrNull() else null

        val parsed: Db = if (raw.isNullOrBlank()) {
            Db()
        } else {
            // Read the version straight from the raw JSON (absent = 0 = pre-versioning) BEFORE
            // deserializing, so we can back up once on an upgrade regardless of how Gson fills the field.
            val onDiskVersion = runCatching {
                gson.fromJson(raw, JsonObject::class.java)?.get("schemaVersion")?.asInt
            }.getOrNull() ?: 0
            val p = try {
                gson.fromJson(raw, Db::class.java)
            } catch (_: Throwable) {
                quarantine(raw); null
            }
            if (p == null) {
                Db()
            } else {
                if (onDiskVersion < SCHEMA_VERSION) {
                    // One-time rollback copy before any migration is persisted.
                    runCatching { file.copyTo(File(file.parentFile, "rgs_data.bak"), overwrite = true) }
                    // Future migrations: transform `p` here based on onDiskVersion before it's re-persisted.
                }
                p
            }
        }

        // Gson allocates without applying Kotlin defaults, so a field added in a LATER version is
        // null in OLDER files — which would NPE the combine. Coerce the top-level collections back
        // to empty before anything reads them.
        val safe = normalize(parsed)
        // Seeding must never take down startup — fall back to the parsed data if it throws.
        val seeded = runCatching { seed(safe) }.getOrDefault(safe)
        val next = seeded.copy(schemaVersion = SCHEMA_VERSION)
        if (next != parsed) persist(next)   // first run, seeding, recovery, null-fix, or a version bump
        return next
    }

    /** Rescues any null top-level collections/strings (Gson omits-missing → null) back to defaults. */
    private fun normalize(db: Db): Db = db.copy(
        companies = db.companies.orEmpty(),
        distributors = db.distributors.orEmpty(),
        shops = db.shops.orEmpty(),
        recces = db.recces.orEmpty(),
        drafts = db.drafts.orEmpty(),
        expenses = db.expenses.orEmpty(),
        installs = db.installs.orEmpty(),
        vehicles = db.vehicles.orEmpty(),
        fuelLogs = db.fuelLogs.orEmpty(),
        serviceLogs = db.serviceLogs.orEmpty(),
        approvals = db.approvals.orEmpty(),
        vehicleNumber = db.vehicleNumber.orEmpty(),
        vehicleType = db.vehicleType.orEmpty(),
    )

    /** Adds any missing seed companies and backfills their default creatives / media types. */
    private fun seed(db: Db): Db {
        val names = db.companies.map { it.name }.toSet()
        // Backfill default creatives / media types for existing seed companies that still have none.
        val existing = db.companies.map { c ->
            var u = c
            seedCreatives[c.name]?.let { if (it.isNotEmpty() && u.creatives.isEmpty()) u = u.copy(creatives = it) }
            seedMediaTypes[c.name]?.let { if (it.isNotEmpty() && u.mediaTypes.isEmpty()) u = u.copy(mediaTypes = it) }
            u
        }
        // Add any seed companies that aren't present yet, so they're always pre-filled.
        val missing = seedCreatives.filterKeys { it !in names }.map { (name, creatives) ->
            Company(
                id = UUID.randomUUID().toString(), name = name,
                creatives = creatives, mediaTypes = seedMediaTypes[name] ?: emptyList(),
                createdAt = System.currentTimeMillis(),
            )
        }
        return db.copy(companies = existing + missing)
    }

    /** Preserves an unreadable data file (instead of silently starting blank) so it can be recovered. */
    private fun quarantine(raw: String) {
        runCatching { File(file.parentFile, "rgs_data.corrupt.json").writeText(raw) }
    }

    // Serializes the read-modify-write below. Mutations arrive from the UI thread AND FirestoreSync's
    // background cloud pulls; without this lock two concurrent mutate() calls race and one is lost.
    private val mutationLock = Any()

    private fun mutate(transform: (Db) -> Db) {
        val next: Db
        synchronized(mutationLock) {
            next = transform(_db.value)  // read-modify-write, atomic against other mutate() callers
            _db.value = next             // instant in-memory update → UI reacts immediately
        }
        ioScope.launch { persist(next) } // disk write off the main thread (no UI freeze), outside the lock
    }

    private fun persist(data: Db) {
        try {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(gson.toJson(data))
            // renameTo() returns false (no exception) on failure — would lose the write silently.
            // Fall back to copy+delete so the freshly-written temp file still lands.
            if (!tmp.renameTo(file)) { tmp.copyTo(file, overwrite = true); tmp.delete() }
        } catch (t: Throwable) {
            // Never fail silently: a failed save means the user THINKS data is saved but it isn't.
            recordPersistFailure(t)
        }
    }

    /** Records a persist failure to Logcat + a shareable diagnostics file (no Crashlytics on this app). */
    private fun recordPersistFailure(t: Throwable) {
        android.util.Log.e("LocalStore", "Failed to persist local data", t)
        runCatching {
            val dir = File(context.getExternalFilesDir(null), "crash").apply { mkdirs() }
            File(dir, "persist_error.txt").writeText(
                "Last persist failure: ${java.util.Date()}\n\n" + android.util.Log.getStackTraceString(t),
            )
        }
    }

    /** The raw data file (whole local DB) — used for cloud backup and project transfer. */
    fun dataFile(): File = file

    /** Replaces all local data with a restored JSON backup. Returns false if the JSON is invalid. */
    fun importJson(json: String): Boolean = try {
        val restored = gson.fromJson(json, Db::class.java)
        if (restored == null) false
        else {
            persist(restored)                                    // write to disk first
            val reloaded = load()                                // re-applies seeding/backfill (no _db access)
            synchronized(mutationLock) { _db.value = reloaded }  // atomic swap vs a concurrent mutate()
            true
        }
    } catch (_: Throwable) { false }

    private fun newId() = UUID.randomUUID().toString()
    private fun now() = System.currentTimeMillis()

    fun addCompany(c: Company): String {
        val id = newId()
        mutate { it.copy(companies = it.companies + c.copy(id = id, createdAt = now())) }
        return id
    }

    fun updateCompany(c: Company) =
        mutate { it.copy(companies = it.companies.map { x -> if (x.id == c.id) c else x }) }

    fun addDistributor(d: Distributor): String {
        val id = newId()
        mutate { it.copy(distributors = it.distributors + d.copy(id = id, createdAt = now())) }
        return id
    }

    fun updateDistributor(d: Distributor) =
        mutate { it.copy(distributors = it.distributors.map { x -> if (x.id == d.id) d else x }) }

    fun addShop(s: Shop): String {
        val id = newId()
        mutate { it.copy(shops = it.shops + s.copy(id = id, createdAt = now())) }
        return id
    }

    /** Bulk-add shops (used by the work-list importer). */
    fun addShops(list: List<Shop>) {
        if (list.isEmpty()) return
        mutate { db -> db.copy(shops = db.shops + list.map { it.copy(id = newId(), createdAt = now()) }) }
    }

    /**
     * Apply admin-assigned shops (from the web dashboard): find-or-create the company + distributor
     * by name, then add each shop as a Pending entry (dedupe by name within its distributor).
     * Returns how many NEW shops were added. Runs in a single atomic mutate.
     */
    /** Replaces the expense-approval map (pulled from the web dashboard). No-op if unchanged. */
    fun setApprovals(map: Map<String, String>) {
        if (db.value.approvals == map) return
        mutate { it.copy(approvals = map) }
    }

    /** Sets the admin-assigned vehicle (pulled from the web fleet registry). No-op if unchanged. */
    fun setVehicle(number: String, type: String) {
        if (db.value.vehicleNumber == number && db.value.vehicleType == type) return
        mutate { it.copy(vehicleNumber = number, vehicleType = type) }
    }

    /** Replaces the whole fleet (vehicles + fuel/service logs) from the shared cloud collection. */
    fun setFleet(vehicles: List<Vehicle>, fuelLogs: List<FuelLog>, serviceLogs: List<ServiceLog>) {
        if (db.value.vehicles == vehicles && db.value.fuelLogs == fuelLogs && db.value.serviceLogs == serviceLogs) return
        mutate { it.copy(vehicles = vehicles, fuelLogs = fuelLogs, serviceLogs = serviceLogs) }
    }

    /** Replaces the shared project catalog (companies + distributors w/ creatives & media). */
    fun setCatalog(companies: List<Company>, distributors: List<Distributor>) {
        if (db.value.companies == companies && db.value.distributors == distributors) return
        mutate { it.copy(companies = companies, distributors = distributors) }
    }

    /** Applies admin-pushed project setups: find-or-create company (merge creatives/media) + distributor (city/contact). */
    fun applyProjectSetups(setups: List<ProjectSetup>, userId: String) {
        if (setups.isEmpty()) return
        mutate { db ->
            val companies = db.companies.toMutableList()
            val distributors = db.distributors.toMutableList()
            for (s in setups) {
                if (s.company.isBlank()) continue
                var ci = companies.indexOfFirst { it.name.trim().equals(s.company.trim(), ignoreCase = true) }
                if (ci < 0) {
                    companies.add(Company(id = newId(), name = s.company.trim(), creatives = s.creatives, mediaTypes = s.mediaTypes, userId = userId, createdAt = now()))
                    ci = companies.size - 1
                } else {
                    val c = companies[ci]
                    companies[ci] = c.copy(
                        creatives = (c.creatives + s.creatives).distinct(),
                        mediaTypes = (c.mediaTypes + s.mediaTypes).distinct(),
                    )
                }
                val comp = companies[ci]
                if (s.name.isNotBlank()) {
                    val di = distributors.indexOfFirst { it.name.trim().equals(s.name.trim(), ignoreCase = true) }
                    if (di < 0) {
                        distributors.add(Distributor(id = newId(), name = s.name.trim(), city = s.city.trim(), contact = s.contact.trim(), companyId = comp.id, companyName = comp.name, userId = userId, createdAt = now()))
                    } else {
                        val d = distributors[di]
                        distributors[di] = d.copy(
                            city = s.city.trim().ifBlank { d.city },
                            contact = s.contact.trim().ifBlank { d.contact },
                            companyId = comp.id, companyName = comp.name,
                        )
                    }
                }
            }
            db.copy(companies = companies, distributors = distributors)
        }
    }

    fun applyAssignments(assigned: List<AssignedShop>, userId: String): Int {
        if (assigned.isEmpty()) return 0
        var added = 0
        mutate { db ->
            val companies = db.companies.toMutableList()
            val distributors = db.distributors.toMutableList()
            val shops = db.shops.toMutableList()

            fun companyIdFor(name: String): String {
                companies.firstOrNull { it.name.trim().equals(name.trim(), ignoreCase = true) }?.let { return it.id }
                val c = Company(id = newId(), name = name.trim(), userId = userId, createdAt = now())
                companies.add(c); return c.id
            }
            fun distributorIdFor(project: String, company: String): String {
                distributors.firstOrNull { it.name.trim().equals(project.trim(), ignoreCase = true) }?.let { return it.id }
                val cid = companyIdFor(company.ifBlank { project })
                val cName = companies.firstOrNull { it.id == cid }?.name ?: company
                val d = Distributor(id = newId(), name = project.trim(), companyId = cid, companyName = cName, userId = userId, createdAt = now())
                distributors.add(d); return d.id
            }

            for (a in assigned) {
                if (a.project.isBlank() || a.name.isBlank()) continue
                val did = distributorIdFor(a.project, a.company)
                val dup = shops.any { it.distributorId == did && it.name.trim().equals(a.name.trim(), ignoreCase = true) }
                if (dup) continue
                shops.add(Shop(
                    id = newId(), name = a.name.trim(), city = a.city.trim(), contact = a.contact.trim(),
                    status = "Pending", distributorId = did, userId = userId, createdAt = now(),
                ))
                added++
            }
            db.copy(companies = companies, distributors = distributors, shops = shops)
        }
        return added
    }

    fun updateShop(s: Shop) =
        mutate { it.copy(shops = it.shops.map { x -> if (x.id == s.id) s else x }) }

    fun addRecce(r: RecceEntry): String {
        val id = newId()
        mutate { it.copy(recces = it.recces + r.copy(id = id, createdAt = now())) }
        return id
    }

    /** Replaces an existing recce (edit flow) — matched by id; createdAt is preserved by the caller. */
    fun updateRecce(r: RecceEntry) =
        mutate { it.copy(recces = it.recces.map { x -> if (x.id == r.id) r else x }) }

    /** Inserts or updates an in-progress draft. Returns its id. */
    fun saveDraft(d: RecceDraft): String {
        val id = d.id.ifBlank { newId() }
        mutate { db ->
            val updated = d.copy(id = id, updatedAt = now())
            db.copy(drafts = if (db.drafts.any { it.id == id }) db.drafts.map { if (it.id == id) updated else it } else db.drafts + updated)
        }
        return id
    }

    fun deleteDraft(id: String) = mutate { it.copy(drafts = it.drafts.filter { d -> d.id != id }) }

    // ── Expenses (Expense Manager) ──
    fun addExpense(e: Expense): String {
        val id = newId()
        mutate { it.copy(expenses = it.expenses + e.copy(id = id, createdAt = now())) }
        return id
    }

    fun updateExpense(e: Expense) =
        mutate { it.copy(expenses = it.expenses.map { x -> if (x.id == e.id) e else x }) }

    fun deleteExpense(id: String) =
        mutate { it.copy(expenses = it.expenses.filter { e -> e.id != id }) }

    // ── Installation ──
    fun addInstall(e: InstallEntry): String {
        val id = newId()
        mutate { it.copy(installs = it.installs + e.copy(id = id, createdAt = now())) }
        return id
    }
    fun updateInstall(e: InstallEntry) =
        mutate { db -> db.copy(installs = db.installs.map { if (it.id == e.id) e else it }) }
    fun deleteInstall(id: String) =
        mutate { db -> db.copy(installs = db.installs.filter { it.id != id }) }
    /** Adds Pending install entries pulled from the web (size-wise: dedupe by recceId#mediaIndex). Returns count added. */
    fun addInstalls(list: List<InstallEntry>, userId: String): Int {
        if (list.isEmpty()) return 0
        var added = 0
        mutate { db ->
            // Dedupe is pure (per recceId#mediaIndex) — see [InstallDedupe]; stamp the survivors here.
            val add = InstallDedupe.selectNew(db.installs, list)
                .map { it.copy(id = newId(), userId = userId, createdAt = now()) }
            added = add.size
            db.copy(installs = db.installs + add)
        }
        return added
    }

    // ── Fleet maintenance ──
    fun addVehicle(v: Vehicle): String {
        val id = newId()
        mutate { it.copy(vehicles = it.vehicles + v.copy(id = id, createdAt = now())) }
        return id
    }
    fun updateVehicle(v: Vehicle) =
        mutate { db -> db.copy(vehicles = db.vehicles.map { if (it.id == v.id) v else it }) }
    fun deleteVehicle(id: String) =
        mutate { db ->
            db.copy(
                vehicles = db.vehicles.filter { it.id != id },
                fuelLogs = db.fuelLogs.filter { it.vehicleId != id },
                serviceLogs = db.serviceLogs.filter { it.vehicleId != id },
            )
        }
    fun addFuelLog(f: FuelLog): String {
        val id = newId()
        mutate { db ->
            val log = f.copy(id = id, createdAt = now())
            val veh = db.vehicles.map { if (it.id == f.vehicleId && f.odometer > it.currentKm) it.copy(currentKm = f.odometer) else it }
            db.copy(fuelLogs = db.fuelLogs + log, vehicles = veh)
        }
        return id
    }
    fun deleteFuelLog(id: String) =
        mutate { db -> db.copy(fuelLogs = db.fuelLogs.filter { it.id != id }) }
    fun addServiceLog(s: ServiceLog): String {
        val id = newId()
        mutate { db ->
            val log = s.copy(id = id, createdAt = now())
            val veh = db.vehicles.map { v ->
                if (v.id != s.vehicleId) v else {
                    var nv = if (s.odometer > v.currentKm) v.copy(currentKm = s.odometer) else v
                    // mark the matching maintenance item as done at this odometer; a general
                    // "Service" also refreshes the oil-change item.
                    nv = nv.copy(maintItems = nv.maintItems.map { mi ->
                        when {
                            mi.name == s.type -> mi.copy(lastKm = s.odometer)
                            s.type == "Service" && mi.name == "Oil change" -> mi.copy(lastKm = s.odometer)
                            else -> mi
                        }
                    })
                    nv
                }
            }
            db.copy(serviceLogs = db.serviceLogs + log, vehicles = veh)
        }
        return id
    }
    fun deleteServiceLog(id: String) =
        mutate { db -> db.copy(serviceLogs = db.serviceLogs.filter { it.id != id }) }

    /**
     * Merges an imported project from another device — fresh local ids (no clashes), createdAt kept,
     * and a same-named company is reused instead of duplicated.
     */
    fun importProject(company: Company?, distributor: Distributor, shops: List<Shop>, recces: List<RecceEntry>) = mutate { db ->
        val existingCo = company?.let { c -> db.companies.find { it.name.equals(c.name, ignoreCase = true) } }
        val co = existingCo ?: company?.copy(id = UUID.randomUUID().toString(), createdAt = now())
        val coId = co?.id ?: distributor.companyId
        val newDist = distributor.copy(
            id = UUID.randomUUID().toString(), companyId = coId, companyName = co?.name ?: distributor.companyName,
        )
        val shopIdMap = shops.associate { it.id to UUID.randomUUID().toString() }
        val newShops = shops.map { it.copy(id = shopIdMap[it.id] ?: UUID.randomUUID().toString(), distributorId = newDist.id) }
        val newRecces = recces.map {
            it.copy(id = UUID.randomUUID().toString(), distributorId = newDist.id, shopId = shopIdMap[it.shopId] ?: it.shopId)
        }
        db.copy(
            companies = if (existingCo == null && co != null) db.companies + co else db.companies,
            distributors = db.distributors + newDist,
            shops = db.shops + newShops,
            recces = db.recces + newRecces,
        )
    }

    /** Deletes a company plus every distributor under it and all their shops & recces. Irreversible. */
    fun deleteCompany(companyId: String) = mutate { db ->
        val distIds = db.distributors.filter { it.companyId == companyId }.map { it.id }.toSet()
        db.copy(
            companies = db.companies.filter { it.id != companyId },
            distributors = db.distributors.filter { it.companyId != companyId },
            shops = db.shops.filter { it.distributorId !in distIds },
            recces = db.recces.filter { it.distributorId !in distIds },
            drafts = db.drafts.filter { it.distributorId !in distIds },
            // Expenses are kept untouched — their denormalized projectName preserves the label so
            // deleted-project expenses stay grouped under their own name (not merged into General).
        )
    }

    /** Deletes a whole project — the distributor itself plus all its shops & recces. Irreversible. */
    fun deleteDistributor(distributorId: String) = mutate {
        it.copy(
            distributors = it.distributors.filter { d -> d.id != distributorId },
            shops = it.shops.filter { s -> s.distributorId != distributorId },
            recces = it.recces.filter { r -> r.distributorId != distributorId },
            drafts = it.drafts.filter { dr -> dr.distributorId != distributorId },
            // Expenses are kept untouched — their denormalized projectName preserves the label so
            // deleted-project expenses stay grouped under their own name (not merged into General).
        )
    }

    /** Clears only the photo paths from a distributor's recces — frees storage but keeps the records. */
    fun clearDistributorPhotos(distributorId: String) = mutate {
        it.copy(
            recces = it.recces.map { r ->
                if (r.distributorId == distributorId)
                    r.copy(shopPhotos = emptyList(), media = r.media.map { m -> m.copy(photos = emptyList()) })
                else r
            },
        )
    }
}
