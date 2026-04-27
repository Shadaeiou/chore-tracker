package com.chore.tracker.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ThemeTest {

    @get:Rule val compose = createComposeRule()

    @Test fun `light mode renders content`() {
        compose.setContent {
            ChoreTheme(themeMode = ThemeMode.LIGHT) { Text("Hello") }
        }
        compose.onNodeWithText("Hello").assertIsDisplayed()
    }

    @Test fun `dark mode renders content`() {
        compose.setContent {
            ChoreTheme(themeMode = ThemeMode.DARK) { Text("Hello") }
        }
        compose.onNodeWithText("Hello").assertIsDisplayed()
    }

    @Test fun `system mode renders content`() {
        compose.setContent {
            ChoreTheme(themeMode = ThemeMode.SYSTEM) { Text("Hello") }
        }
        compose.onNodeWithText("Hello").assertIsDisplayed()
    }
}
