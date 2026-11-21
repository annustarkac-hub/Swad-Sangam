# Third-party software

This project uses these open-source libraries (installed through npm / Gradle, not copied into the repository).
No source code from other restaurant POS projects (including the AGPL-3.0 NodeDR OrderRestro) has been copied; their feature lists were used only as a checklist (see docs/ARCHITECTURE_DECISIONS.md, ADR-5).

| Component | Licence |
|---|---|
| React, React DOM, React Router | MIT |
| Vite, @vitejs/plugin-react, Vitest, TypeScript | MIT / Apache-2.0 (TypeScript) |
| @supabase/supabase-js, Supabase CLI | MIT |
| qrcode (node-qrcode) | MIT |
| lucide-react (icons) | ISC |
| Kotlin, kotlinx.coroutines, kotlinx.serialization | Apache-2.0 |
| AndroidX (Compose, Material 3, Navigation, Room, DataStore, WorkManager, AppCompat, Browser, SplashScreen) | Apache-2.0 |
| Dagger / Hilt | Apache-2.0 |
| Retrofit, OkHttp | Apache-2.0 |
| Coil | Apache-2.0 |
| Firebase Android SDK (Messaging, Crashlytics) — optional | Apache-2.0 (service terms apply) |
| PostgreSQL extensions pg_net, pg_cron (on Supabase) | PostgreSQL Licence |
| GitHub Actions used: actions/*, gradle/actions, supabase/setup-cli, softprops/action-gh-release | MIT |
