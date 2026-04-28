# Roadmap — Tody Feature Parity

Phased plan to bring Chore Tracker to Tody feature parity. Each phase is a self-contained PR-equivalent commit (or small commit chain) that compiles, deploys, and ships a usable improvement on its own. Graphics-heavy work deferred to Phase 7.

**Status legend:** ✅ done · 🔄 in flight · ⏸ deferred (intentional) · ⏳ not started

---

## Quick handoff for the next context

**Where we are:** Phases 1–5 shipped, plus a substantial polish pass (see "Phase 5b polish" below). Phase 6 (gamification) is **on hold** — user explicitly chose to polish before adding leaderboards/Dusty. Phase 7 (mascot art, focus timer, multi-home) untouched.

**Current app shape:**
- 3 tabs: **Today** (date-based "what's due now"), **Household** (manage areas/tasks, with rename + copy + mass-select + search), **Activity** (history feed + workload card)
- Vacation mode toggle in top bar (beach icon)
- Onboarding wizard runs once on first signup
- 16-room template library covering cleaning + errands + vehicle + personal + financial + plants + family
- Notes on tasks (persistent) and completions (per-event) with 📝 prefix display
- Long-press menus on task rows and area headers; long-press Household header for rename / select-areas
- Auto-incrementing version visible in Settings

**Known issue:** the `golden path add area add task complete task` Compose test (Robolectric) consistently times out at the final completeButton click. The same code path tested with a pre-seeded task passes. Not yet root-caused — functionality verifies on real device. If user reports the symptom (tap complete → no "Marked done" snackbar) on a fresh install, this is the bug.

**Things deferred or punted:**
- App name + logo: user paused this. Currently still "Chore Tracker" with no logo on auth screen.
- Phase 6: explicitly on hold per user request.
- Roborazzi snapshot tests: deferred from Phase 1, never picked back up.
- Two-emulator Maestro flows for FCM: only single-device flows exist; real two-device test still relies on real-device sideload.

**Testing locally** (per CLAUDE.md):
- `make backend-test-local` (with `npm run dev:test` running) — ~84 tests, ~3s
- `make android-test` — ~53 Compose tests, ~10s warm

---

## Phase 1 — Quick wins (dark theme + undo)  ✅ shipped

Highest ROI, lowest risk. Both items address immediate user pain.

### Backend
- ✅ `DELETE /api/tasks/:id/completions/last` (`backend/src/index.ts`) — drops latest completion row, recomputes `tasks.last_done_at` from remaining max, single batched D1 transaction. Cross-household guarded.
- ✅ Tests in `backend/test/api.test.ts`: single-completion rollback, multi-completion rollback, no-op 404, cross-household guard, unknown-task 404.

### Android
- ✅ `ui/Theme.kt` (new) — Material3 light + dark color schemes + `ChoreTheme(themeMode)` wrapper.
- ✅ `data/Session.kt` extended with `themeModeFlow: Flow<ThemeMode>` persisted via DataStore.
- ✅ `ui/SettingsScreen.kt` (new) — theme picker (System/Light/Dark) reachable from a gear icon in the home top bar.
- ✅ `MainActivity` rewires `setContent` to `ChoreTheme(themeMode)` reading the preference.
- ✅ `res/values-night/themes.xml` so cold-start window background follows the system.
- ✅ `ui/HomeScreen` Scaffold gets a `SnackbarHost`; on completion, "Marked done" snackbar with "Undo" action calls the new endpoint and refreshes.
- ✅ `data/Api.kt` adds `undoLastCompletion(@Path id: String)`.
- ✅ `data/FakeApi.kt` extended; tracks `undone` list.

### Tests
- ✅ `HomeScreenTest` — snackbar appears post-complete, undo click calls fake API, settings icon invokes callback.
- ✅ `SettingsScreenTest` (new) — picking dark mode persists, back invokes callback, all three options render.
- ✅ `ThemeTest` (new) — Light/Dark/System smoke renders.
- ✅ Pre-existing bug fixed: `AuthScreenTest` was missing `assertCountEquals` import (caught by first push that touched `android/**`).

### Visual verification
- ✅ `.maestro/phase1_undo_and_dark_theme.yaml` asserts every acceptance bullet and captures 9 screenshots across the flow.
- ✅ `.github/workflows/test-android.yml` runs Maestro on every push/PR (was workflow_dispatch only) and uploads `~/.maestro/tests` + logs as `maestro-artifacts`.

### Deferred
- ⏸ **Roborazzi snapshot tests.** Plan called for byte-diffed Compose goldens as a Layer-1 fast visual gate. Skipped for Phase 1 because (a) we have no local Android SDK to verify the gradle plugin wiring, and (b) Maestro already covers visual verification end-to-end. Add in a dedicated commit once Phase 1 Maestro is green and we have a known-good baseline.

---

## Phase 2 — Per-task rotation + activity feed  ✅ shipped

The user's core multi-user ask. Needs schema work; no external services.

### Backend
- ⏳ Migration `0003_rotation_and_effort.sql`:
  ```sql
  ALTER TABLE tasks ADD COLUMN assigned_to TEXT REFERENCES users(id);
  ALTER TABLE tasks ADD COLUMN auto_rotate INTEGER NOT NULL DEFAULT 0;
  ALTER TABLE tasks ADD COLUMN effort_points INTEGER NOT NULL DEFAULT 1;
  CREATE INDEX idx_tasks_assigned ON tasks(assigned_to);
  ```
- ⏳ `POST /api/tasks` — accept `assignedTo`, `autoRotate`, `effortPoints` (defaults: caller, false, 1).
- ⏳ `PATCH /api/tasks/:id` — update assignee/rotation/effort/frequency without recreating.
- ⏳ `POST /api/tasks/:id/complete` — if `auto_rotate=1`, advance `assigned_to` to next member ordered by `users.created_at` (cycle wraparound).
- ⏳ `GET /api/activity?before=<ts>&limit=50` — last N completions with task name, area name, member display name, done_at.
- ⏳ `GET /api/household/workload` — `SUM(effort_points)` per member from current-month completions.
- ⏳ Tests: rotation cycles correctly, non-rotating task keeps assignee, activity orders DESC and is household-scoped, workload sums per user.

### Android
- ⏳ `Models.kt` — extend `Task` with `assignedTo`, `autoRotate`, `effortPoints`.
- ⏳ `HomeScreen` task row shows assignee initials/avatar; create-task sheet adds assignee picker, rotate toggle, effort slider.
- ⏳ `ActivityScreen.kt` (new) — bottom-nav (or pull-tab) destination with `LazyColumn` of completions, date headers.
- ⏳ `WorkloadCard.kt` (new) — top-of-home card with bar chart of effort per member this month.
- ⏳ `Repo` — `activity: StateFlow<List<ActivityEntry>>`, `workload: StateFlow<Map<MemberId, Int>>`.
- ⏳ `RepoTest` extension; Compose tests for assignee badge + new screen rendering.

### Visual verification
- ⏳ `.maestro/phase2_rotation_and_activity.yaml` — two-emulator flow: A creates rotating task, A completes, B sees assignee shifted; both see entry in activity feed; workload card updates.

---

## Phase 2b — Task & area edit/delete (CRUD gap)  ✅ shipped

Long-press task row → Edit / Delete. Long-press area header → Rename / Delete (with cascade warning). Backend PATCH /api/areas/:id added. All existing PATCH/DELETE task endpoints wired to UI.

---

## Phase 3 — Push notifications via FCM  ✅ shipped

Largest infra lift. **Requires one-time external setup before code lands.**

### One-time setup (Burke does)
- ⏳ Create Firebase project at console.firebase.google.com.
- ⏳ Add Android app with package `com.chore.tracker`, download `google-services.json` to `android/app/`.
- ⏳ Generate FCM admin service-account key (JSON) → `wrangler secret put FCM_SERVICE_ACCOUNT` (paste full JSON).

### Backend
- ⏳ Migration `0004_device_tokens.sql`:
  ```sql
  CREATE TABLE device_tokens (
    token TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    platform TEXT NOT NULL,
    updated_at INTEGER NOT NULL
  );
  CREATE INDEX idx_device_tokens_user ON device_tokens(user_id);
  ```
- ⏳ `POST /api/device-tokens` — body `{token, platform}`. Upsert (PK on token).
- ⏳ `DELETE /api/device-tokens/:token` — for logout / token rotation.
- ⏳ `backend/src/fcm.ts` (new):
  - `getAccessToken(env)` — RS256-sign a JWT against the service-account JSON, exchange for OAuth at `https://oauth2.googleapis.com/token`. Module-level memo, 50-min TTL.
  - `sendToTokens(tokens, payload, env)` — fan out to FCM HTTP v1; `Promise.allSettled` so a bad token doesn't block siblings; on `UNREGISTERED`/`INVALID_ARGUMENT`, delete the token.
  - RS256 via Web Crypto `crypto.subtle.importKey` + `sign('RSASSA-PKCS1-v1_5', ...)` — no library.
- ⏳ Wire `fcm.sendToTokens` into the complete handler with `c.executionCtx.waitUntil(...)` so the API response stays fast. Skip the actor's own tokens.
- ⏳ Tests: mock `fetch` for FCM; assert payload shape, recipient list (skip actor), token cleanup on `UNREGISTERED`.

### Android
- ⏳ Gradle: Firebase BOM, `firebase-messaging-ktx`, apply `com.google.gms.google-services` plugin.
- ⏳ Manifest: `POST_NOTIFICATIONS` permission (API 33+); declare `FirebaseMessagingService`.
- ⏳ `service/PushService.kt` (new):
  - `onNewToken` → POST to `/api/device-tokens` (queue if no JWT, flush on next login).
  - `onMessageReceived` → build `NotificationCompat`; if app foreground, silent-refresh via `repo.refresh()` instead of banner.
- ⏳ AuthScreen / Repo — on login/register, request notification permission (Android 13+ runtime), grab token via `Firebase.messaging.token`, register.
- ⏳ Repo polling interval bumped to 60s when FCM is registered (still poll as safety net).

### Visual verification
- ⏳ `.maestro/phase3_push_notifications.yaml` — two emulators: B completes a task; A asserts notification text in system shadow within 5s; tapping the notification opens the activity feed.

---

## Phase 4 — Lifecycle (pause, snooze, retroactive)  ✅ shipped

Tody's "Pause is the #1 abandonment cause" insight. Plus retroactive completion + per-task snooze.

### Backend
- ⏳ Migration `0005_pause_and_snooze.sql`:
  ```sql
  ALTER TABLE households ADD COLUMN paused_until INTEGER;
  ALTER TABLE tasks ADD COLUMN snoozed_until INTEGER;
  ```
- ⏳ `PATCH /api/household` — set/clear `paused_until`.
- ⏳ `POST /api/tasks/:id/snooze` — body `{until: unixMs}`.
- ⏳ `POST /api/tasks/:id/complete` — accept optional `{at}` for retroactive (validate not in future, not before task creation).
- ⏳ Server-computed `dueness` field on GET `/api/tasks` — 0 if paused or snoozed, else the dirtiness ratio.

### Android
- ⏳ `Models.Task.dirtiness` reads server-computed value when present, falls back to local calc.
- ⏳ HomeScreen — vacation banner with one-tap resume; long-press a task → snooze sheet (1 day / 3 days / 1 week); "Mark done at..." datepicker option in completion flow.
- ⏳ Tests: dirtiness clamps to 0 when paused; snoozed task drops to bottom of priority list until snooze expires.

### Visual verification
- ⏳ `.maestro/phase4_pause_snooze_retroactive.yaml` — toggle vacation, indicators freeze; long-press snooze 3 days, task moves; complete with yesterday timestamp, dirtiness reflects.

---

## Phase 5 — Task library + onboarding wizard  ✅ shipped

Cuts setup time. Addresses Tody's #1 user complaint ("setup is heavy").

### Backend
- ⏳ Migration `0006_task_templates.sql` — `task_templates(id, name, suggested_area, suggested_frequency_days, suggested_effort)`.
- ⏳ Seed ~80 templates inline in the migration (kitchen 12, bathroom 8, bedroom 6, living 6, laundry 6, outdoor 8, general 10, pets 6, kids 6, seasonal 12).
- ⏳ `GET /api/task-templates?area=kitchen`.
- ⏳ `POST /api/tasks` — accept optional `templateId` to copy fields.

### Android
- ⏳ `TaskLibraryScreen.kt` (new) — searchable list, filter by area, tap to add to current area.
- ⏳ Onboarding wizard — first-launch flow after household creation: pick rooms → add common tasks from templates → set initial dirtiness slider per task. Skips entirely if user already has areas.

### Visual verification
- ⏳ `.maestro/phase5_library_and_onboarding.yaml` — fresh signup walks through wizard; existing user can browse library from area screen via "+ from library".

---

## Phase 5b — Polish + UX overhaul  ✅ shipped

After Phase 5 the user did an extensive use-and-feedback pass and we shipped a long polish chain. Lots of small commits — grouped here by theme.

### Tab restructure
- Was: single Chores tab + Activity tab. New: **Today / Household / Activity**.
- **Today** filters tasks date-based (`(lastDoneAt + frequencyDays * 24h) <= end-of-today`), hides snoozed tasks, shows nothing during vacation mode.
- **Household** is the management hub: area cards with kebab menus (Rename / Copy as… / Select tasks… / Delete), `+ Add from library` per area, search bar.
- **Activity** absorbed the workload card from the old Chores tab.
- FAB only on Household tab.

### Bulk operations
- **Multi-select Add Areas** — replaces single-area dialog. 16 chips for canonical rooms (auto-hidden if already added) + custom-name field. "Add N" creates them all in parallel.
- **Mass-delete tasks** — kebab on area card → "Select tasks…" → checkbox mode + selection bar.
- **Mass-select areas** — long-press Household header → "Select areas…" → checkbox mode on each area card + selection bar with delete.
- **Multi-pick from library** — `+ Add from library` opens a multi-select template picker filtered to the area's category (falls back to all when no match).

### CRUD additions
- **Copy area** — kebab on area card → "Copy as…". Backend `POST /api/areas/:id/copy` clones area + tasks (no completions). Both bug fixes shipped: response now returns full Area shape (Android serialization no longer errors), copied tasks seed `last_done_at = now` (start green, not red).
- **Rename household** — long-press Household header → "Rename household". Backend `PATCH /api/household` accepts `{name}` alongside `{pausedUntil}` (separate request types on Android to avoid clobbering each other).

### Notes (Migration 0008)
- **Persistent task notes** (e.g. "use Method, not bleach") — column on `tasks`, editable in TaskFormDialog, displayed inline on TaskRow with 📝 prefix.
- **Per-completion notes** (e.g. this week's grocery list) — column on `completions`, captured via "Mark done with notes…" in the task long-press menu, displayed under the entry on Activity tab.
- Backend `PATCH /api/tasks/:id` accepts `notes` (empty clears); `POST /api/tasks/:id/complete` accepts `notes`; GET endpoints return them.

### Activity tab undo
- Long-press any activity row → "Undo this completion" → confirmation → backend `DELETE /api/completions/:id` recomputes `tasks.last_done_at` via `SELECT MAX(done_at)` of remaining completions. Lets users undo accidents that happened before the snackbar timed out (e.g. previous session, app crash).

### Template library expansion (Migration 0007)
- Added 46 templates across 6 non-cleaning categories: Errands, Vehicle, Personal/health, Financial, Plants, Family. Original cleaning categories still there. Total ≈126 templates.
- Onboarding wizard now exposes all 16 categories.
- Browse-library button in TaskFormDialog opens a single-select picker scoped to current area's category.

### Smaller polish
- **Manually-created tasks start green** (`lastDoneAt = now` on create), not all-red.
- **Overdue text wording**: "3 days overdue" instead of "200%".
- **Search in Household tab** — filter areas + their tasks by name; X to clear.
- **Add area dialog suggestions** match the wizard's full 16-room list (was a divergent shorter list).
- **Vacation icon tint** — only set explicit tint when paused (was rendering black on light themes).
- **CancellationException no longer leaked into homeError** — `Repo.refresh()` now rethrows cancellations cleanly.
- **Polling lifecycle-aware** — `LifecycleEventObserver` stops polling on ON_STOP (app backgrounded) and resumes on ON_START. Prevents stale "Unable to resolve host" errors when reopening the app.
- **Auth screen field widths** — email/password/etc. now `fillMaxWidth()` to match the submit button.
- **Invite code dialog** — now copyable (Copy button → clipboard) and shareable (Share… button fires Intent.ACTION_SEND).
- **App version in Settings** — `Version 0.1.N (build N)` where N = git commit count. Workflow uses `fetch-depth: 0` so it's a real number, not always 1.
- **FCM debugging panel** in Settings (visible token + register button) — shipped during the FCM saga, kept around as it's useful.

### Bug fixes worth noting
- **OAuth2 grant type typo** — was `urn:ietf:params:oauth2:grant-type:jwt-bearer`, should be `urn:ietf:params:oauth:grant-type:jwt-bearer` (no `2` in `oauth`). Caused all FCM messages to silently fail. Fixed by inspecting google-auth-library source.
- **`autoRotate` returned as integer** — D1 stores booleans as INTEGER. Backend now coerces to JSON boolean (`r.autoRotate !== 0`) before returning. Test that previously asserted `.toBe(1)` rubber-stamped the bug; tightened to `.toBe(true)`.
- **Device token never registered** — `kotlinx.serialization` skipped `platform: "android"` because it matched the default. Set `encodeDefaults = true` in the Json config.
- **Tab name typo in tests** — Kotlin 2.0 rejects backtick test names containing `:`. Renamed `golden path: add ...` → `golden path add ...`.

### Backend tests
- 84 tests covering all the above
- Local test runner via `wrangler dev --local --port 8788` + `vitest` against HTTP (workerd crashes on Windows directly)

### Tab/Maestro flows updated
All Phase 1–4 Maestro flows updated to handle:
- Onboarding wizard appearing on first launch (taps `onboardingSkip`)
- FAB now only on Household tab (taps `tab:household` before `addAreaFab`)
- Renamed tab tags (`tab:areas` → `tab:household`)

---

## Phase 6 — Gamification (placeholder graphics)  ⏸ on hold

User explicitly paused this in favor of polish. Original spec preserved below for when we resume.

Functional gamification with placeholder Dusty (gray circle for now). Real art lands in Phase 7.

### Backend
- ⏳ Migration `0007_gamification.sql`:
  ```sql
  CREATE TABLE monthly_scores (
    household_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    year_month TEXT NOT NULL,
    points INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (household_id, user_id, year_month)
  );
  ```
- ⏳ Increment `points += effort_points` on each completion.
- ⏳ `GET /api/leaderboard?yearMonth=2026-04` — ordered list of `{member, points}` plus synthetic Dusty entry whose score grows linearly with calendar progression.
- ⏳ `GET /api/fairshare` — target split (default even, override via PATCH /api/household), actual split this month, delta.

### Android
- ⏳ `LeaderboardScreen.kt` (new) — bar chart of members + Dusty (placeholder gray circle).
- ⏳ FairShare tile on HomeScreen — surface only when delta > 10%.
- ⏳ Completion feedback — haptic + sound on Just Did It (addresses Tody complaint #5).

### Visual verification
- ⏳ `.maestro/phase6_leaderboard_and_fairshare.yaml` — two-user completions; leaderboard updates live via Phase 3 push; FairShare flag appears when one user does 70%+ of effort.

---

## Phase 7 — Polish: graphics, icons, focus timer, multi-home  ⏳

Visual parity. Heavy art lift, low logical complexity.

- ⏳ **Dusty mascot** — Lottie animations (idle / climbing / defeated / victorious). Replace placeholder. Add monthly outcome screen.
- ⏳ **Area icon library** — bundled SVG/vector pack (~40 icons). Picker in create-area sheet.
- ⏳ **Focus timer** — 25/45/120-min Pomodoro tied to a Focus List. Wake lock + ongoing notification.
- ⏳ **Monthly challenge** — Dusty issues a tuned challenge ("Complete 3 bathroom tasks this week"); track progress.
- ⏳ **Multi-home (decision point)** — `homes` table parent to `households`. Decide near phase end whether worth it for a single-household self-host.
- ⏳ **Sound + haptic library** — distinct sounds for completion, undo, level-up, monthly reset.

### Visual verification
- ⏳ Visual QA against side-by-side Tody screenshots for each main screen (Burke supplies). Lottie smoothness on a mid-range device.

---

## Cross-cutting reminders (apply every phase)

- Migrations are append-only — new file with the next number.
- All new fields in API responses are additive — never remove/rename without a `/v2/` namespace.
- Every new endpoint sits under `/api/*` middleware and uses `c.get('user').{sub,hh}` for actor + scope.
- Every new Composable gets `Modifier.testTag(...)` on anything an acceptance flow needs.
- Every phase's commit explicitly notes what was *not* done and why (look at Phase 1's Roborazzi note).
- Verification gates: backend tests green + Compose tests green + Maestro flow green + screenshot artifacts reviewed by you (`gh run download` + `Read`) + Burke does final real-device sideload.

## Open questions to revisit

- **Onboarding wizard interaction with existing users** — Phase 5 plan: skip if user already has areas. Verify this still feels right when we get there.
- **Dusty's score curve** — Phase 6 starts linear; tune after shipping.
- **Multi-home** — Phase 7 decision point. May not be worth it for Burke's single-household deployment.
- **iOS** — out of scope for now. FCM works there too via APNs bridge if/when we add it.
- **Roborazzi adoption** — when we add it, drop a single phase's worth of snapshot tests first to validate the gradle wiring before backfilling everything.
