package com.chore.tracker.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.chore.tracker.data.ActivityComment
import com.chore.tracker.data.ActivityEntry
import com.chore.tracker.data.WorkloadEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val REACTION_PALETTE = listOf("👍", "❤️", "🎉", "👏", "😄", "💪")

@Composable
fun ActivityScreen(
    activity: List<ActivityEntry>,
    modifier: Modifier = Modifier,
    workload: List<WorkloadEntry> = emptyList(),
    onUndo: ((ActivityEntry) -> Unit)? = null,
    currentUserId: String? = null,
    onReact: ((completionId: String, emoji: String) -> Unit)? = null,
    onComment: ((completionId: String, text: String) -> Unit)? = null,
    onEditComment: ((completionId: String, commentId: String, text: String) -> Unit)? = null,
    onDeleteComment: ((completionId: String, commentId: String) -> Unit)? = null,
) {
    if (activity.isEmpty() && workload.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().testTag("activityScreen"),
            contentAlignment = Alignment.Center,
        ) {
            Text("No activity yet")
        }
        return
    }

    val dateFormatter = remember { SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()) }
    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val grouped = remember(activity) {
        activity.groupBy { dateFormatter.format(Date(it.doneAt)) }
    }
    var pendingUndo by remember { mutableStateOf<ActivityEntry?>(null) }
    var threadFor by remember { mutableStateOf<ActivityEntry?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("activityScreen"),
    ) {
        if (workload.isNotEmpty()) {
            item(key = "workload") {
                Spacer(Modifier.height(8.dp))
                WorkloadCard(entries = workload)
                Spacer(Modifier.height(16.dp))
            }
        }
        grouped.forEach { (date, entries) ->
            item(key = "header_$date") {
                Spacer(Modifier.height(12.dp))
                Text(
                    date,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("activityDateHeader:$date"),
                )
                HorizontalDivider(Modifier.padding(top = 4.dp))
            }
            items(entries, key = { it.id }) { entry ->
                ActivityRow(
                    entry = entry,
                    timeFormatter = timeFormatter,
                    onClick = { threadFor = entry },
                    onLongPress = if (onUndo != null) {
                        { pendingUndo = entry }
                    } else null,
                )
            }
        }
    }

    threadFor?.let { entry ->
        // Re-resolve to the freshest copy from `activity` so reactions/comments
        // posted while the sheet is open render immediately on the next refresh.
        val live = activity.firstOrNull { it.id == entry.id } ?: entry
        ActivityThreadSheet(
            entry = live,
            currentUserId = currentUserId,
            timeFormatter = timeFormatter,
            dateFormatter = dateFormatter,
            onDismiss = { threadFor = null },
            onReact = onReact,
            onComment = onComment,
            onEditComment = onEditComment,
            onDeleteComment = onDeleteComment,
        )
    }

    pendingUndo?.let { entry ->
        AlertDialog(
            modifier = Modifier.testTag("undoCompletionDialog:${entry.taskName}"),
            onDismissRequest = { pendingUndo = null },
            title = { Text("Undo this completion?") },
            text = {
                Text(
                    "Removes \"${entry.taskName}\" completed by ${entry.doneBy} on " +
                        dateFormatter.format(Date(entry.doneAt)) + ".",
                )
            },
            confirmButton = {
                TextButton(
                    modifier = Modifier.testTag("undoCompletionConfirm"),
                    onClick = {
                        val e = entry
                        pendingUndo = null
                        onUndo?.invoke(e)
                    },
                ) { Text("Undo", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingUndo = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActivityRow(
    entry: ActivityEntry,
    timeFormatter: SimpleDateFormat,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)?,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.padding(vertical = 3.dp)) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = if (onLongPress != null) {
                            { menuExpanded = true }
                        } else null,
                    )
                    .padding(vertical = 10.dp, horizontal = 12.dp)
                    .testTag("activityRow:${entry.taskName}"),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        timeFormatter.format(Date(entry.doneAt)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(width = 72.dp, height = 20.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(entry.taskName, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            entry.areaName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        AvatarBadge(
                            userId = entry.doneById,
                            avatarVersion = entry.doneByAvatarVersion,
                            fallbackText = entry.doneBy.take(1).uppercase(),
                            size = 24,
                        )
                        if (!entry.notes.isNullOrBlank()) {
                            Icon(
                                Icons.AutoMirrored.Filled.Notes,
                                contentDescription = "Has notes",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier
                                    .size(18.dp)
                                    .testTag("activityNotes:${entry.taskName}"),
                            )
                        }
                    }
                }
                // Reactions + comment-count strip (only rendered if non-empty).
                if (entry.reactions.isNotEmpty() || entry.comments.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Group reactions by emoji and show count.
                        entry.reactions
                            .groupBy { it.emoji }
                            .forEach { (emoji, list) ->
                                ReactionChip(emoji = emoji, count = list.size)
                            }
                        if (entry.comments.isNotEmpty()) {
                            Text(
                                "💬 ${entry.comments.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .testTag("activityCommentCount:${entry.taskName}"),
                            )
                        }
                    }
                }
            }
        }
        if (onLongPress != null) {
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.testTag("activityMenu:${entry.taskName}"),
            ) {
                DropdownMenuItem(
                    text = { Text("Undo this completion", color = MaterialTheme.colorScheme.error) },
                    onClick = { menuExpanded = false; onLongPress() },
                    modifier = Modifier.testTag("activityMenuUndo:${entry.taskName}"),
                )
            }
        }
    }
}

@Composable
private fun ReactionChip(emoji: String, count: Int) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.testTag("reactionChip:$emoji"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(emoji, style = MaterialTheme.typography.bodySmall)
            if (count > 1) {
                Spacer(Modifier.size(4.dp))
                Text(
                    count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityThreadSheet(
    entry: ActivityEntry,
    currentUserId: String?,
    timeFormatter: SimpleDateFormat,
    dateFormatter: SimpleDateFormat,
    onDismiss: () -> Unit,
    onReact: ((completionId: String, emoji: String) -> Unit)?,
    onComment: ((completionId: String, text: String) -> Unit)?,
    onEditComment: ((completionId: String, commentId: String, text: String) -> Unit)?,
    onDeleteComment: ((completionId: String, commentId: String) -> Unit)?,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember { mutableStateOf("") }
    val myReaction = entry.reactions.firstOrNull { it.userId == currentUserId }?.emoji

    ModalBottomSheet(
        modifier = Modifier.testTag("activityThreadSheet:${entry.taskName}"),
        sheetState = sheetState,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Header: task + who + when.
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarBadge(
                    userId = entry.doneById,
                    avatarVersion = entry.doneByAvatarVersion,
                    fallbackText = entry.doneBy.take(1).uppercase(),
                    size = 36,
                )
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.taskName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${entry.doneBy} · ${entry.areaName} · " +
                            "${dateFormatter.format(Date(entry.doneAt))} " +
                            timeFormatter.format(Date(entry.doneAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!entry.notes.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        entry.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            // Reaction palette: tap to add/replace; tapping current selection clears.
            if (onReact != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                ) {
                    REACTION_PALETTE.forEach { emoji ->
                        val selected = emoji == myReaction
                        Surface(
                            shape = CircleShape,
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .size(40.dp)
                                .clickable {
                                    onReact(entry.id, if (selected) "" else emoji)
                                }
                                .testTag("reactionPicker:$emoji"),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(emoji, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            // Comments thread.
            if (entry.comments.isEmpty()) {
                Text(
                    "No comments yet. Be the first to chime in.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(entry.comments, key = { it.id }) { comment ->
                        CommentRow(
                            comment = comment,
                            isMine = comment.userId == currentUserId,
                            timeFormatter = timeFormatter,
                            onEdit = if (onEditComment != null && comment.userId == currentUserId) {
                                { newText -> onEditComment(entry.id, comment.id, newText) }
                            } else null,
                            onDelete = if (onDeleteComment != null && comment.userId == currentUserId) {
                                { onDeleteComment(entry.id, comment.id) }
                            } else null,
                        )
                    }
                }
            }

            // Input row.
            if (onComment != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = { Text("Comment…") },
                        modifier = Modifier.weight(1f).testTag("commentField"),
                        maxLines = 4,
                    )
                    Spacer(Modifier.size(8.dp))
                    IconButton(
                        enabled = draft.isNotBlank(),
                        modifier = Modifier.testTag("commentSend"),
                        onClick = {
                            val text = draft.trim()
                            draft = ""
                            onComment(entry.id, text)
                        },
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: ActivityComment,
    isMine: Boolean,
    timeFormatter: SimpleDateFormat,
    onEdit: ((String) -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var draft by remember(comment.text) { mutableStateOf(comment.text) }
    Row(verticalAlignment = Alignment.Top) {
        AvatarBadge(
            userId = comment.userId,
            avatarVersion = comment.avatarVersion,
            fallbackText = comment.displayName?.take(1)?.uppercase().orEmpty().ifBlank { "?" },
            size = 28,
        )
        Spacer(Modifier.size(8.dp))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isMine) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .weight(1f)
                .testTag("comment:${comment.id}"),
        ) {
            Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            comment.displayName ?: "Member",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            timeFormatter.format(Date(comment.createdAt)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (isMine && (onEdit != null || onDelete != null)) {
                            IconButton(
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("commentMenu:${comment.id}"),
                                onClick = { menuOpen = true },
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Notes,
                                    contentDescription = "Comment actions",
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false },
                            ) {
                                if (onEdit != null) {
                                    DropdownMenuItem(
                                        text = { Text("Edit") },
                                        onClick = { menuOpen = false; editing = true },
                                    )
                                }
                                if (onDelete != null) {
                                    DropdownMenuItem(
                                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                        onClick = { menuOpen = false; onDelete() },
                                    )
                                }
                            }
                        }
                    }
                    if (editing && onEdit != null) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .testTag("commentEditField:${comment.id}"),
                            maxLines = 4,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = { editing = false; draft = comment.text }) {
                                Text("Cancel")
                            }
                            TextButton(
                                enabled = draft.isNotBlank() && draft != comment.text,
                                onClick = {
                                    onEdit(draft.trim())
                                    editing = false
                                },
                            ) { Text("Save") }
                        }
                    } else {
                        Text(comment.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
