package com.chore.tracker.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.chore.tracker.data.Area
import com.chore.tracker.data.ActivityEntry
import com.chore.tracker.data.FakeApi
import com.chore.tracker.data.InMemorySession
import com.chore.tracker.data.Member
import com.chore.tracker.data.Repo
import com.chore.tracker.data.Task
import com.chore.tracker.data.WorkloadEntry
import com.chore.tracker.data.dirtiness
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

        // Plan tab (default) shows the task with area context
        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("taskRow:Mop floor").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("taskRow:Mop floor").assertIsDisplayed()

        // Switch to Areas tab and the area card appears
        compose.onNodeWithTag("tab:household").performClick()
        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("areaCard:Kitchen").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("areaCard:Kitchen").assertIsDisplayed()
    }

    @Test fun `empty state shows onboarding wizard`() {
        val repo = newRepo(FakeApi())
        compose.setContent { HomeScreen(repo = repo, onSignOut = {}) }
        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("onboardingScreen").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun `skipping wizard then switching to Areas tab shows manual empty state`() {
        val repo = newRepo(FakeApi())
        compose.setContent { HomeScreen(repo = repo, onSignOut = {}) }
        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("onboardingSkip").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("onboardingSkip").performClick()
        // Areas tab shows "Tap +" empty state with the FAB available
        compose.onNodeWithTag("tab:household").performClick()
        compose.waitUntil(2_000) {
            compose.onAllNodesWithText("Tap + to add your first area").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun `golden path add area then add task in it`() {
        val fake = FakeApi()
        val repo = newRepo(fake)
        compose.setContent { HomeScreen(repo = repo, onSignOut = {}) }

        // Skip onboarding wizard
        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("onboardingSkip").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("onboardingSkip").performClick()

        // FAB lives on Household tab now — switch first
        compose.onNodeWithTag("tab:household").performClick()
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
        assertThat(fake.createdAreas.map { it.name }).containsExactly("Bathroom")
        assertThat(fake.createdTasks.map { it.name }).containsExactly("Scrub tub")
    }

    @Test fun `tapping a task row on household tab opens the edit dialog`() {
        // Today tab is read-only — tap-to-edit only works on Household.
        val fake = FakeApi().apply {
            areas.add(Area("a1", "Kitchen", null, 0, 0))
            tasks.add(Task("t1", "a1", "Mop floor", 7, null, null, 0))
        }
        val repo = newRepo(fake)
        compose.setContent { HomeScreen(repo = repo, onSignOut = {}) }

        compose.onNodeWithTag("tab:household").performClick()
        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("taskRow:Mop floor").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("taskRow:Mop floor").performClick()
        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("editTaskDialog").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("editTaskDialog").assertIsDisplayed()
    }

    @Test fun `invite menu shows the code returned by the api`() {
        // Invite moved from a toolbar icon to the household long-press menu.
        val fake = FakeApi().apply {
            inviteCode = "JOIN-ABCDEF"
            areas.add(Area("a1", "Kitchen", null, 0, 0))
        }
        val repo = newRepo(fake)
        compose.setContent { HomeScreen(repo = repo, onSignOut = {}) }

        compose.onNodeWithTag("tab:household").performClick()
        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("householdHeader").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("householdHeader").performTouchInput { longClick() }
        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("householdMenuInvite").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("householdMenuInvite").performClick()
        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("inviteCodeText").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("inviteCodeText").assertIsDisplayed()
        compose.onNodeWithText("JOIN-ABCDEF").assertIsDisplayed()
    }

    @Test fun `tapping the settings icon invokes the open-settings callback`() {
        val repo = newRepo(FakeApi())
        var opened = false
        compose.setContent {
            HomeScreen(repo = repo, onSignOut = {}, onOpenSettings = { opened = true })
        }
        compose.onNodeWithTag("settingsButton").performClick()
        assertThat(opened).isTrue()
    }

    @Test fun `plan areas and activity tabs are present`() {
        val repo = newRepo(FakeApi())
        compose.setContent { HomeScreen(repo = repo, onSignOut = {}) }
        compose.onNodeWithTag("tab:today").assertIsDisplayed()
        compose.onNodeWithTag("tab:household").assertIsDisplayed()
        compose.onNodeWithTag("tab:activity").assertIsDisplayed()
    }

    @Test fun `tapping activity tab shows activity screen`() {
        val fake = FakeApi().apply {
            activityFeed.add(
                ActivityEntry("c1", "t1", "Vacuum", "Living room", "Alice", System.currentTimeMillis()),
            )
        }
        val repo = newRepo(fake)
        compose.setContent { HomeScreen(repo = repo, onSignOut = {}) }

        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("tab:activity").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("tab:activity").performClick()
        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("activityScreen").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("activityScreen").assertIsDisplayed()
    }

    @Test fun `workload card renders on Activity tab when data is present`() {
        val fake = FakeApi().apply {
            areas.add(Area("a1", "Kitchen", null, 0, 0))
            activityFeed.add(
                ActivityEntry("c1", "t1", "Vacuum", "Living room", "Alice", System.currentTimeMillis()),
            )
            workloadData.add(WorkloadEntry("u1", "Alice", 5))
            workloadData.add(WorkloadEntry("u2", "Bob", 2))
        }
        val repo = newRepo(fake)
        compose.setContent { HomeScreen(repo = repo, onSignOut = {}) }

        // Workload no longer appears on the Today tab
        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("tab:activity").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("tab:activity").performClick()
        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("workloadCard").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("workloadCard").assertIsDisplayed()
        compose.onNodeWithTag("workloadName:Alice").assertIsDisplayed()
        compose.onNodeWithTag("workloadName:Bob").assertIsDisplayed()
    }

    @Test fun `swipe left on today task offers snooze but not delete`() {
        // Today is read-only triage. Swipe-left should let you snooze but
        // never hard-delete — that path lives on Household.
        val fake = FakeApi().apply {
            areas.add(Area("a1", "Kitchen", null, 0, 0))
            tasks.add(Task("t1", "a1", "Mop floor", 7, null, null, 0))
        }
        val repo = newRepo(fake)
        compose.setContent { HomeScreen(repo = repo, onSignOut = {}) }

        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("taskRow:Mop floor").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("taskRow:Mop floor").performTouchInput { swipeLeft() }
        compose.waitUntil(3_000) {
            compose.onAllNodesWithTag("snoozeOrDeleteDialog:Mop floor").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("snoozeAmountField").assertIsDisplayed()
        compose.onAllNodesWithTag("deleteTaskConfirm")
            .fetchSemanticsNodes().let { assertThat(it).isEmpty() }
    }

    @Test fun `swipe right on task opens complete dialog`() {
        val fake = FakeApi().apply {
            areas.add(Area("a1", "Kitchen", null, 0, 0))
            tasks.add(Task("t1", "a1", "Mop floor", 7, null, null, 0))
        }
        val repo = newRepo(fake)
        compose.setContent { HomeScreen(repo = repo, onSignOut = {}) }

        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("taskRow:Mop floor").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("taskRow:Mop floor").performTouchInput { swipeRight() }
        compose.waitUntil(3_000) {
            compose.onAllNodesWithTag("completeTaskDialog:Mop floor").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("completeTaskConfirm").performClick()
        compose.waitUntil(2_000) { fake.completed.isNotEmpty() }
        assertThat(fake.completed).hasSize(1)
    }

    @Test fun `long press area header shows context menu with rename and delete`() {
        val fake = FakeApi().apply {
            areas.add(Area("a1", "Kitchen", null, 0, 0))
        }
        val repo = newRepo(fake)
        compose.setContent { HomeScreen(repo = repo, onSignOut = {}) }

        compose.onNodeWithTag("tab:household").performClick()
        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("areaHeader:Kitchen").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("areaHeader:Kitchen").performTouchInput { longClick() }
        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("areaMenu:Kitchen").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("areaMenuEdit:Kitchen").assertIsDisplayed()
        compose.onNodeWithTag("areaMenuDelete:Kitchen").assertIsDisplayed()
    }

    @Test fun `long press area and tap delete shows cascade warning and calls api`() {
        val fake = FakeApi().apply {
            areas.add(Area("a1", "Kitchen", null, 0, 0))
        }
        val repo = newRepo(fake)
        compose.setContent { HomeScreen(repo = repo, onSignOut = {}) }

        compose.onNodeWithTag("tab:household").performClick()
        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("areaHeader:Kitchen").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("areaHeader:Kitchen").performTouchInput { longClick() }
        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("areaMenuDelete:Kitchen").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("areaMenuDelete:Kitchen").performClick()
        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("deleteAreaDialog:Kitchen").fetchSemanticsNodes().isNotEmpty()
        }
        // Cascade warning text must be visible
        compose.onNodeWithText("also delete all tasks", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("deleteAreaConfirm").performClick()
        compose.waitUntil(2_000) { fake.areas.isEmpty() }
        assertThat(fake.areas).isEmpty()
    }

    @Test fun `vacation banner shows when household is paused`() {
        val fake = FakeApi().apply {
            pausedUntil = System.currentTimeMillis() + 86_400_000L
            areas.add(Area("a1", "Kitchen", null, 0, 0))
        }
        val repo = newRepo(fake)
        compose.setContent { HomeScreen(repo = repo, onSignOut = {}) }

        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("vacationBanner").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("vacationBanner").assertIsDisplayed()
        compose.onNodeWithTag("resumeButton").assertIsDisplayed()
    }

    @Test fun `tapping resume clears the pause`() {
        val fake = FakeApi().apply {
            pausedUntil = System.currentTimeMillis() + 86_400_000L
            areas.add(Area("a1", "Kitchen", null, 0, 0))
        }
        val repo = newRepo(fake)
        compose.setContent { HomeScreen(repo = repo, onSignOut = {}) }

        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("resumeButton").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("resumeButton").performClick()
        compose.waitUntil(2_000) { fake.pausedUntil == null }
        assertThat(fake.pausedUntil).isNull()
    }

    @Test fun `dirtiness uses server-computed dueness when present`() {
        // Server returns dueness = 0 (paused/snoozed); local computation would say > 0.
        val task = Task(
            id = "t1", areaId = "a1", name = "x",
            frequencyDays = 1, lastDoneAt = null, lastDoneBy = null, createdAt = 0,
            dueness = 0.0,
        )
        assertThat(task.dirtiness(now = 1_000_000_000L)).isEqualTo(0.0)
    }

    @Test fun `dirtiness falls back to local calc when server dueness absent`() {
        val task = Task(
            id = "t1", areaId = "a1", name = "x",
            frequencyDays = 7, lastDoneAt = null, lastDoneBy = null, createdAt = 0,
            dueness = null,
        )
        assertThat(task.dirtiness()).isEqualTo(1.0)
    }

    @Test fun `add task dialog shows effort slider and assignee picker when members present`() {
        val fake = FakeApi().apply {
            areas.add(Area("a1", "Kitchen", null, 0, 0))
            members.add(Member("u1", "Alice", "alice@example.com"))
        }
        val repo = newRepo(fake)
        compose.setContent { HomeScreen(repo = repo, onSignOut = {}) }

        compose.onNodeWithTag("tab:household").performClick()
        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("addTaskButton:Kitchen").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("addTaskButton:Kitchen").performClick()
        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("addTaskDialog").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("effortSlider").assertExists()
        compose.onNodeWithTag("assigneePicker").assertExists()
        compose.onNodeWithTag("autoRotateToggle").assertExists()
    }
}
