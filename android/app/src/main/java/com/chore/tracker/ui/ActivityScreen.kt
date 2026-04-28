package com.chore.tracker.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.chore.tracker.data.ActivityEntry
import com.chore.tracker.data.WorkloadEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ActivityScreen(
    activity: List<ActivityEntry>,
    modifier: Modifier = Modifier,
    workload: List<WorkloadEntry> = emptyList(),
    onUndo: ((ActivityEntry) -> Unit)? = null,
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
                    onLongPress = if (onUndo != null) {
                        { pendingUndo = entry }
                    } else null,
                )
            }
        }
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
    onLongPress: (() -> Unit)?,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onLongPress != null) Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = { menuExpanded = true },
                    ) else Modifier,
                )
                .padding(vertical = 8.dp)
                .testTag("activityRow:${entry.taskName}"),
        ) {
            Text(entry.taskName, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${entry.areaName} · ${entry.doneBy} · ${timeFormatter.format(Date(entry.doneAt))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
