# Swad Sangam — Setup guide (no coding needed)

> **Hinglish mein short summary:** Is guide ko step-by-step follow karo. Har step mein sirf website par
> click karna hai aur kuch text copy-paste karna hai. **Sab kuch free hai** — customer website, Android APK
> download, restaurant admin/POS/accounts, backups. Play Store zaroori nahi hai.
>
> Aapko milega:
> - **Customer website:** `https://swadsangam.is-local.org/` — menu, order, table QR ordering, order tracking
> - **Android app (APK):** website ke “Android app” button se free download
> - **Restaurant admin / POS / accounts:** `https://swadsangam.is-local.org/admin.html`
>
> Daily use ke liye Hindi guide: **docs/OWNER_GUIDE_HINDI.md**

> **Pehla kaam:** GitHub par is branch ka Pull Request **main** mein merge karo. GitHub ke buttons
> (Actions → Run workflow) sirf main branch par dikhte hain.

You will set up four free services. Do them in this order. Keep a notebook (or a private Google Doc)
for the passwords and keys you create — **never** post them in WhatsApp groups or on GitHub issues.

| # | Service | What it does | Cost |
|---|---------|--------------|------|
| 1 | **Supabase** | Database: menu, orders, staff logins, photos | Free plan |
| 2 | **GitHub** (this repository) | Builds the Android app and dashboard automatically | Free |
| 3 | **GitHub Pages** | Hosts the customer website, admin/POS, privacy policy | Free (repo is public) |
| 4 | **GitHub Releases** | Free download link for the Android APK | Free |
| 5 | **Firebase** (optional) | Instant push notifications + crash reports | Free (Spark plan) |
| — | Google Play Console | **Not needed.** Optional later ($25 one-time) | — |

---

## Step 1 — Create the Supabase project (database)

1. Go to <https://supabase.com> → **Start your project** → sign in with GitHub.
2. **New project**
   - Name: `swad-sangam`
   - Database password: click **Generate a password** and **save it in your notebook**.
   - Region: **South Asia (Mumbai)** — closest to Srinagar, so the app is faster.
   - Plan: **Free**.
3. Wait ~2 minutes for the project to be ready.
4. Open **Project Settings → API** (or **Data API / API Keys**) and copy into your notebook:
   - **Project URL** — looks like `https://abcdxyz.supabase.co`
   - **anon public** key (or **publishable** key, starts with `sb_publishable_`). This key is safe to put in
     the app — the database rules protect the data.
   - ⚠️ **Do not** copy or share the `service_role` / `secret` key anywhere.
   - The short code in the URL (`abcdxyz`) is your **Project ref**.

## Step 2 — Create the database tables

**Easiest way (copy-paste):**

1. In Supabase open **SQL Editor → New query**.
2. In this GitHub repository open the folder `supabase/migrations`. There are 11 files. For **each file in
   order** (`…0001_schema.sql` … `…0011_erp_reports_security.sql`) — **one file per Run**:
   open it → click **Raw** → select all → copy → paste into the SQL editor → **Run**.
   You should see “Success. No rows returned”.
3. Open **Table Editor** — you should see `menu_items` with the 12 seed dishes.

*(Alternative for later updates: GitHub → **Actions → Deploy backend → Run workflow**, after adding the
secrets `SUPABASE_ACCESS_TOKEN`, `SUPABASE_PROJECT_REF`, `SUPABASE_DB_PASSWORD` — see Step 4.)*

## Step 3 — Supabase login settings

1. **Authentication → Sign In / Providers → Email**: keep **Email** enabled and **Confirm email** ON.
2. **Authentication → URL Configuration → Site URL**: put your dashboard address from Step 5
   (you can come back and change it after Step 5).
3. Optional but recommended for the owner: **Authentication → Multi-Factor** → enable TOTP.

## Step 4 — Add the keys to GitHub (so it can build the app for you)

In this repository: **Settings → Secrets and variables → Actions → New repository secret**. Add:

| Secret name | Value |
|-------------|-------|
| `SUPABASE_URL` | Project URL from Step 1 |
| `SUPABASE_ANON_KEY` | anon / publishable key from Step 1 |

Optional (only for the one-click “Deploy backend” workflow):

| Secret name | Value |
|-------------|-------|
| `SUPABASE_PROJECT_REF` | the short project code |
| `SUPABASE_DB_PASSWORD` | database password from Step 1 |
| `SUPABASE_ACCESS_TOKEN` | Supabase → your avatar → **Account → Access Tokens → Generate** |

## Step 5 — Put the website + admin online (free)

Choose **one**:

**A. GitHub Pages** (recommended — this repository is public, so it is free):
1. **Settings → Pages → Build and deployment → Source: GitHub Actions**.
2. **Settings → Secrets and variables → Actions → Variables tab → New variable**:
   `ENABLE_GITHUB_PAGES` = `true`.
3. **Actions → Admin dashboard → Run workflow**. When it finishes, the address is shown on the run page,
   `https://swadsangam.is-local.org/` = customer website,
   `https://swadsangam.is-local.org/admin.html` = restaurant admin / POS / accounts.

**B. Netlify** (works with **private** repositories):
1. <https://app.netlify.com> → sign in with GitHub → **Add new site → Import an existing project** → pick this repo.
2. It reads `admin-dashboard/netlify.toml` automatically.
3. **Site configuration → Environment variables**: add `VITE_SUPABASE_URL` and `VITE_SUPABASE_ANON_KEY`
   (same values as Step 4) → **Deploys → Trigger deploy**.

Your **privacy policy** is then at `<dashboard address>/privacy.html` and the **order terms** at
`<dashboard address>/terms.html`. Open both files in GitHub (`admin-dashboard/public/`), replace everything in
`[square brackets]`, and get them checked by a professional before launch.

Go back to Supabase Step 3 and set **Site URL** to the dashboard address.

## Step 6 — Create the owner login

1. Open `…/admin.html` → **New staff? Create account** → use the owner's email → confirm the email.
2. In Supabase **SQL Editor** run (with the owner's email):
   ```sql
   select public.make_owner('owner@example.com');
   ```
3. Sign in to the dashboard. You now see Dashboard, Orders, Kitchen, Menu, Settings, Staff, Reports…
4. Other staff: they create an account the same way; the owner opens **Staff** and gives each one a role
   (**Manager**, **Staff**, or **Kitchen**). Deactivate accounts immediately when someone leaves.

## Step 7 — Fill in the restaurant details (owner)

In the dashboard:
1. **Settings** — check phone, address, opening hours (tick “Opening hours confirmed”), FSSAI number,
   UPI ID, support email, privacy policy URL and terms URL (from Step 5).
   Leave **Accepting app orders / Pickup** OFF for now.
2. **Menu** — check every dish: price, veg/non-veg, description, portion, allergens. Upload **real photos**
   of Swad Sangam food. Tick **Details verified** for each dish. Add missing dishes and drinks
   (the Beverages category stays hidden until it has dishes).
3. **QR codes** — print table and “download the app” QR codes when you are ready.

## Step 8 — Test build of the Android app

1. **Actions → Android app → Run workflow** (it also runs automatically on every change).
2. When it is green ✅, open the run → **Artifacts** → download **swad-sangam-debug-apk**.
3. Unzip, send the `.apk` to an Android phone, open it and allow “install unknown apps”.
4. Test: browse menu, call, directions. Then in **Settings** turn on **Accepting app orders** + **Pickup**,
   place a test order from the phone and accept it from the dashboard (see the launch-day checklist).

> The debug app is called `in.swadsangam.restaurant.debug` so it can be installed next to the real one.

## Step 9 — Publish the Android APK for customers (free)

1. **Actions → Create signing key (run once) → Run workflow** → type `YES`.
2. Download the **signing-key** artifact within 24 hours. Save `release.jks` and `KEY-INFO.txt` in **two safe
   places** (e.g. Google Drive + a pen drive). Every future app update must be signed with this same key —
   if it is lost, customers must uninstall and reinstall.
3. Add the four secrets listed in `KEY-INFO.txt` to GitHub (Step 4 screen).
4. **Actions → Release Android APK → Run workflow.** It creates a release with `swad-sangam.apk`.
5. The website’s **Android app** button and the link
   `https://github.com/annustarkac-hub/Swad-Sangam/releases/latest/download/swad-sangam.apk`
   now always give the newest version. Share that link on WhatsApp, print it as a QR (dashboard → QR codes).
6. Customers: open link → download → allow “Install unknown apps” → Install. For an update they install the new APK
   over the old one. (Android shows a warning for apps outside Play Store — that is normal.)

## Step 9b — Daily backups (free, strongly recommended)

The Supabase free plan has **no automatic backups**.
1. Supabase → **Connect** (top bar) → **Session pooler** → copy the URI and put your database password in it.
2. GitHub secrets: `SUPABASE_DB_URL` = that URI, `BACKUP_PASSPHRASE` = a long secret phrase (write it in your notebook —
   without it the backup cannot be opened).
3. **Actions → Database backup** runs every night; each run keeps an encrypted backup for 30 days.
   Restoring needs a technical person: decrypt with `gpg -d file.dump.gpg > db.dump`, then `pg_restore -d <URL> db.dump`.

## Step 9c (optional, later) — Play Store bundle

The same signing key from Step 9 also signs the Play Store bundle: **Actions → Android app** → artifact
**swad-sangam-release** → `app-release.aab`. Only needed if you decide to publish on Google Play later (Step 11).

## Step 10 (optional) — Push notifications with Firebase (free)

Without this, customers still see live status inside the app and the app checks for updates in the
background every ~15 minutes. With it, updates arrive instantly.

1. <https://console.firebase.google.com> → **Add project** → name `swad-sangam` → you can disable Google Analytics.
2. **Add app → Android** twice:
   - package `in.swadsangam.restaurant`
   - package `in.swadsangam.restaurant.debug`
3. Download **google-services.json** (after adding both apps) → open it in a text editor → copy everything →
   GitHub secret **`GOOGLE_SERVICES_JSON`**.
4. Firebase → **Project settings → Service accounts → Generate new private key** → a JSON file downloads.
5. Supabase → **Edge Functions → Secrets** (or Project Settings → Edge Functions): add
   - `FIREBASE_SERVICE_ACCOUNT` = whole content of that JSON file
   - `PUSH_WEBHOOK_SECRET` = any long random text (e.g. 40 random letters)
6. Deploy the function: **Actions → Deploy backend → Run workflow** (needs the optional secrets from Step 4),
   or in Supabase **Edge Functions → Deploy a new function → Via editor**, name `push-order-status`, paste
   `supabase/functions/push-order-status/index.ts`, and turn **off** “Verify JWT”.
7. Supabase **SQL Editor** (replace the two values):
   ```sql
   insert into private.app_config(key, value) values
     ('push_function_url', 'https://YOUR-PROJECT-REF.supabase.co/functions/v1/push-order-status'),
     ('push_function_secret', 'THE SAME RANDOM TEXT AS PUSH_WEBHOOK_SECRET')
   on conflict (key) do update set value = excluded.value;
   ```
8. Re-run **Android app** and install the new APK. Crashlytics crash reports also start working.

## Step 11 (optional, later) — Publish on Google Play

Follow `docs/PLAY_STORE_LISTING.md` (store text, Data safety answers, content rating) and
`docs/LAUNCH_CHECKLIST.md`. Summary:
1. Create a Google Play developer account (**$25 one-time**, in the restaurant's/owner's name — PRD §19).
2. **Create app** → upload `app-release.aab` to **Internal testing** first.
3. New *personal* accounts must run a **closed test with at least 12 testers for 14 days** before production.
4. Complete store listing, Data safety, content rating, privacy policy URL → **Production** → Google review.

## QR table ordering

The dashboard **QR codes** page makes one QR per table. Scanning (any phone camera, Android or iPhone, no app needed)
opens the website menu with the table number filled in (turn on **Dine-in / table orders** in Settings).
Link format: `https://swadsangam.is-local.org/index.html?table=5`. Orders appear in the POS / Orders
screen with the table number.

## Keeping it free and healthy

- Supabase pauses free projects after 7 days without traffic. The **Keep backend awake** workflow pings it
  every 3 days automatically. (GitHub pauses scheduled workflows in repositories with no commits for 60 days —
  just press **Run workflow** on it once if you see that notice.)
- Free limits are far above what one restaurant needs (500 MB database, 1 GB photo storage, 50,000 monthly users).
  Supabase **Usage** page shows how much you use.
- Photos are resized to small WebP files automatically when uploaded from the dashboard.

## Troubleshooting

| Problem | Fix |
|---------|-----|
| App says “Menu could not be loaded” | Check `SUPABASE_URL` / `SUPABASE_ANON_KEY` secrets, then re-run the Android workflow and reinstall. Check the Supabase project is not paused. |
| Dashboard shows “Setup needed” | Dashboard environment variables are missing (Step 5). |
| “Waiting for approval” after login | Owner must activate the account on the **Staff** page (or run `make_owner` for the first owner). |
| Customers can’t order | Settings → **Accepting app orders** + at least one order type ON, and within opening hours. |
| Android build fails on “google-services” | `google-services.json` must contain **both** package names (Step 10.2). |
| New-order sound not playing | Click “Enable sound & notifications” on the dashboard once; keep the Orders page open. |
