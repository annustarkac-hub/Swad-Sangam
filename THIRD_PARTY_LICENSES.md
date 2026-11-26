# Third-party software

This project uses these open-source libraries (installed through npm / Gradle, not copied into the repository).
No source code from other restaurant POS projects (including the AGPL-3.0 NodeDR OrderRestro and ElitaleRestro / tirforge/restro) has been copied; their feature lists were used only as a checklist (see docs/ARCHITECTURE_DECISIONS.md, ADR-5).

| Component | Licence |
|---|---|
| React, React DOM, React Router | MIT |
| Vite, @vitejs/plugin-react, Vitest, TypeScript | MIT / Apache-2.0 (TypeScript) |
| @supabase/supabase-js, Supabase CLI | MIT |
| qrcode (node-qrcode) | MIT |
| lucide-react (icons) | ISC |
| Leaflet (map on the website) | BSD-2-Clause |
| OpenStreetMap map tiles (tile.openstreetmap.org, loaded at runtime; attribution shown on the map) | Data © OpenStreetMap contributors, ODbL |
| Kotlin, kotlinx.coroutines, kotlinx.serialization | Apache-2.0 |
| AndroidX (Compose, Material 3, Navigation, Room, DataStore, WorkManager, AppCompat, Browser, SplashScreen) | Apache-2.0 |
| Dagger / Hilt | Apache-2.0 |
| Retrofit, OkHttp | Apache-2.0 |
| Coil | Apache-2.0 |
| Firebase Android SDK (Messaging, Crashlytics) — optional | Apache-2.0 (service terms apply) |
| PostgreSQL extensions pg_net, pg_cron (on Supabase) | PostgreSQL Licence |
| GitHub Actions used: actions/*, gradle/actions, supabase/setup-cli, softprops/action-gh-release | MIT |

## Plus Jakarta Sans (font)
Copyright 2020 The Plus Jakarta Sans Project Authors. SIL Open Font License 1.1 — full text in `licenses/PlusJakartaSans-OFL.txt`.
Used in the Android app (`android/app/src/main/res/font/`) and the website/admin (`@fontsource/plus-jakarta-sans`, same licence).

## Design language reference
The colour palette, type choice and screen patterns follow the FoodGenz user/partner apps, which the owner states are licensed to them.
No FoodGenz code, logos, illustrations, names or images are included in this repository.
