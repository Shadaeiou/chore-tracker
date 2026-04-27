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

        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("areaCard:Kitchen").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("areaCard:Kitchen").assertIsDisplayed()
        compose.onNodeWithTag("taskRow:Mop floor").assertIsDisplayed()
        compose.onNodeWithText("Tap + to add your first area").assertDoesNotExist()
    }

    @Test fun `empty state shows onboarding wizard`() {
        val repo = newRepo(FakeApi())
        compose.setContent { HomeScreen(repo = repo, onSignOut = {}) }
        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("onboardingScreen").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun `skipping wizard shows the manual empty state`() {
        val repo = newRepo(FakeApi())
        compose.setContent { HomeScreen(repo = repo, onSignOut = {}) }
        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("onboardingSkip").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("onboardingSkip").performClick()
        compose.waitUntil(2_000) {
            compose.onAllNodesWithText("Tap + to add your first area").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun `golden path add area add task complete task`() {
        val fake = FakeApi()
        val repo = newRepo(fake)
        compose.setContent { HomeScreen(repo = repo, onSignOut = {}) }

        // Skip onboarding wizard to access manual add flow
        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("onboardingSkip").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("onboardingSkip").performClick()

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

    @Test fun `completing a task surfaces the undo snackbar`() {
        val fake = FakeApi().apply {
            areas.add(Area("a1", "Kitchen", null, 0, 0))
            tasks.add(Task("t1", "a1", "Mop floor", 7, null, null, 0))
        }
        val repo = newRepo(fake)
        compose.setContent { HomeScreen(repo = repo, onSignOut = {}) }

        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("completeButton:Mop floor").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("completeButton:Mop floor").performClick()

        compose.waitUntil(2_000) { fake.completed.isNotEmpty() }
        compose.waitUntil(3_000) {
            compose.onAllNodesWithText("Marked done").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Marked done").assertIsDisplayed()
        compose.onNodeWithText("Undo").assertIsDisplayed()
    }

    @Test fun `tapping undo on the snackbar calls the undo api`() {
        val fake = FakeApi().apply {
            areas.add(Area("a1", "Kitchen", null, 0, 0))
            tasks.add(Task("t1", "a1", "Mop floor", 7, null, null, 0))
        }
        val repo = newRepo(fake)
        compose.setContent { HomeScreen(repo = repo, onSignOut = {}) }

        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("completeButton:Mop floor").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("completeButton:Mop floor").performClick()
        compose.waitUntil(3_000) {
            compose.onAllNodesWithText("Undo").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(2_000) { fake.undone.isNotEmpty() }
        assertThat(fake.undone).containsExactly("t1")
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

    @Test fun `chores and activity tabs are present`() {
        val repo = newRepo(FakeApi())
        compose.setContent { HomeScreen(repo = repo, onSignOut = {}) }
        compose.onNodeWithTag("tab:chores").assertIsDisplayed()
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

    @Test fun `workload card renders when data is present`() {
        val fake = FakeApi().apply {
            areas.add(Area("a1", "Kitchen", null, 0, 0))
            workloadData.add(WorkloadEntry("u1", "Alice", 5))
            workloadData.add(WorkloadEntry("u2", "Bob", 2))
        }
        val repo = newRepo(fake)
        compose.setContent { HomeScreen(repo = repo, onSignOut = {}) }

        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("workloadCard").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("workloadCard").assertIsDisplayed()
        compose.onNodeWithTag("workloadName:Alice").assertIsDisplayed()
        compose.onNodeWithTag("workloadName:Bob").assertIsDisplayed()
    }

    @Test fun `long press task row shows context menu with edit and delete`() {
        val fake = FakeApi().apply {
            areas.add(Area("a1", "Kitchen", null, 0, 0))
            tasks.add(Task("t1", "a1", "Mop floor", 7, null, null, 0))
        }
        val repo = newRepo(fake)
        compose.setContent { HomeScreen(repo = repo, onSignOut = {}) }

        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("taskRow:Mop floor").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("taskRow:Mop floor").performTouchInput { longClick() }
        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("taskMenu:Mop floor").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("taskMenuEdit:Mop floor").assertIsDisplayed()
        compose.onNodeWithTag("taskMenuDelete:Mop floor").assertIsDisplayed()
    }

    @Test fun `long press task and tap delete shows confirmation dialog`() {
        val fake = FakeApi().apply {
            areas.add(Area("a1", "Kitchen", null, 0, 0))
            tasks.add(Task("t1", "a1", "Mop floor", 7, null, null, 0))
        }
        val repo = newRepo(fake)
        compose.setContent { HomeScreen(repo = repo, onSignOut = {}) }

        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("taskRow:Mop floor").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("taskRow:Mop floor").performTouchInput { longClick() }
        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("taskMenuDelete:Mop floor").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("taskMenuDelete:Mop floor").performClick()
        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("deleteTaskDialog:Mop floor").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("deleteTaskConfirm").performClick()
        compose.waitUntil(2_000) { fake.tasks.isEmpty() }
        assertThat(fake.tasks).isEmpty()
    }

    @Test fun `long press area header shows context menu with rename and delete`() {
        val fake = FakeApi().apply {
            areas.add(Area("a1", "Kitchen", null, 0, 0))
        }
        val repo = newRepo(fake)
        compose.setContent { HomeScreen(repo = repo, onSignOut = {}) }

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

    @Test fun `long press task shows snooze and mark done at options`() {
        val fake = FakeApi().apply {
            areas.add(Area("a1", "Kitchen", null, 0, 0))
            tasks.add(Task("t1", "a1", "Mop floor", 7, null, null, 0))
        }
        val repo = newRepo(fake)
        compose.setContent { HomeScreen(repo = repo, onSignOut = {}) }

        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("taskRow:Mop floor").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("taskRow:Mop floor").performTouchInput { longClick() }
        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("taskMenuSnooze:Mop floor").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("taskMenuSnooze:Mop floor").assertIsDisplayed()
        compose.onNodeWithTag("taskMenuMarkDoneAt:Mop floor").assertIsDisplayed()
    }

    @Test fun `snooze 3 days calls fake api with correct future timestamp`() {
        val fake = FakeApi().apply {
            areas.add(Area("a1", "Kitchen", null, 0, 0))
            tasks.add(Task("t1", "a1", "Mop floor", 7, null, null, 0))
        }
        val repo = newRepo(fake)
        compose.setContent { HomeScreen(repo = repo, onSignOut = {}) }

        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("taskRow:Mop floor").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("taskRow:Mop floor").performTouchInput { longClick() }
        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("taskMenuSnooze:Mop floor").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("taskMenuSnooze:Mop floor").performClick()
        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("snoozeOption:3").fetchSemanticsNodes().isNotEmpty()
        }
        val before = System.currentTimeMillis()
        compose.onNodeWithTag("snoozeOption:3").performClick()
        compose.waitUntil(2_000) { fake.snoozed.isNotEmpty() }
        assertThat(fake.snoozed).hasSize(1)
        val (taskId, until) = fake.snoozed.first()
        assertThat(taskId).isEqualTo("t1")
        // Snooze should be ~3 days from now (with a generous fudge factor)
        val expected = before + 3 * 86_400_000L
        assertThat(until).isAtLeast(expected - 5_000)
        assertThat(until).isAtMost(expected + 5_000)
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

        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("addTaskButton:Kitchen").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("addTaskButton:Kitchen").performClick()
        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("addTaskDialog").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("effortSlider").assertIsDisplayed()
        compose.onNodeWithTag("assigneePicker").assertIsDisplayed()
        compose.onNodeWithTag("autoRotateToggle").assertIsDisplayed()
    }
}
