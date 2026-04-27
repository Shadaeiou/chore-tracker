# Roadmap — Tody Feature Parity

Phased plan to bring Chore Tracker to Tody feature parity. Each phase is a self-contained PR-equivalent commit (or small commit chain) that compiles, deploys, and ships a usable improvement on its own. Graphics-heavy work deferred to Phase 7.

**Status legend:** ✅ done · 🔄 in flight · ⏸ deferred (intentional) · ⏳ not started

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

## Phase 4 — Lifecycle (pause, snooze, retroactive)  ⏳

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

## Phase 5 — Task library + onboarding wizard  ⏳

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

## Phase 6 — Gamification (placeholder graphics)  ⏳

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
