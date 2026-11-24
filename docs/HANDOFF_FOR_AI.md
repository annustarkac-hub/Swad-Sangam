# Swad Sangam — handoff for the next AI / developer

Read this first. It is the full working state as of **22 Nov 2026**, written so another assistant can continue without
the old chat. The owner is **not a developer** and writes in **Hinglish**: reply in simple Hinglish, do the steps yourself
where possible, and give click-by-click steps only for what needs the owner's own accounts.

## 1. What this is
Restaurant system for **Swad Sangam**, Bhaktiyana, Badrinath Marg, near NIT Ground, Srinagar (Garhwal), Uttarakhand 246174.
Phone/WhatsApp +91 96757 50485. Everything must stay **free** (no paid APIs or services without the owner's approval).

| Part | Where | Tech |
|---|---|---|
| Customer website (PWA) | `https://annustarkac-hub.github.io/Swad-Sangam/` | React + Vite + TS, `admin-dashboard/src/site/` (entry `index.html`) |
| Admin / POS / ERP web | `…/Swad-Sangam/admin.html` | React + Vite + TS, `admin-dashboard/src/` (entry `admin.html`) |
| Backend | Supabase project ref `gqpyriurdbhhujtpizen` (region ap-southeast-1, free plan) | Postgres + RLS + RPCs, `supabase/migrations/` |
| Customer Android app | `android/app` | Kotlin, Jetpack Compose, Hilt, Room, Retrofit; id `in.swadsangam.restaurant` |
| Admin + Staff Android apps | `android/staffapp` (flavours `admin`, `staff`) | WebView shell around `admin.html`; ids `in.swadsangam.admin`, `in.swadsangam.staff` |
| Hosting / CI | GitHub repo `annustarkac-hub/Swad-Sangam` (public), GitHub Pages, GitHub Actions, GitHub Releases for APKs | |

Owner login (OWNER role): the owner's own email (ask them; do not store it in code).

## 2. Repository map
- `supabase/migrations/2026112*_*.sql` — 18 migrations. 0001–0016 are applied live; **0017 (DELIVERY role) and 0018
  (delivery code, COD handover, payment integrity) are applied when PR #31 is merged** (dry-run on live passed).
  Key ones: `0001_schema`, `0002_security_and_logic` (place_order, statuses, feedback, RLS), `0006_roles`,
  `0007_accounting_core` (double-entry journal), `0008_inventory_purchases_expenses`, `0009_pos_payments`, `0010_payroll`,
  `0012_kds_bills_loyalty_qr`, `0013_public_tables`, `0014_menu_media`, `0015_menu_variants`, `0016_customer_accounts`.
- `supabase/tests/` — `run_local.sh` runs `backend_tests`, `erp_tests`, `pos_upgrade_tests`, `customer_tests`, `delivery_tests` twice
  (default + strict privileges) against a scratch Postgres with `supabase_stub.sql`.
- `supabase/menu/swad_sangam_menu.py` → `swad_sangam_menu.sql`: the real menu (22 categories, 199 dishes) from the owner's menu card.
- `supabase/seed_*.sql` — dish-photo links (all already applied live).
- `admin-dashboard/` — one Vite build, two entries. `src/pages/*` admin pages, `src/site/*` customer site
  (`SiteApp.tsx`, `Account.tsx`, `common.ts`, `i18n.ts`, `site.css`), `src/lib/native.ts` Android bridge,
  `public/` static files (dish photos in `public/dishes/`, `hero-banner.webp`, `privacy.html`, `terms.html`, `photo-credits.html`, `sw.js`).
- `android/app` — customer app (theme `ui/theme/Theme.kt`, accounts `data/auth/`, `ui/account/`).
- `android/staffapp` — admin/staff shell (`MainActivity.kt`).
- `.github/workflows/` — `admin-dashboard.yml` (build + Pages deploy), `backend-tests.yml`, `android.yml` (CI builds),
  `release-apk.yml` (signed release of all 3 APKs), `create-signing-key.yml`, `keepalive.yml`, `backup.yml`, `deploy-backend.yml`.
- `docs/` — `SETUP_GUIDE.md`, `OWNER_GUIDE_HINDI.md` (daily use), `OWNER_TODO_HINDI.md` (owner's remaining tasks),
  `FIX_SPEC_STATUS.md` (latest audit spec, item by item), `FREE_FEATURES.md`, `UI_UX_AUDIT.md`, `ANDROID_UI_AUDIT.md` (Android design tokens + components), `PRD_TRACEABILITY.md`, `ARCHITECTURE_DECISIONS.md`, `LAUNCH_CHECKLIST.md`.

## 3. Rules that must not be broken
- **Do not invent** menu items, prices, restaurant details, ratings or reviews. Unknown → leave blank / mark for owner verification.
- **Preserve** existing backend, database, APIs and working features; never replace the running project with a demo.
- **Money in integer paise**, prices computed **on the server** (`quote_order`, `place_order`), idempotency keys on orders,
  order money immutable after placement (trigger `protect_order_money`; POS edits use `app.pos_edit`).
- RPCs are `security definer` with `set search_path = ''`. Roles via `private.my_role()` / `private.role_matches`
  (CASHIER and WAITER count as STAFF). A customer account has **no** staff row → no admin access.
- Secrets never go in code, commits or chat. The repo is **public** — artifacts are downloadable by anyone.
- AGPL projects (OrderRestro, tirforge/restro) were used only as feature references; **no code copied**.
- FoodGenz (owner says licensed): only palette, font (Plus Jakarta Sans, OFL) and UX patterns were used; no FoodGenz assets.
- Photos: Foodiesfeed (free licence, some AI-generated) and Wikimedia Commons (CC) — all credited on `photo-credits.html`.
  The owner asked for **no labels on dish photos**; the disclosure is in Terms §6a and Photo credits.
- Dates/times in India time: use `(now() at time zone 'Asia/Kolkata')`.

## 4. How work was done (tools / access)
- **Live DB changes**: Supabase Management API `POST https://api.supabase.com/v1/projects/gqpyriurdbhhujtpizen/database/query`
  with `{"query": "..."}`. The access token is saved as a network secret on the Claude Code environment "swad-sangam"
  (the proxy injects `Authorization`), so requests send a dummy bearer. After adding a migration file, apply it this way and
  insert its version into `supabase_migrations.schema_migrations`.
- `*.supabase.co` and `github.io` are **not reachable** from that sandbox; test UI with Playwright + mocked API (`page.route`)
  using menu JSON fetched through the Management API.
- **Local Postgres tests**: `PGHOST=/tmp/pg PGPORT=5433 PGUSER=postgres ./supabase/tests/run_local.sh`
  (start with `su postgres -c "/usr/lib/postgresql/16/bin/pg_ctl -D /tmp/pg/data -o '-p 5433 -k /tmp/pg' -l /tmp/pg/log start"`).
- **Web**: `cd admin-dashboard && npm ci && npx tsc --noEmit -p . && npm run build && npx vitest run`.
- **Android**: `cd android && ./gradlew --init-script /tmp/claude-0/mirror.gradle :app:assembleDebug :app:testDebugUnitTest
  :staffapp:assembleAdminDebug :staffapp:assembleStaffDebug :app:lintDebug` (the init script points Maven Central to a Google
  mirror because of 429s). `android/local.properties` (gitignored) holds `sdk.dir`, `SUPABASE_URL`, `SUPABASE_ANON_KEY` (public anon key).
- Git: develop on branch `claude/swad-sangam-full-stack-4ndt3v`, open a PR to `main`, merge when the owner says
  "merge kar do" (or has asked to finish everything). Pages deploys automatically from `main` (~1 min).
  When merging use the full 40-char head SHA. PRs #1–#31 are merged.

## 5. Live configuration now
- Settings: accepting orders ON; **Pickup ON, Dine-in ON, Delivery OFF** (delivery fee ₹20 prepared); Cash only;
  **no GST** (gst_registered false, tax 0); WhatsApp +919675750485; **open 24×7 for testing** (owner asked) — real hours still needed;
  `hours_verified` false; FSSAI empty; tables T1–T6 (4 seats); hero banner `hero-banner.webp` (says "Since 2025" — owner to confirm 2025 vs 2026).
- Menu: 199 dishes, 195 with photos (missing: Hot Milk, Lime Soda, Onion Salad, Paneer Pasanda); all `details_verified=false` (internal badge only).
- Auth: Supabase email "Confirm email" is **ON** (built-in mailer ~2 emails/hour). Owner should turn it OFF; Claude was not allowed to change it.
- Keepalive workflow runs every 3 days (tested OK). Backup workflow needs secrets (not set).
- **No GitHub Release yet**: release needs secrets `ANDROID_KEYSTORE_PASSWORD` (owner sets) and `ANDROID_KEYSTORE_BASE64`
  (from "Create signing key" run). Test APKs given to the owner are debug-signed and must be uninstalled before the release ones.

## 6. Feature summary (all built)
Customer web: menu with categories/pills, 2-column cards, search + recent searches, filters, Pure Veg mode, Half/Full & Dry/Gravy
variants, cart, coupon, bill details, cooking instructions, no-cutlery, pickup / dine-in (table picker, table code) / delivery,
COD, order tracking with ETA, cancel, reorder, ratings, customer accounts (email+password, profile, 10 saved addresses,
orders on any phone), About, WhatsApp, Hindi/English, dark mode, PWA.
Admin/ERP: dashboard with new-order sound, online orders (accept with ETA / reject / status), POS (split/merge/move, discounts,
loyalty, KOT/bill print), kitchen display with stations, tables + QR + table codes, menu (variants, photos), offers & coupons,
reports (daily/monthly, item-wise, CSV), double-entry accounting (P&L, balance sheet, ledger, GST summary), inventory/recipes/
purchases/wastage, suppliers, customer khata, expenses with approval, payroll/attendance, staff roles, audit log, feedback, settings.
Customer Android: same ordering flow natively, Pure Veg, variants, table code, accounts, design system in `ui/theme/Tokens.kt` +
`ui/components/DesignSystem.kt` (use these, never raw dp/hex), Plus Jakarta Sans, brand **tandoor crimson #B83227** + saffron on porcelain #F8F6F3 (same on web/admin) /
accent #FD4E11, cream/near-black #131217). Admin/Staff Android: WebView shell with native print, CSV save, file upload, outside links.

## 6b. Delivery & payment rules (Nov 2026)
- App delivery orders: code generated on accept → customer sees it (app/website) → staff assign a rider (role DELIVERY,
  sees only own orders, never the code) → rider Start delivery → Deliver: types the code + confirms Cash/UPI →
  `complete_order_with_payment` (payment + PAID + COMPLETED in one transaction).
- No order can be COMPLETED unpaid via `update_order_status`; owner/manager may pass a reason (audited).
- Waiters: no payments, no cash register. Riders: no POS.
- Notifications work without Firebase (20 s in-app check, ~1 min background job). Staff app: `SwadSangamApp.notify()`.

## 7. Open items (most important first)
1. Owner: real opening hours (replace 24×7), FSSAI number, verify dishes, decide on delivery.
2. Owner: turn off Supabase "Confirm email".
3. Owner: signing secrets → run "Create signing key" → "Release Android APK" (publishes 3 APKs).
4. Owner: backup secrets `SUPABASE_DB_URL`, `BACKUP_PASSPHRASE`.
5. Owner: staff logins (sign up on admin.html, owner activates in Staff logins).
6. Photos for the 4 dishes; banner year.
7. Not verified on a real Android device yet (customer app login, admin/staff printing).
8. Optional later (paid): online payment gateway, SMS OTP, live rider tracking, distance-based delivery fee.

Step-by-step owner instructions in Hindi: `docs/OWNER_TODO_HINDI.md`.
