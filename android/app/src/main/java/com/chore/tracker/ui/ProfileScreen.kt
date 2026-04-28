package com.chore.tracker.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.chore.tracker.data.PatchProfileRequest
import com.chore.tracker.data.Repo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(repo: Repo, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var avatar by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { repo.api.me() }
            .onSuccess { me ->
                displayName = me.displayName
                email = me.email
                avatar = me.avatar
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
            Spacer(Modifier.height(20.dp))
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
                            // Send avatar field unconditionally so explicit removal (null) is honored.
                            repo.api.patchMe(PatchProfileRequest(displayName = displayName.trim(), avatar = avatar))
                        }
                            .onSuccess {
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
}

@Composable
fun AvatarPreview(
    avatarDataUrl: String?,
    fallbackText: String,
    size: Int,
) {
    val bitmap = remember(avatarDataUrl) { decodeAvatarDataUrl(avatarDataUrl) }
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
            .testTag("avatarPreview"),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Medium,
                modifier = Modifier
                    .size(size.dp)
                    .clip(CircleShape),
            )
        } else {
            val style = when {
                size <= 28 -> MaterialTheme.typography.labelSmall
                size <= 48 -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.headlineSmall
            }
            Text(
                fallbackText,
                style = style,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

