package com.chore.tracker.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.chore.tracker.data.Area
import com.chore.tracker.data.FakeApi
import com.chore.tracker.data.InMemorySession
import com.chore.tracker.data.Repo
import com.chore.tracker.data.Task
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HomeScreenTest {

    @get:Rule val compose = createComposeRule()

    private fun newRepo(fake: FakeApi): Repo =
        Repo(
            session = InMemorySession(initial = "fake-token"),
            api = fake,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            pollIntervalMs = 999_999,
        )

    @Test fun `renders areas and their tasks from repo state`() {
        val fake = FakeApi().apply {
            areas.add(Area("a1", "Kitchen", null, 0, 0))
            tasks.add(Task("t1", "a1", "Mop floor", 7, null, null, 0))
        }
        val repo = newRepo(fake)
        compose.setContent { HomeScreen(repo = repo, onSignOut = {}) }

        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("areaCard:Kitchen").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("areaCard:Kitchen").assertIsDisplayed()
        compose.onNodeWithTag("taskRow:Mop floor").assertIsDisplayed()
        compose.onNodeWithText("Tap + to add your first area").assertDoesNotExist()
    }

    @Test fun `empty state prompts user to add area`() {
        val repo = newRepo(FakeApi())
        compose.setContent { HomeScreen(repo = repo, onSignOut = {}) }
        compose.waitUntil(2_000) {
            compose.onAllNodesWithText("Tap + to add your first area").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun `golden path: add area, add task, complete task`() {
        val fake = FakeApi()
        val repo = newRepo(fake)
        compose.setContent { HomeScreen(repo = repo, onSignOut = {}) }

        // Add area
        compose.onNodeWithTag("addAreaFab").performClick()
        compose.onNodeWithTag("textDialogField").performTextInput("Bathroom")
        compose.onNodeWithTag("textDialogConfirm").performClick()
        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("areaCard:Bathroom").fetchSemanticsNodes().isNotEmpty()
        }

        // Add task in that area
        compose.onNodeWithTag("addTaskButton:Bathroom").performClick()
        compose.onNodeWithTag("taskNameField").performTextInput("Scrub tub")
        compose.onNodeWithTag("addTaskConfirm").performClick()
        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("taskRow:Scrub tub").fetchSemanticsNodes().isNotEmpty()
        }

        // Complete it
        compose.onNodeWithTag("completeButton:Scrub tub").performClick()
        compose.waitUntil(2_000) { fake.completed.isNotEmpty() }
        assertThat(fake.completed).hasSize(1)
        assertThat(fake.createdAreas.map { it.name }).containsExactly("Bathroom")
        assertThat(fake.createdTasks.map { it.name }).containsExactly("Scrub tub")
    }

    @Test fun `tapping invite shows the code returned by the api`() {
        val fake = FakeApi().apply { inviteCode = "JOIN-ABCDEF" }
        val repo = newRepo(fake)
        compose.setContent { HomeScreen(repo = repo, onSignOut = {}) }

        compose.onNodeWithTag("inviteButton").performClick()
        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("inviteCodeText").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("inviteCodeText").assertIsDisplayed()
        compose.onNodeWithText("JOIN-ABCDEF").assertIsDisplayed()
    }
}
