package com.chore.tracker.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.chore.tracker.data.ActivityEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ActivityScreen(activity: List<ActivityEntry>, modifier: Modifier = Modifier) {
    if (activity.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().testTag("activityScreen"),
            contentAlignment = Alignment.Center,
        ) {
            Text("No activity yet")
        }
        return
    }

    val dateFormatter = remember { SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()) }
    val grouped = remember(activity) {
        activity.groupBy { dateFormatter.format(Date(it.doneAt)) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("activityScreen"),
    ) {
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
                ActivityRow(entry)
            }
        }
    }
}

@Composable
private fun ActivityRow(entry: ActivityEntry) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("activityRow:${entry.taskName}"),
    ) {
        Text(entry.taskName, style = MaterialTheme.typography.bodyMedium)
        Text(
            "${entry.areaName} · ${entry.doneBy}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
