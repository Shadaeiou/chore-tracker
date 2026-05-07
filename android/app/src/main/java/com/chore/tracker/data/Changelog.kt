package com.chore.tracker.data

/** One entry in the user-facing changelog shown on the Settings screen. */
data class ReleaseNote(
    val version: String,
    val date: String,
    val bullets: List<String>,
)

/**
 * Manually maintained release history. The first entry is the latest and
 * shows expanded by default in Settings. Add a new entry at the top of
 * the list whenever you ship a meaningful change worth surfacing to users.
 *
 * Version numbers match the auto-incrementing build versionCode (= git
 * commit count on main), so each entry corresponds to a real release tag.
 */
val CHANGELOG: List<ReleaseNote> = listOf(
    ReleaseNote(
        version = "0.1.81",
        date = "2026-05-07",
        bullets = listOf(
            "Rewards now have two pools: shared Household rewards and your own Personal rewards — every effort point you earn lands in both",
            "Pick a Household reward as the round goal; when the shared pool reaches its cost, claim it as a household win and start a new round",
            "Personal rewards are private — spend your individual point balance to redeem your own rewards anytime you have enough",
            "Any household member can add and edit Household rewards (name, emoji, points)",
            "New rock-paper-scissors mini-game in pixel art — best of 3 between household members; winner gets to pick the next household reward",
        ),
    ),
    ReleaseNote(
        version = "0.1.80",
        date = "2026-05-07",
        bullets = listOf(
            "Rewards now share one household pool — points from every member count toward the same goal",
            "Any household member can edit a reward's name, emoji, or point cost",
            "Softer overdue colors on the Today tab — red 300 instead of red 700, fainter row tints",
        ),
    ),
    ReleaseNote(
        version = "0.1.77",
        date = "2026-05-06",
        bullets = listOf(
            "New Rewards tab — earn effort points from chores, redeem against household-defined rewards, or pick from suggested ones",
            "Profile color ring around your avatar — pick a color in your profile, see it everywhere your avatar shows",
            "Today tab redesign: overdue tasks bold-red, snoozed tasks italic-amber with 💤, member sections show avatar + ring color",
        ),
    ),
    ReleaseNote(
        version = "0.1.76",
        date = "2026-04-30",
        bullets = listOf(
            "Push notification when someone reacts to one of your completed activities",
        ),
    ),
    ReleaseNote(
        version = "0.1.75",
        date = "2026-04-30",
        bullets = listOf(
            "Update notification now fires the moment a new release is published — no 24-hour wait",
        ),
    ),
    ReleaseNote(
        version = "0.1.74",
        date = "2026-04-30",
        bullets = listOf(
            "Tap a chore on Today to see its notes and reassign it in one dialog",
            "Push notification when a new app update is available; tap to download and install with no detour through Settings",
        ),
    ),
    ReleaseNote(
        version = "0.1.73",
        date = "2026-04-30",
        bullets = listOf(
            "Comment notifications now have inline Reply and 👍 React actions — answer right from the notification shade without opening the app",
        ),
    ),
    ReleaseNote(
        version = "0.1.72",
        date = "2026-04-30",
        bullets = listOf(
            "React with an emoji or comment on completed activities — tap any row on the Activity tab to open the thread",
            "Multi-turn comment threads, owners can edit or delete their own comments",
            "The original completer gets a push notification when someone comments on their work",
            "Status-bar notification icon is now the sock instead of a white circle",
        ),
    ),
    ReleaseNote(
        version = "0.1.71",
        date = "2026-04-29",
        bullets = listOf(
            "Snoozing a chore now keeps its recurring schedule on track. If trash is normally Thursday and you snooze a day for a holiday pickup, completing it on Friday still anchors the next due to the following Thursday rather than shifting to a Friday cadence forever.",
        ),
    ),
    ReleaseNote(
        version = "0.1.67",
        date = "2026-04-29",
        bullets = listOf(
            "Compact area sort screen with send-to-top and send-to-bottom arrows for quick reordering",
        ),
    ),
    ReleaseNote(
        version = "0.1.66",
        date = "2026-04-29",
        bullets = listOf(
            "Area ordering is now per-device — each housemate can lay out their list however they like without affecting anyone else",
        ),
    ),
    ReleaseNote(
        version = "0.1.65",
        date = "2026-04-29",
        bullets = listOf(
            "Changelog section in Settings (this list) — see what shipped recently, expand for full history",
        ),
    ),
    ReleaseNote(
        version = "0.1.64",
        date = "2026-04-29",
        bullets = listOf(
            "Tap any chore on Today to reassign it to a household member",
            "Push notification when someone assigns you a chore due today",
            "Reminders can be created on behalf of another member; they're notified",
        ),
    ),
    ReleaseNote(
        version = "0.1.62",
        date = "2026-04-29",
        bullets = listOf(
            "Today tab now shows each housemate's today list (chores + public reminders)",
        ),
    ),
    ReleaseNote(
        version = "0.1.60",
        date = "2026-04-28",
        bullets = listOf(
            "Household roles: admins can remove other members from Settings",
        ),
    ),
    ReleaseNote(
        version = "0.1.59",
        date = "2026-04-28",
        bullets = listOf(
            "10-second undo snackbar when deleting a chore or area",
        ),
    ),
    ReleaseNote(
        version = "0.1.58",
        date = "2026-04-28",
        bullets = listOf(
            "À la carte daily reminders, public or private, on the Today tab",
            "Assignee photo replaces the name chip on chore rows",
            "Snooze dialog turns into a one-tap Unsnooze on already-snoozed chores",
        ),
    ),
    ReleaseNote(
        version = "0.1.57",
        date = "2026-04-28",
        bullets = listOf(
            "On-demand chores rotate through the household without a fixed schedule",
        ),
    ),
    ReleaseNote(
        version = "0.1.55",
        date = "2026-04-28",
        bullets = listOf(
            "Avatars cached per user, only fetched when their version bumps",
        ),
    ),
    ReleaseNote(
        version = "0.1.54",
        date = "2026-04-28",
        bullets = listOf(
            "User profile editing — change your display name, upload a photo",
        ),
    ),
    ReleaseNote(
        version = "0.1.53",
        date = "2026-04-28",
        bullets = listOf(
            "Drag handles to reorder areas on the Household tab",
            "Area cards collapse and remember their state per area",
        ),
    ),
    ReleaseNote(
        version = "0.1.51",
        date = "2026-04-28",
        bullets = listOf(
            "Household search supports @user, 7d, notes, yesterday filters",
        ),
    ),
    ReleaseNote(
        version = "0.1.46",
        date = "2026-04-28",
        bullets = listOf(
            "Rebrand: app name and launcher icon are now the sock motif",
            "Password visibility toggle on the auth screen",
        ),
    ),
    ReleaseNote(
        version = "0.1.45",
        date = "2026-04-28",
        bullets = listOf(
            "Other devices in your household now stay in sync silently — no more polling",
        ),
    ),
    ReleaseNote(
        version = "0.1.44",
        date = "2026-04-28",
        bullets = listOf(
            "Release builds are signed with a stable keystore so updates can chain across builds",
        ),
    ),
    ReleaseNote(
        version = "0.1.43",
        date = "2026-04-28",
        bullets = listOf(
            "Auto-updater bootstrap: opt in from Settings to be told about new builds",
        ),
    ),
    ReleaseNote(
        version = "0.1.42",
        date = "2026-04-28",
        bullets = listOf(
            "Multiple theme palettes to pick from in Settings",
            "Status indicator dots are recolorable per state (overdue / due today / not due)",
        ),
    ),
    ReleaseNote(
        version = "0.1.41",
        date = "2026-04-28",
        bullets = listOf(
            "Move invite to the household menu, vacation mode + indicators consolidated under Settings",
        ),
    ),
    ReleaseNote(
        version = "0.1.40",
        date = "2026-04-28",
        bullets = listOf(
            "Tap-to-view notes on Today tab",
            "Fix UTC drift on the complete dialog when backdating",
        ),
    ),
    ReleaseNote(
        version = "0.1.38",
        date = "2026-04-28",
        bullets = listOf(
            "Rounded task rows with tag chips for area / frequency / assignee / notes",
            "Today tab is read-only triage; edits live on Household",
        ),
    ),
    ReleaseNote(
        version = "0.1.37",
        date = "2026-04-28",
        bullets = listOf(
            "Task and activity rows redesigned for clarity",
            "Move a task between areas via the edit dialog",
        ),
    ),
    ReleaseNote(
        version = "0.1.36",
        date = "2026-04-28",
        bullets = listOf(
            "Backdate a completion to any past day, not just within the task's lifetime",
        ),
    ),
    ReleaseNote(
        version = "0.1.35",
        date = "2026-04-28",
        bullets = listOf(
            "Swipe-driven task UX: swipe right to complete, swipe left to snooze or delete",
            "Today tab now filtered to chores assigned to you (or unassigned)",
        ),
    ),
    ReleaseNote(
        version = "0.1.34",
        date = "2026-04-28",
        bullets = listOf(
            "Log a completion on behalf of someone else in the household",
        ),
    ),
    ReleaseNote(
        version = "0.1.32",
        date = "2026-04-28",
        bullets = listOf(
            "Notes on tasks and per-completion notes",
            "Search bar in the Household tab",
        ),
    ),
    ReleaseNote(
        version = "0.1.30",
        date = "2026-04-28",
        bullets = listOf(
            "Mass-select areas to delete in bulk",
            "Manually-added tasks now start green instead of immediately red",
        ),
    ),
    ReleaseNote(
        version = "0.1.29",
        date = "2026-04-28",
        bullets = listOf(
            "Rename and copy areas, mass-delete tasks, add many tasks at once from the library",
        ),
    ),
    ReleaseNote(
        version = "0.1.28",
        date = "2026-04-28",
        bullets = listOf(
            "Tab refresh: Today / Household / Activity, with workload moved to its own panel",
            "Today tab now date-based (due today or earlier) instead of percentage-based",
        ),
    ),
    ReleaseNote(
        version = "0.1.27",
        date = "2026-04-28",
        bullets = listOf(
            "Invite codes are now copyable and shareable from the household menu",
        ),
    ),
    ReleaseNote(
        version = "0.1.24",
        date = "2026-04-28",
        bullets = listOf(
            "Template library expanded beyond cleaning chores — yard, car, kids, pets, finances, and more",
        ),
    ),
    ReleaseNote(
        version = "0.1.22",
        date = "2026-04-28",
        bullets = listOf(
            "Long-press an activity row to undo any past completion",
        ),
    ),
    ReleaseNote(
        version = "0.1.21",
        date = "2026-04-27",
        bullets = listOf(
            "Phase 5: starter template library and onboarding wizard for new households",
        ),
    ),
    ReleaseNote(
        version = "0.1.20",
        date = "2026-04-27",
        bullets = listOf(
            "Phase 4: vacation mode (pause the household), per-task snooze, retroactive completion",
        ),
    ),
    ReleaseNote(
        version = "0.1.17",
        date = "2026-04-27",
        bullets = listOf(
            "Settings now shows the running version + build number; versionCode auto-increments per push",
        ),
    ),
    ReleaseNote(
        version = "0.1.16",
        date = "2026-04-27",
        bullets = listOf(
            "Edit and delete existing tasks and areas",
        ),
    ),
    ReleaseNote(
        version = "0.1.4",
        date = "2026-04-27",
        bullets = listOf(
            "Phase 3: FCM push notifications when household members complete chores",
        ),
    ),
)
