package com.chore.tracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.chore.tracker.data.CreateAreaRequest
import com.chore.tracker.data.CreateTaskRequest
import com.chore.tracker.data.Repo
import com.chore.tracker.data.TaskTemplate
import kotlinx.coroutines.launch

private val SUPPORTED_ROOMS = listOf(
    // Cleaning
    "kitchen" to "Kitchen",
    "bathroom" to "Bathroom",
    "bedroom" to "Bedroom",
    "living" to "Living room",
    "laundry" to "Laundry",
    "outdoor" to "Outdoor",
    "general" to "Whole home",
    "pets" to "Pets",
    "kids" to "Kids",
    "seasonal" to "Seasonal",
    // Beyond cleaning — recurring life admin
    "errands" to "Errands",
    "vehicle" to "Vehicle",
    "personal" to "Personal",
    "financial" to "Financial",
    "plants" to "Plants",
    "family" to "Family",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    repo: Repo,
    onSkip: () -> Unit,
    onComplete: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(1) }
    val selectedRooms = remember { mutableStateOf(setOf<String>()) }
    val selectedTaskIds = remember { mutableStateOf(setOf<String>()) }
    var templates by remember { mutableStateOf<List<TaskTemplate>>(emptyList()) }
    var creating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { repo.api.taskTemplates() }
            .onSuccess { templates = it }
            .onFailure { errorMessage = "Couldn't load templates: ${it.message}" }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).testTag("onboardingScreen"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Welcome — let's set up your home",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(modifier = Modifier.testTag("onboardingSkip"), onClick = onSkip) {
                Text("Skip")
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Step $step of 2",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
        }

        when (step) {
            1 -> {
                Text(
                    "Pick the rooms you want to track:",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SUPPORTED_ROOMS.forEach { (key, label) ->
                        val selected = key in selectedRooms.value
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedRooms.value = if (selected)
                                    selectedRooms.value - key else selectedRooms.value + key
                            },
                            label = { Text(label) },
                            modifier = Modifier.testTag("roomChip:$key"),
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                Button(
                    enabled = selectedRooms.value.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().testTag("onboardingNext"),
                    onClick = {
                        // Pre-select first 3 templates for each chosen room as sensible defaults.
                        val defaults = SUPPORTED_ROOMS
                            .filter { (k, _) -> k in selectedRooms.value }
                            .flatMap { (k, _) ->
                                templates.filter { it.suggestedArea == k }.take(3)
                            }
                            .map { it.id }
                            .toSet()
                        selectedTaskIds.value = defaults
                        step = 2
                    },
                ) { Text("Next") }
            }
            2 -> {
                Text(
                    "Pick a few starter chores. You can edit anytime.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.weight(1f)) {
                    val groups = SUPPORTED_ROOMS.filter { (k, _) -> k in selectedRooms.value }
                    groups.forEach { (key, label) ->
                        val roomTemplates = templates.filter { it.suggestedArea == key }
                        if (roomTemplates.isNotEmpty()) {
                            item(key = "header_$key") {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    label,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.testTag("roomHeader:$key"),
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            items(roomTemplates, key = { it.id }) { tmpl ->
                                val selected = tmpl.id in selectedTaskIds.value
                                TaskTemplateRow(
                                    template = tmpl,
                                    selected = selected,
                                    onToggle = {
                                        selectedTaskIds.value = if (selected)
                                            selectedTaskIds.value - tmpl.id
                                        else selectedTaskIds.value + tmpl.id
                                    },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        modifier = Modifier.weight(1f).testTag("onboardingBack"),
                        onClick = { step = 1 },
                    ) { Text("Back") }
                    Button(
                        enabled = !creating,
                        modifier = Modifier.weight(1f).testTag("onboardingFinish"),
                        onClick = {
                            creating = true
                            errorMessage = null
                            scope.launch {
                                runCatching {
                                    val now = System.currentTimeMillis()
                                    val createdAreas = mutableMapOf<String, String>()
                                    SUPPORTED_ROOMS
                                        .filter { (k, _) -> k in selectedRooms.value }
                                        .forEach { (k, label) ->
                                            val a = repo.api.createArea(CreateAreaRequest(label))
                                            createdAreas[k] = a.id
                                        }
                                    selectedTaskIds.value.forEach { tmplId ->
                                        val tmpl = templates.firstOrNull { it.id == tmplId } ?: return@forEach
                                        val areaId = createdAreas[tmpl.suggestedArea] ?: return@forEach
                                        repo.api.createTask(
                                            CreateTaskRequest(
                                                areaId = areaId,
                                                templateId = tmplId,
                                                lastDoneAt = now, // start as "just done" so indicators aren't all-red
                                            ),
                                        )
                                    }
                                    repo.refresh()
                                }
                                    .onSuccess { onComplete() }
                                    .onFailure { errorMessage = "Setup failed: ${it.message}"; creating = false }
                            }
                        },
                    ) {
                        if (creating) {
                            CircularProgressIndicator(
                                modifier = Modifier.width(20.dp).height(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Set up my home")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskTemplateRow(
    template: TaskTemplate,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .testTag("templateRow:${template.id}"),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(template.name, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "every ${template.suggestedFrequencyDays}d · effort ${template.suggestedEffort}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilterChip(
                selected = selected,
                onClick = onToggle,
                label = { Text(if (selected) "Added" else "Add") },
                modifier = Modifier.testTag("templateToggle:${template.id}"),
            )
        }
    }
}
