# Launch checklist (PRD §15.5, §20, §22)

## A. Owner approvals before development is “live” (PRD §22)

The public information is enough to build the product but **not** enough to switch on live ordering.

- [ ] 1. Official restaurant logo and branding (replace placeholder icon + colours if different)
- [ ] 2. Complete, current menu with confirmed prices (Dashboard → Menu)
- [ ] 3. Actual dish photographs uploaded (no stock photos presented as menu items)
- [ ] 4. Confirmed operating hours (Settings → tick “Opening hours confirmed”)
- [ ] 5. FSSAI licence/registration number (14 digits) entered in Settings
- [ ] 6. GST and invoicing configuration confirmed by accountant (tax rate, prices include tax?)
- [ ] 7. Pickup ordering permitted? → Settings → Pickup
- [ ] 8. Delivery offered? → leave OFF unless the restaurant delivers
- [ ] 9. Delivery radius and charges (if delivery)
- [ ] 10. Order acceptance process and responsible staff (Staff page roles)
- [ ] 11. Cancellation and refund policy (edit `terms.html`, Settings → “Customers may cancel after acceptance”)
- [ ] 12. Payment methods (Cash / UPI at restaurant + UPI ID)
- [ ] 13. Official customer support email
- [ ] 14. Business ownership and Play Store publishing details (account in owner’s name)

### Unresolved launch decisions (owner to tick one each)

1. Customers ordering through the app? ☐ Pickup ordering ☐ Menu + call-to-order only ☐ Both, configurable *(built-in: the switch is in Settings)*
2. Own delivery service? ☐ Yes ☐ No, pickup only ☐ Not yet decided *(delivery stays OFF until decided)*
3. Payment methods at launch? ☐ Cash only ☐ Cash + UPI at restaurant ☐ Online UPI + cash *(online = Phase 2, needs gateway)*
4. Who manages incoming orders? ☐ Owner ☐ Manager/staff ☐ Multiple staff *(assign roles on Staff page)*
5. Languages? ☐ English ☐ English + Hindi *(built)* ☐ + Garhwali *(only with a professional translation)*

**Release gate (PRD §5):** if nobody can reliably watch and accept app orders, launch with ordering OFF —
the app then works as a menu + call-to-order app. Never leave checkout on unattended.

## B. Definition of Done (PRD §15.5)

- [ ] All MVP features pass functional tests (section D)
- [ ] No critical/high security defects (backend tests ✅ in GitHub Actions)
- [ ] Menu prices approved by the owner
- [ ] Restaurant operating details verified
- [ ] Admin dashboard operational on the restaurant phone/tablet
- [ ] Order acceptance tested **inside the restaurant**
- [ ] Notifications tested on physical devices
- [ ] Privacy policy published (`/privacy.html`) and linked in Settings
- [ ] Google Play Data safety form completed accurately
- [ ] FSSAI and business details verified
- [ ] Store listing assets prepared
- [ ] Signed release AAB generated
- [ ] Play Console review completed

## C. Launch day (PRD §20)

1. Confirm the restaurant is ready to accept app orders today.
2. Verify all published menu prices.
3. Check item availability (tap Available/Unavailable on the Menu page).
4. Log into the admin dashboard on the counter phone/tablet; click **Enable sound & notifications**; keep **Orders** open.
5. Turn on **Accepting app orders** + **Pickup**; place a test order from a customer phone.
6. Accept the test order on the dashboard.
7. Confirm the customer phone shows the correct status (and notification, if Firebase is set up).
8. Move it through Preparing → Ready → Completed.
9. Mark payment received; check the order appears correctly in **Reports**.
10. Enable customer ordering publicly (QR codes, signage, WhatsApp groups).

## D. Functional / negative / security test script (PRD §15)

Functional: open app · browse categories · search · view dish · add to cart · change quantity · remove item ·
verify subtotal · checkout · receive order number · accept on dashboard · preparing · ready · complete ·
notification · call restaurant · open directions.

Negative: no internet (menu still shows from cache) · slow internet · double-tap “Place order” (only one order) ·
make a dish unavailable during checkout · change a price during checkout (app asks to review) · invalid phone ·
empty cart · pause ordering while a customer is in checkout · admin session expiry · invalid status change ·
deny notification permission · (Phase 2: payment timeout / duplicate webhook).

Security (automated in `supabase/tests/backend_tests.sql`, run by **Backend tests** workflow): customers can’t read
orders or the dashboard · staff can’t change owner settings · customers can’t change prices · customers can’t see or
change another customer’s order · admin functions reject anonymous calls · no secrets in the APK (only the public
anon key) · personal data not logged (HTTP logging is headers-only and debug-only).

Devices: entry-level, mid-range and large-screen Android phones; one Android 8/9 phone if possible; current Android.
Weak mobile data, Wi-Fi, switching apps, force-closing the app, low storage.

## E. First 30 days

| Days | Focus |
|---|---|
| 1–7 | Watch every order and staff response time (target: acknowledge within 2 minutes) |
| 8–14 | Find checkout friction (Reports → App funnel) |
| 15–21 | Review popular items and cancellations (Reports) |
| 22–30 | Evaluate repeat customers (Reports → returning customers) and whether the app adds business |

Then mark genuinely best-selling dishes as **Popular** (only with sales data or owner approval).

## F. Monthly maintenance

- [ ] Update dependencies (open the latest Android/dashboard workflow run — it must be green)
- [ ] Review GitHub security alerts (Dependabot)
- [ ] Verify menu prices
- [ ] Check crash reports (Firebase Crashlytics / Play Console)
- [ ] Read customer feedback (Dashboard → Feedback)
- [ ] Check Supabase usage page (stays within free plan)
- [ ] Export Reports CSV as a business record backup
- [ ] Place one test order end-to-end
