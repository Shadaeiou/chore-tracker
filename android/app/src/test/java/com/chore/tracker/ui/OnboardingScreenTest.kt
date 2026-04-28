package com.chore.tracker.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.chore.tracker.data.FakeApi
import com.chore.tracker.data.InMemorySession
import com.chore.tracker.data.Repo
import com.chore.tracker.data.TaskTemplate
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
class OnboardingScreenTest {

    @get:Rule val compose = createComposeRule()

    private fun newRepo(fake: FakeApi): Repo = Repo(
        session = InMemorySession(initial = "fake-token"),
        api = fake,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    )

    private fun seedFake(): FakeApi = FakeApi().apply {
        taskTemplatesData.addAll(
            listOf(
                TaskTemplate("tmpl-kitchen-1", "Wipe counters", "kitchen", 1, 1),
                TaskTemplate("tmpl-kitchen-2", "Mop floor", "kitchen", 7, 3),
                TaskTemplate("tmpl-bathroom-1", "Clean toilet", "bathroom", 7, 2),
            ),
        )
    }

    @Test fun `room chips render with templates loaded`() {
        val fake = seedFake()
        val repo = newRepo(fake)
        compose.setContent { OnboardingScreen(repo, onSkip = {}, onComplete = {}) }
        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("roomChip:kitchen").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("roomChip:kitchen").assertIsDisplayed()
        compose.onNodeWithTag("roomChip:bathroom").assertIsDisplayed()
    }

    @Test fun `picking room and tapping next shows that room's templates`() {
        val fake = seedFake()
        val repo = newRepo(fake)
        compose.setContent { OnboardingScreen(repo, onSkip = {}, onComplete = {}) }
        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("roomChip:kitchen").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("roomChip:kitchen").performClick()
        compose.onNodeWithTag("onboardingNext").performClick()
        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("templateRow:tmpl-kitchen-1").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("templateRow:tmpl-kitchen-1").assertIsDisplayed()
    }

    @Test fun `finishing wizard creates the selected areas and tasks`() {
        val fake = seedFake()
        val repo = newRepo(fake)
        var completed = false
        compose.setContent { OnboardingScreen(repo, onSkip = {}, onComplete = { completed = true }) }
        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("roomChip:kitchen").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("roomChip:kitchen").performClick()
        compose.onNodeWithTag("onboardingNext").performClick()
        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("onboardingFinish").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("onboardingFinish").performClick()
        compose.waitUntil(3_000) { completed }
        assertThat(completed).isTrue()
        assertThat(fake.createdAreas.map { it.name }).contains("Kitchen")
        // Default = top 3 templates per room; we seeded 2 kitchen templates.
        assertThat(fake.createdTasks.map { it.templateId }).containsExactly("tmpl-kitchen-1", "tmpl-kitchen-2")
    }

    @Test fun `skip button calls onSkip`() {
        val fake = seedFake()
        val repo = newRepo(fake)
        var skipped = false
        compose.setContent { OnboardingScreen(repo, onSkip = { skipped = true }, onComplete = {}) }
        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("onboardingSkip").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("onboardingSkip").performClick()
        assertThat(skipped).isTrue()
    }
}
