# PRD traceability — where every requirement lives

Status key: ✅ built · ⚙️ built, owner switches it on · 🧾 owner/legal input needed · 🔜 later phase (designed for, not built)

## Product & scope (PRD §1–5)

| Requirement | Status | Where |
|---|---|---|
| Restaurant-owned ordering + discovery app (not a marketplace) | ✅ | whole repo |
| Online ordering, takeaway (pickup), delivery, dine-in independently configurable | ✅⚙️ | `restaurant_settings` switches; Dashboard → Settings |
| Works as a useful app even if ordering is off on launch day | ✅ | App shows menu + “Call to order” when ordering is off (`HomeScreen`, `CartScreen`) |
| Release gate: no unattended checkout | ✅⚙️ | All ordering switches default **false** (`20261120000001_schema.sql`); `place_order` rejects with `ORDERS_CLOSED` |
| Phase 1 MVP list | ✅ | see FR table |
| Phase 2: order history, push notifications, offers, delivery | ✅⚙️ | Orders tab (device history + reorder), FCM push, Offers page, delivery switch |
| Phase 2: customer accounts & saved addresses | 🔜 | Guest checkout chosen (PRD §21); name/phone remembered on device only |
| Phase 2: online UPI/payment gateway | 🔜 | Enum `ONLINE` + `online_payment_enabled` reserved; `place_order` refuses `ONLINE` until a gateway + webhook verification is added (needs owner’s gateway account; gateways charge per transaction) |
| Phase 3: digital coupons | ✅ | `coupons` table, server-side validation in `place_order`/`quote_order`, Dashboard → Offers |
| Phase 3: QR-code table ordering | ✅ | `swadsangam://menu?table=N` deep link, Dashboard → QR codes |
| Phase 3: sales & menu analytics | ✅ | `sales_report()`, Dashboard → Reports |
| Phase 3: loyalty, table booking, customer segmentation | 🔜 | PRD §18: measure repeat purchases first — Reports shows new vs returning customers |

## Customer app (PRD §6.1)

| FR | Status | Where |
|---|---|---|
| FR-01 Splash: logo, name, brand colours, short animation, ≤2 s, cached content offline, no crash on image failure | ✅ | `Theme.SwadSangam.Splash`, `MainActivity` (keep-on-screen max 1.5 s), `AppViewModel`, `DishImage` fallback |
| FR-02 Home: logo/name, location+directions, search, hero dish, popular, budget, breakfast & snacks, veg, non-veg, reviews, timings, contact/order buttons, suggested copy | ✅ | `ui/home/HomeScreen.kt` |
| FR-02 “Popular” only when data-backed / owner-approved | ✅ | `menu_items.is_popular` set by owner; best-sellers shown in dashboard only after ≥20 completed orders |
| FR-03 Menu: categories, name, INR price, veg mark, description, photo, availability, add, quantity, sort by price, category filters | ✅ | `ui/menu/MenuScreen.kt`, `DishRow` |
| FR-04 Search by name/category/veg/price, <500 ms local, case-insensitive, no-result state, no account | ✅ | `MenuFilter` (pure, unit-tested) |
| FR-05 Product detail fields; final price before adding | ✅ | `ItemScreen` (options, portion, prep time, allergens) |
| FR-06 Cart: add/remove/qty, subtotal, taxes/fees/discounts separately, total, survives restart; server recalculates | ✅ | Room `cart_lines`, `PriceSummary`, `quote_order`/`place_order` |
| FR-07 Checkout: name, mobile, order type, pickup instructions, notes, payment, summary, terms checkbox; guest checkout | ✅ | `ui/checkout/` |
| FR-08 Payment: cash on pickup, UPI at restaurant; never trust client success; no card/PIN storage | ✅ | Staff mark payment in dashboard; UPI intent is convenience only |
| FR-09 Tracking: placed → accepted → preparing → ready → completed; rejected/cancelled/payment failed/refund pending; never “accepted” early; “Awaiting restaurant confirmation” + phone | ✅ | `OrderScreen`, timeline, 2-minute call prompt |
| FR-10 Notifications (submitted, accepted, preparing, ready, rejected, cancelled, payment) via FCM; status also in-app | ✅⚙️ | `push-order-status` Edge Function, `PushService`, `OrderStatusWorker` fallback, local “submitted” notification |
| FR-11 Restaurant info: name, address, phone, hours, categories, parking, dine-in, directions, support; call/directions/share/menu | ✅ | `RestaurantInfoScreen`, `Intents` |
| FR-12 Reviews: verified rating only, no fake/imported reviews; private feedback form (5 criteria + comments, consent) | ✅ | Home “Customer reviews” (rating/count/link from Settings), `FeedbackScreen`, `feedback` table (owner/manager only) |

## Admin dashboard (PRD §6.2)

| Requirement | Status | Where |
|---|---|---|
| Roles Owner / Manager / Staff / Kitchen, enforced server-side | ✅ | `staff` table, `private.require_role`, RLS policies |
| A. Dashboard: today’s / pending / accepted / preparing / completed / cancelled, gross sales, best sellers | ✅ | `dashboard_summary()`, `DashboardPage` |
| B. Menu: add, edit, delete/archive, price, photo, category, availability, description, veg type | ✅ | `MenuPage` (+ options, allergens, portion, verified flag) |
| C. Orders: realtime, alert sound, accept, reject w/ reason, prep status, ready, completed, customer details, payment status, history | ✅ | `OrdersPage` (Realtime + 30 s fallback, WebAudio alert, browser notification), `OrderDetail` |
| D. Configuration: hours, temporary closure, order toggle, pickup, delivery, phone, min order, tax, prep estimates | ✅ | `SettingsPage` (password re-check before saving) |
| E. Reports: daily/weekly/monthly, item-wise, volume, AOV, cancellation rate, payment breakdown; gross/discounts/taxes/refunds/net kept separate | ✅ | `sales_report()`, `ReportsPage` (+ CSV export) |

## Journeys (PRD §7)

A student order, B family order, C visitor directions, D owner handling — all covered by the screens above and the
automated backend test `backend_tests.sql` (full lifecycle, auditable history, mixed veg/non-veg pricing).

## UX (PRD §8)

| Requirement | Status | Where |
|---|---|---|
| Palette #9E3528 / #E8A13B / #FFF8EF / #FFFFFF / #34241F / #27834A / #C83D35 | ✅ | `ui/theme/Theme.kt`, `colors.xml`, dashboard `styles.css` |
| Roboto/system sans, semibold headings, bold prices, 48 dp touch targets | ✅ | `AppTypography`, `PriceStyle`, 48 dp buttons |
| Bottom nav Home / Menu / Cart / Orders / More | ✅ | `AppNav` |
| English first, Hindi next, Garhwali only if professionally translated; strings externalised | ✅ | `values/` + `values-hi/` generated by `android/tools/gen_strings.py`; per-app language picker |

## Architecture (PRD §9) — stack

| PRD | Implemented | Note |
|---|---|---|
| Kotlin, Jetpack Compose, MVVM + repository, Coroutines/Flow, Jetpack Navigation, Hilt | ✅ same | |
| Backend “Firebase or equivalent managed backend” | **Supabase** (PostgreSQL) | Chosen because it is free with server-side functions. Firebase Cloud Functions + Storage now require the paid Blaze plan; Supabase gives PostgreSQL functions (server-authoritative pricing), auth, storage, realtime and edge functions on the free plan. PRD §9.4 already names Supabase as the relational option. |
| Firebase Auth for admin | Supabase Auth | email + password, optional TOTP MFA |
| FCM, Crashlytics | ✅ Firebase (optional, free Spark plan) | auto-enabled when `google-services.json` is provided |
| Cloud Storage for images | Supabase Storage (`menu-images` bucket) | WebP-compressed in browser |
| React/Next.js admin | React + Vite (static) | free static hosting (GitHub Pages / Netlify) |
| Google Maps deep links | ✅ | `Intents.directions` |
| Privacy-conscious analytics | ✅ own `analytics_events` table | random per-session id, whitelist of event names |
| §9.3 server-authoritative prices & states, RBAC, no admin creds in app, offline cache, HTTPS, audit, no secrets | ✅ | migrations 2 & 5; `network_security_config.xml` |
| §9.4 cost control: listeners only on active screens, paginated history, bounded reads | ✅ | Realtime only on Orders/Kitchen pages; `admin_list_orders` limit/offset; app polls only while order screen is visible |

## Database & API (PRD §10–11)

| Requirement | Status | Where |
|---|---|---|
| MenuItems, Categories, Customers*, Orders, RestaurantSettings, AuditLogs; paise integers | ✅ | `20261120000001_schema.sql` (*guest checkout: customer data is stored on the order, no profile table) |
| Settings default operational switches false | ✅ | schema defaults; unit + SQL tests |
| AuditLogs: who/what/old/new/when; no editing historical prices | ✅ | `audit_row` trigger, `protect_order_money` trigger, immutable `order_items`, `order_adjustments` |
| Endpoints `/v1/...` | ✅ (PostgREST equivalents) | mapping comment in `SupabaseApi.kt` |
| Rules 1–10: validate items, reject unavailable, server totals, idempotency keys, unique order numbers, snapshots, transitions, auth, audit, notify after commit | ✅ | `place_order`, `apply_status`, `private.valid_transition`, push trigger reads committed row |
| Transition table incl. ACCEPTED → CANCELLED subject to policy | ✅ | staff can cancel; customer only if `allow_cancel_after_accept` |

## Security, privacy, legal (PRD §12)

| Requirement | Status | Where |
|---|---|---|
| Minimal data, no contacts/SMS/call log/location/IDs | ✅ | `AndroidManifest.xml` (INTERNET, NETWORK_STATE, POST_NOTIFICATIONS only) |
| HTTPS, access control on personal data | ✅ | cleartext disabled; kitchen view hides phone numbers |
| Admin auth, server-side roles, deactivation, extra verification for sensitive ops | ✅ | Staff page, password re-entry for settings/staff changes, optional MFA |
| Privacy policy (in app + store listing) | ✅🧾 | `admin-dashboard/public/privacy.html` template; link in app More menu |
| FSSAI number display, tax/invoicing, DPDP review | 🧾 | Settings fields; shown in app info + order terms; professional review required |
| Data retention / deletion | ✅ | `purge_old_personal_data()` daily via pg_cron; “Clear my data” in app |
| Food info approval; no unverified claims | ✅🧾 | `details_verified` flag + dashboard warning; seed copy makes no such claims |

## Non-functional (PRD §13)

Android 8.0+ (minSdk 26) · target API 36 · offline menu (Room) · WebP images + Coil cache · screen-reader labels on
icons/veg marks · light & dark themes · small to large screens · Asia/Kolkata · INR · server-side validation. ✅

## Analytics (PRD §14) ✅ — all 14 events

`app_opened, home_viewed, menu_opened, item_viewed, item_added_to_cart, cart_viewed, checkout_started` (app) ·
`order_submitted, order_accepted, order_rejected, order_completed, order_cancelled` (server, so they can’t be faked
or lost) · `directions_clicked, call_clicked` (app). Funnel shown in Reports as measured counts, not targets.

## QA (PRD §15) ✅

Automated: 50+ SQL assertions (`supabase/tests`), Kotlin unit tests (`BusinessRulesTest`), dashboard unit tests
(`vitest`), Android lint — all run in GitHub Actions. Manual script: `docs/LAUNCH_CHECKLIST.md` §D.

## Deployment, plan, risks, ownership (PRD §16–21)

`docs/PLAY_STORE_LISTING.md`, `docs/SETUP_GUIDE.md`, `.github/workflows/*`, `docs/LAUNCH_CHECKLIST.md`.
Ownership (PRD §19): all accounts (GitHub, Supabase, Firebase, Play Console, signing key) are created **by the owner**
following the setup guide; developers get role-based access only.

---

# Restaurant ERP (owner's expanded scope, Nov 2026)

Owner direction: **free website + Android APK are the focus; Play Store not needed; everything on free services.**

| Module | Status | Where |
|---|---|---|
| Free customer website (PWA, installable, offline menu, Hindi/English, QR table ordering, order tracking, reorder) | ✅ | `admin-dashboard/index.html`, `src/site/` |
| Free APK distribution | ✅ | `.github/workflows/release-apk.yml` → GitHub Releases `swad-sangam.apk` |
| POS: dine-in / takeaway / delivery / counter, table-wise, multiple open bills, hold & resume (add items), KOT print & reprint | ✅ | `pos_create_order`, `pos_add_items`, `PosPage` |
| Split, partial and credit (khata) payments; cash change; dynamic UPI QR; card/UPI references; idempotent | ✅ | `record_order_payment`, `PaymentModal` |
| Discounts (bill-level) and complimentary items — manager/owner only, audited | ✅ | `pos_set_discount`, `price_lines` |
| Receipts / tax invoice / bill of supply, 58 & 80 mm, duplicate marking, invoice numbering | ✅ | `get_receipt`, `components/Receipt.ts` |
| Cash register: opening cash, expected vs counted, shortage/excess posted | ✅ | `open/close_cash_session` |
| Refunds and corrections posted to ledger | ✅ | `order_adjustments` trigger |
| Double-entry ledger: chart of accounts, journal, balanced & immutable, reversal, manual journal, opening balances | ✅ | migration 7 |
| Trial balance, P&L, balance sheet, cash flow, account & party ledgers, CSV/Excel export, print-to-PDF | ✅ | migration 11, `AccountingPage` |
| Customer ledger (khata): credit limits, receipts, statements, FIFO ageing, WhatsApp reminder link | ✅ | `PartiesPage` |
| Supplier ledger: purchase invoices, partial payments, statements, ageing | ✅ | `record_purchase`, `record_supplier_payment` |
| GST: registration/scheme settings, CGST/SGST split, inclusive/exclusive, SAC, input credit option, GST summary | ✅ (verify with CA) | settings + `gst_report` |
| Inventory: items, units (kg/g/l/ml/pc conversion), opening stock, receipts, wastage, physical count, low-stock alerts, batch & expiry, weighted-average valuation, movement history | ✅ | migration 8, `InventoryPage` |
| Recipes, auto stock deduction once per completed order, recipe cost, food cost %, margin | ✅ | `consume_stock`, `menu_costing` |
| Expenses: categories, tender, vendor, bill photo (private storage), approval, journal posting, recurring reminders | ✅ | `ExpensesPage` |
| Employees, attendance, overtime hours, salary advances, monthly payroll, payslips | ✅ | migration 10, `PayrollPage` |
| Tables (status, cleaning) and reservations / waiting list | ✅ | `TablesPage` |
| Roles: Owner, Manager, Cashier, Waiter, Kitchen, Accountant (+Staff) enforced in the database | ✅ | `private.role_matches` |
| Owner overview: sales, orders, AOV, cash/UPI/card, receivables, payables, expenses, est. gross profit, food cost %, top items, low stock, KOTs, attendance | ✅ | `erp_overview` |
| Business insights: busiest hours, dish profitability, wastage, supplier rate comparison, break-even | ✅ (estimates labelled) | `business_insights` |
| Daily encrypted backups | ✅ | `.github/workflows/backup.yml` |
| Hindi owner guide | ✅ | `docs/OWNER_GUIDE_HINDI.md` |

## Not built yet (honest list — next phases)

- Fully **offline POS** with local queue and background sync (POS is online-first with safe retries — ADR-6).
- Split one bill into several / merge two bills / table transfer.
- Purchase requests → purchase orders → quotations → GRN workflow (purchases are recorded directly as invoices); purchase returns / debit & credit notes.
- Kitchen stations, item-level preparation status, voice alerts in the kitchen.
- Loyalty points, birthday offers, customer lifetime value; automatic WhatsApp/SMS sending (needs a paid/approved provider — not added without owner approval).
- Leave balances and shift rosters; bank reconciliation screen; FIFO stock valuation option (weighted average only).
- Customer display screen; direct ESC/POS printer driver (printing uses the browser print dialog, which works with thermal printers that have a driver).
- Android app UI polish with the external design skill repositories the owner listed (not fetched in this round).

---

# POS upgrade (OrderRestro feature parity, Nov 2026)

The owner asked for OrderRestro (NodeDR, AGPL-3.0) features and a matching clean, mobile-first POS design. Its README
feature list was used as a checklist; **no code was copied** (ADR-5). Status against that list:

| OrderRestro module | Swad Sangam | Where |
|---|---|---|
| POS & billing (dine-in/takeaway, merge, split, discounts, refunds, GST incl./excl.) | ✅ incl. split bill, merge bills, split tender | `pos_split_bill`, `pos_merge_bills`, `PosPage` |
| Tables & reservations (floor, waitlist, QR table ordering with live table code, table transfer) | ✅ | `open_table_session`, `pos_transfer_table`, `TablesPage` |
| Kitchen / KDS (stations, prep tracking, priority, timers, reprint) | ✅ | `kds_board`, `kds_set_item_status`, `KitchenPage` |
| Online ordering (QR, no duplicate orders, call waiter, request bill) | ✅ | website + `request_service`, POS banner |
| Inventory & recipes, procurement, suppliers | ✅ (PO/GRN workflow not built) | `InventoryPage` |
| CRM: loyalty points | ✅ earn on close, redeem at POS | `pos_redeem_points` |
| Staff: attendance, payroll, payslips | ✅ (clock-in kiosk, tip pool not built) | `PayrollPage` |
| Accounting: cash close with variance, P&L, balance sheet, tax summary | ✅ (budgets, bank reconciliation not built) | `AccountingPage` |
| Analytics: peak hours, food cost & margins, exports | ✅ | `ReportsPage`, `InsightsPage` |
| UPI QR for exact amount | ✅ | POS payment |
| Security: RBAC, audit log | ✅ (PIN quick-switch not built; optional TOTP via Supabase) | |
| Design: sidebar nav, dark mode, phone bottom bar, POS tabs on phones | ✅ | `App.tsx`, `styles.css` |
| Not built: offline LAN server, multi-branch, gift cards, tips, customer display, ESC/POS USB, delivery zones/drivers, marketing campaigns, maintenance, documents | — | next phases |

## Customer website redesign (ElitaleRestro-style guest flow, Nov 2026)
`tirforge/restro` (ElitaleRestro, AGPL-3.0) was used as a feature reference only — no code copied.
Built: sticky search + scroll-spy category tabs, popular carousel, card dishes with photo and inline ADD → stepper,
sold-out badges, add-on/variant bottom sheet, cart as bottom sheet with order-type switch, table picker when the QR has
no table (`public_tables()`), table code field, cash/UPI choice, order tracking with progress steps and an exact-amount
UPI QR + "Pay with UPI app" link (staff confirm receipt), call waiter / bill / water, Hindi/English, dark mode.
Not built: phone OTP login (needs a paid SMS provider).

## Premium customer website (Zomato/Swiggy-style, Nov 2026)
- Research: the owner's Justdial page returned "Access Denied" to automated access and the Google share link only resolves
  to a JavaScript search page, so **no menu, prices, photos or reviews were taken from them**. Menu items/prices remain the
  owner-supplied PRD seed, still flagged "Unverified" in admin until the owner ticks "Details verified".
- Photos: each seeded dish has a dish-specific **representative** photo from Wikimedia Commons (CC0/CC BY/CC BY-SA), shown
  with a "Representative" label and credited at `/photo-credits.html`. Uploading a real photo in Admin → Menu replaces it
  and removes the label automatically (`menu_items.image_is_representative`, `image_credit`). Banner photo and "cost for
  two" are editable in Settings (`hero_image_url`, `cost_for_two_paise`; cost for two hidden until set).
- UI: hero banner, info card with rating/open status/call/directions/app, order-mode switch (dine-in/takeaway/delivery,
  only modes enabled in Settings), "What's on your mind?" category circles, recommended carousel, filters (veg, non-veg,
  under ₹100, bestseller, price sort), dish detail sheet (portion, prep time, allergens, add-ons, quantity), desktop
  two-column layout with live order summary. Ordering, UPI, tracking, QR table ordering unchanged (same RPCs → POS/KOT).

## Real menu import (owner's menu card, Nov 2026)
- Source: menu-card photos and a storefront photo shared by the owner. 199 dishes in 22 categories, prices exactly as
  printed — `supabase/menu/swad_sangam_menu.py` → `swad_sangam_menu.sql` (idempotent; keeps photos, availability and Hindi
  names set by the owner). Applied to the live database.
- Two prices without a printed meaning ("₹40 | ₹70") are modelled as **Half / Full** and the dish stays "Unverified" so the
  owner can rename the variant (e.g. "1 pc / 2 pc") in Admin → Menu. Printed variants (Dry/Gravy, Sweet/Salty,
  Salted/Spicy, Fried Rice/Noodles, Spaghetti/Penne/Macaroni, Boil/Bhurji) use their printed names. Veg/Egg/Chicken
  variants are separate dishes so the veg/non-veg mark stays correct.
- Variant groups (migration 15): `options[].group` + `menu_items.variant_base_label`; the database rejects two options of
  the same group on one line. Website, POS and KOT show the chosen variant.
- Branding: storefront photo is the website banner (genuine restaurant photo); orange + charcoal colours from the
  signboard and menu card.
