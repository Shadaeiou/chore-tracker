package com.chore.tracker.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.chore.tracker.data.ActivityEntry
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
        compose.onNodeWithText("Living room · Alice").assertIsDisplayed()
        compose.onNodeWithText("Bathroom · Bob").assertIsDisplayed()
    }
}
