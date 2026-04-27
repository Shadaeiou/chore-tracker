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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import kotlinx.coroutines.tasks.await
import androidx.compose.ui.unit.dp
import com.chore.tracker.data.Area
import com.chore.tracker.data.CreateAreaRequest
import com.chore.tracker.data.CreateTaskRequest
import com.chore.tracker.data.Member
import com.chore.tracker.data.Repo
import com.chore.tracker.data.Task
import com.chore.tracker.data.dirtiness
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repo: Repo,
    onSignOut: () -> Unit,
    onOpenSettings: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val state by repo.state.collectAsState()
    var showAddArea by remember { mutableStateOf(false) }
    var showAddTaskFor by remember { mutableStateOf<Area?>(null) }
    var inviteCode by remember { mutableStateOf<String?>(null) }
    val pullState = rememberPullToRefreshState()
    val snackbarHost = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) }

    DisposableEffect(repo) {
        repo.startPolling()
        onDispose { repo.stopPolling() }
    }

    // Request POST_NOTIFICATIONS permission and register FCM token on first composition.
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* granted or denied — FCM delivery handled by OS regardless of banner permission */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        try {
            val token = Firebase.messaging.token.await()
            repo.session.setFcmToken(token)
            repo.api.registerDeviceToken(com.chore.tracker.data.DeviceTokenRequest(token))
        } catch (_: Throwable) {}
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost, modifier = Modifier.testTag("snackbarHost")) },
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
                                    .onFailure { snackbarHost.showSnackbar("Invite failed: ${it.message}") }
                            }
                        },
                    ) { Icon(Icons.Default.PersonAdd, contentDescription = "Invite") }
                    IconButton(
                        modifier = Modifier.testTag("settingsButton"),
                        onClick = onOpenSettings,
                    ) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                    IconButton(onClick = {
                        scope.launch { repo.logout(); onSignOut() }
                    }) { Icon(Icons.Default.Logout, contentDescription = "Sign out") }
                },
            )
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    modifier = Modifier.testTag("addAreaFab"),
                    onClick = { showAddArea = true },
                ) { Icon(Icons.Default.Add, contentDescription = "Add area") }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("homeScreen"),
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.testTag("tab:chores"),
                    text = { Text("Chores") },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    modifier = Modifier.testTag("tab:activity"),
                    text = { Text("Activity") },
                )
            }
            when (selectedTab) {
                0 -> PullToRefreshBox(
                    state = pullState,
                    isRefreshing = state.isLoading,
                    onRefresh = { scope.launch { repo.refresh() } },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Column(Modifier.fillMaxSize()) {
                        state.error?.let {
                            Text(
                                it,
                                modifier = Modifier.padding(16.dp).testTag("homeError"),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        if (state.workload.isNotEmpty()) {
                            WorkloadCard(
                                entries = state.workload,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
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
                                                    .onSuccess {
                                                        repo.refresh()
                                                        val result = snackbarHost.showSnackbar(
                                                            message = "Marked done",
                                                            actionLabel = "Undo",
                                                        )
                                                        if (result == SnackbarResult.ActionPerformed) {
                                                            runCatching { repo.api.undoLastCompletion(task.id) }
                                                                .onSuccess { repo.refresh() }
                                                                .onFailure { snackbarHost.showSnackbar("Undo failed: ${it.message}") }
                                                        }
                                                    }
                                                    .onFailure { snackbarHost.showSnackbar("Failed to complete: ${it.message}") }
                                            }
                                        },
                                    )
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }
                1 -> ActivityScreen(
                    activity = state.activity,
                    modifier = Modifier.fillMaxSize(),
                )
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
                        .onFailure { snackbarHost.showSnackbar("Failed to create area: ${it.message}") }
                }
            },
        )
    }

    showAddTaskFor?.let { area ->
        AddTaskDialog(
            areaName = area.name,
            members = state.members,
            onDismiss = { showAddTaskFor = null },
            onConfirm = { name, freq, assignedTo, autoRotate, effortPoints ->
                showAddTaskFor = null
                scope.launch {
                    runCatching {
                        repo.api.createTask(
                            CreateTaskRequest(
                                areaId = area.id,
                                name = name,
                                frequencyDays = freq,
                                assignedTo = assignedTo,
                                autoRotate = autoRotate,
                                effortPoints = effortPoints,
                            ),
                        )
                    }
                        .onSuccess { repo.refresh() }
                        .onFailure { snackbarHost.showSnackbar("Failed to create task: ${it.message}") }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(task.name, modifier = Modifier.weight(1f))
                task.assignedToName?.let { name ->
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                            .testTag("assigneeBadge:${task.name}"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            name.take(1).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
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
                    .background(color, shape = CircleShape)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTaskDialog(
    areaName: String,
    members: List<Member>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, frequencyDays: Int, assignedTo: String?, autoRotate: Boolean, effortPoints: Int) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var freq by remember { mutableStateOf("7") }
    var selectedMember by remember { mutableStateOf<Member?>(members.firstOrNull()) }
    var autoRotate by remember { mutableStateOf(false) }
    var effortPoints by remember { mutableFloatStateOf(1f) }
    var assigneeExpanded by remember { mutableStateOf(false) }

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
                if (members.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = assigneeExpanded,
                        onExpandedChange = { assigneeExpanded = it },
                        modifier = Modifier.testTag("assigneePicker"),
                    ) {
                        OutlinedTextField(
                            value = selectedMember?.displayName ?: "Unassigned",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Assign to") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(assigneeExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = assigneeExpanded,
                            onDismissRequest = { assigneeExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Unassigned") },
                                onClick = { selectedMember = null; assigneeExpanded = false },
                            )
                            members.forEach { member ->
                                DropdownMenuItem(
                                    text = { Text(member.displayName) },
                                    onClick = { selectedMember = member; assigneeExpanded = false },
                                    modifier = Modifier.testTag("assigneeOption:${member.displayName}"),
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Auto-rotate", modifier = Modifier.weight(1f))
                        Switch(
                            checked = autoRotate,
                            onCheckedChange = { autoRotate = it },
                            modifier = Modifier.testTag("autoRotateToggle"),
                        )
                    }
                }
                Column {
                    Text(
                        "Effort: ${effortPoints.roundToInt()}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Slider(
                        value = effortPoints,
                        onValueChange = { effortPoints = it },
                        valueRange = 1f..5f,
                        steps = 3,
                        modifier = Modifier.testTag("effortSlider"),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && (freq.toIntOrNull() ?: 0) > 0,
                modifier = Modifier.testTag("addTaskConfirm"),
                onClick = {
                    onConfirm(
                        name.trim(),
                        freq.toInt(),
                        selectedMember?.id,
                        autoRotate,
                        effortPoints.roundToInt(),
                    )
                },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
