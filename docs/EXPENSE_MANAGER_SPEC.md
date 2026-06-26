# Expense Manager — Build Spec (v1)

Standalone module for field surveyors to track trip expenses, Khatabook-style.
Offline-first; syncs to Firebase like recces. Currency: ₹ INR only.

## Locked decisions (2026-06-25)
- **Placement:** side-drawer item **"Expenses"** + a balance **summary card on the Dashboard** (bottom nav stays 3 tabs).
- **Project link:** BOTH — an expense may link to a project (distributor) for project-wise reimbursement, OR be **"General"** (no project). Each expense stores a denormalized **projectName** so deleting a project KEEPS its expenses grouped under that name (they are NOT moved to General — that would collapse/merge the history). Grouping/reports key off `projectName`.
- **Goal:** advance + balance ledger. Headline = `Advance taken − Spent = Balance (to return / overspent)`.

## Core model
Running ledger per signed-in user:
```
Advance (money IN)  −  Expenses (money OUT)  =  Balance
```
Balance > 0 → advance left to return; Balance < 0 → company owes the surveyor.

## Data model — `Expense` (new list in LocalStore.Db, syncs to users/{uid}/backup/expenses)
- `id: String` (UUID)
- `kind: String` — "ADVANCE" | "EXPENSE"
- `category: String` — see categories
- `amount: Double` (₹)
- `date: Long` (expense date, editable; default today)
- `projectId: String` — distributor id (live link), or "" = General
- `projectName: String` — denormalized project name; **survives project deletion** (display/grouping/reports key off this, NOT a lookup by projectId)
- `note: String`
- `paymentMode: String` — "Cash" | "UPI" | "Card" | "Company" | "". **"Company" = paid directly by the company** (e.g. fuel on the company UPI): recorded + shown in reports, but EXCLUDED from the surveyor's Spent/Balance (Balance = Advance − expenses where paymentMode != "Company"); surfaced as a separate "Paid by company" total. Reports show: Spent by you, Paid by company, Total project cost.
- `billPhotos: List<String>` — MULTIPLE bill/receipt photos per expense (e.g. fuel bill + odometer reading + UPI payment screenshot, "and more"). Captured via camera/gallery, **compressed small but readable** (~200 KB, WhatsApp-like) then uploaded to Storage. Small files (<500 KB) upload as-is (no re-compression).
- `lat: Double, lng: Double` — optional auto GPS
- `createdAt: Long, userId: String`
- Fuel-only: `odometer: Double` (km), `litres: Double`, `ratePerLitre: Double` (amount auto = litres × rate)
- Daily wages / Driver: `days: Double`, `nights: Double`, `ratePerDay: Double` (amount auto = (days + nights) × ratePerDay; note field for worker name)

## Categories (preset, grouped, each with icon) + custom
**Final list (2026-06-25):** Tea / Snacks · Dinner / Lunch · Lodge · Fuel · Travel · Material · Daily wages · Driver · Police fine · Other Expenses. (Breakfast+Tea/Snacks merged → "Tea / Snacks"; Lunch+Dinner merged → "Dinner / Lunch"; removed Toll, Parking, State Permit, Mobile; Misc renamed → "Other Expenses". All 10 fit the dropdown without scrolling.)
- **Material** — signage/printing material, hardware, etc. The **note field is prominent/encouraged** here (what was bought, qty, rate). Bill photo strongly recommended.
- **Daily wages / Driver** (project-wise, labour/staff) — entered as **No. of days + No. of nights, × Rate/day** (amount auto-fills = (days + nights) × rate); note field for worker name.
- Money in: Advance
- ➕ user-added custom categories (name + icon), stored in settings

## Fuel & mileage (tank-to-tank)
- Each Fuel entry records odometer + litres (+ rate).
- `mileage(km/L) = (thisOdometer − previousFuelOdometer) ÷ thisLitres`; `cost/km = spend ÷ distance`.
- Fuel & Mileage summary: total km, total litres, avg mileage, total fuel cost, ₹/km, per-fill trend. First fill = baseline only.

## Screens
1. **Expenses (main):** balance card (scope: All / a project) + category breakdown; filters (project · date range · category); transaction list grouped by date, green=advance, red=expense. FAB → + Expense / + Advance.
2. **Add Expense:** category grid → amount → date → project dropdown (incl. "General") → note → payment mode → attach bill photo. Fuel shows odometer/litres/rate (amount auto). For **Material**, the note field is shown prominently (label "Material details — what / qty / rate") and a bill photo is encouraged.
3. **Add Advance:** amount → date → project → received-from → note.
4. **Summary / Report:** per-category + per-project + per-day totals, date range, **Export PDF/Excel** reimbursement sheet (advance, itemised, balance, optional bill thumbnails) — reuse existing report/share pipeline (PDF/Excel + WhatsApp/email).
5. **Entry details:** view + edit/delete (confirm).

## Dashboard card
Compact balance widget: current project (or All) → Advance / Spent / Balance, tap → Expenses screen.

## Sync & independence
- `expenses` added to `LocalStore.Db` → FirestoreSync auto-backs-up to `users/{uid}/backup/expenses`; bill photos reuse the Storage upload (compressed 2560/Q85). Auto-restore on sign-in like recces.
- Works with zero projects (General expenses). Module is self-contained.

## Settings
Default payment mode · manage custom categories · default odometer unit (km) · auto-attach GPS to expenses (on/off).

## v1 scope vs later
- v1: ledger, categories, fuel+mileage, advance/balance, bill photos, reports, sync, dashboard card.
- Later: manager approval workflow, multi-currency, scheduled monthly reports, per-km allowance rules.

## Suggested build phases
1. Data model (`Expense`, `ExpenseStore`/extend LocalStore.Db) + FirestoreSync wiring.
2. Expenses screen (balance card + list + filters) + drawer entry + Dashboard card.
3. Add Expense / Add Advance forms (incl. Fuel fields, bill photo).
4. Fuel & mileage summary.
5. Reports/export (PDF/Excel) + share.
