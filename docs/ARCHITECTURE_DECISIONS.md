# Architecture decisions

## ADR-1 — Free-first hosting: Supabase + GitHub
**Decision:** Supabase free plan (PostgreSQL, Auth, Storage, Realtime, Edge Functions) for the backend; GitHub Pages for
the website + admin; GitHub Releases for the Android APK; GitHub Actions for builds, tests, keep-alive and backups.
**Why:** ₹0 recurring cost, no server to maintain, server-side business logic possible (Firebase needs the paid Blaze plan
for Cloud Functions and new Storage buckets). **Trade-offs (honest):** free tiers have no uptime guarantee, Supabase pauses
after 7 idle days (mitigated by the keep-alive workflow) and has no automatic backups (mitigated by the encrypted daily
backup workflow). If the restaurant outgrows the free plan, Supabase Pro is the upgrade path — only with owner approval.

## ADR-2 — Business logic lives in the database
All money and stock rules (pricing, tax, payments, journal postings, stock deduction, payroll) are PostgreSQL functions
guarded by row-level security. Apps and browsers can't send their own prices or totals and can't skip a rule.
Every rule has an automated test (`supabase/tests/*.sql`, run in CI).

## ADR-3 — Double-entry accounting as the single financial source of truth
Reports (P&L, balance sheet, trial balance, cash flow, ledgers, ageing, GST summary) are computed from
`journal_entries` / `journal_lines`. Entries must balance (checked on insert and again at commit), are immutable,
and are corrected by reversal. Automatic postings are idempotent by `(source, source_ref)` so retries never double-post.

## ADR-4 — Customer channels: website first, APK second, Play Store optional
The customer website (installable PWA, works on any phone, QR table ordering) and the native Android APK talk to the
same backend. The APK is distributed free through GitHub Releases. Google Play ($25 one-time) stays optional.

## ADR-5 — Open-source restaurant POS repositories: ideas, not code
The owner asked us to consider OrderRestro (nodedr-restaurant-pos), POSR (ahmedali5530/restaurant-pos), ElitaleRestro and
Posnic POS. Their feature lists informed the module list (KOT, split payments, recipes, ledgers, payroll). **No source
code was copied** from them. Reasons: (1) AGPL-3.0 projects would require publishing the source of the whole network
service and keeping it AGPL; (2) POSR is "source-available" with licence terms that must be checked before any reuse;
(3) they use different stacks (local servers, other databases) and would have replaced, not extended, the PRD-specified
system that already passes its tests. Re-implementing the *ideas* independently on our stack is allowed and keeps this
repository's licence choice open. If code is ever imported from them, add it to THIRD_PARTY_LICENSES.md first.

## ADR-6 — Offline behaviour (scope actually delivered)
- Customer website + Android app: menu cached for offline viewing; orders use idempotency keys so a retry after a lost
  connection never creates a duplicate.
- POS/admin: online-first. Bill creation and payments are idempotent (client reference ids), so a retry is safe, but the
  POS does **not** yet queue bills while fully offline. A local-first POS with background sync is the next major item
  (see PRD_TRACEABILITY "Not built yet"). Until then: keep a mobile-data backup on the counter phone, and write manual bills
  if the internet is down, entering them when it returns.
