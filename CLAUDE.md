# CLAUDE.md

Bootstrap context for any Claude working on this repo. Read this first, then `docs/ROADMAP.md` for what to build next and `docs/TODY_FEATURES.md` for the source-of-truth feature inventory we're cloning toward.

---

## What this is

**Chore Tracker** — a household chore-tracking app cloning the Tody Android app. Frequency-based "dirtiness" indicators, multi-user households, rotation, push notifications, notes, search, template library. Backend on Cloudflare Workers + D1, Android client in Kotlin/Compose/Retrofit. The user (Burke) and his household are the primary users; eventual Play Store distribution is in scope but not required for early phases.

## Current state (read this for handoff)

Phases 1–5 + an extensive 5b polish pass shipped. **Phase 6 (gamification) is on hold** — user wants more polish first. **Read `docs/ROADMAP.md` "Quick handoff for the next context" section first**; it summarizes everything that's actually built.

App is currently structured around **3 tabs**: Today / Household / Activity. Notes, search, copy area, mass-select, multi-pick from library, vacation mode, snooze, retroactive completion, FCM push notifications all working. ~84 backend tests + ~53 Android tests passing locally. One Compose test (`golden path add area add task complete task`) consistently times out at the final completeButton click — code path verifies on inspection and a similar pre-seeded test passes; investigate if user reports the symptom on real device.

App name + logo deliberately deferred (still "Chore Tracker", no logo). User uses real-device sideload from CI APK builds, no local emulator.

## Working with the user

- **User:** Burke (`Shadaeiou` on GitHub). Email `burke.blazer@gmail.com`. Windows 11 + Git Bash. Has a real Android device for sideload testing.
- **Tone:** terse. He skim-reads — front-load the answer, no preambles, no trailing recaps. He'll redirect if he wants more.
- **Action vs. ask:**
  - Local file edits, tests, typechecks — just do them.
  - Pushing to remote, deploying, deleting branches, rotating secrets, anything destructive or visible to others — confirm first.
  - Auto mode (when active) means "execute autonomously, course-correct as you go" — but destructive actions still need confirmation.
- **Workflow:** trunk-based on `main`. No PRs. Push directly to `main` once tests pass locally; let CI verify. The deleted `claude/cloudflare-backend-deployment-6u9lj` branch was a one-time scaffold, not a permanent pattern.

## Stack at a glance

| Layer | Tech | Where |
|---|---|---|
| API | Cloudflare Worker (Hono) + D1 (SQLite) + JWT (HS256) | `backend/` |
| Migrations | D1 SQL files, append-only | `backend/migrations/` |
| Backend tests | Vitest with `@cloudflare/vitest-pool-workers` (real Workers runtime + in-memory D1) | `backend/test/` |
| Client | Kotlin + Jetpack Compose + Material3 + Retrofit + DataStore | `android/app/` |
| Client unit tests | JUnit4 + Robolectric + Compose UI test + Truth | `android/app/src/test/` |
| E2E visual | Maestro flows on a Pixel 5 emulator (api-33) in CI | `.maestro/` |
| Auto-deploy backend | Cloudflare Workers Builds (dashboard-driven, runs `npm run deploy:ci` on push to main) | not in repo |
| Android APK build | GitHub Actions (`build-android.yml`) | `.github/workflows/` |

## Live URLs and identifiers

- **Worker URL:** `https://chore-tracker-api.shadaeiou.workers.dev`
- **D1 database id:** `a37ef031-ce55-4a72-bd2f-cd95aabb3921` (in `backend/wrangler.toml`)
- **GitHub repo:** `Shadaeiou/chore-tracker`
- **JWT_SECRET:** stored as a Worker secret. **Was leaked once in chat** and rotated — never paste secret values back.
- **API_BASE_URL** (GitHub repo variable for the APK build): set to the worker URL above.

## Commands you'll actually run

```bash
# Backend
cd backend && npm ci                    # install deps
npm run typecheck                       # tsc --noEmit
npm run dev:test                        # wrangler dev --local --port 8788 (for local tests)
npm run test:local                      # vitest against running dev:test server (~3s)
npm test                                # vitest-pool-workers — DOES NOT WORK ON WINDOWS; CI only
npm run dev                             # local wrangler dev (default port 8787)
npm run migrate:remote                  # apply migrations to live D1
npm run deploy:ci                       # migrations + deploy (this is what Workers Builds runs)

# Git / deploy flow
git push origin main                    # triggers Workers Build (~1-2 min) + Test Android (~10 min) + Build APK (~5 min)

# CI / artifacts
gh auth status                          # gh CLI is installed at ~/.local/bin/gh.exe
gh run list --repo Shadaeiou/chore-tracker --limit 8
gh run view <run-id> --repo Shadaeiou/chore-tracker --json jobs --jq '.jobs[] | "\(.name): \(.status) \(.conclusion // "")"'
gh api repos/Shadaeiou/chore-tracker/actions/jobs/<job-id>/logs > /tmp/log.txt   # pull a single job's log mid-run
gh run download <run-id> --repo Shadaeiou/chore-tracker --name maestro-artifacts # screenshots after Maestro run
```

## Local test commands (fast feedback before pushing)

```bash
# Backend — typecheck only (instant)
make backend-typecheck

# Backend — full integration tests (~3s)
# Terminal 1 (keep running):
cd backend && npm run dev:test      # wrangler dev --local --port 8788
# Terminal 2:
make backend-test-local

# Android — JVM/Robolectric unit tests (~1s warm, ~90s cold first run)
make android-test

# Everything at once (requires wrangler dev running in another terminal)
make test
```

## Local-environment gotchas (Windows)

- **`workerd` crashes on Windows** under `npm test` (vitest-pool-workers uses the real workerd binary). Use `make backend-test-local` instead — runs against wrangler dev --local which is stable.
- **Android SDK** is installed at `C:/Users/burke/android-sdk`. `local.properties` (gitignored) points Gradle at it. Gradle 8.10.2 is at `C:/Users/burke/gradle-8.10.2`.
- **`gradle` not on PATH by default.** The Makefile sets it up. Don't run `gradle` directly from bash; use `make android-test` or the full path.
- **APK builds and Maestro E2E** still require CI — no local emulator configured.
- **gh CLI** is at `/c/Program Files/GitHub CLI/gh`. Auth tokens have `repo`, `workflow`, `read:org`, `gist` scopes.

## Conventions you must follow

### Commits
- One commit per phase (or per logical sub-feature within a large phase). Don't bundle unrelated work.
- Commit messages: imperative subject, short body explaining the **why**, signed `Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>`.
- **Migrations are append-only.** Never edit a published migration. Create a new one with the next sequential number.
- **API fields are additive.** A stale APK must keep working when the worker ships new fields. Don't remove or rename without a `/v2/` namespace.

### Tests
- Every endpoint gets a backend integration test in `test/api.test.ts` covering happy path, household-scoping (cross-household must 404), and at least one failure mode.
- Every new Composable gets a Robolectric Compose test asserting its testTag(s) render and its callbacks fire.
- Every phase gets a Maestro flow (`.maestro/phase<N>_<feature>.yaml`) with `assertVisible`/`assertText` for each acceptance bullet and `takeScreenshot:` at each significant state. CI uploads screenshots as artifacts.

### Test IDs
- Attach `Modifier.testTag("camelCaseId")` to anything an acceptance flow needs to find. Cheaper than relying on visible text (which moves for copy/i18n).
- **Tabs:** `tab:today`, `tab:household`, `tab:activity` (renamed from older `tab:plan` / `tab:areas` / `tab:chores` — those are gone)
- **Layout:** `addAreaFab` (only on Household tab now), `inviteButton`, `settingsButton`, `vacationButton`, `homeScreen`, `homeError`, `snackbarHost`, `vacationBanner`, `resumeButton`, `householdHeader`, `householdName`, `householdMenu`, `householdMenuRename`, `householdMenuSelectAreas`, `householdSearchField`, `areaSelectionBar`, `massDeleteAreasButton`
- **Area cards:** `areaCard:<name>`, `areaHeader:<name>`, `areaMenu:<name>`, `areaMenuEdit:<name>`, `areaMenuCopy:<name>`, `areaMenuSelect:<name>`, `areaMenuDelete:<name>`, `addTaskButton:<name>`, `addFromLibraryButton:<name>`, `selectionBar:<name>`, `massDeleteButton:<name>`, `selectableAreaCard:<name>`, `selectableAreaCheckbox:<name>`
- **Task rows:** `taskRow:<name>` (tap → edit, swipe-right → complete dialog, swipe-left → snooze/delete dialog; long-press menu and green completeButton are gone), `assigneeBadge:<name>`, `taskNotes:<name>`, `selectableTaskRow:<name>`, `selectableCheckbox:<name>`
- **Activity rows:** `activityScreen`, `activityRow:<taskName>`, `activityDateHeader:<date>`, `activityNotes:<taskName>`, `activityMenu:<taskName>`, `activityMenuUndo:<taskName>`, `undoCompletionDialog:<taskName>`, `undoCompletionConfirm`
- **Dialogs:** `addTaskDialog`, `editTaskDialog`, `addAreaDialog`, `taskNameField`, `taskFreqField`, `assigneePicker`, `assigneeOption:<name>`, `autoRotateToggle`, `effortSlider`, `taskNotesField`, `addTaskConfirm`, `browseLibraryButton`, `libraryPicker`, `libraryTemplate:<id>`, `multiLibraryPicker`, `multiTemplate:<id>`, `multiLibraryConfirm`, `textDialog`, `textDialogField`, `textDialogConfirm`, `areaSuggestion:<name>`, `inviteDialog`, `inviteCodeText`, `inviteCopyButton`, `inviteShareButton`, `deleteAreaDialog:<name>`, `deleteAreaConfirm`
- **Complete dialog (swipe-right):** `completeTaskDialog:<name>`, `completeDateField`, `completedByPicker`, `completedByOption:<name>`, `completionNotesField`, `completeTaskConfirm`
- **Snooze/Delete dialog (swipe-left):** `snoozeOrDeleteDialog:<name>`, `snoozeAmountField`, `snoozeUnitField`, `snoozeUnitOption:<DAYS|WEEKS|MONTHS|YEARS>`, `snoozeConfirm`, `deleteTaskConfirm`
- **Onboarding wizard:** `onboardingScreen`, `onboardingSkip`, `onboardingNext`, `onboardingBack`, `onboardingFinish`, `roomChip:<key>`, `roomHeader:<key>`, `templateRow:<id>`, `templateToggle:<id>`
- **Auth:** `authScreen`, `emailField`, `passwordField`, `displayNameField`, `householdNameField`, `inviteCodeField`, `submitButton`, `authError`
- **Settings:** `settingsScreen`, `settingsBack`, `themeOption:SYSTEM|LIGHT|DARK`, `versionText`, `signOutButton`

### Auth and scoping
- Every protected endpoint goes under the `/api/*` Hono middleware (`backend/src/index.ts:154-162`).
- Use `c.get('user').sub` (user id) and `c.get('user').hh` (household id) for the actor and scope. Never trust ids from the request body for household scoping.

## Verification loop (the one Burke cares about)

The plan was approved with explicit Playwright-equivalent visual verification:

1. **You write code.**
2. **You push to `main`.** CI runs backend tests (Linux), unit tests, builds APK, runs Maestro on emulator.
3. **You run `gh run list` to find the run, `gh run view` to see job statuses, and `gh run view --log-failed` once it's complete.**
4. **For visual verification:** `gh run download <id> --name maestro-artifacts`, then `Read` the PNGs to confirm UI matches expectations.
5. **Only after all three layers are green AND the screenshots match expectations** does the work merge (well — already merged on main here, so really: only after that does the phase count as "shipped").
6. **Burke does the final real-device sideload** for things emulator-only verification can't catch (real OEM notification skin, FCM delivery on actual Google Play Services).

If a phase isn't visually verified by you (because the artifact is still uploading, or you ran out of time), say so explicitly — don't claim "done" prematurely.

## Decisions locked with the user

These are not up for renegotiation without explicit user buy-in:

| Decision | Locked to |
|---|---|
| Push transport | **FCM via Firebase HTTP v1**, not WebSocket DO or aggressive polling |
| FCM grant type | `urn:ietf:params:oauth:grant-type:jwt-bearer` (no `2` — easy typo, took hours to find) |
| Rotation model | **Per-task `auto_rotate` flag**, Tody's pattern |
| Scope target | **Full Tody parity, phased**; graphics/icons/Dusty mascot deferred to Phase 7 |
| Default branch & flow | `main`-only, push directly, no PR review process |
| Deploy mechanism | Cloudflare Workers Builds via dashboard. The old `deploy-backend.yml` was deleted. |
| Tab structure | **Today / Household / Activity** (3 tabs). Today = read-only triage, Household = manage, Activity = history+stats. Don't bring back the old single Chores tab. |
| New tasks default | `lastDoneAt = now` so indicators start green, not red. Both manual creation and area-copy do this. |
| `kotlinx.serialization` Json config | **`encodeDefaults = true`** — fields with default values must serialize, otherwise backend validators see missing fields. Don't change this. |
| Boolean coercion | D1 stores booleans as INTEGER; backend GET handlers must coerce `autoRotate: number` → `autoRotate: boolean` before returning JSON. Tests assert the type, not just truthiness. |
| Undo path | Snackbar undo for last action, **plus** long-press any activity row → undo any past completion (`DELETE /api/completions/:id`). Don't remove the activity-tab undo. |
| App name + logo | **Deferred** until user picks one. Don't propose names without explicit ask. |

## Where to look next

- **`docs/ROADMAP.md`** — the 7-phase plan, current progress, what to pick up next.
- **`docs/TODY_FEATURES.md`** — feature inventory of the app we're cloning. Reference before designing anything new — chances are Tody already has a pattern for it.
- **`README.md`** — public-facing onboarding doc (less detail than this file, broader audience).
- **`~/.claude/plans/expressive-chasing-balloon.md`** — the original approved plan; same content as `docs/ROADMAP.md` but unversioned. Prefer the in-repo copy.
