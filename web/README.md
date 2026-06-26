# RGS — Manager Web Dashboard

A read-only browser dashboard for **managers** to see **all surveyors'** recces, shops, photos and
expenses — pulled live from the same Firebase the app already uses. No backend/server, no app changes.

- **Auth:** Firebase Google sign-in (only emails in the manager allow-list get in).
- **Data:** `collectionGroup("backup")` reads every surveyor's `users/{uid}/backup/*` docs (each is a JSON list).
- **Photos:** Firebase Storage `users/{uid}/files/<path>`.
- **Hosting:** Firebase Hosting (free) — or just open `index.html` locally for testing.

---

## One-time setup (≈10 min)

### 1. Register a Web app & paste config
Firebase Console → ⚙ **Project settings** → **Your apps** → **Add app → Web** (`</>`).
Copy the `firebaseConfig` and paste **apiKey** and **appId** into `index.html`
(`PASTE_WEB_API_KEY`, `PASTE_WEB_APP_ID`). projectId / authDomain / storageBucket are already filled.

### 2. Manager email
The allow-list is set to `info4manojgraphics@gmail.com` in **three** places — keep them identical:
- `index.html` → `MANAGER_EMAILS`
- `firestore.rules` → `isManager()`
- `storage.rules` → `isManager()`
Add more manager emails to all three if needed.

### 3. Deploy the rules (gives the manager read access to everyone's data)
Install the CLI once: `npm i -g firebase-tools`, then `firebase login`.
From this `web/` folder:
```
firebase deploy --only firestore:rules,storage
```
(Or paste the two `.rules` files in Console → Firestore → Rules, and Storage → Rules.)

### 4. Host it
```
firebase deploy --only hosting
```
→ gives a live URL like `https://recee-gps-stamp.web.app`. Open it, sign in with the manager Google account.
*(Local test: just open `index.html` — Google sign-in works on `localhost`. Other domains must be added in
Auth → Settings → Authorized domains; the `.web.app` host is authorised automatically.)*

---

## What you get
- **Overview** — surveyors, projects, stores, surveyed, interested, conversion %, coverage, per-surveyor table.
- **Recces** — every shop visit with status, project, surveyor, date, **photos** (click to view full-size), Google Maps link. Filter by surveyor / status / search. Export CSV. Print → PDF.
- **Expenses** — team advance / spent / balance / company-paid, per-surveyor and per-category breakdown. Export CSV. Print → PDF.

## Notes
- 100% read-only — it never writes, so it can't affect the app's data.
- A surveyor's data appears here only after their app has **synced to cloud** at least once.
- Add charts, date filters, or richer PDF later — this is v1.
