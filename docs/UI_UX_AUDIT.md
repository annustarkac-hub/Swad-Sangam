# UI/UX audit — Swad Sangam (customer app, customer website, admin)

Date: Nov 2026. Scope: Android customer app (Kotlin + Jetpack Compose, Material 3), customer website (React PWA, `index.html`),
admin / POS (React, `admin.html`). There is no separate Android admin app; staff use `admin.html` (installable from Chrome).

References used for patterns, not copied: Material Design 3 guidelines, Now in Android (theme roles, adaptive layout),
Compose samples (Jetsnack-style cards, radio-row selection), WCAG 2.1 AA. No new dependencies were added.

## Method
- Read every Compose screen, theme and component; built the APK and ran unit tests.
- Rendered the website and admin with the **real live menu and settings** (loaded read-only from the database) in a headless browser:
  phone 390 px, desktop 1280–1366 px, light and dark.
- Checked colour contrast numerically (WCAG formula), touch-target sizes, broken-image behaviour, empty/error states.

## Findings and status (most important first)

| # | App | Finding | Impact | Status |
|---|---|---|---|---|
| 1 | Android | Dishes with Half/Full (or Dry/Gravy) showed the variants as **checkboxes**: a customer could tick Half **and** Full; the server then rejects the order. List showed only the base price. | Orders fail; wrong price expectations | **Fixed**: one-choice radio rows per group with full price; list shows "Half ₹40 · Full ₹70"; cards show "from ₹40" |
| 2 | Android | Dine-in had **no table-code field**; with "table code required" on, every app dine-in order failed. | Orders fail | **Fixed**: 4-digit code field + validation when the setting is on |
| 3 | Website | First 12 dish photos linked to `thumb.wikimedia.org`, which does not serve images → **broken images showing the dish name in huge text**. | Looks broken | **Fixed**: photos re-hosted on the website; every photo now falls back to a neat placeholder if it cannot load |
| 4 | Admin | One page error (unexpected server data) **blanked the whole admin**, including the menu. | Staff locked out of all pages | **Fixed**: per-page error boundary with "Try again" / "Reload"; the rest of the admin keeps working |
| 5 | All | Brand colour differed: Android deep red, admin red, website orange; the signboard is orange on navy. | Inconsistent brand | **Fixed**: one palette — orange `#C94A07` (buttons/text), bright `#E8590C` (accents), navy, saffron |
| 6 | Website | Bright orange buttons/links with white text: **3.6:1** contrast (AA needs 4.5:1). | Hard to read in sunlight; fails accessibility | **Fixed**: buttons/links use `#C94A07` (4.7:1); dark mode uses dark text on light orange |
| 7 | Android | Dark theme set only some colour roles → Material **default purple** leaked into banners, chips and containers. No dark window background (cream flash on launch). | Dark mode looks broken | **Fixed**: every colour role defined for light and dark; `values-night` theme with dark system bars |
| 8 | Android | Hard-coded white/grey on Home (search bar, tagline, "Get directions" in saffron on red). | Invisible/low-contrast text in dark mode | **Fixed**: theme colours; white text on the brand header |
| 9 | Android | Several tap targets 40 dp ("Add", directions link); Material minimum is 48 dp. | Missed taps | **Fixed**: 48 dp minimum via `Dimens.touch` |
| 10 | Android | "›" used as the "see all" action (not read by screen readers, unclear). | Accessibility | **Fixed**: "See all" / "सभी देखें" |
| 11 | Android | Checkout fields stacked with no spacing; payment rows not proper radio rows. | Cramped, error-prone | **Fixed**: 10 dp rhythm; whole row selectable with radio role |
| 12 | Android | A Room schema change would have **wiped the cart and "My orders"** (destructive migration only). | Lost order history after update | **Fixed**: real migration v1→v2; destructive fallback kept only as last resort |
| 13 | Website (desktop) | Phone-style bottom tab bar on desktop; promo poster pushed the menu below the fold. | Wasted space, unusual navigation | **Fixed**: navigation moves into the header on desktop; poster sits in the side column |
| 14 | Website | No visible keyboard focus; icon buttons 38 px. | Accessibility | **Fixed**: 3 px focus ring; 44 px minimum |
| 15 | Admin (phone) | Menu list squeezed dish names into one or two words per line. | Hard to scan | **Fixed**: photo / name, then price + actions on one line |
| 16 | Admin | Reports showed "−₹0" for zero discounts/refunds; "sample" label unclear. | Polish | **Fixed**: "₹0"; label "stock" with tooltip |
| 17 | Admin | 199 dishes still "Unverified" (price/description/photo not confirmed by the owner). | Owner task, not a code bug | **Open — owner** to confirm in Menu → Edit → "Details verified" |
| 18 | Android | Home has six horizontal carousels (popular, budget, thalis, breakfast, veg, non-veg) — long scroll. | Minor friction | Open (kept to avoid removing features; the menu screen has filters) |
| 19 | Android | Customer account, saved addresses and ratings exist on the website only. | Feature gap | Open — next step if wanted |

## Design system (now shared by all three)

| Token | Value |
|---|---|
| Primary (buttons, links) | `#C94A07` — white text 4.7:1 |
| Accent (highlights) | `#E8590C` |
| Secondary | Navy `#1F2A44` · Saffron `#F59F00` (stars, highlights) |
| Background / surface (light) | Cream `#FFF8F1` / white |
| Background / surface (dark) | `#141110` / `#1C1918`, primary `#FFB68F` with dark text |
| Status | Success `#1F8A4C`, Error `#C8392F`, Veg green / Non-veg brown marks (Indian standard) |
| Spacing | 8 / 12 / 16 dp (gutter 16) |
| Radius | 8 (chips, steppers) · 12 (cards, images) · 16 (hero) |
| Touch target | 48 dp Android, 44 px web minimum |
| Type | System sans (Roboto / Inter fallback); titles semibold, prices bold |
| States | Loading, empty, offline, error banners exist on every list screen; photo placeholder for missing/failed images |

Android tokens live in `ui/theme/Theme.kt` (`Dimens`, colour schemes); web tokens are CSS variables in `site/site.css` and `styles.css`.

## Validation
- Android: `assembleDebug` builds; unit tests pass (10/10, including a new test for variant groups). Room schema `2.json` generated.
- Web: TypeScript check and production build pass; web unit tests pass. Screens re-rendered after fixes (phone, desktop, dark).
- Backend: unchanged except one data fix (re-hosted photo links).

## Not verified here
- Android screens were not run on a phone or emulator in this environment (no device). Please install the next APK and check Home,
  a Half/Full dish, checkout and dark mode.
- Admin pages that need live orders were rendered with sample data, not real orders.
