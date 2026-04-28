package com.chore.tracker.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.chore.tracker.data.ActivityEntry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ActivityScreenTest {

    @get:Rule val compose = createComposeRule()

    @Test fun `empty state shows placeholder text`() {
        compose.setContent { ActivityScreen(activity = emptyList()) }
        compose.onNodeWithTag("activityScreen").assertIsDisplayed()
        compose.onNodeWithText("No activity yet").assertIsDisplayed()
    }

    @Test fun `renders activity entries with task and area names`() {
        val entries = listOf(
            ActivityEntry(
                id = "c1",
                taskId = "t1",
                taskName = "Vacuum",
                areaName = "Living room",
                doneBy = "Alice",
                doneAt = System.currentTimeMillis(),
            ),
            ActivityEntry(
                id = "c2",
                taskId = "t2",
                taskName = "Scrub tub",
                areaName = "Bathroom",
                doneBy = "Bob",
                doneAt = System.currentTimeMillis() - 3_600_000,
            ),
        )
        compose.setContent { ActivityScreen(activity = entries) }

        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("activityRow:Vacuum").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("activityRow:Vacuum").assertIsDisplayed()
        compose.onNodeWithText("Vacuum").assertIsDisplayed()
        compose.onNodeWithTag("activityRow:Scrub tub").assertIsDisplayed()
        // Area renders as a separate label beneath the task name now.
        compose.onNodeWithText("Living room").assertIsDisplayed()
        compose.onNodeWithText("Bathroom").assertIsDisplayed()
    }

    @Test fun `long press shows undo menu and confirmation dialog`() {
        val entry = ActivityEntry(
            id = "c1", taskId = "t1",
            taskName = "Vacuum", areaName = "Living room", doneBy = "Alice",
            doneAt = System.currentTimeMillis(),
        )
        var undone: ActivityEntry? = null
        compose.setContent {
            ActivityScreen(activity = listOf(entry), onUndo = { undone = it })
        }

        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("activityRow:Vacuum").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("activityRow:Vacuum").performTouchInput { longClick() }
        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("activityMenuUndo:Vacuum").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("activityMenuUndo:Vacuum").performClick()
        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("undoCompletionDialog:Vacuum").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("undoCompletionConfirm").performClick()
        compose.waitUntil(2_000) { undone != null }
        assertThat(undone?.id).isEqualTo("c1")
    }

    @Test fun `no long press menu when onUndo is null`() {
        val entry = ActivityEntry(
            id = "c1", taskId = "t1",
            taskName = "Vacuum", areaName = "Living room", doneBy = "Alice",
            doneAt = System.currentTimeMillis(),
        )
        compose.setContent { ActivityScreen(activity = listOf(entry)) }
        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("activityRow:Vacuum").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("activityRow:Vacuum").performTouchInput { longClick() }
        // Menu should not appear since onUndo is null
        compose.onAllNodesWithTag("activityMenu:Vacuum").fetchSemanticsNodes().let {
            assertThat(it).isEmpty()
        }
    }
}
