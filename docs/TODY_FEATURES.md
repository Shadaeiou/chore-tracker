# Tody Feature Inventory

Source-of-truth feature reference for the app we're cloning. Compiled from the Tody official site (todyapp.com), the Google Play listing, third-party reviews (tidied.app, makeuseof, gethomsy.com), and user complaints (justuseapp.com, mobilesyrup, WAKA8). Use this when scoping new features — Tody likely already has a pattern for what you're considering.

**Confidence:** **[C]** = corroborated across 2+ independent sources · **[P]** = single source · **[I]** = inferred / uncertain.

---

## Core data model

- **Areas / Rooms** **[C]** — top-level container per home. Each can have a color scheme. A "General" bucket exists for room-agnostic tasks.
- **Tasks** **[C]** — belong to an area. Tody ships a library tiered as **Basic**, **Special**, **Custom** (user-created).
- **Task attributes:**
  - **Frequency** **[C]** — "every N days" interval (not calendar-day pinned).
  - **Effort** **[C]** — difficulty/weight, used for fair-distribution math (Premium-gated **[P]**).
  - **Assignee** **[C]** — single member, with optional rotation flag.
  - **Initial state** **[C]** — at first setup, mark current cleanliness on Good→Overdue scale so the indicator doesn't start all-red.
  - **Last-completed timestamp** **[C]** — drives dueness.
- **Dueness / Indicator** **[C]** — derived value: how far past the optimal interval. Animated bar green→yellow→red. Two tick marks denote a 20% optimal-window around the target day.
- **Plan** **[C]** — auto-generated prioritized to-do list across all areas, sorted by dueness (not date).
- **Members / Participants** **[C]** — household members; assignable; have per-member effort totals.
- **Home / Plan instance** **[P]** — Premium "multiple homes" tier implies a Home is a top-level entity above areas.
- **History / completion log** **[C]** — per-task events, fed to leaderboards and FairShare.

---

## Multi-user / household features

- **Cloud sync is Premium-only** **[C]**. Free tier is single-device.
- **Task assignment** to a specific member **[C]**.
- **Auto-rotate assignee on completion** **[C]** — the model we picked.
- **Per-member workload totals** visible to everyone **[C]**.
- **FairShare (2025)** **[C]** — set a target split (e.g., 60/40); app surfaces imbalance using effort-weighted history.
- **Anyone can claim credit** **[C]** — any household member can mark any task done.
- **Notifications: scheduled reminders** **[C]**, "just enough" cadence.
- **Push when others complete** — **NOT confirmed** in any Tody source. **[I]** likely absent or weak; reviewers describe Tody as best for "one manager + participants" rather than peer-coordinated. **This is where our clone differentiates** (Phase 3).
- **Realtime sync latency** — DataSync exists, latency uncharacterized **[I]**.

---

## Task lifecycle features

- **Just Did It!** **[C]** — primary completion CTA; resets dueness, demotes task to bottom of priority list.
- **Retroactive completion** **[C]** — complete a task even when not currently due.
- **Pause / Vacation Mode** **[C]** — freezes indicators globally so dueness doesn't accrue while away. **Tody's docs say ignoring Pause is the #1 reason users churn** — surface it well in our clone (Phase 4).
- **Skip / postpone individual task** **[P]** — described as "pausing a task you don't want to do today". UI affordance label not confirmed.
- **Edit frequency / effort / assignee** at any time **[C]**.
- **Focus Timer** **[C]** — 25/45/120-min Pomodoro tied to a Focus List of selected tasks. Premium **[C]**.
- **Undo a completion** — **NOT explicitly documented** in any Tody source **[I]**. We're shipping it as a primary affordance (Phase 1) — likely a differentiator.
- **"I'll do it later" as a distinct button** — **NOT found** **[I]**. Model is tap → Just Did It rather than triage.

---

## Gamification / engagement

- **Dusty the dustball mascot** **[C]** — antagonist; you compete against his progress monthly.
- **Monthly Challenges** **[C]** — Dusty issues a challenge tuned to your habits.
- **Points** awarded per completed task, weighted by effort **[C]**.
- **Household leaderboard** vs. Dusty and vs. each other, monthly **[C]**.
- **Unlockable rewards / cosmetics** mentioned in 2025–2026 updates **[P]**.
- **Streaks / XP / levels** — **NOT confirmed** as Tody features. Generic gamification roundups mention them but none cite Tody **[I]**. Tody's hook is Dusty + dueness bar, not streaks.

---

## Free vs. Premium split

| Free | Premium |
|---|---|
| Full Indicator Method | **DataSync** (cloud, multi-device) |
| Unlimited areas/tasks | **Multi-user sharing** (household) — *the practical gate for households* |
| Basic Dusty gamification | **Effort manager / FairShare** |
| Single-device use | **Vacation/Pause** **[P]** |
| Library of tasks | **Focus Timer & Focus List** |
| Free-tier marketing pitch: "feature complete for single users" | **Multiple homes** |

**Pricing [C]:** ~$9.99–$19.99/yr; up to ~$30/yr family; in-app range $9.99–$59.99. **30-day free trial.** iOS historically had a $6.99 one-time tier; Android has always been free + IAP. The 2024–2025 shift to subscription is a friction point with longtime users (see complaints).

**Tiers [C]:** Premium Solo / Duo / Family / Team.

For our self-hosted clone the Free vs. Premium split doesn't matter — everything is "free" because the user pays for hosting. But it informs prioritization: anything Tody puts behind a paywall is what they consider their highest-value features.

---

## Notable UX patterns

- **Color-animated dueness bar** per task, green→yellow→red — Tody's signature visual.
- **Per-area status dot** — blank / orange / red, summarizes "how dirty is this room" at a glance.
- **Auto-prioritized plan** — main screen is a single sorted list of "what needs it most" rather than a calendar.
- **Initial cleanliness slider** during onboarding so the system isn't all-red on day one.
- **Tap-task → "Just Did It!"** is the dominant interaction (one-tap commit).
- **Indicator method, not deadlines** — a recurring Tody differentiator.
- **No calendar view** — intentional design call. Don't add one.

---

## Common user complaints (avoid these)

Sourced from Play Store reviews, justuseapp.com, mobilesyrup, and tidied.app. Listed roughly in frequency order:

1. **No global search / no cross-area "complete same task in N rooms"** **[C]** — power users hit this hard. Add search + bulk-complete-by-name.
2. **Subscription resentment after the 2024 model change** **[C]** — long-time buyers felt features were taken away. Be transparent; never move a Free feature behind a paywall later.
3. **Sync failures losing data** **[C]** — multiple reports of tasks not migrating to a new phone. **Server-side backup tied to account, easy restore** is essential.
4. **Billing/refund issues** **[C]** — including a £181/month report. Clean cancel flows + clear pricing.
5. **Unsatisfying completion feedback** **[C]** — "the click isn't as rewarding as a ding". Invest in audio/haptic on Just Did It (Phase 6).
6. **Multi-person coordination feels tacked-on** **[C]** — reviewers call Tody best for "one person managing it". Real-time activity feed + "X completed Y" notifications would meaningfully differentiate (Phase 2 + 3).
7. **No non-cleaning tasks** **[C]** — users want errands/cooking/kid-task support. **Decide deliberately whether to widen scope.** Current plan: stay cleaning-focused but don't actively block other use.
8. **Setup is heavy** **[C]** — "time-consuming". Templates + onboarding wizard (Phase 5) directly addresses this.
9. **Forgetting to use Pause during travel** **[C]** is the top abandonment cause per Tody's own docs. Phase 4 should surface a smart prompt ("Headed out? Pause indicators?") — not a passive setting.

---

## Honest gaps in this research

The following I could not directly verify and you should validate by installing Tody yourself before locking related design decisions:

1. Whether Tody pushes "X just completed Y" notifications to other members (likely no; we're betting on "no" and shipping it as a differentiator).
2. Whether per-task **postpone/skip** is a distinct UI affordance vs. just editing frequency.
3. Whether **streaks** exist as a first-class Tody concept.
4. Exact realtime-vs-eventual nature of DataSync.
5. Whether **undo a completion** is supported in any form.
6. Apartment Therapy and the Play Store full review wall both 403'd during research — worth a manual pass if a related decision is high-stakes.
7. r/CleaningTips and r/HomeImprovement weren't crawlable via search — direct Reddit visit would sharpen the complaints section.

---

## Sources used (corroborated)

- [Tody official site](https://todyapp.com/) and its [Tips page](https://todyapp.com/tips.html)
- [Google Play listing](https://play.google.com/store/apps/details?id=com.looploop.tody&hl=en)
- [Tidied — Tody App Review 2025](https://www.tidied.app/blog/tody-app-review)
- [MakeUseOf — How to Use Tody](https://www.makeuseof.com/how-to-use-tody-app-for-tidying-up/)
- [MobileSyrup — App of the Week](https://mobilesyrup.com/2019/07/14/tody-cleaning-review/)
- [Homsy vs Tody comparison](https://gethomsy.com/blog/comparisons/homsy-vs-tody)
- [Impact Influence Inspire — Managing my household with Tody](https://impactinfluenceinspire.com/managing-my-household-with-tody-a-review/)
- [JustUseApp Tody reviews](https://justuseapp.com/en/app/595339588/tody/reviews)
- [Productivity.directory Tody review](https://productivity.directory/tody)
- [WAKA8 / WILX TV "What the Tech" segments](https://www.waka.com/2024/09/02/what-the-tech-app-of-the-day-tody/)
