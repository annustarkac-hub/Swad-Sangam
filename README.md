# Swad Sangam — Restaurant App

Android app, restaurant admin dashboard and backend for **Swad Sangam**, a North Indian restaurant at
Bhaktiyana, Badrinath Marg, near NIT Ground, Srinagar, Uttarakhand. Built from the PRD in
[`docs/`](docs/PRD_TRACEABILITY.md) — every requirement is mapped to code in **docs/PRD_TRACEABILITY.md**.

> **Owner ke liye:** coding ki zaroorat nahi. Setup: **[docs/SETUP_GUIDE.md](docs/SETUP_GUIDE.md)** · Roz ka kaam (Hindi):
> **[docs/OWNER_GUIDE_HINDI.md](docs/OWNER_GUIDE_HINDI.md)**. Sab kuch free — website, Android APK, POS, accounts, backups.
> Play Store ki zaroorat nahi.

| Link (after setup) | What |
|---|---|
| `https://swadsangam.is-local.org/` | Customer website — menu, ordering, table QR ordering, order tracking, installable |
| `…/releases/latest/download/swad-sangam.apk` | Android app, free direct download |
| `https://swadsangam.is-local.org/admin.html` | Restaurant ERP: POS, orders, kitchen, accounts, stock, khata, payroll |

## What’s inside

| Folder | What it is | Tech |
|---|---|---|
| `android/` | Customer app: menu, search, cart, pickup/dine-in/delivery ordering, live order status, notifications, call & directions, English + Hindi, works offline | Kotlin, Jetpack Compose, MVVM, Hilt, Room, Retrofit, Coil, WorkManager, optional Firebase (FCM + Crashlytics) |
| `admin-dashboard/` | **Two sites in one free static build.** `index.html`: customer ordering website (PWA, English/Hindi, offline menu, QR table ordering, order tracking, APK download). `admin.html`: restaurant ERP — POS billing (KOT, split/partial payments, UPI QR, khata credit, discounts with manager rights, thermal 58/80 mm receipts, cash register open/close), live orders, kitchen, tables & reservations, menu, inventory & recipes (auto stock deduction, food cost %), purchases & suppliers, customer khata with ageing, expenses with bill photos & approval, double-entry accounts (P&L, balance sheet, trial balance, cash flow, ledgers, GST summary), payroll (attendance, advances, payslips), business insights, offers, staff roles, audit log | React + TypeScript + Vite |
| `supabase/` | Backend: database, security rules, server-side pricing & order logic, push-notification function, tests | Supabase (PostgreSQL, Auth, Storage, Realtime, Edge Functions) |
| `.github/workflows/` | Free cloud automation: builds the APK/AAB, runs tests, deploys dashboard & backend, keeps the free database awake, creates the signing key | GitHub Actions |
| `docs/` | Setup guide, Play Store pack, launch checklist, PRD traceability, store graphics | |

## How it fits together

```mermaid
flowchart TD
    A[Android customer app] -->|HTTPS, public anon key| B[Supabase API]
    C[Admin dashboard - browser] -->|staff login| B
    B --> D[(PostgreSQL: menu, orders, audit log)]
    B --> E[Auth - staff roles]
    B --> F[Storage - dish photos]
    D -->|status change trigger| G[Edge Function push-order-status]
    G --> H[Firebase Cloud Messaging] --> A
    D -->|Realtime| C
```

Key rules enforced **in the database**, not in the app (so they can’t be bypassed):
prices recalculated on every order · unavailable items rejected · duplicate submissions blocked (idempotency key) ·
unique order numbers `SS-2026-0001` · immutable price snapshots · only valid status changes · role-based access
(Owner / Manager / Staff / Kitchen) · every admin change audited · ordering switches **off** until the owner turns them on.

## Cost

| Item | Cost |
|---|---|
| Supabase (database, login, photos, realtime, functions) | Free plan |
| GitHub (code, cloud builds, dashboard hosting if public) / Netlify | Free |
| Firebase push notifications + crash reports (optional) | Free (Spark plan) |
| Customer website + admin hosting (GitHub Pages) and APK download (GitHub Releases) | Free |
| Daily encrypted database backups (GitHub Actions) | Free |
| Google Play developer account | Not needed (optional later: $25 one-time) |
| Online payment gateway (Phase 2, not built) | Per-transaction fee charged by the gateway |

## Developer quick start

```bash
# Backend tests (needs PostgreSQL 15+ running locally)
PGHOST=localhost PGUSER=postgres ./supabase/tests/run_local.sh

# Dashboard
cd admin-dashboard && cp .env.example .env.local   # fill in Supabase URL + anon key
npm install && npm run dev                          # http://localhost:5173
npm test && npm run build

# Android (JDK 17 + Android SDK 36)
cd android
echo "SUPABASE_URL=https://xxxx.supabase.co" >> local.properties
echo "SUPABASE_ANON_KEY=..." >> local.properties
./gradlew testDebugUnitTest assembleDebug bundleRelease
```

UI strings live in `android/tools/gen_strings.py` (English + Hindi in one table) — run it after editing.

## Status

Built and tested: the PRD MVP, the Phase 2/3 items marked built, and the restaurant ERP modules listed in
`docs/PRD_TRACEABILITY.md` (section "Restaurant ERP"). That file also lists what is **not built yet**.
Design decisions: `docs/ARCHITECTURE_DECISIONS.md`. Licences: `THIRD_PARTY_LICENSES.md`.
Going live still needs the owner approvals in `docs/LAUNCH_CHECKLIST.md` (verified prices, photos, hours, FSSAI,
tax treatment, privacy-policy review) and Google Play review.
