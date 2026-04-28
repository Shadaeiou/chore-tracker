package com.chore.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.chore.tracker.ui.AuthScreen
import com.chore.tracker.ui.ChoreTheme
import com.chore.tracker.ui.HomeScreen
import com.chore.tracker.ui.SettingsScreen
import com.chore.tracker.ui.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as ChoreApp
        setContent {
            val themeMode by app.session.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
            ChoreTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Root(app)
                }
            }
        }
    }
}

@Composable
private fun Root(app: ChoreApp) {
    val nav = rememberNavController()
    val token by app.session.tokenFlow.collectAsState(initial = null)
    val start = if (token == null) "auth" else "home"
    NavHost(nav, startDestination = start) {
        composable("auth") {
            AuthScreen(repo = app.repo, onSignedIn = {
                nav.navigate("home") { popUpTo("auth") { inclusive = true } }
            })
        }
        composable("home") {
            HomeScreen(
                repo = app.repo,
                onSignOut = {
                    nav.navigate("auth") { popUpTo("home") { inclusive = true } }
                },
                onOpenSettings = { nav.navigate("settings") },
            )
        }
        composable("settings") {
            SettingsScreen(
                session = app.session,
                onBack = { nav.popBackStack() },
                repo = app.repo,
                onSignOut = {
                    nav.navigate("auth") {
                        popUpTo("home") { inclusive = true }
                    }
                },
            )
        }
    }
}
