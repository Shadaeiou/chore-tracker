package com.chore.tracker.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.chore.tracker.data.PatchProfileRequest
import com.chore.tracker.data.Repo
import kotlinx.coroutines.launch

private val RING_COLOR_PRESETS = listOf(
    Color(0xFFE53935), Color(0xFFE91E63), Color(0xFF9C27B0),
    Color(0xFF673AB7), Color(0xFF3F51B5), Color(0xFF2196F3),
    Color(0xFF03A9F4), Color(0xFF00BCD4), Color(0xFF009688),
    Color(0xFF4CAF50), Color(0xFF8BC34A), Color(0xFFCDDC39),
    Color(0xFFFFEB3B), Color(0xFFFFC107), Color(0xFFFF9800),
    Color(0xFFFF5722), Color(0xFF795548), Color(0xFF607D8B),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(repo: Repo, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var avatar by remember { mutableStateOf<String?>(null) }
    var profileColor by remember { mutableStateOf<String?>(null) }
    var userId by remember { mutableStateOf<String?>(null) }
    var avatarVersion by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showColorPicker by remember { mutableStateOf(false) }
    var hexInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        runCatching { repo.api.me() }
            .onSuccess { me ->
                displayName = me.displayName
                email = me.email
                avatar = me.avatar
                profileColor = me.profileColor
                userId = me.id
                avatarVersion = me.avatarVersion
                hexInput = me.profileColor?.removePrefix("#") ?: ""
            }
            .onFailure { error = it.message ?: "Failed to load profile" }
        loading = false
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val encoded = encodeAvatarFromUri(context.contentResolver, uri)
                if (encoded != null) avatar = encoded
                else error = "Could not read that image"
            }
        }
    }

    val ringColorParsed = profileColor?.let { hex ->
        runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit profile") },
                navigationIcon = {
                    IconButton(modifier = Modifier.testTag("profileBack"), onClick = onBack) {
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
                .testTag("profileScreen"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))
            AvatarPreview(
                avatarDataUrl = avatar,
                fallbackText = displayName.ifBlank { email }.take(1).uppercase(),
                size = 96,
                ringColor = ringColorParsed,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    modifier = Modifier.testTag("profileChooseAvatar"),
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                ) { Text(if (avatar == null) "Choose photo" else "Change photo") }
                if (avatar != null) {
                    OutlinedButton(
                        modifier = Modifier.testTag("profileRemoveAvatar"),
                        onClick = { avatar = null },
                    ) { Text("Remove") }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // Ring color section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Profile ring color", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Shown around your avatar throughout the app",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Preview swatch + tap to pick
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .then(
                            if (ringColorParsed != null)
                                Modifier.background(ringColorParsed, CircleShape)
                            else
                                Modifier.border(
                                    2.dp,
                                    MaterialTheme.colorScheme.outline,
                                    CircleShape,
                                )
                        )
                        .clickable { showColorPicker = true }
                        .testTag("ringColorSwatch"),
                )
            }

            // Hex input field for custom color
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { v ->
                        val clean = v.removePrefix("#").filter { it.isLetterOrDigit() }.take(6).uppercase()
                        hexInput = clean
                        profileColor = if (clean.length == 6) "#$clean" else null
                    },
                    label = { Text("Hex color (RRGGBB)") },
                    placeholder = { Text("e.g. FF5722") },
                    prefix = { Text("#") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("ringColorHexField"),
                )
                if (profileColor != null) {
                    TextButton(
                        onClick = { profileColor = null; hexInput = "" },
                        modifier = Modifier.testTag("ringColorClear"),
                    ) { Text("Clear") }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Display name") },
                singleLine = true,
                enabled = !loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profileDisplayName"),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = email,
                onValueChange = {},
                label = { Text("Email") },
                singleLine = true,
                enabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profileEmail"),
            )
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("profileError"))
            }
            Spacer(Modifier.height(20.dp))
            Button(
                enabled = !loading && !saving && displayName.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profileSave"),
                onClick = {
                    saving = true
                    error = null
                    scope.launch {
                        runCatching {
                            repo.api.patchMe(
                                PatchProfileRequest(
                                    displayName = displayName.trim(),
                                    avatar = avatar,
                                    profileColor = profileColor,
                                ),
                            )
                        }
                            .onSuccess { updated ->
                                AvatarCache.put(updated.id, updated.avatarVersion, updated.avatar, updated.profileColor)
                                repo.refresh()
                                onBack()
                            }
                            .onFailure { error = it.message ?: "Save failed" }
                        saving = false
                    }
                },
            ) { Text(if (saving) "Saving…" else "Save") }
        }
    }

    if (showColorPicker) {
        RingColorPickerDialog(
            currentHex = profileColor,
            onDismiss = { showColorPicker = false },
            onPick = { hex ->
                profileColor = hex
                hexInput = hex.removePrefix("#")
                showColorPicker = false
            },
            onClear = {
                profileColor = null
                hexInput = ""
                showColorPicker = false
            },
        )
    }
}

@Composable
private fun RingColorPickerDialog(
    currentHex: String?,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.testTag("ringColorPickerDialog"),
        onDismissRequest = onDismiss,
        title = { Text("Pick ring color") },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(180.dp),
            ) {
                items(RING_COLOR_PRESETS) { color ->
                    val hex = "#%02X%02X%02X".format(
                        (color.red * 255).toInt(),
                        (color.green * 255).toInt(),
                        (color.blue * 255).toInt(),
                    )
                    val isCurrent = hex.equals(currentHex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(color, CircleShape)
                            .border(
                                width = if (isCurrent) 3.dp else 1.dp,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                shape = CircleShape,
                            )
                            .clickable { onPick(hex) }
                            .testTag("ringColorOption:$hex"),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClear) { Text("No ring") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
