# Chore Tracker

A Tody-inspired chore tracking app. Each task in each area has a frequency in days; the app shows a rising "dirtiness" indicator and prioritizes the most-due tasks. Multi-user via shared households.

- **Backend** — Cloudflare Workers + D1 (SQLite). Hono framework. JWT auth.
- **Android client** — Kotlin + Jetpack Compose + Retrofit.

## Repo layout

```
backend/      Cloudflare Worker + D1 schema and migrations
android/      Android Studio project (single :app module)
.github/      Workflows: deploy backend + build APK
```

---

## One-time backend setup

You'll do this once on your machine, then auto-deploy takes over.

1. **Install wrangler** and log in:
   ```
   npm i -g wrangler
   wrangler login
   ```
2. **Create the D1 database**:
   ```
   cd backend
   wrangler d1 create chore_tracker
   ```
   Copy the printed `database_id` into `backend/wrangler.toml` (replace `REPLACE_WITH_D1_DATABASE_ID`).
3. **Set your JWT secret** (used to sign auth tokens):
   ```
   wrangler secret put JWT_SECRET
   # paste a long random string, e.g. `openssl rand -base64 48`
   ```
4. **Apply migrations to the remote DB**:
   ```
   npm install
   npm run migrate:remote
   ```
5. **First manual deploy** (later pushes auto-deploy):
   ```
   npm run deploy
   ```
   Note the URL printed (e.g. `https://chore-tracker-api.<your-subdomain>.workers.dev`).

### Local dev

```
cd backend
cp .dev.vars.example .dev.vars   # then put a real secret in
npm run migrate:local
npm run dev                       # http://127.0.0.1:8787
```

---

## Auto-deploy on git push

Two options — pick one.

### Option A — GitHub Actions (already wired up)

`.github/workflows/deploy-backend.yml` runs `wrangler deploy` on every push to `main` that touches `backend/`. Add two repo secrets:

- `CLOUDFLARE_API_TOKEN` — create at <https://dash.cloudflare.com/profile/api-tokens> with the *"Edit Cloudflare Workers"* template, plus *D1: Edit* on the account.
- `CLOUDFLARE_ACCOUNT_ID` — visible in the Cloudflare dashboard sidebar.

### Option B — Cloudflare Workers Builds (dashboard)

In Cloudflare Dashboard → Workers & Pages → **Create** → **Connect to Git** → pick `shadaeiou/chore-tracker`. Settings:

- Root directory: `backend`
- Build command: `npm ci`
- Deploy command: `npx wrangler deploy`

No GitHub secrets needed — Cloudflare authenticates itself. If you use this, you can delete `.github/workflows/deploy-backend.yml`.

---

## Android app

### Local development

You need Android Studio (Ladybug or newer) and JDK 17.

```
cd android
gradle wrapper                                  # one-time, generates ./gradlew + jar
./gradlew :app:assembleDebug -PapiBaseUrl=https://chore-tracker-api.<you>.workers.dev/
```

Or open `android/` in Android Studio, set `apiBaseUrl` in `~/.gradle/gradle.properties`, and Run.

### Build via GitHub Actions

`.github/workflows/build-android.yml` builds a debug-signed APK on every push to `main` and on every `v*` tag. The APK is uploaded as a workflow artifact, and on tags it's also attached to the GitHub Release.

Set the API URL in one place: **Repo Settings → Variables → Actions → New variable** named `API_BASE_URL` set to `https://chore-tracker-api.<you>.workers.dev/`.

### Distributing the APK

The workflow produces `app-debug.apk`. Three ways to share it:

1. **GitHub Releases** — tag `v0.1.0` and push; the APK is attached automatically. Send the download link.
2. **Workflow artifact** — open the run on GitHub, download `chore-tracker-debug-apk`.
3. **Firebase App Distribution / Play Console** — upload the APK once you have an account.

Recipients must enable *"Install unknown apps"* for their browser/file manager (Settings → Apps → Special access).

### Going to the Play Store later

The current build is **debug-signed** — fine for sideloading, not eligible for Play. To publish:

1. Pay the $25 Google Play developer fee.
2. Generate a release keystore: `keytool -genkey -v -keystore release.jks -alias chore -keyalg RSA -keysize 2048 -validity 10000`
3. Add it as a base64 GitHub secret (`ANDROID_KEYSTORE_B64`) plus `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`.
4. Add a `release` signingConfig in `android/app/build.gradle.kts` and switch the workflow to `:app:bundleRelease` (AAB).
5. Upload to Play Console → Internal testing track.

---

## Multi-user sync

Households are shared. The first user creates a household at sign-up. To bring more people in:

1. The existing user taps the **person-add** icon → gets a one-shot invite code (7-day TTL).
2. The new user picks **Join with code** on the auth screen and registers using the code instead of creating a new household.

Once joined, both clients see the same areas, tasks, and completion attribution. The Android app refreshes via:

- **Pull-to-refresh** on the home screen, and
- A **15-second polling loop** while the app is foregrounded.

This is sync-by-polling — fine for a chore app where seconds of latency don't matter. Real-time push (WebSockets via a Durable Object per household) is a documented follow-up; not built yet.

## API surface

All `/api/*` endpoints require `Authorization: Bearer <jwt>`.

| Method | Path                          | Description                                                |
| ------ | ----------------------------- | ---------------------------------------------------------- |
| POST   | `/auth/register`              | Create household OR join one via `inviteCode`              |
| POST   | `/auth/login`                 | Get JWT                                                    |
| GET    | `/api/household`              | Household + member list                                    |
| POST   | `/api/invites`                | Mint a 7-day invite code                                   |
| GET    | `/api/areas`                  | List areas                                                 |
| POST   | `/api/areas`                  | Create area                                                |
| DELETE | `/api/areas/:id`              | Delete area (cascades tasks)                               |
| GET    | `/api/tasks`                  | List tasks (with `lastDoneBy` attribution)                 |
| POST   | `/api/tasks`                  | Create task                                                |
| POST   | `/api/tasks/:id/complete`     | Mark task done now (attributed to caller)                  |
| DELETE | `/api/tasks/:id`              | Delete task                                                |

## Tests

| Layer                                    | Where                                  | How to run                          |
| ---------------------------------------- | -------------------------------------- | ----------------------------------- |
| Backend unit (auth helpers, JWT, hash)   | `backend/test/auth.test.ts`            | `cd backend && npm test`            |
| Backend integration (full HTTP API + D1) | `backend/test/api.test.ts`             | `cd backend && npm test`            |
| Android JVM unit (dirtiness, repo)       | `android/app/src/test/.../data/`       | `cd android && gradle :app:testDebugUnitTest` |
| Android Compose UI (Robolectric)         | `android/app/src/test/.../ui/`         | same as above                       |
| Android E2E on emulator (Maestro)        | `.maestro/*.yaml`                      | `maestro test .maestro/`            |

The backend integration tests use `@cloudflare/vitest-pool-workers` — they run against a real Workers runtime + an in-memory D1 with the production migrations applied. No mocks of Cloudflare APIs.

The Compose UI tests use Robolectric so they run on the JVM (no emulator). The Maestro flows are the closest analogue to Playwright for true device-level E2E; they need an emulator and run only on `workflow_dispatch` / schedule, not on every push.

## Limitations vs. the real Tody app

This is an MVP, not a 1:1 clone. **In:** areas, tasks, frequency-based dirtiness, multi-user household with invites + completion attribution, prioritized list, pull-to-refresh + 15s polling. **Out (for now):** real-time push (WebSockets/SSE), Dusty/gamification, plan templates, custom icons, offline-first conflict resolution, push reminders, iOS, premium features.
