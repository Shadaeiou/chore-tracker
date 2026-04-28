package com.chore.tracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.chore.tracker.data.Member
import com.chore.tracker.data.Repo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembersScreen(repo: Repo, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val state by repo.state.collectAsState()
    val me = state.members.firstOrNull { it.id == state.currentUserId }
    val iAmAdmin = me?.role == "admin"
    var confirmKick by remember { mutableStateOf<Member?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Household members") },
                navigationIcon = {
                    IconButton(modifier = Modifier.testTag("membersBack"), onClick = onBack) {
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
                .testTag("membersScreen"),
        ) {
            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp).testTag("membersError"),
                )
            }
            state.members.forEach { member ->
                MemberRow(
                    member = member,
                    isMe = member.id == state.currentUserId,
                    canKick = iAmAdmin && member.id != state.currentUserId,
                    onKick = { confirmKick = member },
                )
                HorizontalDivider()
            }
            Spacer(Modifier.height(8.dp))
            if (!iAmAdmin) {
                Text(
                    "Only admins can remove other members.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    confirmKick?.let { target ->
        AlertDialog(
            modifier = Modifier.testTag("kickMemberDialog"),
            onDismissRequest = { confirmKick = null },
            title = { Text("Remove ${target.displayName}?") },
            text = {
                Text(
                    "They'll lose access to this household. Their assigned chores " +
                        "will become unassigned. This can't be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    modifier = Modifier.testTag("kickMemberConfirm"),
                    onClick = {
                        confirmKick = null
                        scope.launch {
                            runCatching { repo.api.removeMember(target.id) }
                                .onSuccess { repo.refresh() }
                                .onFailure { error = it.message ?: "Couldn't remove member" }
                        }
                    },
                ) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmKick = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun MemberRow(
    member: Member,
    isMe: Boolean,
    canKick: Boolean,
    onKick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .testTag("memberRow:${member.displayName}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AvatarBadge(
            userId = member.id,
            avatarVersion = member.avatarVersion,
            fallbackText = member.displayName.take(1).uppercase(),
            size = 40,
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(member.displayName, style = MaterialTheme.typography.bodyLarge)
                if (isMe) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "(you)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (member.role == "admin") {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "admin",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("memberRoleBadge:${member.displayName}"),
                    )
                }
            }
            Text(
                member.email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (canKick) {
            TextButton(
                modifier = Modifier.testTag("kickMemberButton:${member.displayName}"),
                onClick = onKick,
            ) { Text("Remove", color = MaterialTheme.colorScheme.error) }
        }
    }
}

