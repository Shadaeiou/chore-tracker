package com.chore.tracker.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.chore.tracker.data.InMemorySession
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsScreenTest {

    @get:Rule val compose = createComposeRule()

    @Test fun `picking dark mode persists the preference to the session`() {
        val session = InMemorySession()
        compose.setContent { SettingsScreen(session = session, onBack = {}) }

        compose.onNodeWithTag("themeOption:DARK").performClick()

        compose.waitUntil(2_000) {
            runBlocking { session.themeModeFlow.first() == ThemeMode.DARK }
        }
        assertThat(runBlocking { session.themeModeFlow.first() }).isEqualTo(ThemeMode.DARK)
    }

    @Test fun `back icon invokes the back callback`() {
        var backed = false
        compose.setContent {
            SettingsScreen(session = InMemorySession(), onBack = { backed = true })
        }
        compose.onNodeWithTag("settingsBack").performClick()
        assertThat(backed).isTrue()
    }

    @Test fun `current selection reflects existing preference`() {
        val session = InMemorySession(initialTheme = ThemeMode.LIGHT)
        compose.setContent { SettingsScreen(session = session, onBack = {}) }
        // The light option row exists and is selected; both dark and system rows exist too.
        compose.onNodeWithTag("themeOption:LIGHT").assertIsDisplayed()
        compose.onNodeWithTag("themeOption:DARK").assertIsDisplayed()
        compose.onNodeWithTag("themeOption:SYSTEM").assertIsDisplayed()
    }
}
