# Verified audit + UI/UX modernization spec: what was done (24 Nov 2026)

Source: the two specs the owner shared ("Verified Audit & Bug Fix Specification" and "Deep UI/UX Audit & Modernization Plan").
Everything here is free (no paid SMS, no payment gateway). ✅ = done and tested, ◐ = partly done, ⏳ = needs the owner, ✗ = not done (reason given).

## P0 — critical
| # | Item | Status | Where |
|---|---|---|---|
| P0-1 | Delivery could skip "Out for delivery"; orders completed without collecting money | ✅ READY delivery orders only offer "Out for delivery". Completing an unpaid order is blocked in the database (owner/manager may override with a reason, which is audit-logged). New handover dialog: amount to collect, Cash/UPI/Card/Bank, UPI QR, reference. `complete_order_with_payment` records the payment, PAID and COMPLETED in one transaction | migration `0018`, `HandoverModal.tsx`, `orderFlow.ts` |
| P0-2 | Free delivery code (OTP) + rider role + assignment | ✅ `DELIVERY` role; 4-digit code created on accept, shown only to the customer (app + website); the rider must type it (5 wrong tries lock it; owner/manager override with reason). Assign rider from the order card; rider screen "My deliveries" (COD amount, Call, Directions, Start, Deliver) | `0017`, `0018`, `DeliveryPage.tsx`, `OrderScreen.kt`, `SiteApp.tsx` |
| P0-3 | Waiter could take payments / open-close the register; unguarded routes | ✅ Database blocks waiters in `update_payment_status`, cash register functions, handover. `/kitchen` and `/menu` guarded; waiters no longer see Take payment / Close bill / cash bar; expenses not for waiters | `0018`, `App.tsx`, `PosPage.tsx`, `OrderDetail.tsx` |
| P0-4 | Duplicate POS bill on retry | ✅ one idempotency key per bill attempt, renewed only after success or Clear | `PosPage.tsx` |
| P0-5 | Wrong tender on app orders, refund always from cash, broken correction UI | ✅ tender chosen when marking paid and when refunding; refunds leave the right account; refunds cannot exceed the amount paid; "reduce bill / add charge" corrections | `0018`, `OrderDetail.tsx` |
| P0-6 | Loyalty matched by customer name | ✅ matched by customer id (`customerId` added to staff order data) | `0018`, `PosPage.tsx` |
| P0-7 | "Clear my data" kept the login | ✅ also logs out | `MoreScreen.kt` |
| P0-8 | No top-bar notifications | ✅ Works **without Firebase**: every 20 s while the app is open, about every minute in the background while an order is active. Push (when Firebase is set up) is now data-only so tapping opens the order; text fits pickup / delivery / dine-in; permission asked when checkout opens. Staff/Admin app: native notification with sound for new orders and new deliveries, on every page (not only Orders) | `AppViewModel.kt`, `OrderStatusWorker.kt`, `PushService.kt`, `push-order-status`, staff `MainActivity.kt`, `alert.ts` |

## P1
| # | Item | Status |
|---|---|---|
| P1-1 | Staff app offline screen vanished | ✅ |
| P1-2 | PDF upload / camera in staff app | ✅ all accepted types, several files, "Take photo" |
| P1-3 | Service worker returned HTML for API errors, cached admin | ✅ v3 |
| P1-4 | Offers/coupons time shifted 5 h 30 m on save | ✅ India time both ways |
| P1-5 | POS add-ons without group; no live sync | ✅ add-on ticks; Realtime (debounced) + 20 s fallback |
| P1-6 | Settings ₹0 overwrite; hidden errors; payroll race; one UPI QR | ✅ validated money fields, GST scheme / receipt width dropdowns, visible errors (tables, expenses, khata statement, stations), controlled payroll inputs, QR for all UPI rows |
| P1-7 | Merge with big discount, points lost on cancel, negative payroll | ✅ |
| P1-8 | Customer app: coupon hidden at checkout, Rate button stayed, "Pay at restaurant" on delivery, Non-veg chip in Pure Veg, reorder unavailable options | ✅ all five |
| P1-9 | Review spam, print injection, CSP, password length, legal placeholders, staff-signup spam | ✅ reviews need a completed order; print escaping; CSP meta in the build; 8+ characters for new accounts. ⏳ legal placeholders need the owner's details (legal name, date, grievance officer). ✗ staff sign-ups: left as is — they only create an inactive row the owner must approve |

## P2
| # | Item | Status |
|---|---|---|
| P2-1 | Slow profit report | ✅ recipe cost computed once |
| P2-2 | Kitchen display re-render, sound, loop | ✅ timer per ticket, sound remembered + "muted" warning, parallel "All ready", no SERVED→PENDING loop, Undo |
| P2-3 | 200-order refetch on every change | ✅ debounced |
| P2-4 | Print WebView leak; browser confirm/prompt | ✅ in-app dialogs everywhere (admin + website) |
| P2-5 | APK size / release config | ✅ release customer APK is 3.3 MB (R8 removes unused icons, so the icon library was kept); staff app shrinks resources; key-password fallback |
| P2-6 | POS search all categories, report month time zone, account claim errors / no reload, theme-color, brand | ✅ |
| Extra | Unaccepted order escalation | ✅ beeps every minute while a new order has waited 2+ minutes |
| Extra | Rider COD settlement report | ◐ rider sees today's delivered total; payments record who received them (`received_by`). A cashier hand-over report is still to do |

## UI/UX modernization plan
- **Brand** ✅ Swad Sangam's own palette everywhere: tandoor crimson `#B83227`, saffron `#C27C0E` (stars; `#E09619` failed contrast), warm porcelain `#F8F6F3`; dark mode coral `#FF7A6B`. All pairs checked for WCAG contrast.
- **Website CSS** ◐ one colour token block (conflicting dark values removed), 90 unused rules (40 dead classes) deleted (41.5 KB → 32 KB), checked before/after with screenshots. The remaining sections were not rewritten into a new file, to avoid layout regressions.
- **Admin dark mode** ✅ tables/attendance colours are theme-aware; dark button text rule only for filled buttons.
- **Android** ✅ light header, three curated rows + full-menu button, floating cart bar, live order pill, inline − qty + stepper, "Customizable", category quick-jump, pull to refresh, edge-to-edge dish page, cart bin-at-1 + clear cart + savings banner, 2-tap repeat checkout, delivery code card, progress bar, Active/Past orders with badges, profile header, password show/hide, address delete confirm, messages above the keyboard, tabular price digits. ✗ shimmer skeletons (spinner kept).
- **Admin/Staff** ✅ new-order strip + sound on every page, COD/PAID pills, phone order tabs, maps links, rider screen, POS tile counts + APP/POS badge + sticky "View cart" bar, KDS tap states, login logo + password toggle, staff-app loading screen. ✗ compact cash bar; skeleton loaders for admin tables.

## Verification
- `supabase/tests/run_local.sh`: backend, ERP, POS, customer and the new **delivery** tests pass (default + strict privileges).
- Live database: both migrations dry-run inside a transaction and rolled back without errors. They are applied when the PR is merged.
- Web: `tsc`, `vitest` (6/6), `vite build`; Playwright screenshots of the website and admin (light/dark, phone/desktop).
- Android: `assembleDebug`, `assembleRelease`, `lintDebug` (0 errors), `testDebugUnitTest` (10/10), staff app builds + lint (0 errors).
- **Not tested on a real phone** (no emulator here): notifications, camera upload and the handover flow need one real end-to-end test (see the checklist in the spec).
