package com.chore.tracker.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
    var copyingArea by remember { mutableStateOf<Area?>(null) }
    var renamingHousehold by remember { mutableStateOf(false) }
    var libraryForArea by remember { mutableStateOf<Area?>(null) }
    var selectAreasMode by remember { mutableStateOf(false) }
    var selectedAreaIds by remember { mutableStateOf(setOf<String>()) }
    var householdSearchQuery by remember { mutableStateOf("") }
    var editingTask by remember { mutableStateOf<Task?>(null) }
    // Swipe-left opens snooze/delete; swipe-right opens the unified complete dialog.
    var snoozingTask by remember { mutableStateOf<Task?>(null) }
    var notesCompletionTask by remember { mutableStateOf<Task?>(null) }
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
                        val isPaused = state.pausedUntil != null && state.pausedUntil!! > System.currentTimeMillis()
                        if (isPaused) {
                            Icon(
                                Icons.Default.BeachAccess,
                                contentDescription = "Vacation mode (active)",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Icon(Icons.Default.BeachAccess, contentDescription = "Vacation mode")
                        }
                    }
                    IconButton(
                        modifier = Modifier.testTag("settingsButton"),
                        onClick = onOpenSettings,
                    ) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                },
            )
        },
        floatingActionButton = {
            // FAB only on the Household tab — Today is for triage, Activity is read-only.
            if (selectedTab == 1) {
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
                    modifier = Modifier.testTag("tab:today"),
                    text = { Text("Today") },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    modifier = Modifier.testTag("tab:household"),
                    text = { Text("Household") },
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    modifier = Modifier.testTag("tab:activity"),
                    text = { Text("Activity") },
                )
            }
            when (selectedTab) {
                // ── Today tab: tasks due today or earlier, no workload here ──
                0 -> PullToRefreshBox(
                    state = pullState,
                    isRefreshing = state.isLoading,
                    onRefresh = { scope.launch { repo.refresh() } },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Column(Modifier.fillMaxSize()) {
                        TabHeader(state, repo, scope, snackbarHost)
                        if (state.areas.isEmpty()) {
                            if (wizardSkipped) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Switch to Household tab and tap + to add your first area")
                                }
                            } else {
                                OnboardingScreen(
                                    repo = repo,
                                    onSkip = { wizardSkipped = true },
                                    onComplete = {},
                                )
                            }
                        } else {
                            TodayList(
                                state = state,
                                onEditTask = { editingTask = it },
                                onSwipeRight = { notesCompletionTask = it },
                                onSwipeLeft = { snoozingTask = it },
                            )
                        }
                    }
                }
                // ── Household tab: hierarchical view for managing areas + tasks ──
                1 -> PullToRefreshBox(
                    state = pullState,
                    isRefreshing = state.isLoading,
                    onRefresh = { scope.launch { repo.refresh() } },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Column(Modifier.fillMaxSize()) {
                        TabHeader(state, repo, scope, snackbarHost)
                        HouseholdHeader(
                            household = state.household,
                            memberCount = state.members.size,
                            selectAreasMode = selectAreasMode,
                            selectedCount = selectedAreaIds.size,
                            onLongPressRename = { renamingHousehold = true },
                            onLongPressSelectAreas = {
                                selectAreasMode = true
                                selectedAreaIds = emptySet()
                            },
                            onConfirmDeleteSelected = {
                                val toDelete = selectedAreaIds
                                selectAreasMode = false
                                selectedAreaIds = emptySet()
                                scope.launch {
                                    var failures = 0
                                    toDelete.forEach { id ->
                                        runCatching { repo.api.deleteArea(id) }
                                            .onFailure { failures++ }
                                    }
                                    repo.refresh()
                                    if (failures == 0) {
                                        snackbarHost.showSnackbar("Deleted ${toDelete.size} area${if (toDelete.size == 1) "" else "s"}")
                                    } else {
                                        snackbarHost.showSnackbar("$failures of ${toDelete.size} deletes failed")
                                    }
                                }
                            },
                            onCancelSelection = {
                                selectAreasMode = false
                                selectedAreaIds = emptySet()
                            },
                        )
                        if (state.areas.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Tap + to add your first area")
                            }
                        } else {
                            // Search bar
                            if (!selectAreasMode) {
                                OutlinedTextField(
                                    value = householdSearchQuery,
                                    onValueChange = { householdSearchQuery = it },
                                    placeholder = { Text("Search areas and tasks") },
                                    singleLine = true,
                                    leadingIcon = { Icon(androidx.compose.material.icons.Icons.Default.Search, null) },
                                    trailingIcon = if (householdSearchQuery.isNotEmpty()) {
                                        @Composable {
                                            IconButton(onClick = { householdSearchQuery = "" }) {
                                                Icon(androidx.compose.material.icons.Icons.Default.Clear, "Clear")
                                            }
                                        }
                                    } else null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                        .testTag("householdSearchField"),
                                )
                            }
                            // Filter areas + tasks based on search
                            val q = householdSearchQuery.trim().lowercase()
                            val filteredAreas = if (q.isEmpty()) state.areas else state.areas.filter { area ->
                                area.name.lowercase().contains(q) ||
                                    state.tasks.any { it.areaId == area.id && it.name.lowercase().contains(q) }
                            }
                            if (filteredAreas.isEmpty()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("No matches for \"${householdSearchQuery.trim()}\"")
                                }
                            } else {
                            LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                                items(filteredAreas, key = { it.id }) { area ->
                                    if (selectAreasMode) {
                                        SelectableAreaCard(
                                            area = area,
                                            taskCount = state.tasks.count { it.areaId == area.id },
                                            checked = area.id in selectedAreaIds,
                                            onToggle = {
                                                selectedAreaIds = if (area.id in selectedAreaIds)
                                                    selectedAreaIds - area.id
                                                else selectedAreaIds + area.id
                                            },
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        return@items
                                    }
                                    val areaTasks = if (q.isEmpty()) state.tasks.filter { it.areaId == area.id }
                                    else state.tasks.filter {
                                        it.areaId == area.id &&
                                            (area.name.lowercase().contains(q) || it.name.lowercase().contains(q))
                                    }
                                    AreaCard(
                                        area = area,
                                        tasks = areaTasks,
                                        onAddTask = { showAddTaskFor = area },
                                        onAddFromLibrary = { libraryForArea = area },
                                        onEditArea = { editingArea = area },
                                        onCopyArea = { copyingArea = area },
                                        onDeleteArea = { deletingArea = area },
                                        onEditTask = { task -> editingTask = task },
                                        onMassDeleteTasks = { taskIds ->
                                            scope.launch {
                                                var failures = 0
                                                taskIds.forEach { id ->
                                                    runCatching { repo.api.deleteTask(id) }
                                                        .onFailure { failures++ }
                                                }
                                                repo.refresh()
                                                if (failures == 0) {
                                                    snackbarHost.showSnackbar("Deleted ${taskIds.size} task${if (taskIds.size == 1) "" else "s"}")
                                                } else {
                                                    snackbarHost.showSnackbar("$failures of ${taskIds.size} deletes failed")
                                                }
                                            }
                                        },
                                        onSwipeRightTask = { task -> notesCompletionTask = task },
                                        onSwipeLeftTask = { task -> snoozingTask = task },
                                    )
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                            }
                        }
                    }
                }
                // ── Activity tab ─────────────────────────────────────────────
                2 -> ActivityScreen(
                    activity = state.activity,
                    workload = state.workload,
                    modifier = Modifier.fillMaxSize(),
                    onUndo = { entry ->
                        scope.launch {
                            runCatching { repo.api.deleteCompletion(entry.id) }
                                .onSuccess {
                                    repo.refresh()
                                    snackbarHost.showSnackbar("Completion undone")
                                }
                                .onFailure { snackbarHost.showSnackbar("Undo failed: ${it.message}") }
                        }
                    },
                )
            }
        }
    }

    // ── Add area(s) ───────────────────────────────────────────────────────────
    if (showAddArea) {
        AddAreaDialog(
            existingAreaNames = state.areas.map { it.name.lowercase() }.toSet(),
            onDismiss = { showAddArea = false },
            onConfirm = { names ->
                showAddArea = false
                scope.launch {
                    var failures = 0
                    names.forEach { name ->
                        runCatching { repo.api.createArea(CreateAreaRequest(name)) }
                            .onFailure { failures++ }
                    }
                    repo.refresh()
                    if (failures == 0 && names.size > 1) {
                        snackbarHost.showSnackbar("Added ${names.size} areas")
                    } else if (failures > 0) {
                        snackbarHost.showSnackbar("$failures of ${names.size} adds failed")
                    }
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

    // ── Add from library (multi-select) ───────────────────────────────────────
    libraryForArea?.let { area ->
        MultiTemplatePicker(
            repo = repo,
            preferredArea = area.name.lowercase(),
            onDismiss = { libraryForArea = null },
            onConfirm = { templateIds ->
                libraryForArea = null
                scope.launch {
                    val now = System.currentTimeMillis()
                    var failures = 0
                    templateIds.forEach { tmplId ->
                        runCatching {
                            repo.api.createTask(
                                CreateTaskRequest(
                                    areaId = area.id,
                                    templateId = tmplId,
                                    // Start green so a fresh batch isn't a wall of red.
                                    lastDoneAt = now,
                                ),
                            )
                        }.onFailure { failures++ }
                    }
                    repo.refresh()
                    if (failures == 0) {
                        snackbarHost.showSnackbar("Added ${templateIds.size} task${if (templateIds.size == 1) "" else "s"}")
                    } else {
                        snackbarHost.showSnackbar("$failures of ${templateIds.size} adds failed")
                    }
                }
            },
        )
    }

    // ── Rename household ──────────────────────────────────────────────────────
    if (renamingHousehold) {
        val current = state.household?.name ?: ""
        TextDialog(
            title = "Rename household",
            label = "Household name",
            initialValue = current,
            confirmLabel = "Save",
            onDismiss = { renamingHousehold = false },
            onConfirm = { name ->
                renamingHousehold = false
                scope.launch {
                    runCatching { repo.api.renameHousehold(com.chore.tracker.data.RenameHouseholdRequest(name)) }
                        .onSuccess { repo.refresh() }
                        .onFailure { snackbarHost.showSnackbar("Rename failed: ${it.message}") }
                }
            },
        )
    }

    // ── Copy area ─────────────────────────────────────────────────────────────
    copyingArea?.let { area ->
        TextDialog(
            title = "Copy \"${area.name}\" as…",
            label = "Name for the copy",
            initialValue = "${area.name} copy",
            confirmLabel = "Copy",
            onDismiss = { copyingArea = null },
            onConfirm = { name ->
                copyingArea = null
                scope.launch {
                    runCatching { repo.api.copyArea(area.id, com.chore.tracker.data.CopyAreaRequest(name)) }
                        .onSuccess {
                            repo.refresh()
                            snackbarHost.showSnackbar("Copied to \"$name\"")
                        }
                        .onFailure { snackbarHost.showSnackbar("Copy failed: ${it.message}") }
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
            areaNameForLibrary = area.name,
            repo = repo,
            onDismiss = { showAddTaskFor = null },
            onConfirm = { name, freq, assignedTo, autoRotate, effortPoints, notes ->
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
                                notes = notes,
                                // Start the indicator green: treat the task as just done.
                                // Avoids the bad UX where every newly added task immediately
                                // shows as overdue.
                                lastDoneAt = System.currentTimeMillis(),
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
            onConfirm = { name, freq, assignedTo, autoRotate, effortPoints, notes ->
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
                                // PATCH treats notes="" as clear; pass empty string so the
                                // user can clear by emptying the field, vs null = "leave alone".
                                notes = notes ?: "",
                            ),
                        )
                    }
                        .onSuccess { repo.refresh() }
                        .onFailure { snackbarHost.showSnackbar("Failed to update task: ${it.message}") }
                }
            },
        )
    }

    // ── Snooze or delete (swipe-left) ─────────────────────────────────────────
    snoozingTask?.let { task ->
        SnoozeOrDeleteDialog(
            task = task,
            onDismiss = { snoozingTask = null },
            onSnooze = { until ->
                snoozingTask = null
                scope.launch {
                    runCatching { repo.api.snoozeTask(task.id, SnoozeRequest(until)) }
                        .onSuccess { repo.refresh() }
                        .onFailure { snackbarHost.showSnackbar("Snooze failed: ${it.message}") }
                }
            },
            onDelete = {
                snoozingTask = null
                scope.launch {
                    runCatching { repo.api.deleteTask(task.id) }
                        .onSuccess { repo.refresh() }
                        .onFailure { snackbarHost.showSnackbar("Failed to delete task: ${it.message}") }
                }
            },
        )
    }

    // ── Complete (swipe-right): unified date + notes + assignee picker ────────
    notesCompletionTask?.let { task ->
        CompleteTaskDialog(
            task = task,
            members = state.members,
            currentUserId = state.currentUserId,
            onDismiss = { notesCompletionTask = null },
            onConfirm = { at, notes, completedBy ->
                notesCompletionTask = null
                scope.launch {
                    runCatching {
                        repo.api.completeTask(
                            task.id,
                            CompleteRequest(
                                at = at,
                                notes = notes.ifBlank { null },
                                completedBy = completedBy,
                            ),
                        )
                    }
                        .onSuccess { repo.refresh() }
                        .onFailure { snackbarHost.showSnackbar("Failed: ${it.message}") }
                }
            },
        )
    }

    // ── Invite code ───────────────────────────────────────────────────────────
    inviteCode?.let { code ->
        InviteCodeDialog(
            code = code,
            snackbarHost = snackbarHost,
            scope = scope,
            onDismiss = { inviteCode = null },
        )
    }
}

@Composable
private fun InviteCodeDialog(
    code: String,
    snackbarHost: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    AlertDialog(
        modifier = Modifier.testTag("inviteDialog"),
        onDismissRequest = onDismiss,
        title = { Text("Invite to your household") },
        text = {
            Column {
                Text("Share this code with someone you trust. It expires in 7 days.")
                Spacer(Modifier.height(16.dp))
                // Big tappable code box — long-press to select on most keyboards too,
                // but the explicit Copy button is the primary affordance.
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("inviteCodeText"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Text(
                        code,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        modifier = Modifier.weight(1f).testTag("inviteCopyButton"),
                        onClick = {
                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(code))
                            scope.launch { snackbarHost.showSnackbar("Code copied") }
                        },
                    ) { Text("Copy") }
                    androidx.compose.material3.OutlinedButton(
                        modifier = Modifier.weight(1f).testTag("inviteShareButton"),
                        onClick = {
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    android.content.Intent.EXTRA_TEXT,
                                    "Join my household on Chore Tracker with this code: $code",
                                )
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, "Share invite"))
                        },
                    ) { Text("Share…") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

/** Household tab header — long-press for actions, switches to a selection bar
 *  when select-areas mode is on. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HouseholdHeader(
    household: com.chore.tracker.data.Household?,
    memberCount: Int,
    selectAreasMode: Boolean,
    selectedCount: Int,
    onLongPressRename: () -> Unit,
    onLongPressSelectAreas: () -> Unit,
    onConfirmDeleteSelected: () -> Unit,
    onCancelSelection: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    if (selectAreasMode) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("areaSelectionBar"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$selectedCount selected",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(
                enabled = selectedCount > 0,
                modifier = Modifier.testTag("massDeleteAreasButton"),
                onClick = onConfirmDeleteSelected,
            ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = onCancelSelection) { Text("Cancel") }
        }
        return
    }

    if (household == null) return
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {},
                    onLongClick = { menuExpanded = true },
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("householdHeader"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    household.name,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.testTag("householdName"),
                )
                val memberWord = if (memberCount == 1) "member" else "members"
                Text(
                    "$memberCount $memberWord · long-press for actions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier.testTag("householdMenu"),
        ) {
            DropdownMenuItem(
                text = { Text("Rename household") },
                onClick = { menuExpanded = false; onLongPressRename() },
                modifier = Modifier.testTag("householdMenuRename"),
            )
            DropdownMenuItem(
                text = { Text("Select areas…") },
                onClick = { menuExpanded = false; onLongPressSelectAreas() },
                modifier = Modifier.testTag("householdMenuSelectAreas"),
            )
        }
    }
}

@Composable
private fun SelectableAreaCard(
    area: Area,
    taskCount: Int,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("selectableAreaCard:${area.name}"),
        colors = CardDefaults.cardColors(
            containerColor = if (checked) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Checkbox(
                checked = checked,
                onCheckedChange = { onToggle() },
                modifier = Modifier.testTag("selectableAreaCheckbox:${area.name}"),
            )
            Column(Modifier.weight(1f)) {
                Text(area.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (taskCount == 1) "1 task" else "$taskCount tasks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Shared header for Plan and Areas tabs: error message + vacation banner. */
@Composable
private fun TabHeader(
    state: com.chore.tracker.data.HouseholdState,
    repo: Repo,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbarHost: SnackbarHostState,
) {
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
                    Icons.Default.BeachAccess, contentDescription = null,
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
}

/** Today tab content: tasks whose due date is today or earlier (date-based, not %). */
@Composable
private fun TodayList(
    state: com.chore.tracker.data.HouseholdState,
    onEditTask: (Task) -> Unit,
    onSwipeRight: (Task) -> Unit,
    onSwipeLeft: (Task) -> Unit,
) {
    val now = System.currentTimeMillis()
    val startOfTomorrow = remember(now) {
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = now
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            add(java.util.Calendar.DAY_OF_MONTH, 1)
        }
        cal.timeInMillis
    }
    // A task shows if its due date is before tomorrow AND it's either assigned
    // to the current user or unassigned. Snoozed and paused tasks are hidden
    // (paused household → empty list, banner explains why).
    val me = state.currentUserId
    val visibleTasks = remember(state.tasks, state.pausedUntil, me) {
        if (state.pausedUntil != null && state.pausedUntil > now) return@remember emptyList()
        state.tasks
            .filter { it.snoozedUntil == null || it.snoozedUntil <= now }
            .filter { task -> task.assignedTo == null || task.assignedTo == me }
            .filter { task ->
                val due = (task.lastDoneAt ?: 0L) +
                    task.frequencyDays.toLong() * 86_400_000L
                task.lastDoneAt == null || due < startOfTomorrow
            }
            .sortedByDescending { it.dirtiness(now) }
    }
    val areaNamesById = remember(state.areas) { state.areas.associate { it.id to it.name } }

    if (visibleTasks.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                if (state.pausedUntil != null && state.pausedUntil > now)
                    "Vacation mode — nothing's due."
                else "Nothing due today 🎉",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        items(visibleTasks, key = { it.id }) { task ->
            TaskRow(
                task = task,
                areaName = areaNamesById[task.areaId],
                onTap = { onEditTask(task) },
                onSwipeRight = { onSwipeRight(task) },
                onSwipeLeft = { onSwipeLeft(task) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AreaCard(
    area: Area,
    tasks: List<Task>,
    onAddTask: () -> Unit,
    onAddFromLibrary: () -> Unit,
    onEditArea: () -> Unit,
    onCopyArea: () -> Unit,
    onDeleteArea: () -> Unit,
    onEditTask: (Task) -> Unit,
    onMassDeleteTasks: (List<String>) -> Unit,
    onSwipeRightTask: (Task) -> Unit,
    onSwipeLeftTask: (Task) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateOf(setOf<String>()) }

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
                        text = { Text("Copy as…") },
                        onClick = { menuExpanded = false; onCopyArea() },
                        modifier = Modifier.testTag("areaMenuCopy:${area.name}"),
                    )
                    if (tasks.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text(if (selectionMode) "Done selecting" else "Select tasks…") },
                            onClick = {
                                menuExpanded = false
                                selectionMode = !selectionMode
                                if (!selectionMode) selectedIds.value = emptySet()
                            },
                            modifier = Modifier.testTag("areaMenuSelect:${area.name}"),
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Delete area", color = MaterialTheme.colorScheme.error) },
                        onClick = { menuExpanded = false; onDeleteArea() },
                        modifier = Modifier.testTag("areaMenuDelete:${area.name}"),
                    )
                }
            }
            // Selection bar (shown when in selection mode)
            if (selectionMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .testTag("selectionBar:${area.name}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${selectedIds.value.size} selected",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(
                        enabled = selectedIds.value.isNotEmpty(),
                        modifier = Modifier.testTag("massDeleteButton:${area.name}"),
                        onClick = {
                            onMassDeleteTasks(selectedIds.value.toList())
                            selectedIds.value = emptySet()
                            selectionMode = false
                        },
                    ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    TextButton(
                        onClick = {
                            selectedIds.value = emptySet()
                            selectionMode = false
                        },
                    ) { Text("Cancel") }
                }
            }
            if (tasks.isEmpty()) {
                Text("No tasks yet", style = MaterialTheme.typography.bodySmall)
            } else {
                tasks.sortedByDescending { it.dirtiness() }.forEach { task ->
                    if (selectionMode) {
                        SelectableTaskRow(
                            task = task,
                            checked = task.id in selectedIds.value,
                            onToggle = {
                                selectedIds.value = if (task.id in selectedIds.value)
                                    selectedIds.value - task.id
                                else selectedIds.value + task.id
                            },
                        )
                    } else {
                        TaskRow(
                            task = task,
                            onTap = { onEditTask(task) },
                            onSwipeRight = { onSwipeRightTask(task) },
                            onSwipeLeft = { onSwipeLeftTask(task) },
                        )
                    }
                }
            }
            // "+ from library" link at the bottom of each area card
            TextButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("addFromLibraryButton:${area.name}"),
                onClick = onAddFromLibrary,
            ) { Text("+ Add from library") }
        }
    }
}

@Composable
private fun SelectableTaskRow(
    task: Task,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .testTag("selectableTaskRow:${task.name}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            modifier = Modifier.testTag("selectableCheckbox:${task.name}"),
        )
        Text(task.name, modifier = Modifier.weight(1f))
        Text(
            "every ${task.frequencyDays}d",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskRow(
    task: Task,
    onTap: () -> Unit,
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit,
    areaName: String? = null,
) {
    val ratio = task.dirtiness().toFloat()
    val color = when {
        ratio >= 1.0f -> Color(0xFFD32F2F)
        ratio >= 0.66f -> Color(0xFFF57C00)
        ratio >= 0.33f -> Color(0xFFFBC02D)
        else -> Color(0xFF388E3C)
    }

    val swipeState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> { onSwipeRight(); false }
                SwipeToDismissBoxValue.EndToStart -> { onSwipeLeft(); false }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
        // Require ~30% of the row width before a swipe counts as triggered.
        positionalThreshold = { distance -> distance * 0.3f },
    )

    SwipeToDismissBox(
        state = swipeState,
        modifier = Modifier.testTag("taskRow:${task.name}"),
        backgroundContent = {
            val direction = swipeState.dismissDirection
            val bg = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Color(0xFF388E3C)
                SwipeToDismissBoxValue.EndToStart -> Color(0xFFE65100)
                else -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bg)
                    .padding(horizontal = 20.dp),
            ) {
                when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Icon(
                        Icons.Default.Check,
                        contentDescription = "Complete",
                        tint = Color.White,
                        modifier = Modifier.align(Alignment.CenterStart),
                    )
                    SwipeToDismissBoxValue.EndToStart -> Icon(
                        Icons.Default.MoreHoriz,
                        contentDescription = "Snooze or delete",
                        tint = Color.White,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                    else -> {}
                }
            }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onTap)
                .padding(vertical = 6.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(task.name)
                        if (areaName != null) {
                            Text(
                                areaName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
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
                    val statusText = when {
                        ratio > 1.0f -> {
                            val days = ((ratio - 1.0f) * task.frequencyDays).toLong()
                            if (days <= 0) "due now"
                            else "$days day${if (days == 1L) "" else "s"} overdue"
                        }
                        else -> "${(ratio * 100).toInt()}%"
                    }
                    Text(
                        "every ${task.frequencyDays}d · $statusText · $attribution",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                task.notes?.takeIf { it.isNotBlank() }?.let { n ->
                    Text(
                        "📝 $n",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("taskNotes:${task.name}"),
                    )
                }
            }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddAreaDialog(
    existingAreaNames: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    // Match the room labels from the onboarding wizard so the lists never diverge.
    val allSuggestions = remember {
        listOf(
            "Kitchen", "Bathroom", "Bedroom", "Living room", "Laundry",
            "Outdoor", "Whole home", "Pets", "Kids", "Seasonal",
            "Errands", "Vehicle", "Personal", "Financial", "Plants", "Family",
        )
    }
    val suggestions = remember(existingAreaNames, allSuggestions) {
        allSuggestions.filter { it.lowercase() !in existingAreaNames }
    }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var custom by remember { mutableStateOf("") }

    val pendingCount = selected.size + (if (custom.isNotBlank()) 1 else 0)

    AlertDialog(
        modifier = Modifier.testTag("addAreaDialog"),
        onDismissRequest = onDismiss,
        title = { Text("Add areas") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (suggestions.isNotEmpty()) {
                    Text("Pick any common rooms to add:", style = MaterialTheme.typography.bodySmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        suggestions.forEach { name ->
                            FilterChip(
                                selected = name in selected,
                                onClick = {
                                    selected = if (name in selected) selected - name else selected + name
                                },
                                label = { Text(name) },
                                modifier = Modifier.testTag("areaSuggestion:$name"),
                            )
                        }
                    }
                }
                Text("Or type a custom name:", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    custom,
                    { custom = it },
                    label = { Text("Area name") },
                    modifier = Modifier.fillMaxWidth().testTag("textDialogField"),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = pendingCount > 0,
                modifier = Modifier.testTag("textDialogConfirm"),
                onClick = {
                    val names = buildList {
                        addAll(selected)
                        if (custom.isNotBlank()) add(custom.trim())
                    }
                    onConfirm(names)
                },
            ) { Text(if (pendingCount > 0) "Add $pendingCount" else "Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Unified swipe-right modal: pick when, who, and per-completion notes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompleteTaskDialog(
    task: Task,
    members: List<Member>,
    currentUserId: String?,
    onDismiss: () -> Unit,
    onConfirm: (at: Long?, notes: String, completedBy: String?) -> Unit,
) {
    val now = remember { System.currentTimeMillis() }
    val dayMs = 86_400_000L
    val createdDay = (task.createdAt / dayMs) * dayMs
    val todayDay = (now / dayMs) * dayMs

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = todayDay,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis in createdDay..todayDay
        },
    )
    var showDatePicker by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }
    val initialMember = currentUserId?.let { id -> members.firstOrNull { it.id == id } }
        ?: members.firstOrNull()
    var selectedMember by remember { mutableStateOf<Member?>(initialMember) }
    var assigneeExpanded by remember { mutableStateOf(false) }

    val selectedDay = datePickerState.selectedDateMillis ?: todayDay
    val isToday = selectedDay == todayDay
    val dateLabel = remember(selectedDay) {
        java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(selectedDay))
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) { DatePicker(state = datePickerState) }
    }

    AlertDialog(
        modifier = Modifier.testTag("completeTaskDialog:${task.name}"),
        onDismissRequest = onDismiss,
        title = { Text("Mark \"${task.name}\" done") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Done on", style = MaterialTheme.typography.labelMedium)
                Button(
                    onClick = { showDatePicker = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("completeDateField"),
                ) { Text(dateLabel) }
                if (members.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = assigneeExpanded,
                        onExpandedChange = { assigneeExpanded = it },
                        modifier = Modifier.testTag("completedByPicker"),
                    ) {
                        OutlinedTextField(
                            value = selectedMember?.displayName ?: "Unassigned",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Completed by") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(assigneeExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = assigneeExpanded,
                            onDismissRequest = { assigneeExpanded = false },
                        ) {
                            members.forEach { member ->
                                DropdownMenuItem(
                                    text = { Text(member.displayName) },
                                    onClick = { selectedMember = member; assigneeExpanded = false },
                                    modifier = Modifier.testTag("completedByOption:${member.displayName}"),
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    notes,
                    { notes = it },
                    label = { Text("Notes for this completion (optional)") },
                    placeholder = { Text("e.g. used paper towels, ran out of cleaner") },
                    modifier = Modifier.fillMaxWidth().testTag("completionNotesField"),
                    minLines = 3,
                )
            }
        },
        confirmButton = {
            TextButton(
                modifier = Modifier.testTag("completeTaskConfirm"),
                onClick = {
                    // Pass `at` only when not today; otherwise let the server stamp `now`
                    // so we don't accidentally send a past timestamp for "today".
                    val at = if (isToday) null else selectedDay
                    onConfirm(at, notes.trim(), selectedMember?.id)
                },
            ) { Text("Complete") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Unified swipe-left modal: snooze for an arbitrary duration, or hard-delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SnoozeOrDeleteDialog(
    task: Task,
    onDismiss: () -> Unit,
    onSnooze: (until: Long) -> Unit,
    onDelete: () -> Unit,
) {
    var amount by remember { mutableStateOf("1") }
    var unit by remember { mutableStateOf(SnoozeUnit.DAYS) }
    var unitExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        modifier = Modifier.testTag("snoozeOrDeleteDialog:${task.name}"),
        onDismissRequest = onDismiss,
        title = { Text("\"${task.name}\"") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Snooze for")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { v -> amount = v.filter { it.isDigit() }.take(4) },
                        label = { Text("N") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("snoozeAmountField"),
                    )
                    ExposedDropdownMenuBox(
                        expanded = unitExpanded,
                        onExpandedChange = { unitExpanded = it },
                        modifier = Modifier.weight(2f),
                    ) {
                        OutlinedTextField(
                            value = unit.label,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Unit") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(unitExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                                .testTag("snoozeUnitField"),
                        )
                        ExposedDropdownMenu(
                            expanded = unitExpanded,
                            onDismissRequest = { unitExpanded = false },
                        ) {
                            SnoozeUnit.entries.forEach { u ->
                                DropdownMenuItem(
                                    text = { Text(u.label) },
                                    onClick = { unit = u; unitExpanded = false },
                                    modifier = Modifier.testTag("snoozeUnitOption:${u.name}"),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = (amount.toIntOrNull() ?: 0) > 0,
                modifier = Modifier.testTag("snoozeConfirm"),
                onClick = {
                    val n = amount.toIntOrNull() ?: 1
                    val cal = java.util.Calendar.getInstance()
                    when (unit) {
                        SnoozeUnit.DAYS -> cal.add(java.util.Calendar.DAY_OF_MONTH, n)
                        SnoozeUnit.WEEKS -> cal.add(java.util.Calendar.WEEK_OF_YEAR, n)
                        SnoozeUnit.MONTHS -> cal.add(java.util.Calendar.MONTH, n)
                        SnoozeUnit.YEARS -> cal.add(java.util.Calendar.YEAR, n)
                    }
                    onSnooze(cal.timeInMillis)
                },
            ) { Text("Snooze") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("deleteTaskConfirm"),
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            }
        },
    )
}

private enum class SnoozeUnit(val label: String) {
    DAYS("Days"),
    WEEKS("Weeks"),
    MONTHS("Months"),
    YEARS("Years"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskFormDialog(
    title: String,
    members: List<Member>,
    initialTask: Task? = null,
    confirmLabel: String = "Add",
    areaNameForLibrary: String? = null,
    repo: Repo? = null,
    onDismiss: () -> Unit,
    onConfirm: (name: String, frequencyDays: Int, assignedTo: String?, autoRotate: Boolean, effortPoints: Int, notes: String?) -> Unit,
) {
    var name by remember { mutableStateOf(initialTask?.name ?: "") }
    var freq by remember { mutableStateOf(initialTask?.frequencyDays?.toString() ?: "7") }
    val initialMember = initialTask?.assignedTo?.let { id -> members.firstOrNull { it.id == id } }
        ?: members.firstOrNull()
    var selectedMember by remember { mutableStateOf<Member?>(initialMember) }
    var autoRotate by remember { mutableStateOf(initialTask?.autoRotate ?: false) }
    var effortPoints by remember { mutableFloatStateOf(initialTask?.effortPoints?.toFloat() ?: 1f) }
    var notes by remember { mutableStateOf(initialTask?.notes ?: "") }
    var assigneeExpanded by remember { mutableStateOf(false) }
    var showLibrary by remember { mutableStateOf(false) }

    val testDialogTag = if (initialTask == null) "addTaskDialog" else "editTaskDialog"

    AlertDialog(
        modifier = Modifier.testTag(testDialogTag),
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (initialTask == null && repo != null) {
                    TextButton(
                        onClick = { showLibrary = true },
                        modifier = Modifier.testTag("browseLibraryButton"),
                    ) { Text("Browse template library") }
                }
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
                OutlinedTextField(
                    notes,
                    { notes = it },
                    label = { Text("Notes (optional)") },
                    placeholder = { Text("e.g. Use Method, not bleach") },
                    modifier = Modifier.fillMaxWidth().testTag("taskNotesField"),
                    minLines = 2,
                )
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
                        notes.trim().ifBlank { null },
                    )
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (showLibrary && repo != null) {
        TemplateLibraryPicker(
            repo = repo,
            preferredArea = areaNameForLibrary?.lowercase(),
            onDismiss = { showLibrary = false },
            onPick = { tmpl ->
                name = tmpl.name
                freq = tmpl.suggestedFrequencyDays.toString()
                effortPoints = tmpl.suggestedEffort.toFloat()
                showLibrary = false
            },
        )
    }
}

@Composable
private fun MultiTemplatePicker(
    repo: Repo,
    preferredArea: String,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    var templates by remember { mutableStateOf<List<com.chore.tracker.data.TaskTemplate>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    val selected = remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(Unit) {
        runCatching { repo.api.taskTemplates() }
            .onSuccess { templates = it }
            .onFailure { error = it.message }
    }

    val filtered = remember(templates, preferredArea) {
        val areaLabelToKey = mapOf("living room" to "living", "whole home" to "general")
        val normalized = areaLabelToKey[preferredArea] ?: preferredArea
        val matched = templates.filter { tmpl ->
            tmpl.suggestedArea == normalized
                || tmpl.suggestedArea in normalized
                || normalized in tmpl.suggestedArea
        }
        if (matched.isNotEmpty()) matched else templates
    }

    AlertDialog(
        modifier = Modifier.testTag("multiLibraryPicker"),
        onDismissRequest = onDismiss,
        title = { Text("Add tasks from library") },
        text = {
            Column {
                if (error != null) {
                    Text("Couldn't load: $error", color = MaterialTheme.colorScheme.error)
                } else if (templates.isEmpty()) {
                    Text("Loading…")
                } else {
                    Text(
                        "${selected.value.size} selected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(filtered, key = { it.id }) { tmpl ->
                            val isSel = tmpl.id in selected.value
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .testTag("multiTemplate:${tmpl.id}"),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                androidx.compose.material3.Checkbox(
                                    checked = isSel,
                                    onCheckedChange = {
                                        selected.value = if (isSel)
                                            selected.value - tmpl.id
                                        else selected.value + tmpl.id
                                    },
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(tmpl.name)
                                    Text(
                                        "every ${tmpl.suggestedFrequencyDays}d · effort ${tmpl.suggestedEffort}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected.value.isNotEmpty(),
                modifier = Modifier.testTag("multiLibraryConfirm"),
                onClick = { onConfirm(selected.value.toList()) },
            ) { Text("Add ${selected.value.size}") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TemplateLibraryPicker(
    repo: Repo,
    preferredArea: String?,
    onDismiss: () -> Unit,
    onPick: (com.chore.tracker.data.TaskTemplate) -> Unit,
) {
    var templates by remember { mutableStateOf<List<com.chore.tracker.data.TaskTemplate>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching { repo.api.taskTemplates() }
            .onSuccess { templates = it }
            .onFailure { error = it.message }
    }
    // If the area's name maps to a known template category (exact match or contains match),
    // show only those templates. Otherwise show everything so the user can still browse.
    val ordered = remember(templates, preferredArea) {
        if (preferredArea == null) return@remember templates
        val areaLabelToKey = mapOf(
            "living room" to "living",
            "whole home" to "general",
        )
        val normalized = areaLabelToKey[preferredArea] ?: preferredArea
        val matched = templates.filter { tmpl ->
            tmpl.suggestedArea == normalized
                || tmpl.suggestedArea in normalized
                || normalized in tmpl.suggestedArea
        }
        if (matched.isNotEmpty()) matched else templates
    }
    AlertDialog(
        modifier = Modifier.testTag("libraryPicker"),
        onDismissRequest = onDismiss,
        title = { Text("Pick a template") },
        text = {
            if (error != null) {
                Text("Couldn't load: $error", color = MaterialTheme.colorScheme.error)
            } else if (templates.isEmpty()) {
                Text("Loading…")
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(ordered, key = { it.id }) { tmpl ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .testTag("libraryTemplate:${tmpl.id}"),
                        ) {
                            TextButton(
                                onClick = { onPick(tmpl) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(tmpl.name)
                                    Text(
                                        "${tmpl.suggestedArea} · every ${tmpl.suggestedFrequencyDays}d · effort ${tmpl.suggestedEffort}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
