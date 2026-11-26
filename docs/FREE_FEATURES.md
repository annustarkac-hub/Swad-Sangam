# Swad Sangam — free food-ordering features (status)

Everything below runs on free services only: GitHub Pages (website + admin), Supabase free plan (database, login, storage),
GitHub Releases (Android APK). No payment gateway, no SMS, no paid maps or notification service.

Website: `https://annustarkac-hub.github.io/Swad-Sangam/` · Admin: `…/admin.html`

| # | Feature | Status | Where |
|---|---|---|---|
| 1 | Sign up / login (email + password, no OTP) | ✅ | Website → 👤 Login |
| 1 | Name + mobile number in profile, edit profile | ✅ | Website → 👤 Account |
| 1 | Multiple saved delivery addresses (up to 10, one default) | ✅ | Account → Saved addresses; also picker in cart |
| 1 | Logout, forgot password (reset link by email) | ✅ | Account |
| 2 | Categories, photos, name, description, price, veg/non-veg, search, veg/non-veg filter | ✅ | Website → Menu |
| 3 | Add/remove, quantity +/−, subtotal and total update automatically | ✅ | Cart |
| 4 | Item-wise bill, subtotal, delivery charge, packaging, coupon discount, GST (configurable), final amount | ✅ | Cart bill + order page bill (calculated on the server) |
| 5 | Place order, Cash on Delivery / cash at counter, confirmation, unique order number | ✅ | Cart → Place order |
| 5 | My orders + history (on any phone when logged in), reorder | ✅ | 🧾 My orders |
| 5 | Cancel order (before acceptance; after acceptance only if the owner allows) | ✅ | Order page |
| 6 | Delivery address + landmark, delivery charge set by admin | ✅ | Admin → Settings |
| 6 | Status: Accepted → Preparing → Ready for pickup / Packed → Out for delivery → Delivered | ✅ | Order page (updates every 10 s) |
| 6 | Estimated time (admin types minutes when accepting) | ✅ | Admin → Orders → Accept |
| 7 | Admin login, new-order sound + list, accept / reject, update status | ✅ | Admin → Dashboard / Orders |
| 7 | Add / edit / archive dishes, prices, photos, categories | ✅ | Admin → Menu |
| 7 | Delivery charges, GST, packaging, minimum order | ✅ | Admin → Settings |
| 7 | Coupons and offers | ✅ | Admin → Offers & coupons |
| 7 | Daily / monthly sales reports, item-wise sales, CSV export | ✅ | Admin → Reports |
| 8 | WhatsApp link, phone, Google Maps directions link | ✅ | Website → Info (WhatsApp shows when the number is set in Settings) |
| 8 | Privacy policy, terms, about us, photo credits | ✅ | Website → Info |
| 8 | Customer rating (1–5 ★ + comment) after a completed order; average shown on Info | ✅ | Order page / Info |

“Delete” of a dish is **Archive** on purpose: old bills still need the dish name and price.

## Needs a paid service later (not built now)

| Feature | Why it is not free | What to add later |
|---|---|---|
| Online UPI / card payment with automatic confirmation | Needs a payment gateway (Razorpay, Cashfree, PhonePe PG) — fee per payment | Gateway account + server webhook |
| SMS OTP login | SMS costs money per message | MSG91 / Twilio / Firebase phone auth (paid after a small free quota) |
| Live rider location on a map | Google Maps live APIs are paid | Maps SDK + rider app |
| Automatic distance-based delivery charge | Needs a paid distance API | Google Distance Matrix |
| Push notifications when the website is closed | Possible free with Firebase Cloud Messaging, but needs a Firebase project and a key | Firebase project (free) |

## Free add-ons the owner switches on (steps in OWNER_TODO_HINDI.md, sections G and H)

| Add-on | What it fixes | Free limit |
|---|---|---|
| Brevo SMTP in Supabase Auth | Sign-up / password-reset emails no longer stop after ~2 per hour | 300 emails/day |
| UptimeRobot (website, admin, backend `functions/v1/health` keyword `SWAD-OK`) | Owner is alerted within 5 minutes when anything is down | 50 monitors, 5-minute checks |

## One setting the owner should check (Supabase dashboard)

Supabase → **Authentication → Sign In / Providers → Email → “Confirm email”**.
- **On** (current): a customer must click a link in an email before logging in. Supabase's free email allows only about 2 emails per hour, so many sign-ups in one hour will fail until the next hour.
- **Off**: customers can log in right after sign-up. Recommended for a small restaurant. Staff are still safe: a new account gets **no** staff access until the owner activates it in **Staff logins**. Activate only people you know.
