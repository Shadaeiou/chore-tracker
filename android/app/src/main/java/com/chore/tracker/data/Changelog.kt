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
 */
val CHANGELOG: List<ReleaseNote> = listOf(
    ReleaseNote(
        version = "0.1.48",
        date = "2026-04-29",
        bullets = listOf(
            "Push notification when someone reacts to one of your completed activities",
        ),
    ),
    ReleaseNote(
        version = "0.1.47",
        date = "2026-04-29",
        bullets = listOf(
            "Update notification now fires the moment a new release is published, not on a 24h timer",
        ),
    ),
    ReleaseNote(
        version = "0.1.46",
        date = "2026-04-29",
        bullets = listOf(
            "Tap a chore on Today to see its notes and reassign it in one dialog",
            "Push notification when a new app update is available; tap to download and install with no detour through Settings",
        ),
    ),
    ReleaseNote(
        version = "0.1.45",
        date = "2026-04-29",
        bullets = listOf(
            "Comment notifications now have inline Reply and 👍 React actions — answer right from the notification shade without opening the app",
            "Fix Android resource-linking error that broke the build for the previous notification-icon update",
        ),
    ),
    ReleaseNote(
        version = "0.1.44",
        date = "2026-04-29",
        bullets = listOf(
            "React with an emoji or comment on completed activities — tap any row on the Activity tab to open the thread",
            "Multi-turn comment threads, owners can edit or delete their own comments",
            "The original completer gets a push notification when someone comments on their work",
            "Status-bar notification icon is now the sock instead of a white circle",
        ),
    ),
    ReleaseNote(
        version = "0.1.43",
        date = "2026-04-29",
        bullets = listOf(
            "Snoozing a chore now keeps its recurring schedule on track. If trash is normally Thursday and you snooze a day for a holiday pickup, completing it on Friday still anchors the next due to the following Thursday rather than shifting to a Friday cadence forever.",
        ),
    ),
    ReleaseNote(
        version = "0.1.42",
        date = "2026-04-29",
        bullets = listOf(
            "Fix Kotlin compile failure in the visible-areas sort — the destructuring lambda was tripping type inference",
        ),
    ),
    ReleaseNote(
        version = "0.1.41",
        date = "2026-04-29",
        bullets = listOf(
            "Area ordering is now per-device — each housemate can lay out their list however they like without affecting anyone else",
        ),
    ),
    ReleaseNote(
        version = "0.1.40",
        date = "2026-04-29",
        bullets = listOf(
            "Tap any chore on Today to reassign it to a household member",
            "Push notification when someone assigns you a chore due today",
            "Reminders can be created on behalf of another member; they're notified",
        ),
    ),
    ReleaseNote(
        version = "0.1.39",
        date = "2026-04-29",
        bullets = listOf(
            "Today tab now shows each housemate's today list (chores + public reminders)",
            "Fix area reorder failing when only sortOrder changed",
            "Edit areas screen: \"Cancel\" renamed to \"Done\"",
            "Dedicated unsnooze endpoint so unsnooze no longer 400s",
        ),
    ),
    ReleaseNote(
        version = "0.1.34",
        date = "2026-04-28",
        bullets = listOf(
            "Household roles: admins can remove other members from Settings",
            "10-second undo snackbar when deleting a chore or area",
            "Snooze dialog turns into a one-tap Unsnooze on already-snoozed chores",
            "Assignee photo replaces the name chip on chore rows",
            "À la carte daily reminders, public or private, on the Today tab",
            "On-demand chores rotate through the household without a fixed schedule",
        ),
    ),
    ReleaseNote(
        version = "0.1.30",
        date = "2026-04-28",
        bullets = listOf(
            "User profile editing — change your display name, upload a photo",
            "Avatars cached per user, only fetched when their version bumps",
            "Drag handles to reorder areas on the Household tab",
            "Area cards collapse and remember their state per area",
            "Household search supports @user, 7d, notes, yesterday filters",
        ),
    ),
)
