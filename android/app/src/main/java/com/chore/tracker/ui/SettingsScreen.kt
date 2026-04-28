package com.chore.tracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chore.tracker.BuildConfig
import com.chore.tracker.data.Repo
import com.chore.tracker.data.Session
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    session: Session,
    onBack: () -> Unit,
    repo: Repo? = null,
    onSignOut: () -> Unit = {},
) {
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
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    scope.launch {
                        repo?.logout() ?: session.setToken(null)
                        onSignOut()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("signOutButton"),
            ) { Text("Sign out") }

            // Version pinned to the bottom, centered.
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Text(
                    "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .testTag("versionText"),
                )
            }
        }
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
