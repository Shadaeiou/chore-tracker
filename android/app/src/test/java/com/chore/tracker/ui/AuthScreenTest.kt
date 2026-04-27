package com.chore.tracker.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.chore.tracker.data.FakeApi
import com.chore.tracker.data.InMemorySession
import com.chore.tracker.data.Repo
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
class AuthScreenTest {

    @get:Rule val compose = createComposeRule()

    private fun newRepo(fake: FakeApi = FakeApi()): Repo =
        Repo(
            session = InMemorySession(),
            api = fake,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )

    @Test fun `login flow calls api and signals signed-in`() {
        val fake = FakeApi()
        val repo = newRepo(fake)
        var signedIn = false

        compose.setContent { AuthScreen(repo = repo, onSignedIn = { signedIn = true }) }

        compose.onNodeWithTag("emailField").performTextInput("e@x.com")
        compose.onNodeWithTag("passwordField").performTextInput("longenoughpass")
        compose.onNodeWithTag("submitButton").performClick()

        compose.waitUntil(timeoutMillis = 2_000) { signedIn }
        assertThat(signedIn).isTrue()
    }

    @Test fun `switching to new household reveals extra fields`() {
        compose.setContent { AuthScreen(repo = newRepo(), onSignedIn = {}) }

        // No display name field in login mode.
        compose.onAllNodesWithTag("displayNameField").assertCountEquals(0)

        compose.onNodeWithText("New household").performClick()
        compose.onNodeWithTag("displayNameField").assertIsDisplayed()
        compose.onNodeWithTag("householdNameField").assertIsDisplayed()
    }

    @Test fun `switching to join shows invite code field`() {
        compose.setContent { AuthScreen(repo = newRepo(), onSignedIn = {}) }
        compose.onNodeWithText("Join with code").performClick()
        compose.onNodeWithTag("inviteCodeField").assertIsDisplayed()
        compose.onNodeWithTag("displayNameField").assertIsDisplayed()
    }

    @Test fun `error from api surfaces in ui`() {
        val fake = FakeApi().apply { raise = RuntimeException("invalid credentials") }
        compose.setContent { AuthScreen(repo = newRepo(fake), onSignedIn = {}) }

        compose.onNodeWithTag("emailField").performTextInput("e@x.com")
        compose.onNodeWithTag("passwordField").performTextInput("longenoughpass")
        compose.onNodeWithTag("submitButton").performClick()

        compose.waitUntil(timeoutMillis = 2_000) {
            compose.onAllNodesWithTag("authError").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("authError").assertIsDisplayed()
    }
}
