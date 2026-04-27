package com.chore.tracker.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import com.chore.tracker.data.Area
import com.chore.tracker.data.CreateAreaRequest
import com.chore.tracker.data.CreateTaskRequest
import com.chore.tracker.data.Repo
import com.chore.tracker.data.Task
import com.chore.tracker.data.dirtiness
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(repo: Repo, onSignOut: () -> Unit) {
    val scope = rememberCoroutineScope()
    val state by repo.state.collectAsState()
    var showAddArea by remember { mutableStateOf(false) }
    var showAddTaskFor by remember { mutableStateOf<Area?>(null) }
    var inviteCode by remember { mutableStateOf<String?>(null) }
    val pullState = rememberPullToRefreshState()

    DisposableEffect(repo) {
        repo.startPolling()
        onDispose { repo.stopPolling() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chores") },
                actions = {
                    IconButton(
                        modifier = Modifier.testTag("inviteButton"),
                        onClick = {
                            scope.launch {
                                runCatching { repo.api.createInvite() }
                                    .onSuccess { inviteCode = it.code }
                            }
                        },
                    ) { Icon(Icons.Default.PersonAdd, contentDescription = "Invite") }
                    IconButton(onClick = {
                        scope.launch { repo.logout(); onSignOut() }
                    }) { Icon(Icons.Default.Logout, contentDescription = "Sign out") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                modifier = Modifier.testTag("addAreaFab"),
                onClick = { showAddArea = true },
            ) { Icon(Icons.Default.Add, contentDescription = "Add area") }
        },
    ) { padding ->
        PullToRefreshBox(
            state = pullState,
            isRefreshing = state.isLoading,
            onRefresh = { scope.launch { repo.refresh() } },
            modifier = Modifier.fillMaxSize().padding(padding).testTag("homeScreen"),
        ) {
            Column(Modifier.fillMaxSize()) {
                state.error?.let {
                    Text(
                        it,
                        modifier = Modifier.padding(16.dp).testTag("homeError"),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (state.areas.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Tap + to add your first area")
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                        items(state.areas, key = { it.id }) { area ->
                            AreaCard(
                                area = area,
                                tasks = state.tasks.filter { it.areaId == area.id },
                                onAddTask = { showAddTaskFor = area },
                                onComplete = { task ->
                                    scope.launch {
                                        runCatching { repo.api.completeTask(task.id) }
                                            .onSuccess { repo.refresh() }
                                    }
                                },
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }

    if (showAddArea) {
        TextDialog(
            title = "New area",
            label = "e.g. Kitchen",
            onDismiss = { showAddArea = false },
            onConfirm = { name ->
                showAddArea = false
                scope.launch {
                    runCatching { repo.api.createArea(CreateAreaRequest(name)) }
                        .onSuccess { repo.refresh() }
                }
            },
        )
    }

    showAddTaskFor?.let { area ->
        AddTaskDialog(
            areaName = area.name,
            onDismiss = { showAddTaskFor = null },
            onConfirm = { name, freq ->
                showAddTaskFor = null
                scope.launch {
                    runCatching { repo.api.createTask(CreateTaskRequest(area.id, name, freq)) }
                        .onSuccess { repo.refresh() }
                }
            },
        )
    }

    inviteCode?.let { code ->
        AlertDialog(
            modifier = Modifier.testTag("inviteDialog"),
            onDismissRequest = { inviteCode = null },
            title = { Text("Invite to your household") },
            text = {
                Column {
                    Text("Share this code. It expires in 7 days.")
                    Spacer(Modifier.height(12.dp))
                    Text(code, modifier = Modifier.testTag("inviteCodeText"))
                }
            },
            confirmButton = { TextButton(onClick = { inviteCode = null }) { Text("Done") } },
        )
    }
}

@Composable
private fun AreaCard(
    area: Area,
    tasks: List<Task>,
    onAddTask: () -> Unit,
    onComplete: (Task) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().testTag("areaCard:${area.name}")) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    area.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    modifier = Modifier.testTag("addTaskButton:${area.name}"),
                    onClick = onAddTask,
                ) { Icon(Icons.Default.Add, contentDescription = "Add task") }
            }
            if (tasks.isEmpty()) {
                Text("No tasks yet", style = MaterialTheme.typography.bodySmall)
            } else {
                tasks.sortedByDescending { it.dirtiness() }.forEach { task ->
                    TaskRow(task, onComplete = { onComplete(task) })
                }
            }
        }
    }
}

@Composable
private fun TaskRow(task: Task, onComplete: () -> Unit) {
    val ratio = task.dirtiness().toFloat()
    val color = when {
        ratio >= 1.0f -> Color(0xFFD32F2F)
        ratio >= 0.66f -> Color(0xFFF57C00)
        ratio >= 0.33f -> Color(0xFFFBC02D)
        else -> Color(0xFF388E3C)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("taskRow:${task.name}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(task.name)
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { ratio.coerceIn(0f, 1f) },
                color = color,
                modifier = Modifier.fillMaxWidth(),
            )
            val attribution = task.lastDoneBy?.let { "last done by $it" } ?: "never done"
            Text(
                "every ${task.frequencyDays}d · ${(ratio * 100).toInt()}% · $attribution",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        IconButton(
            modifier = Modifier.testTag("completeButton:${task.name}"),
            onClick = onComplete,
        ) {
            Box(
                modifier = Modifier
                    .background(color, shape = androidx.compose.foundation.shape.CircleShape)
                    .padding(6.dp),
            ) {
                Icon(Icons.Default.Check, contentDescription = "Done", tint = Color.White)
            }
        }
    }
}

@Composable
private fun TextDialog(
    title: String,
    label: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        modifier = Modifier.testTag("textDialog"),
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value,
                { value = it },
                label = { Text(label) },
                modifier = Modifier.testTag("textDialogField"),
            )
        },
        confirmButton = {
            TextButton(
                enabled = value.isNotBlank(),
                modifier = Modifier.testTag("textDialogConfirm"),
                onClick = { onConfirm(value.trim()) },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AddTaskDialog(
    areaName: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, frequencyDays: Int) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var freq by remember { mutableStateOf("7") }
    AlertDialog(
        modifier = Modifier.testTag("addTaskDialog"),
        onDismissRequest = onDismiss,
        title = { Text("New task in $areaName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    name,
                    { name = it },
                    label = { Text("Task name") },
                    modifier = Modifier.testTag("taskNameField"),
                )
                OutlinedTextField(
                    freq,
                    { freq = it.filter { c -> c.isDigit() } },
                    label = { Text("Every N days") },
                    modifier = Modifier.testTag("taskFreqField"),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && (freq.toIntOrNull() ?: 0) > 0,
                modifier = Modifier.testTag("addTaskConfirm"),
                onClick = { onConfirm(name.trim(), freq.toInt()) },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
