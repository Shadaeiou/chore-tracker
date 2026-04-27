package com.chore.tracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.chore.tracker.BuildConfig
import com.chore.tracker.data.DeviceTokenRequest
import com.chore.tracker.data.Repo
import com.chore.tracker.data.Session
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(session: Session, onBack: () -> Unit, repo: Repo? = null) {
    val scope = rememberCoroutineScope()
    val current by session.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(
                        modifier = Modifier.testTag("settingsBack"),
                        onClick = onBack,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .testTag("settingsScreen"),
        ) {
            Text("Theme", style = MaterialTheme.typography.titleMedium)
            ThemeMode.entries.forEach { mode ->
                ThemeRow(
                    label = mode.label(),
                    selected = current == mode,
                    onSelect = { scope.launch { session.setThemeMode(mode) } },
                    testTag = "themeOption:${mode.name}",
                )
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text(
                "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("versionText"),
            )

            if (repo != null) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                DebugSection(session = session, repo = repo)
            }
        }
    }
}

@Composable
private fun DebugSection(session: Session, repo: Repo) {
    val scope = rememberCoroutineScope()
    var fcmToken by remember { mutableStateOf("loading…") }
    var storedToken by remember { mutableStateOf("loading…") }
    var registerStatus by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        fcmToken = try {
            Firebase.messaging.token.await().let { it.take(20) + "…" }
        } catch (t: Throwable) {
            "ERROR: ${t.message}"
        }
        storedToken = session.fcmToken()?.take(20)?.plus("…") ?: "none"
    }

    Text("Debug", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    Text("FCM token: $fcmToken", style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(4.dp))
    Text("Stored token: $storedToken", style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))
    Button(onClick = {
        registerStatus = "registering…"
        scope.launch {
            registerStatus = try {
                val token = Firebase.messaging.token.await()
                session.setFcmToken(token)
                repo.api.registerDeviceToken(DeviceTokenRequest(token))
                "✓ registered (${token.take(12)}…)"
            } catch (t: Throwable) {
                "✗ ${t.javaClass.simpleName}: ${t.message}"
            }
        }
    }) { Text("Register FCM token") }
    if (registerStatus.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        Text(registerStatus, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ThemeRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    testTag: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "Match system"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}
