package com.chore.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chore.tracker.BuildConfig
import com.chore.tracker.data.PatchHouseholdRequest
import com.chore.tracker.data.Repo
import com.chore.tracker.data.Session
import com.chore.tracker.data.StatusIndicators
import com.chore.tracker.data.StatusKey
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
    val themeMode by session.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
    val indicators by session.statusIndicatorsFlow.collectAsState(initial = StatusIndicators())
    val houseState = repo?.state?.collectAsState()
    val isPaused = houseState?.value?.pausedUntil?.let { it > System.currentTimeMillis() } == true

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
                .verticalScroll(rememberScrollState())
                .testTag("settingsScreen"),
        ) {
            Text("Theme", style = MaterialTheme.typography.titleMedium)
            ThemeMode.entries.forEach { mode ->
                ThemeRow(
                    label = mode.label(),
                    selected = themeMode == mode,
                    onSelect = { scope.launch { session.setThemeMode(mode) } },
                    testTag = "themeOption:${mode.name}",
                )
            }

            // Vacation mode (formerly the umbrella icon in the toolbar).
            if (repo != null) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .testTag("vacationToggle"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Vacation mode", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Pause due indicators while you're away.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = isPaused,
                        onCheckedChange = { wantPause ->
                            scope.launch {
                                val until = if (wantPause)
                                    System.currentTimeMillis() + 365L * 86_400_000L
                                else null
                                runCatching { repo.api.patchHousehold(PatchHouseholdRequest(until)) }
                                    .onSuccess { repo.refresh() }
                            }
                        },
                    )
                }
            }

            // Status indicators — colorblind-friendly / "be funny" customization.
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text("Status indicators", style = MaterialTheme.typography.titleMedium)
            Text(
                "Leave blank for the default colored dot, or enter an emoji or " +
                    "short text to use instead.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            IndicatorRow(
                label = "Overdue",
                defaultColor = Color(0xFFD32F2F),
                value = indicators.overdue,
                onChange = { scope.launch { session.setStatusIndicator(StatusKey.OVERDUE, it) } },
                testTag = "indicatorField:OVERDUE",
            )
            IndicatorRow(
                label = "Due today",
                defaultColor = Color(0xFFFBC02D),
                value = indicators.dueToday,
                onChange = { scope.launch { session.setStatusIndicator(StatusKey.DUE_TODAY, it) } },
                testTag = "indicatorField:DUE_TODAY",
            )
            IndicatorRow(
                label = "Not due",
                defaultColor = Color(0xFF388E3C),
                value = indicators.notDue,
                onChange = { scope.launch { session.setStatusIndicator(StatusKey.NOT_DUE, it) } },
                testTag = "indicatorField:NOT_DUE",
            )

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

            Spacer(Modifier.height(16.dp))
            Text(
                "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("versionText"),
            )
        }
    }
}

@Composable
private fun IndicatorRow(
    label: String,
    defaultColor: Color,
    value: String,
    onChange: (String) -> Unit,
    testTag: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Preview: emoji/text if user has overridden, otherwise the colored dot.
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(24.dp)) {
            if (value.isBlank()) {
                Box(modifier = Modifier.size(14.dp).background(defaultColor, CircleShape))
            } else {
                Text(value)
            }
        }
        Text(label, modifier = Modifier.weight(1f))
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(it.take(4)) },
            singleLine = true,
            placeholder = { Text("dot") },
            modifier = Modifier
                .width(96.dp)
                .testTag(testTag),
        )
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
