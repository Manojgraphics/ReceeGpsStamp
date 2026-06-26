# RGS — Installation + POE Module — FINAL SPEC (2026-06-26)

## 0. Goal
Recce → company Approval → Installation (field) → **POE** (Proof of Execution report for the client).
POE = per-shop **Before + After** photo report (PPT/PDF/Excel) = proof the work was done = client pays against it.

## 1. Key simplifications (decided)
1. **Recce photo = BEFORE.** Installer only captures **AFTER**. (No double "before" capture.)
2. **One place:** Recce + Installation are a **Work Mode toggle inside Project** (NOT in the drawer).
3. **Bulk data via Excel on web.** 100–200 shops are approved/corrected/status-managed by **Excel round-trip** on the web — never one-by-one in the app.
4. **App = photos only.** Field user's whole job = capture AFTER photo + mark Done/reason.
5. **POE needs photos** (option A) — proof without photos is meaningless.
6. **Approval is SIZE-WISE, not shop-wise.** One shop's recce can hold several media items (creative + media type + size). Each size is **approved, installed, and proven (before/after) separately** → **one `InstallEntry` per (recce × media index)**, keyed by `recceId + "#" + mediaIndex`. A shop with 3 sizes → 3 install rows → 3 approvals → 3 before/after pairs. Before photo for a size = that media item's recce photos (`rec.media[i].photos`).

## 2. Division of labour
| Who | Does what |
|---|---|
| 📱 **App (field)** | Recce (before photos) + Installation (after photo) + Done/reason |
| 📊 **Web Excel** | Bulk list, approval, size/media change, install status, remarks (100–200 shops) |
| 📑 **POE Module (web)** | Pair Before+After by id → generate PPT / PDF / Excel for the client |

## 3. Lifecycle (end-to-end)
```
① Recce        (app)  shop photo = BEFORE + status + media/size        ✅ exists
② Sync         (auto) → cloud                                          ✅ exists
③ Approval     (web)  admin approves shop (Approved/Cancel/Size/Media) ✅ Phase-1
④ Install list (sync) approved shop → Installation mode, assigned uid  🆕
⑤ Install      (app)  see BEFORE → install → AFTER photo → Done/reason 🆕
⑥ Sync         (auto) after-photo + status → cloud                     🆕 (uses existing sync)
⑦ Review       (web)  admin checks before+after (match/missing/proper) ✅ Phase-1
⑧ POE          (web)  Generate POE PPT/PDF/Excel                        ⬆ enhance Phase-1
```
Only ④⑤ (app) + ⑧ enhance (web) are new.

## 4. Navigation — Work Mode (in Project)
```
PROJECT → shop work-list
  ┌──────────────────────────────┐
  │  [ Recce ]  [ Installation ] │   segmented toggle
  └──────────────────────────────┘
  Recce mode        → Pending shops → tap → recce capture (as today)
  Installation mode → Approved shops → tap → AFTER-photo capture
       Installation toggle is enabled only when ≥1 approved shop exists.
```

## 5. App — screens
**Install List** (Installation mode)
- Approved shops, grouped by project. Card: shop + city + **media type + size** + status badge.
- Counter: "N to install · M done".
- Tap → Install Capture.

**Install Capture**
- Shop info (name, address, media type + size to install).
- **BEFORE** (read-only): the recce photo, for reference.
- **AFTER**: 📷 capture (GPS-stamped + watermarked, reuse CameraScreen) → thumbnails (≥1).
- **Outcome:**
  - ✅ **Done** — enabled only after ≥1 after-photo. → status `Installed`.
  - ⚠ **Reason** (if not installed): one of —
    - Print missing
    - No frame
    - Location change
    - Shop closed
    - Owner refused
    - Other → free-text note
  - (reason photo optional)
- Save → capture GPS → store → back to list.

## 6. Statuses & flow (LOCKED — handles the before→install→after time gap)
**`Pending → InProgress → Installed`** (or `NotDone · <reason>`). Three buckets: **To Install · In Progress · Done**.
- Before captured (or from recce) + **▶ Start** → **InProgress** (saved offline). Installer does the physical work
  (may close the app / move to other shops). Returns later → **After** photo → **✅ Done** → **Installed**.

**Entry paths (all converge to the same flow):**
- (A) **Recce done → auto-ready** to install (before = recce photo). Same-visit, no approval wait.
- (B) **Web approval → install appears** (gated).
- (C) **No recce → installer picks project → adds shop → captures Before + After** directly.

**Before photo:** from the recce if it exists, else the installer captures it in the install module.
New field: `startedAt` (when ▶ Start pressed). All offline-persisted in LocalStore.

## 7. Data model — `InstallEntry` (light)
```
id
recceId            ← links to recce (BEFORE photos paired by this on web, not copied)
shopId, distributorId, project, shopName, city
mediaType, size    ← what to install (from approval)
status             ← Pending | Installed | NotDone
reason             ← "" | Print missing | No frame | Shop moved / closed | Owner refused | Other
locationChanged    ← Bool — installed but at a different spot than recce (new GPS auto-saved)
shopChanged        ← Bool — installed at a different/NEW shop (shopId now points to the actual one)
beforePhotos[]     ← only for new/changed shops that have NO recce (else before = recce's photos)
afterPhotos[]      ← the installer's capture
installedAt, lat, lng, address, note
assignedUid, userId
```
**One InstallEntry per media-SIZE, NOT per shop** (key = `recceId + "#" + mediaIndex`).
Before photos normally NOT stored — web pairs recce.media[i].photos + install.afterPhotos by `recceId + media index`.
Exception: a NEW/changed shop (no recce) → installer captures before+after, stored here.

## 7b. Outcome model (final)
```
✅ DONE (Installed) — after photo (+ before if new shop)
   ☐ 📍 Location changed        (same shop, moved a bit)
   🔁 Shop changed → installed   (pick existing list shop / + add new shop)
─ not installed ─
○ Print missing   ○ No frame   ○ Shop moved / closed   ○ Owner refused   ○ Other (+note)
```

## 8. Sync flow
```
Admin approve (web) → assignments/{installerUid}.installList push (approved shops + media/size)
   → app pull on sign-in → create InstallEntry "Pending"
   → installer: after-photo + Done/reason → LocalStore → backup → cloud (existing photo+data sync)
   → web Installation tab: pair before+after by recceId → review → POE
```
Who installs: default = same field user the shop is assigned to (admin can re-assign install via the assignment).

## 9. Web — Installation management (Excel round-trip)
- **Approve / correct in bulk:** Export Excel (with an **ID column** per recce/shop) → admin edits approval / size / media / install-status / remark for many rows → **Import** → matched by ID → bulk update. (Photos never in Excel — text only.)
- The Installation tab also shows live before+after photos for visual verification.
- ID column is mandatory so app-photos pair correctly after a round-trip.

## 10. POE Module (web report generator)
- "Generate POE" → pick project + format (PPT / PDF / Excel) + scope (Installed only / all).
- Pulls installed shops → pairs **BEFORE (recce) + AFTER (install)** by id → builds:
  - Cover page (project, company, date, totals).
  - Per shop: Before | After side-by-side + GPS + date + field user + media/size.
  - (optional) per-size grouping (mirrors the manual PPTX).
- Formats: **PPT** (PptxGenJS), **PDF** (jsPDF), **Excel** (SheetJS).
- Phase-1 already has a basic PPT/Excel; this enhances it to client-ready.

## 11. Decisions locked (defaults — adjustable)
1. Who installs → **same field user** (admin can re-assign). 
2. After photo for Done → **≥1 required**.
3. Reason photo → optional.
4. Reasons → **Print missing · No frame · Location change · Shop closed · Owner refused · Other**.
5. Navigation → **Work Mode toggle in Project**.
6. POE → **with photos** (before+after).

## 12. Baby-step build plan
```
Step 1  → InstallEntry model + LocalStore CRUD                 [data only, compile]
Step 2  → Web: on approve, push installList to assignments     [web]
Step 3  → App: pull installList → InstallEntry "Pending"       [sync]
Step 4  → App: Project Work-Mode toggle + Install List (read)  [UI]
Step 5  → App: Install Capture screen (before + outcome buttons, no camera) [UI]
Step 6  → App: AFTER photo capture (reuse camera) + GPS        [UI]
Step 7  → App: Save + sync after-photo & status                [sync]
Step 8  → Web: Installation tab shows before+after pair        [web]
Step 9  → POE Module: client-ready PPT/PDF (before+after, cover, per-size) [web]
Step 10 → Build release + on-device test
```
Build one step at a time, verify, then next.
