package com.chore.tracker.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.chore.tracker.data.Area
import com.chore.tracker.data.CompleteRequest
import com.chore.tracker.data.CreateAreaRequest
import com.chore.tracker.data.CreateTaskRequest
import com.chore.tracker.data.DeviceTokenRequest
import com.chore.tracker.data.Member
import com.chore.tracker.data.PatchAreaRequest
import com.chore.tracker.data.PatchHouseholdRequest
import com.chore.tracker.data.PatchTaskRequest
import com.chore.tracker.data.Repo
import com.chore.tracker.data.SnoozeRequest
import com.chore.tracker.data.Task
import com.chore.tracker.data.dirtiness
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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
    var editingArea by remember { mutableStateOf<Area?>(null) }
    var deletingArea by remember { mutableStateOf<Area?>(null) }
    var editingTask by remember { mutableStateOf<Task?>(null) }
    var deletingTask by remember { mutableStateOf<Task?>(null) }
    var snoozingTask by remember { mutableStateOf<Task?>(null) }
    var retroactiveTask by remember { mutableStateOf<Task?>(null) }
    var wizardSkipped by remember { mutableStateOf(false) }
    var inviteCode by remember { mutableStateOf<String?>(null) }
    val pullState = rememberPullToRefreshState()
    val snackbarHost = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> repo.startPolling()
                Lifecycle.Event.ON_STOP -> repo.stopPolling()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); repo.stopPolling() }
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        try {
            val token = Firebase.messaging.token.await()
            repo.session.setFcmToken(token)
            repo.api.registerDeviceToken(DeviceTokenRequest(token))
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
                        modifier = Modifier.testTag("vacationButton"),
                        onClick = {
                            scope.launch {
                                val isPaused = state.pausedUntil != null && state.pausedUntil!! > System.currentTimeMillis()
                                val newPause = if (isPaused) null else System.currentTimeMillis() + 365L * 86_400_000L
                                runCatching { repo.api.patchHousehold(PatchHouseholdRequest(newPause)) }
                                    .onSuccess { repo.refresh() }
                                    .onFailure { snackbarHost.showSnackbar("Failed: ${it.message}") }
                            }
                        },
                    ) {
                        val tint = if (state.pausedUntil != null && state.pausedUntil!! > System.currentTimeMillis())
                            MaterialTheme.colorScheme.primary
                        else androidx.compose.ui.graphics.Color.Unspecified
                        Icon(Icons.Default.BeachAccess, contentDescription = "Vacation mode", tint = tint)
                    }
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
                        if (state.pausedUntil != null && state.pausedUntil!! > System.currentTimeMillis()) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(8.dp).testTag("vacationBanner"),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Default.BeachAccess,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    )
                                    Spacer(Modifier.size(8.dp))
                                    Text(
                                        "Vacation mode — indicators paused",
                                        modifier = Modifier.weight(1f),
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    )
                                    TextButton(
                                        modifier = Modifier.testTag("resumeButton"),
                                        onClick = {
                                            scope.launch {
                                                runCatching { repo.api.patchHousehold(PatchHouseholdRequest(null)) }
                                                    .onSuccess { repo.refresh() }
                                                    .onFailure { snackbarHost.showSnackbar("Failed: ${it.message}") }
                                            }
                                        },
                                    ) { Text("Resume") }
                                }
                            }
                        }
                        if (state.workload.isNotEmpty()) {
                            WorkloadCard(
                                entries = state.workload,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                        if (state.areas.isEmpty()) {
                            if (wizardSkipped) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Tap + to add your first area")
                                }
                            } else {
                                OnboardingScreen(
                                    repo = repo,
                                    onSkip = { wizardSkipped = true },
                                    onComplete = { /* refresh already happened in wizard */ },
                                )
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                                items(state.areas, key = { it.id }) { area ->
                                    AreaCard(
                                        area = area,
                                        tasks = state.tasks.filter { it.areaId == area.id },
                                        onAddTask = { showAddTaskFor = area },
                                        onEditArea = { editingArea = area },
                                        onDeleteArea = { deletingArea = area },
                                        onEditTask = { task -> editingTask = task },
                                        onDeleteTask = { task -> deletingTask = task },
                                        onSnoozeTask = { task -> snoozingTask = task },
                                        onMarkDoneAt = { task -> retroactiveTask = task },
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

    // ── Add area ──────────────────────────────────────────────────────────────
    if (showAddArea) {
        TextDialog(
            title = "New area",
            label = "e.g. Kitchen",
            initialValue = "",
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

    // ── Edit area ─────────────────────────────────────────────────────────────
    editingArea?.let { area ->
        TextDialog(
            title = "Rename area",
            label = "Area name",
            initialValue = area.name,
            confirmLabel = "Save",
            onDismiss = { editingArea = null },
            onConfirm = { name ->
                editingArea = null
                scope.launch {
                    runCatching { repo.api.patchArea(area.id, PatchAreaRequest(name = name)) }
                        .onSuccess { repo.refresh() }
                        .onFailure { snackbarHost.showSnackbar("Failed to rename area: ${it.message}") }
                }
            },
        )
    }

    // ── Delete area ───────────────────────────────────────────────────────────
    deletingArea?.let { area ->
        AlertDialog(
            modifier = Modifier.testTag("deleteAreaDialog:${area.name}"),
            onDismissRequest = { deletingArea = null },
            title = { Text("Delete ${area.name}?") },
            text = { Text("This will also delete all tasks in this area. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    modifier = Modifier.testTag("deleteAreaConfirm"),
                    onClick = {
                        deletingArea = null
                        scope.launch {
                            runCatching { repo.api.deleteArea(area.id) }
                                .onSuccess { repo.refresh() }
                                .onFailure { snackbarHost.showSnackbar("Failed to delete area: ${it.message}") }
                        }
                    },
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deletingArea = null }) { Text("Cancel") } },
        )
    }

    // ── Add task ──────────────────────────────────────────────────────────────
    showAddTaskFor?.let { area ->
        TaskFormDialog(
            title = "New task in ${area.name}",
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

    // ── Edit task ─────────────────────────────────────────────────────────────
    editingTask?.let { task ->
        TaskFormDialog(
            title = "Edit task",
            initialTask = task,
            members = state.members,
            confirmLabel = "Save",
            onDismiss = { editingTask = null },
            onConfirm = { name, freq, assignedTo, autoRotate, effortPoints ->
                editingTask = null
                scope.launch {
                    runCatching {
                        repo.api.patchTask(
                            task.id,
                            PatchTaskRequest(
                                name = name,
                                frequencyDays = freq,
                                assignedTo = assignedTo,
                                autoRotate = autoRotate,
                                effortPoints = effortPoints,
                            ),
                        )
                    }
                        .onSuccess { repo.refresh() }
                        .onFailure { snackbarHost.showSnackbar("Failed to update task: ${it.message}") }
                }
            },
        )
    }

    // ── Delete task ───────────────────────────────────────────────────────────
    deletingTask?.let { task ->
        AlertDialog(
            modifier = Modifier.testTag("deleteTaskDialog:${task.name}"),
            onDismissRequest = { deletingTask = null },
            title = { Text("Delete ${task.name}?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(
                    modifier = Modifier.testTag("deleteTaskConfirm"),
                    onClick = {
                        deletingTask = null
                        scope.launch {
                            runCatching { repo.api.deleteTask(task.id) }
                                .onSuccess { repo.refresh() }
                                .onFailure { snackbarHost.showSnackbar("Failed to delete task: ${it.message}") }
                        }
                    },
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deletingTask = null }) { Text("Cancel") } },
        )
    }

    // ── Snooze task ───────────────────────────────────────────────────────────
    snoozingTask?.let { task ->
        AlertDialog(
            modifier = Modifier.testTag("snoozeDialog:${task.name}"),
            onDismissRequest = { snoozingTask = null },
            title = { Text("Snooze \"${task.name}\"") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1L to "1 day", 3L to "3 days", 7L to "1 week").forEach { (days, label) ->
                        TextButton(
                            modifier = Modifier.fillMaxWidth().testTag("snoozeOption:$days"),
                            onClick = {
                                snoozingTask = null
                                scope.launch {
                                    val until = System.currentTimeMillis() + days * 86_400_000L
                                    runCatching { repo.api.snoozeTask(task.id, SnoozeRequest(until)) }
                                        .onSuccess { repo.refresh() }
                                        .onFailure { snackbarHost.showSnackbar("Snooze failed: ${it.message}") }
                                }
                            },
                        ) { Text(label) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { snoozingTask = null }) { Text("Cancel") } },
        )
    }

    // ── Mark done at... (retroactive completion) ──────────────────────────────
    retroactiveTask?.let { task ->
        RetroactiveDoneDialog(
            task = task,
            onDismiss = { retroactiveTask = null },
            onConfirm = { ts ->
                retroactiveTask = null
                scope.launch {
                    runCatching { repo.api.completeTask(task.id, CompleteRequest(at = ts)) }
                        .onSuccess { repo.refresh() }
                        .onFailure { snackbarHost.showSnackbar("Failed: ${it.message}") }
                }
            },
        )
    }

    // ── Invite code ───────────────────────────────────────────────────────────
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AreaCard(
    area: Area,
    tasks: List<Task>,
    onAddTask: () -> Unit,
    onEditArea: () -> Unit,
    onDeleteArea: () -> Unit,
    onEditTask: (Task) -> Unit,
    onDeleteTask: (Task) -> Unit,
    onSnoozeTask: (Task) -> Unit,
    onMarkDoneAt: (Task) -> Unit,
    onComplete: (Task) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth().testTag("areaCard:${area.name}")) {
        Column(Modifier.padding(12.dp)) {
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { menuExpanded = true },
                        )
                        .testTag("areaHeader:${area.name}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.testTag("areaMenu:${area.name}"),
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = { menuExpanded = false; onEditArea() },
                        modifier = Modifier.testTag("areaMenuEdit:${area.name}"),
                    )
                    DropdownMenuItem(
                        text = { Text("Delete area", color = MaterialTheme.colorScheme.error) },
                        onClick = { menuExpanded = false; onDeleteArea() },
                        modifier = Modifier.testTag("areaMenuDelete:${area.name}"),
                    )
                }
            }
            if (tasks.isEmpty()) {
                Text("No tasks yet", style = MaterialTheme.typography.bodySmall)
            } else {
                tasks.sortedByDescending { it.dirtiness() }.forEach { task ->
                    TaskRow(
                        task = task,
                        onComplete = { onComplete(task) },
                        onEdit = { onEditTask(task) },
                        onDelete = { onDeleteTask(task) },
                        onSnooze = { onSnoozeTask(task) },
                        onMarkDoneAt = { onMarkDoneAt(task) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskRow(
    task: Task,
    onComplete: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSnooze: () -> Unit,
    onMarkDoneAt: () -> Unit,
) {
    val ratio = task.dirtiness().toFloat()
    val color = when {
        ratio >= 1.0f -> Color(0xFFD32F2F)
        ratio >= 0.66f -> Color(0xFFF57C00)
        ratio >= 0.33f -> Color(0xFFFBC02D)
        else -> Color(0xFF388E3C)
    }
    var menuExpanded by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = { menuExpanded = true },
                )
                .testTag("taskRow:${task.name}"),
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
                val snoozed = task.snoozedUntil != null && task.snoozedUntil > System.currentTimeMillis()
                if (snoozed) {
                    val days = ((task.snoozedUntil!! - System.currentTimeMillis()) / 86_400_000L).coerceAtLeast(0)
                    Text(
                        "snoozed · $days day${if (days == 1L) "" else "s"} left",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                } else {
                    Text(
                        "every ${task.frequencyDays}d · ${(ratio * 100).toInt()}% · $attribution",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
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
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier.testTag("taskMenu:${task.name}"),
        ) {
            DropdownMenuItem(
                text = { Text("Edit") },
                onClick = { menuExpanded = false; onEdit() },
                modifier = Modifier.testTag("taskMenuEdit:${task.name}"),
            )
            DropdownMenuItem(
                text = { Text("Snooze…") },
                onClick = { menuExpanded = false; onSnooze() },
                modifier = Modifier.testTag("taskMenuSnooze:${task.name}"),
            )
            DropdownMenuItem(
                text = { Text("Mark done at…") },
                onClick = { menuExpanded = false; onMarkDoneAt() },
                modifier = Modifier.testTag("taskMenuMarkDoneAt:${task.name}"),
            )
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                onClick = { menuExpanded = false; onDelete() },
                modifier = Modifier.testTag("taskMenuDelete:${task.name}"),
            )
        }
    }
}

@Composable
private fun TextDialog(
    title: String,
    label: String,
    initialValue: String = "",
    confirmLabel: String = "Add",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
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
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RetroactiveDoneDialog(
    task: Task,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val now = System.currentTimeMillis()
    val createdAt = task.createdAt
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = now,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis in createdAt..now
        },
    )
    DatePickerDialog(
        modifier = Modifier.testTag("retroactiveDialog:${task.name}"),
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                modifier = Modifier.testTag("retroactiveConfirm"),
                onClick = { pickerState.selectedDateMillis?.let(onConfirm) },
            ) { Text("Mark done") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = pickerState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskFormDialog(
    title: String,
    members: List<Member>,
    initialTask: Task? = null,
    confirmLabel: String = "Add",
    onDismiss: () -> Unit,
    onConfirm: (name: String, frequencyDays: Int, assignedTo: String?, autoRotate: Boolean, effortPoints: Int) -> Unit,
) {
    var name by remember { mutableStateOf(initialTask?.name ?: "") }
    var freq by remember { mutableStateOf(initialTask?.frequencyDays?.toString() ?: "7") }
    val initialMember = initialTask?.assignedTo?.let { id -> members.firstOrNull { it.id == id } }
        ?: members.firstOrNull()
    var selectedMember by remember { mutableStateOf<Member?>(initialMember) }
    var autoRotate by remember { mutableStateOf(initialTask?.autoRotate ?: false) }
    var effortPoints by remember { mutableFloatStateOf(initialTask?.effortPoints?.toFloat() ?: 1f) }
    var assigneeExpanded by remember { mutableStateOf(false) }

    val testDialogTag = if (initialTask == null) "addTaskDialog" else "editTaskDialog"

    AlertDialog(
        modifier = Modifier.testTag(testDialogTag),
        onDismissRequest = onDismiss,
        title = { Text(title) },
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
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
