package com.chore.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.chore.tracker.BuildConfig
import com.chore.tracker.data.DownloadResult
import com.chore.tracker.data.PatchHouseholdRequest
import com.chore.tracker.data.Repo
import com.chore.tracker.data.Session
import com.chore.tracker.data.StatusIndicators
import com.chore.tracker.data.StatusKey
import com.chore.tracker.data.UpdateInfo
import com.chore.tracker.data.Updater
import kotlinx.coroutines.launch

private val INDICATOR_PRESETS = listOf(
    Color(0xFFD32F2F), Color(0xFFE65100), Color(0xFFF57C00), Color(0xFFFBC02D),
    Color(0xFF7CB342), Color(0xFF388E3C), Color(0xFF00897B), Color(0xFF0288D1),
    Color(0xFF1976D2), Color(0xFF512DA8), Color(0xFF8E24AA), Color(0xFFC2185B),
    Color(0xFF6D4C41), Color(0xFF455A64), Color(0xFF212121), Color(0xFF9E9E9E),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    session: Session,
    onBack: () -> Unit,
    repo: Repo? = null,
    onSignOut: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val themeMode by session.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
    val palette by session.themePaletteFlow.collectAsState(initial = ThemePalette.GREEN)
    val indicators by session.statusIndicatorsFlow.collectAsState(initial = StatusIndicators())
    val autoUpdate by session.autoUpdateFlow.collectAsState(initial = false)
    val houseState = repo?.state?.collectAsState()
    val isPaused = houseState?.value?.pausedUntil?.let { it > System.currentTimeMillis() } == true

    var pickingColorFor by remember { mutableStateOf<StatusKey?>(null) }
    var updateState by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Idle) }

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

            Spacer(Modifier.height(12.dp))
            Text("Color palette", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ThemePalette.entries.forEach { p ->
                    PaletteSwatch(
                        palette = p,
                        selected = palette == p,
                        onClick = { scope.launch { session.setThemePalette(p) } },
                    )
                }
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

            // Status indicators.
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text("Status indicators", style = MaterialTheme.typography.titleMedium)
            Text(
                "Tap a dot to recolor it. Type an emoji or short text on the " +
                    "right to use that instead.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            IndicatorRow(
                key = StatusKey.OVERDUE,
                label = "Overdue",
                defaultColor = Color(0xFFD32F2F),
                indicators = indicators,
                onTextChange = { scope.launch { session.setStatusIndicator(StatusKey.OVERDUE, it) } },
                onPickColor = { pickingColorFor = StatusKey.OVERDUE },
            )
            IndicatorRow(
                key = StatusKey.DUE_TODAY,
                label = "Due today",
                defaultColor = Color(0xFFFBC02D),
                indicators = indicators,
                onTextChange = { scope.launch { session.setStatusIndicator(StatusKey.DUE_TODAY, it) } },
                onPickColor = { pickingColorFor = StatusKey.DUE_TODAY },
            )
            IndicatorRow(
                key = StatusKey.NOT_DUE,
                label = "Not due",
                defaultColor = Color(0xFF388E3C),
                indicators = indicators,
                onTextChange = { scope.launch { session.setStatusIndicator(StatusKey.NOT_DUE, it) } },
                onPickColor = { pickingColorFor = StatusKey.NOT_DUE },
            )

            // Auto-update.
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text("Updates", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .testTag("autoUpdateToggle"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-check on launch", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Look for a newer build on GitHub when the app starts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = autoUpdate,
                    onCheckedChange = { scope.launch { session.setAutoUpdate(it) } },
                )
            }
            Button(
                onClick = {
                    if (updateState is UpdateUiState.Idle ||
                        updateState is UpdateUiState.UpToDate ||
                        updateState is UpdateUiState.Error) {
                        updateState = UpdateUiState.Checking
                        scope.launch {
                            val updater = Updater(context.applicationContext)
                            val info = updater.checkForUpdate(BuildConfig.VERSION_CODE)
                            updateState = if (info == null) UpdateUiState.UpToDate
                            else UpdateUiState.Available(info)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("checkUpdatesButton"),
            ) {
                Text(
                    when (updateState) {
                        is UpdateUiState.Checking -> "Checking…"
                        is UpdateUiState.Downloading -> "Downloading…"
                        else -> "Check for updates"
                    }
                )
            }
            when (val s = updateState) {
                is UpdateUiState.UpToDate -> Text(
                    "You're on the latest build (${BuildConfig.VERSION_NAME}).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                is UpdateUiState.Error -> Text(
                    s.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
                else -> {}
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

    (updateState as? UpdateUiState.Available)?.let { available ->
        AlertDialog(
            modifier = Modifier.testTag("updateAvailableDialog"),
            onDismissRequest = { updateState = UpdateUiState.Idle },
            title = { Text("Update available") },
            text = {
                Column {
                    Text("Version ${available.info.versionName} is ready to install.")
                    if (!available.info.notes.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            available.info.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val info = available.info
                    updateState = UpdateUiState.Downloading
                    scope.launch {
                        val updater = Updater(context.applicationContext)
                        val id = updater.startDownload(info)
                        when (val r = updater.awaitDownload(id)) {
                            DownloadResult.Success -> {
                                updater.launchInstall(id)
                                updateState = UpdateUiState.Idle
                            }
                            is DownloadResult.Failure -> {
                                updateState = UpdateUiState.Error("Download failed: ${r.reason}")
                            }
                        }
                    }
                }) { Text("Update") }
            },
            dismissButton = {
                TextButton(onClick = { updateState = UpdateUiState.Idle }) { Text("Later") }
            },
        )
    }

    pickingColorFor?.let { key ->
        ColorPickerDialog(
            currentHex = when (key) {
                StatusKey.OVERDUE -> indicators.overdueColor
                StatusKey.DUE_TODAY -> indicators.dueTodayColor
                StatusKey.NOT_DUE -> indicators.notDueColor
            },
            onDismiss = { pickingColorFor = null },
            onPick = { hex ->
                scope.launch { session.setStatusColor(key, hex) }
                pickingColorFor = null
            },
            onReset = {
                scope.launch { session.setStatusColor(key, "") }
                pickingColorFor = null
            },
        )
    }
}

@Composable
private fun PaletteSwatch(
    palette: ThemePalette,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp)
            .testTag("paletteOption:${palette.name}"),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(palette.swatch, CircleShape)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = borderColor,
                    shape = CircleShape,
                ),
        )
        Spacer(Modifier.height(4.dp))
        Text(palette.displayName, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun IndicatorRow(
    key: StatusKey,
    label: String,
    defaultColor: Color,
    indicators: StatusIndicators,
    onTextChange: (String) -> Unit,
    onPickColor: () -> Unit,
) {
    val text = when (key) {
        StatusKey.OVERDUE -> indicators.overdue
        StatusKey.DUE_TODAY -> indicators.dueToday
        StatusKey.NOT_DUE -> indicators.notDue
    }
    val customColorHex = when (key) {
        StatusKey.OVERDUE -> indicators.overdueColor
        StatusKey.DUE_TODAY -> indicators.dueTodayColor
        StatusKey.NOT_DUE -> indicators.notDueColor
    }
    val effectiveColor = customColorHex.parseHexOrNull() ?: defaultColor
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clickable(onClick = onPickColor)
                .testTag("indicatorDot:${key.name}"),
            contentAlignment = Alignment.Center,
        ) {
            if (text.isNotBlank()) {
                Text(text)
            } else {
                Box(modifier = Modifier.size(16.dp).background(effectiveColor, CircleShape))
            }
        }
        Text(label, modifier = Modifier.weight(1f))
        OutlinedTextField(
            value = text,
            onValueChange = { onTextChange(it.take(4)) },
            singleLine = true,
            placeholder = { Text("dot") },
            modifier = Modifier
                .width(96.dp)
                .testTag("indicatorField:${key.name}"),
        )
    }
}

@Composable
private fun ColorPickerDialog(
    currentHex: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onReset: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.testTag("colorPickerDialog"),
        onDismissRequest = onDismiss,
        title = { Text("Pick a color") },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.height(220.dp),
            ) {
                items(INDICATOR_PRESETS) { color ->
                    val hex = color.toHex()
                    val isCurrent = hex.equals(currentHex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(color, CircleShape)
                            .border(
                                width = if (isCurrent) 3.dp else 1.dp,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                shape = CircleShape,
                            )
                            .clickable { onPick(hex) }
                            .testTag("colorOption:$hex"),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onReset) { Text("Use default") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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

private fun Color.toHex(): String = String.format(
    "#%02X%02X%02X",
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt(),
)

private sealed class UpdateUiState {
    data object Idle : UpdateUiState()
    data object Checking : UpdateUiState()
    data class Available(val info: UpdateInfo) : UpdateUiState()
    data object Downloading : UpdateUiState()
    data object UpToDate : UpdateUiState()
    data class Error(val message: String) : UpdateUiState()
}

internal fun String.parseHexOrNull(): Color? {
    if (isBlank()) return null
    val s = trim().removePrefix("#")
    if (s.length != 6) return null
    return runCatching { Color(android.graphics.Color.parseColor("#$s")) }.getOrNull()
}
