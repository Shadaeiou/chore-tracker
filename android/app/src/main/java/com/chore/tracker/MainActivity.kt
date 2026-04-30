package com.chore.tracker

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
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
import com.chore.tracker.data.DownloadResult
import com.chore.tracker.data.Updater
import com.chore.tracker.ui.AuthScreen
import com.chore.tracker.ui.ChoreTheme
import com.chore.tracker.ui.HomeScreen
import com.chore.tracker.ui.MembersScreen
import com.chore.tracker.ui.ProfileScreen
import com.chore.tracker.ui.SettingsScreen
import com.chore.tracker.ui.ThemeMode
import com.chore.tracker.ui.ThemePalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as ChoreApp
        if (intent?.getBooleanExtra(EXTRA_AUTO_UPDATE, false) == true) {
            // Reset the flag so a config-change recreate doesn't re-trigger.
            intent.removeExtra(EXTRA_AUTO_UPDATE)
            startAutoUpdateFlow()
        }
        setContent {
            val themeMode by app.session.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
            val themePalette by app.session.themePaletteFlow.collectAsState(initial = ThemePalette.GREEN)
            ChoreTheme(themeMode = themeMode, themePalette = themePalette) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Root(app)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_AUTO_UPDATE, false)) {
            intent.removeExtra(EXTRA_AUTO_UPDATE)
            startAutoUpdateFlow()
        }
        setIntent(intent)
    }

    /** Tapping the update notification lands here. Kick off download + install
     *  without making the user navigate to Settings. The system install dialog
     *  is the only confirmation they have to OK. */
    private fun startAutoUpdateFlow() {
        Toast.makeText(this, "Checking for update…", Toast.LENGTH_SHORT).show()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val updater = Updater(applicationContext)
            val info = runCatching { updater.checkForUpdate(BuildConfig.VERSION_CODE) }.getOrNull()
            if (info == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "You're already on the latest build.",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                return@launch
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@MainActivity,
                    "Downloading v${info.versionName}…",
                    Toast.LENGTH_SHORT,
                ).show()
            }
            val id = updater.startDownload(info)
            when (val r = updater.awaitDownload(id)) {
                DownloadResult.Success -> updater.launchInstall(id)
                is DownloadResult.Failure -> withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Update failed: ${r.reason}",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    companion object {
        const val EXTRA_AUTO_UPDATE = "auto_update"
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
                onOpenProfile = { nav.navigate("profile") },
                onOpenMembers = { nav.navigate("members") },
            )
        }
        composable("profile") {
            ProfileScreen(repo = app.repo, onBack = { nav.popBackStack() })
        }
        composable("members") {
            MembersScreen(repo = app.repo, onBack = { nav.popBackStack() })
        }
    }
}
