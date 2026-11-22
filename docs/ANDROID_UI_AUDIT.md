# Android UI/UX audit and design-system refactor (22 Nov 2026)

Method: the UI UX Pro Max skill (nextlevelbuilder/ui-ux-pro-max-skill, MIT). I ran its `--stack jetpack-compose` and
`--domain ux` searches and its pro-rules checklist: no emoji icons, touch targets of at least 48 dp, a 4/8 dp spacing rhythm,
semantic colour tokens, contrast in dark mode, safe areas, and loading, empty and error states.
The skill suggested a generic red/Playfair palette for restaurants. I did not use it, so the brand stays the same:
Swad Sangam orange, logo green, Plus Jakarta Sans.

Scope: the customer app (`android/app`, 11 screens plus navigation and shared components) and the Admin/Staff shell
(`android/staffapp`, one native view; the rest is the web admin, which has its own audit in `UI_UX_AUDIT.md`).

## 1. Audit findings (before)
| Area | Problem found |
|---|---|
| Whole app | About 130 raw `dp` values in padding/spacing; 3 separate spacing systems (`Dimens`, raw dp, Material defaults) |
| Colours | Raw hex in screens (`Color(0xFFE8A13B)` stars, `Color(0xCC000000)` scrim, `Color.White` headers), `SuccessGreen` used directly |
| Buttons | Mix of `Button`, `OutlinedButton`, `TextButton`, `androidx…TextButton` with different heights; some under 48 dp |
| Inputs | `OutlinedTextField` with placeholder only (no label) on Menu search; errors shown as loose red text |
| Cards | `Card` with different shapes/padding on every screen; flat lists with no grouping (More, Orders, Account) |
| Status | Order status always the same colour (cancelled looked like in-progress) |
| Feedback | 36 dp stars, no selected state for screen readers, hard-coded amber |
| Loading | Bare `CircularProgressIndicator` with no text on Order, Item, Account |
| Bottom actions | Place order / Add to cart buttons scrolled away on long pages |
| Contrast (light) | Primary text on cream 4.45:1 (<4.5), rating star 2.0:1 (<3), checkbox/radio/field outline 1.6:1 (<3) |
| Staff shell | Offline screen used raw pixels (`setPadding(64…)`), which looks tiny on high-density phones; hex colours in code |

## 2. Design system (new)
`ui/theme/Tokens.kt`: the only place that holds raw values.
- **Spacing** (4 dp grid): xxs 2 · xs 4 · s 8 · m 12 · l 16 · xl 24 · xxl 32 · xxxl 48, `gutter` = 16, `section` = 24
- **Radius**: xxs 3 (veg mark) · xs 6 · s 10 · m 14 (buttons/fields) · l 20 (cards) · xl 28 (sheets); `AppShapes` maps to them
- **IconSize**: xs 14 · s 18 · m 20 · l 24 · hero 64
- **Size**: touch 48, buttonLarge 52, buttonMedium 44 (+ invisible 48 dp target), avatar, thumbnail, card and image sizes, listRow 56
- **Border**: hairline 1, mark 1.5, progress 2 · **Elevation**: none/low/mid/high/barShadow
- **AppColors** (light + dark via `AppTheme.colors`): success, warning, info (+ containers), veg, nonVeg, rating, brandAccent, brandGreen, onBrandHeader
- **Typography**: all 15 Material roles in Plus Jakarta Sans with tuned weight and letter spacing (`Theme.kt`)
- **Colour schemes**: every Material role is set for light and dark, so no default purple shows up anywhere

`ui/components/DesignSystem.kt`: shared components.
`AppButton` (Primary/Secondary/Tonal/Text/Danger, large, loading, icon) · `AppTextField` (label, error, supporting text) ·
`SearchField` · `AppCard` · `StatusBanner` + `Tone` (Neutral/Brand/Success/Warning/Error) · `StatusChip` · `ListRow` ·
`BottomActionBar` (sticky, navigation-bar safe) · `KeyValueRow` · `LoadingState` · `SectionDivider`.
The older `Tag`, `InfoBanner`, `EmptyState`, `DishRow` and `QuantityStepper` in `Components.kt` now use these as well.

## 3. Screens fixed (11 of 11, plus navigation and Staff shell)
| Screen | Main changes |
|---|---|
| Home | Tokens, pill search, Pure Veg switch uses veg token, theme scrim, reviews/timings in AppCards, AppButtons, 48 dp chips |
| Menu | `SearchField` (label/clear/filter toggle state), 48 dp category chips, SectionHeader per category, token dividers |
| Item | LoadingState, price in primary, unavailable banner, sticky BottomActionBar (stepper + Add) |
| Cart | Items grouped in one card, delete icon button, coupon field with inline error, bill card, sticky checkout bar |
| Checkout | 4 grouped cards, payment radio rows at 48 dp, terms row in sticky bar, loading on Place order |
| Order tracking | Status chip coloured by status, timeline, UPI/bill cards, KeyValueRow bill, AppButtons, labelled cancel field |
| My orders | AppCard per order, status tone (done = green, cancelled = red, in progress = orange), Reorder tonal button with icon |
| Feedback | 48 dp stars with `selected` semantics, rating token, grouped cards, labelled comment field, sticky Send with loading |
| More | Three grouped settings cards with `ListRow` (icon badge + chevron, 56 dp), 48 dp language rows |
| Restaurant info | Info in two cards, sticky Call / Directions / Share bar |
| Account | LoadingState, labelled fields with IME actions, password hint, loading button, avatar header, address cards with Default chip, Danger delete, success/error banners |
| Navigation | Top bar content colours from `onPrimary` (no `Color.White`); bottom bar unchanged |
| Staff/Admin shell | Offline view uses colour resources and dp, Retry button 48 dp |

## 4. Inconsistencies resolved
- Raw `dp` outside the theme and component files: **0**. Inside the component files only token references remain.
- Raw hex or `Color.White`/`Color.Black` in screens: **0**.
- `Dimens` object, `SuccessGreen`, `NonVegBrown` removed; the other palette constants are now private to `Theme.kt`.
- Unused resources removed: `brand_saffron`, `brand_text`, 3 duplicate strings (lint UnusedResources: 5 → 0).
- Lint `ModifierParameter` warnings fixed (PriceSummary, StatusChip).
- Contrast (WCAG formula, checked by script): primary `#D63B06` → `#D03905` (same orange to the eye; 4.7:1 on both
  white and cream), rating `#F5A623` → `#C77C00` (3.3:1), light outline `#D9CCC4` → `#8F837B` (3.5:1).
  All 33 light/dark pairs checked now pass (text ≥ 4.5:1, icons/borders ≥ 3:1).

## 5. Verification
- `:app:assembleDebug :app:lintDebug :app:testDebugUnitTest :staffapp:assembleAdminDebug :staffapp:assembleStaffDebug
  :staffapp:lintAdminDebug`: **BUILD SUCCESSFUL**
- Lint: app 0 errors, 46 warnings (all dependency versions, KTX suggestions, plurals; none about UI); staffapp 0 errors.
- Unit tests: 10/10 pass.
- No new dependencies. Navigation, ViewModels, repositories, API calls, Room and auth are unchanged (only UI files changed).

## 6. Remaining / not verified
- **Not seen on a real phone or emulator**: this build machine has no KVM, so no emulator could run. Check on a phone:
  light and dark mode, Hindi text length in buttons, large font size (Settings → Display → Font size).
- The website colour is still `#D63B06`; the Android colour is now `#D03905`. The two look almost the same, and the website could be changed later to match.
- Remaining lint warnings are dependency upgrades; they were left alone on purpose to avoid risky version changes.
