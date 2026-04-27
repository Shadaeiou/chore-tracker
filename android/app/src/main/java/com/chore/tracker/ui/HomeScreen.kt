package com.chore.tracker.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Logout
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
    var areas by remember { mutableStateOf<List<Area>>(emptyList()) }
    var tasks by remember { mutableStateOf<List<Task>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var showAddArea by remember { mutableStateOf(false) }
    var showAddTaskFor by remember { mutableStateOf<Area?>(null) }

    suspend fun reload() {
        runCatching {
            areas = repo.api.areas()
            tasks = repo.api.tasks()
            error = null
        }.onFailure { error = it.message }
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chores") },
                actions = {
                    IconButton(onClick = {
                        scope.launch { repo.logout(); onSignOut() }
                    }) { Icon(Icons.Default.Logout, contentDescription = "Sign out") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddArea = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add area")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            error?.let {
                Text(it, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            }
            if (areas.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Tap + to add your first area")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    items(areas, key = { it.id }) { area ->
                        AreaCard(
                            area = area,
                            tasks = tasks.filter { it.areaId == area.id },
                            onAddTask = { showAddTaskFor = area },
                            onComplete = { task ->
                                scope.launch {
                                    runCatching { repo.api.completeTask(task.id) }
                                        .onSuccess { reload() }
                                        .onFailure { error = it.message }
                                }
                            },
                        )
                        Spacer(Modifier.height(8.dp))
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
                        .onSuccess { reload() }
                        .onFailure { error = it.message }
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
                        .onSuccess { reload() }
                        .onFailure { error = it.message }
                }
            },
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(area.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onAddTask) {
                    Icon(Icons.Default.Add, contentDescription = "Add task")
                }
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
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
            Text(
                "every ${task.frequencyDays}d · ${(ratio * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        IconButton(onClick = onComplete) {
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
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value, { value = it }, label = { Text(label) }) },
        confirmButton = {
            TextButton(
                enabled = value.isNotBlank(),
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
        onDismissRequest = onDismiss,
        title = { Text("New task in $areaName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Task name") })
                OutlinedTextField(
                    freq,
                    { freq = it.filter { c -> c.isDigit() } },
                    label = { Text("Every N days") },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && (freq.toIntOrNull() ?: 0) > 0,
                onClick = { onConfirm(name.trim(), freq.toInt()) },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
