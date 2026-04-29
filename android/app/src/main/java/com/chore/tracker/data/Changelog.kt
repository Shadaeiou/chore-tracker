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
