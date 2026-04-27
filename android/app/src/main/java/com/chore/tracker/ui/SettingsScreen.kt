package com.chore.tracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import com.chore.tracker.data.Session
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(session: Session, onBack: () -> Unit) {
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
                    onSelect = {
                        scope.launch { session.setThemeMode(mode) }
                    },
                    testTag = "themeOption:${mode.name}",
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
