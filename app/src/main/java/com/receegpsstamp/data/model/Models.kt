package com.receegpsstamp.data.model

// Plain offline-first models — stored locally as JSON (no Firebase types).
// id is a locally generated UUID; createdAt is epoch millis.

data class Company(
    val id: String = "",
    val name: String = "",
    val creatives: List<String> = emptyList(),
    val mediaTypes: List<String> = emptyList(),
    val userId: String = "",
    val createdAt: Long = 0L,
)

data class Distributor(
    val id: String = "",
    val name: String = "",
    val city: String = "",
    val contact: String = "",
    // Optional WhatsApp group name for this project — shown as a reminder when sharing a recce.
    val waGroup: String = "",
    // Sticky default creative for this project — auto-fills new media so it isn't picked every time.
    val defaultCreative: String = "",
    val companyId: String = "",
    val companyName: String = "",
    val userId: String = "",
    val createdAt: Long = 0L,
    // Project completion — epoch millis when marked complete (0 = still active).
    val completedAt: Long = 0L,
    // Lifecycle stage — "" (Active) | "RecceDone" | "InstallDone". Drives the Dashboard project badge.
    val stage: String = "",
)

data class Shop(
    val id: String = "",
    val name: String = "",
    val city: String = "",
    val contact: String = "",
    val distributorId: String = "",
    // Lifecycle source of truth: "Pending" (imported, not surveyed) -> recce outcome
    // ("Interested"/"Not Interested"/"Closed").
    val status: String = "Pending",
    // Permanent shop code (e.g. PE-ME-001). Assigned only once the recce is completed;
    // blank for shops still Pending.
    val code: String = "",
    val userId: String = "",
    val createdAt: Long = 0L,
)

data class RecceEntry(
    val id: String = "",
    val shopId: String = "",
    val distributorId: String = "",
    val status: String = "Interested",
    val remark: String = "",
    val media: List<MediaItem> = emptyList(),
    val shopPhotos: List<String> = emptyList(),
    // GPS captured at recce time — used in the CSV export coordinates (0.0 = unknown).
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    // Reverse-geocoded street address at recce time (blank if offline/unavailable) — for the CSV report.
    val address: String = "",
    val userId: String = "",
    val createdAt: Long = 0L,
)

data class MediaItem(
    val type: String = "Shop Board",
    val creative: String = "",
    val width: Double = 0.0,
    val height: Double = 0.0,
    val qty: Int = 1,
    val unit: String = "ft",
    val remark: String = "",
    val photos: List<String> = emptyList(),
)

// ── Drafts ── an in-progress recce saved to continue later. Holds the raw form state (strings,
// just like the entry form) so reopening restores it exactly; converts to a RecceEntry on finalise.
data class RecceDraft(
    val id: String = "",
    val distributorId: String = "",
    val shopId: String = "",            // set when continuing an existing master shop
    val shopName: String = "",
    val shopCity: String = "",
    val shopContact: String = "",
    val status: Int = 0,                // form status index: 0 Interested, 1 Not Interested, 2 Closed
    val remark: String = "",
    val media: List<DraftMedia> = emptyList(),
    val photos: List<String> = emptyList(), // all captured photo paths (front + media)
    val updatedAt: Long = 0L,
)

data class DraftMedia(
    val creative: String = "",
    val mediaType: String = "",
    val width: String = "",
    val height: String = "",
    val qty: String = "1",
    val unit: String = "in",
    val remark: String = "",
    val photos: List<String> = emptyList(),
)

// ── Expense Manager ── a travel/job expense (money out) or an advance taken (money in),
// linked to a project (distributorId) or "" = General. Khatabook-style: advance − spent = balance.
data class Expense(
    val id: String = "",
    val kind: String = "EXPENSE",          // "EXPENSE" (out) | "ADVANCE" (in)
    val category: String = "",             // Breakfast, Tea, Lunch, Dinner, Lodge, Fuel, Toll, Material, Advance, … (+ custom)
    val amount: Double = 0.0,              // ₹
    val date: Long = 0L,                   // the expense date (epoch millis)
    val projectId: String = "",            // distributor id (live link), or "" = General
    val projectName: String = "",          // denormalized project name — survives project deletion (used for display/grouping/reports)
    val note: String = "",
    val paymentMode: String = "",          // "Cash" | "UPI" | "Card" | ""
    val billPhotos: List<String> = emptyList(),  // bill/receipt photos: fuel bill, odometer reading, UPI screenshot, …
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    // Fuel-only (category == "Fuel"): mileage is computed tank-to-tank from consecutive odometer readings.
    val odometer: Double = 0.0,            // km reading at fill-up
    val litres: Double = 0.0,
    val ratePerLitre: Double = 0.0,        // amount auto = litres × ratePerLitre
    val vehicleId: String = "",            // company vehicle (Fleet module) this fuel was logged to
    // Daily wages / Driver: amount auto = (days + nights) × ratePerDay.
    val days: Double = 0.0,
    val nights: Double = 0.0,
    val ratePerDay: Double = 0.0,
    val userId: String = "",
    val createdAt: Long = 0L,
)

// ── Fleet maintenance ── company-owned vehicles + their fuel/service logs (offline-first, synced).
// The fuel log (odometer + litres) drives fuel-economy + all km-based service alerts.
// One maintenance item on a vehicle's checklist (oil, service, alignment, brake, clutch, coolant,
// battery, tyre, …). Service schedule varies by model, so intervals are editable per vehicle.
data class MaintItem(
    val name: String = "",
    val intervalKm: Int = 0,                // km between checks (0 = no auto-reminder)
    val lastKm: Int = 0,                    // odometer when last done (0 = never recorded)
)

data class Vehicle(
    val id: String = "",
    val number: String = "",                // e.g. "MH12 AB 1234"
    val name: String = "",                  // friendly label, e.g. "Activa" / "Office Car"
    val model: String = "",                 // make/model — service schedule differs model-wise
    val type: String = "Bike",              // Bike | Car | Transport vehicle | Other
    val currentKm: Int = 0,                 // latest odometer (kept up to date from fuel/service logs)
    // per-vehicle maintenance checklist with model-wise intervals (oil, service, alignment, brake, …)
    val maintItems: List<MaintItem> = emptyList(),
    // document expiry dates (epoch millis, 0 = not set) — commercial vehicles also need fitness.
    val insuranceExpiry: Long = 0L,
    val pucExpiry: Long = 0L,
    val fitnessExpiry: Long = 0L,
    // Registration details — entered once on the web (vehicles are added web-only).
    val make: String = "",                  // manufacturer / company (Tata, Bajaj, Honda…)
    val year: Int = 0,                      // manufacture year
    val chassis: String = "",
    val engine: String = "",
    // Last known location — set from the GPS of the latest fuel/service log (shown on the web fleet map).
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val userId: String = "",
    val createdAt: Long = 0L,
)

data class FuelLog(
    val id: String = "",
    val vehicleId: String = "",
    val date: Long = 0L,
    val odometer: Int = 0,                  // km reading at fill-up
    val litres: Double = 0.0,
    val amount: Double = 0.0,               // ₹
    val note: String = "",
    val userId: String = "",
    val createdAt: Long = 0L,
)

data class ServiceLog(
    val id: String = "",
    val vehicleId: String = "",
    val date: Long = 0L,
    val odometer: Int = 0,
    val type: String = "Service",           // Oil change | Wheel alignment | Service | Other
    val amount: Double = 0.0,
    val note: String = "",
    val userId: String = "",
    val createdAt: Long = 0L,
)

// ── Installation ── a field installation of approved media at a shop. The recce's photos are the
// "before"; the installer captures the "after". On the web, before+after are paired by recceId.
data class InstallEntry(
    val id: String = "",
    val recceId: String = "",               // source recce (before photos paired by this on the web)
    val mediaIndex: Int = 0,                // which media item of the recce — size-wise: one entry per media item
    val shopId: String = "",
    val distributorId: String = "",
    val project: String = "",
    val shopName: String = "",
    val city: String = "",
    val mediaType: String = "",             // what to install (from the approval)
    val size: String = "",
    val status: String = "Pending",         // Pending | InProgress | Installed | NotDone
    val reason: String = "",                // when NotDone: Print missing | No frame | Shop moved / closed | Owner refused | Other
    val locationChanged: Boolean = false,   // installed but at a different spot than the recce
    val shopChanged: Boolean = false,       // installed at a different/new shop (shopId points to the actual one)
    val beforePhotos: List<String> = emptyList(), // captured here when there's no recce (else before = recce photos)
    val afterPhotos: List<String> = emptyList(),   // the installer's capture (after the physical install)
    val frontPhotos: List<String> = emptyList(),   // shop-front (storefront) photos — shop-level, kept on the head entry
    val startedAt: Long = 0L,               // when ▶ Start pressed (moves to InProgress) — handles the before→after gap
    val installedAt: Long = 0L,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val address: String = "",
    val note: String = "",
    val assignedUid: String = "",
    val userId: String = "",
    val createdAt: Long = 0L,
)
