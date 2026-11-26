# Google Play submission pack (PRD §16)

Only advertise features that are **actually enabled** in the published version (PRD §16.3).

## App identity

| Property | Value |
|---|---|
| App name | Swad Sangam |
| Package / application ID | `in.swadsangam.restaurant` (confirm it is available in Play Console before first upload) |
| Category | Food & Drink |
| Release format | Android App Bundle (`app-release.aab` from the **Android app** workflow) |
| Target API | 36 (Android 16) — meets Play’s requirement from 31 Aug 2026 |
| Minimum Android | 8.0 (API 26) |
| Default language | English (India) — Hindi strings included |
| Contact email | [restaurant support email] |
| Privacy policy URL | `<dashboard address>/privacy.html` |

## Short description (max 80 characters)

> Affordable North Indian thalis, snacks & more at Swad Sangam, Srinagar.

(PRD suggestion, 87 chars, is slightly too long for Play’s 80-character limit: “Explore affordable North Indian
food, thalis, snacks and more at Swad Sangam, Srinagar.”)

## Full description

```
Swad Sangam — Good Food. Honest Prices.

Explore the Swad Sangam menu from Bhaktiyana, near NIT Ground, Srinagar (Uttarakhand).

• Discover affordable vegetarian and non-vegetarian meals
• Browse thalis, parathas, momos, snacks and North Indian favourites
• See current dish prices and availability — no sign-up needed
• Clear veg / non-veg marks on every dish
• Call the restaurant with one tap
• Get directions to our Bhaktiyana location
• Place pickup orders where available and track their status
• Reorder your favourites from your order history
• English and Hindi

Prices are set by the restaurant and are always confirmed when you order.
Pay in cash or UPI at the restaurant — the app never asks for card details or UPI PIN.
```
Remove the “Place pickup orders…” and “Reorder…” lines if ordering is not switched on at launch.

## Graphics

| Asset | Size | Status |
|---|---|---|
| App icon | 512 × 512 PNG | `docs/store-assets/icon-512.png` (approved Sangam S; Admin/Staff versions in `docs/brand/playstore/`) |
| Feature graphic | 1024 × 500 PNG | `docs/store-assets/feature-graphic.png` (Sangam S + menu photos) |
| Phone screenshots | 2–8, 16:9 or 9:16 | Take on a real phone after the menu photos are uploaded (Home, Menu, Dish, Cart, Order status, Info) |

## Data safety form — suggested answers

Verify against the final build and any SDKs you enable (Play requires the form to match the app **and** its SDKs).

| Question | Answer |
|---|---|
| Does the app collect or share user data? | **Yes, collects.** Does **not** share (service providers processing on our behalf are not “sharing”). |
| Encrypted in transit? | **Yes** (HTTPS only) |
| Can users request deletion? | **Yes** — via contact in the privacy policy; local data via *More → Clear my data* |
| Personal info → **Name** | Collected, required to place an order, purpose: *App functionality* |
| Personal info → **Phone number** | Collected, required to place an order, purpose: *App functionality* |
| Personal info → **Address** | Only if delivery is enabled: collected, optional, *App functionality* |
| Financial info → **Purchase history** | Collected (order items/amounts), *App functionality* |
| App activity → **App interactions** | Collected (anonymous events), *Analytics* |
| App info and performance → **Crash logs / Diagnostics** | Collected **only if Firebase Crashlytics is enabled**, *App functionality / Analytics* |
| Device or other IDs | Collected **only if Firebase is enabled** (FCM token for notifications), *App functionality* |
| Location, contacts, photos, messages, payment card | **Not collected** |
| Account creation | **No** customer accounts (guest checkout) — account deletion requirement does not apply |

## Content rating questionnaire

Category: *Reference, News, or Educational / Other* → no violence, no sexual content, no gambling, no user-generated
public content, no location sharing. Expected rating: **Everyone / 3+**. Note: the menu lists non-vegetarian food,
which is not a rated content category.

## App access (for the reviewer)

“No login is required. All features are available without an account. Ordering may show ‘Please call to order’
when the restaurant has paused app orders.”

## Ads

The app contains **no ads**.

## Testing track rules (PRD §16.4)

- New **personal** developer accounts (created after 13 Nov 2023): closed test with **≥ 12 opted-in testers for
  14 continuous days** before applying for production access.
- **Organization** accounts have different requirements.
- Suggested testers: owner, manager, staff, family, and regular NIT student customers.

## Deployment sequence (PRD §16.5)

1. Create Google Play Developer account (owner’s / restaurant’s identity)
2. Create app in Play Console
3. Upload signed AAB → **Internal testing**
4. **Closed testing** (if required) — 12 testers × 14 days
5. Fix bugs; complete policy declarations (Data safety, content rating, target audience, privacy policy, ads)
6. Apply for production access (if required)
7. Submit production release → Google review
8. Launch → monitor crashes (Crashlytics / Play Console vitals) and orders (dashboard)

A successful upload is not the same as being published — Google review must complete first.
