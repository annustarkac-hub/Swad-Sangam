# State machines (where each status's rules live)

Every status in Swad Sangam has one transition table. The database enforces it with a function or trigger, so the
rule holds for every writer: RPCs, other triggers, and direct table edits from the admin. Screens only *offer* the
allowed moves (and say why when a move is refused). Tests: `supabase/tests/state_machine_tests.sql`.

| Status | Allowed moves | Enforced by | Screens |
|---|---|---|---|
| **Order** `order_status` | PLACED→ACCEPTED/REJECTED/CANCELLED · ACCEPTED→PREPARING/CANCELLED · PREPARING→READY · READY→COMPLETED (pickup, dine-in) · READY→OUT_FOR_DELIVERY→COMPLETED (delivery) | `private.valid_transition`; who may do what: `private.allowed_next` → `nextStatuses` in staff order data; completing needs payment (`update_order_status`, `complete_order_with_payment`) | `orderFlow.ts` shows only `nextStatuses` |
| **Payment** `payment_status` | PENDING→PAID/FAILED · FAILED→PAID · PAID→REFUND_PENDING/REFUNDED · REFUND_PENDING→REFUNDED. FAILED = money never collected (cancelled, rejected or merged; the cancel reason says which) | `private.payment_transition_ok` + trigger `trg_guard_payment_status` | OrderDetail payment buttons |
| **Kitchen item** `prep_status` | PENDING→PREPARING/READY · PREPARING→READY/PENDING · READY→PREPARING (only while the order is not yet Ready) · READY→SERVED. VOID reserved, never left | `private.prep_transition_ok` in `kds_set_item_status` | Kitchen display: tap forward, ↶ undo |
| **Table** `restaurant_tables.status` | FREE→OCCUPIED/RESERVED · RESERVED→OCCUPIED/FREE · OCCUPIED→CLEANING/FREE · CLEANING→FREE/OCCUPIED/RESERVED. Never FREE/CLEANING/RESERVED while a bill is open; FREE/CLEANING clears the table code | trigger `trg_guard_table_status`; orders keep tables in sync (`trg_sync_table_from_order`, also for app dine-in orders); `close_table_session` → CLEANING | Tables page dropdown lists only allowed moves |
| **Reservation** | BOOKED→WAITING/SEATED/CANCELLED/NO_SHOW · WAITING→SEATED/CANCELLED/NO_SHOW · SEATED→COMPLETED. New bookings start BOOKED or WAITING | trigger `trg_guard_reservation_status` | Tables page buttons |
| **Delivery code** | none → created on accept → 0–4 wrong tries → locked at 5 → (owner/manager `unlock_delivery_code`) → delivered | `complete_order_with_payment`, `unlock_delivery_code`; a rider can be swapped but not removed while out for delivery | Order card: "Unlock code" |
| **Expense** | PENDING→APPROVED/REJECTED | `decide_expense` | Expenses |
| **Journal entry** | POSTED→REVERSED | `reverse_journal`, immutability trigger | Accounts |
| **Service request** | OPEN→DONE (one open per table and type) | `complete_service_request`, unique index | POS |
| **Cash register** | closed ⇄ open (one open at a time) | `open_cash_session` / `close_cash_session`, unique index | POS cash bar |
| **Staff account** | pending (no role / inactive) → active → deactivated; customers have no staff row | `set_staff_member`, `handle_new_user` | Staff logins |
| **Can order now** | open hours × not temporarily closed × accepting orders × some order type on | `restaurant_status().canOrder` (same test as `place_order`) | website, Android cart/home |

## In the apps
- **Android checkout retry key**: kept for a retry only while the order is exactly the same (`orderFingerprint`);
  any change to the cart, type, table, address, payment or coupon gives a new key. Tested in `OrderFingerprintTest`.
- **Android notified status** (`notifiedStatus` per order): compare-and-mark runs under one lock
  (`OrderRepository.notifyLock`), shared by the in-app check, the background job and push → one notification per change.
- **POS selected bill**: if it is closed on another device, the cashier is told and the cart becomes a new bill visibly.
- Login / signup / forgot-password modes exist separately in the admin login, the website and the Android app (one per
  platform, screen-only state — left as is on purpose).
