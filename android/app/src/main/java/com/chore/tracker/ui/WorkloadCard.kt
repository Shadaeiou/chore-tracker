package com.chore.tracker.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.chore.tracker.data.WorkloadEntry

@Composable
fun WorkloadCard(entries: List<WorkloadEntry>, modifier: Modifier = Modifier) {
    if (entries.isEmpty()) return
    val maxPoints = entries.maxOf { it.effortPoints }.coerceAtLeast(1)
    Card(modifier = modifier.fillMaxWidth().testTag("workloadCard")) {
        Column(Modifier.padding(12.dp)) {
            Text("This month's effort", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            entries.forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        entry.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .width(80.dp)
                            .testTag("workloadName:${entry.displayName}"),
                    )
                    LinearProgressIndicator(
                        progress = { entry.effortPoints.toFloat() / maxPoints },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${entry.effortPoints}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("workloadPoints:${entry.displayName}"),
                    )
                }
            }
        }
    }
}
