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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.MoreHoriz
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.chore.tracker.data.Area
import com.chore.tracker.data.CompleteRequest
import com.chore.tracker.data.CreateAreaRequest
import com.chore.tracker.data.CreateTaskRequest
import com.chore.tracker.data.CreateTodoRequest
import com.chore.tracker.data.MarkTodoDoneRequest
import com.chore.tracker.data.TodoItem
import com.chore.tracker.data.DeviceTokenRequest
import com.chore.tracker.data.Member
import com.chore.tracker.data.PatchAreaRequest
import com.chore.tracker.data.PatchHouseholdRequest
import com.chore.tracker.data.PatchTaskRequest
import com.chore.tracker.data.Repo
import com.chore.tracker.BuildConfig
import com.chore.tracker.data.DownloadResult
import com.chore.tracker.data.SnoozeRequest
import com.chore.tracker.data.StatusIndicators
import com.chore.tracker.data.Task
import com.chore.tracker.data.UpdateInfo
import com.chore.tracker.data.Updater
import com.chore.tracker.data.areaNameMatchesAllTokens
import com.chore.tracker.data.dirtiness
import com.chore.tracker.data.taskMatchesHouseholdSearch
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
    // Swipe-left opens snooze (+ delete on Household); swipe-right opens the
    // unified complete dialog.
    var snoozingTask by remember { mutableStateOf<SnoozeAction?>(null) }
    var notesCompletionTask by remember { mutableStateOf<Task?>(null) }
    var viewingNotes by remember { mutableStateOf<Task?>(null) }
    var reassigningTask by remember { mutableStateOf<Task?>(null) }
    var wizardSkipped by remember { mutableStateOf(false) }
    var inviteCode by remember { mutableStateOf<String?>(null) }
    val pullState = rememberPullToRefreshState()
    val snackbarHost = remember { SnackbarHostState() }
    // IDs the user just "deleted" but whose API call is still pending while
    // the undo snackbar is up. Filtered out of the visible task/area lists so
    // the row disappears immediately, but the actual DELETE only fires once
    // the snackbar dismisses without the Undo action being tapped.
    var pendingDeletedTaskIds by remember { mutableStateOf(setOf<String>()) }
    var pendingDeletedAreaIds by remember { mutableStateOf(setOf<String>()) }
    val deferDeleteTasks: (List<Task>) -> Unit = { tasksToDelete ->
        if (tasksToDelete.isNotEmpty()) {
            val ids = tasksToDelete.map { it.id }
            pendingDeletedTaskIds = pendingDeletedTaskIds + ids
            scope.launch {
                val message = if (tasksToDelete.size == 1) "Deleted \"${tasksToDelete[0].name}\""
                else "Deleted ${tasksToDelete.size} tasks"
                val result = snackbarHost.showSnackbar(
                    message = message,
                    actionLabel = "Undo",
                    duration = androidx.compose.material3.SnackbarDuration.Long,
                    withDismissAction = true,
                )
                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                    pendingDeletedTaskIds = pendingDeletedTaskIds - ids.toSet()
                } else {
                    var failures = 0
                    ids.forEach { id ->
                        runCatching { repo.api.deleteTask(id) }.onFailure { failures++ }
                    }
                    pendingDeletedTaskIds = pendingDeletedTaskIds - ids.toSet()
                    repo.refresh()
                    if (failures > 0) {
                        snackbarHost.showSnackbar("$failures of ${ids.size} deletes failed")
                    }
                }
            }
        }
    }
    val deferDeleteAreas: (List<Area>) -> Unit = { areasToDelete ->
        if (areasToDelete.isNotEmpty()) {
            val ids = areasToDelete.map { it.id }
            pendingDeletedAreaIds = pendingDeletedAreaIds + ids
            scope.launch {
                val message = if (areasToDelete.size == 1) "Deleted \"${areasToDelete[0].name}\""
                else "Deleted ${areasToDelete.size} areas"
                val result = snackbarHost.showSnackbar(
                    message = message,
                    actionLabel = "Undo",
                    duration = androidx.compose.material3.SnackbarDuration.Long,
                    withDismissAction = true,
                )
                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                    pendingDeletedAreaIds = pendingDeletedAreaIds - ids.toSet()
                } else {
                    var failures = 0
                    ids.forEach { id ->
                        runCatching { repo.api.deleteArea(id) }.onFailure { failures++ }
                    }
                    pendingDeletedAreaIds = pendingDeletedAreaIds - ids.toSet()
                    repo.refresh()
                    if (failures > 0) {
                        snackbarHost.showSnackbar("$failures of ${ids.size} deletes failed")
                    }
                }
            }
        }
    }
    var selectedTab by remember { mutableIntStateOf(0) }
    // Apply pending-delete filtering once. Anything mid-undo is hidden from
    // the UI but not yet removed on the server.
    // Sort areas by the per-device order (set on this device by drag-reorder).
    // Areas not in the saved order — newly-added ones, or fresh installs —
    // fall to the end in createdAt order.
    val visibleAreas = remember(state.areas, pendingDeletedAreaIds, areaOrder) {
        val orderIndex: Map<String, Int> = areaOrder
            .mapIndexed { idx, id -> id to idx }
            .toMap()
        state.areas
            .filter { it.id !in pendingDeletedAreaIds }
            .sortedWith(
                compareBy<Area>(
                    { orderIndex[it.id] ?: Int.MAX_VALUE },
                    { it.createdAt },
                ),
            )
    }
    val visibleTasks = remember(state.tasks, pendingDeletedTaskIds, pendingDeletedAreaIds) {
        state.tasks.filter {
            it.id !in pendingDeletedTaskIds && it.areaId !in pendingDeletedAreaIds
        }
    }
    val visibleState = remember(state, visibleAreas, visibleTasks) {
        state.copy(areas = visibleAreas, tasks = visibleTasks)
    }
    val statusIndicators by repo.session.statusIndicatorsFlow.collectAsState(initial = StatusIndicators())
    val autoUpdate by repo.session.autoUpdateFlow.collectAsState(initial = false)
    val collapsedAreaIds by repo.session.collapsedAreaIdsFlow.collectAsState(initial = emptySet())
    val areaOrder by repo.session.areaOrderFlow.collectAsState(initial = emptyList())
    var pendingUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateDownloading by remember { mutableStateOf(false) }

    LaunchedEffect(autoUpdate) {
        if (!autoUpdate) return@LaunchedEffect
        runCatching {
            Updater(context.applicationContext).checkForUpdate(BuildConfig.VERSION_CODE)
        }.getOrNull()?.let { pendingUpdate = it }
    }

    // First-load: pull state once when the screen composes. Pull-to-refresh
    // covers explicit refreshes; silent FCM data pushes (action=refresh) cover
    // updates from other household members.
    LaunchedEffect(Unit) { repo.refresh() }

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
                title = { Text("Dobby") },
                actions = {
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
                                state = visibleState,
                                indicators = statusIndicators,
                                onSwipeRight = { notesCompletionTask = it },
                                onSwipeLeft = { snoozingTask = SnoozeAction(it, allowDelete = false) },
                                onViewNotes = { viewingNotes = it },
                                onAddTodo = { text, isPublic, ownerId ->
                                    scope.launch {
                                        runCatching {
                                            repo.api.createTodo(
                                                CreateTodoRequest(text, isPublic, ownerId),
                                            )
                                        }
                                            .onSuccess { repo.refresh() }
                                            .onFailure { snackbarHost.showSnackbar("Couldn't add: ${it.message}") }
                                    }
                                },
                                onToggleTodoDone = { todo, done ->
                                    scope.launch {
                                        val payload = MarkTodoDoneRequest(if (done) System.currentTimeMillis() else null)
                                        runCatching { repo.api.markTodoDone(todo.id, payload) }
                                            .onSuccess { repo.refresh() }
                                            .onFailure { snackbarHost.showSnackbar("Couldn't update: ${it.message}") }
                                    }
                                },
                                onDeleteTodo = { todo ->
                                    scope.launch {
                                        runCatching { repo.api.deleteTodo(todo.id) }
                                            .onSuccess { repo.refresh() }
                                            .onFailure { snackbarHost.showSnackbar("Couldn't delete: ${it.message}") }
                                    }
                                },
                                onReassignTask = { task -> reassigningTask = task },
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
                            onLongPressInvite = {
                                scope.launch {
                                    runCatching { repo.api.createInvite() }
                                        .onSuccess { inviteCode = it.code }
                                        .onFailure { snackbarHost.showSnackbar("Invite failed: ${it.message}") }
                                }
                            },
                            onLongPressSelectAreas = {
                                selectAreasMode = true
                                selectedAreaIds = emptySet()
                            },
                            onConfirmDeleteSelected = {
                                val toDelete = state.areas.filter { it.id in selectedAreaIds }
                                selectAreasMode = false
                                selectedAreaIds = emptySet()
                                deferDeleteAreas(toDelete)
                            },
                            onCancelSelection = {
                                selectAreasMode = false
                                selectedAreaIds = emptySet()
                            },
                        )
                        if (visibleAreas.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Tap + to add your first area")
                            }
                        } else if (selectAreasMode) {
                            ReorderableAreaList(
                                areas = visibleAreas,
                                taskCount = { area -> visibleTasks.count { it.areaId == area.id } },
                                selected = selectedAreaIds,
                                onToggle = { id ->
                                    selectedAreaIds = if (id in selectedAreaIds) selectedAreaIds - id
                                    else selectedAreaIds + id
                                },
                                onCommitReorder = { newOrder ->
                                    // Per-device only — each member can lay out areas
                                    // however they like without nudging other devices.
                                    scope.launch {
                                        repo.session.setAreaOrder(newOrder.map { it.id })
                                    }
                                },
                            )
                        } else {
                            // Search bar
                            OutlinedTextField(
                                value = householdSearchQuery,
                                onValueChange = { householdSearchQuery = it },
                                placeholder = { Text("Search — name, @user, 7d, notes, yesterday") },
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
                            // Filter areas + tasks based on search
                            val rawQuery = householdSearchQuery
                            val hasQuery = rawQuery.trim().isNotEmpty()
                            val filteredAreas = if (!hasQuery) visibleAreas else visibleAreas.filter { area ->
                                val areaTaskList = visibleTasks.filter { it.areaId == area.id }
                                if (areaTaskList.isEmpty()) {
                                    areaNameMatchesAllTokens(area, rawQuery)
                                } else {
                                    areaTaskList.any { taskMatchesHouseholdSearch(it, area, rawQuery) }
                                }
                            }
                            if (filteredAreas.isEmpty()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("No matches for \"${householdSearchQuery.trim()}\"")
                                }
                            } else {
                            LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                                items(filteredAreas, key = { it.id }) { area ->
                                    val areaTasks = if (!hasQuery) visibleTasks.filter { it.areaId == area.id }
                                    else visibleTasks.filter {
                                        it.areaId == area.id && taskMatchesHouseholdSearch(it, area, rawQuery)
                                    }
                                    AreaCard(
                                        area = area,
                                        tasks = areaTasks,
                                        indicators = statusIndicators,
                                        avatarVersionByUser = state.members.associate { it.id to it.avatarVersion },
                                        collapsed = area.id in collapsedAreaIds,
                                        onToggleCollapsed = {
                                            scope.launch {
                                                repo.session.setAreaCollapsed(area.id, area.id !in collapsedAreaIds)
                                            }
                                        },
                                        onAddTask = { showAddTaskFor = area },
                                        onAddFromLibrary = { libraryForArea = area },
                                        onEditArea = { editingArea = area },
                                        onCopyArea = { copyingArea = area },
                                        onDeleteArea = { deletingArea = area },
                                        onEditTask = { task -> editingTask = task },
                                        onMassDeleteTasks = { taskIds ->
                                            val toDelete = state.tasks.filter { it.id in taskIds }
                                            deferDeleteTasks(toDelete)
                                        },
                                        onSwipeRightTask = { task -> notesCompletionTask = task },
                                        onSwipeLeftTask = { task -> snoozingTask = SnoozeAction(task, allowDelete = true) },
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
            text = { Text("This will also delete all tasks in this area. You can undo for 10 seconds.") },
            confirmButton = {
                TextButton(
                    modifier = Modifier.testTag("deleteAreaConfirm"),
                    onClick = {
                        deletingArea = null
                        deferDeleteAreas(listOf(area))
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
            onConfirm = { name, freq, assignedTo, autoRotate, effortPoints, notes, _, onDemand ->
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
                                onDemand = onDemand,
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
            areas = state.areas,
            confirmLabel = "Save",
            onDismiss = { editingTask = null },
            onConfirm = { name, freq, assignedTo, autoRotate, effortPoints, notes, areaId, onDemand ->
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
                                areaId = areaId,
                                onDemand = onDemand,
                            ),
                        )
                    }
                        .onSuccess { repo.refresh() }
                        .onFailure { snackbarHost.showSnackbar("Failed to update task: ${it.message}") }
                }
            },
        )
    }

    // ── Reassign (Today-tab tap) ─────────────────────────────────────────
    reassigningTask?.let { task ->
        ReassignTaskDialog(
            task = task,
            members = state.members,
            onDismiss = { reassigningTask = null },
            onConfirm = { newAssignee ->
                reassigningTask = null
                if (newAssignee != task.assignedTo) {
                    scope.launch {
                        runCatching {
                            repo.api.patchTask(
                                task.id,
                                PatchTaskRequest(assignedTo = newAssignee),
                            )
                        }
                            .onSuccess { repo.refresh() }
                            .onFailure { snackbarHost.showSnackbar("Reassign failed: ${it.message}") }
                    }
                }
            },
        )
    }

    // ── Snooze or delete (swipe-left) ─────────────────────────────────────────
    snoozingTask?.let { (task, allowDelete) ->
        SnoozeOrDeleteDialog(
            task = task,
            onDismiss = { snoozingTask = null },
            onSnooze = { until ->
                snoozingTask = null
                scope.launch {
                    val result = if (until <= System.currentTimeMillis()) {
                        runCatching { repo.api.unsnoozeTask(task.id) }
                    } else {
                        runCatching { repo.api.snoozeTask(task.id, SnoozeRequest(until)) }
                    }
                    result
                        .onSuccess { repo.refresh() }
                        .onFailure { snackbarHost.showSnackbar("Snooze failed: ${it.message}") }
                }
            },
            onDelete = if (allowDelete) {
                {
                    snoozingTask = null
                    deferDeleteTasks(listOf(task))
                }
            } else null,
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

    // ── View notes (read-only popup from the row's notes chip) ────────────────
    viewingNotes?.let { task ->
        AlertDialog(
            modifier = Modifier.testTag("viewNotesDialog:${task.name}"),
            onDismissRequest = { viewingNotes = null },
            title = { Text(task.name) },
            text = {
                Text(
                    task.notes ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = { viewingNotes = null }) { Text("Close") }
            },
        )
    }

    // ── Auto-update prompt (when enabled in Settings) ─────────────────────────
    pendingUpdate?.let { info ->
        AlertDialog(
            modifier = Modifier.testTag("autoUpdateDialog"),
            onDismissRequest = { pendingUpdate = null },
            title = { Text("Update available") },
            text = {
                Column {
                    Text("Version ${info.versionName} is ready to install.")
                    if (updateDownloading) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Downloading…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !updateDownloading,
                    onClick = {
                        updateDownloading = true
                        scope.launch {
                            val updater = Updater(context.applicationContext)
                            val id = updater.startDownload(info)
                            when (val r = updater.awaitDownload(id)) {
                                DownloadResult.Success -> updater.launchInstall(id)
                                is DownloadResult.Failure ->
                                    snackbarHost.showSnackbar("Update failed: ${r.reason}")
                            }
                            pendingUpdate = null
                            updateDownloading = false
                        }
                    },
                ) { Text("Update") }
            },
            dismissButton = {
                TextButton(
                    enabled = !updateDownloading,
                    onClick = { pendingUpdate = null },
                ) { Text("Later") }
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
    onLongPressInvite: () -> Unit,
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
                if (selectedCount > 0) "$selectedCount selected" else "Drag to reorder",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(
                enabled = selectedCount > 0,
                modifier = Modifier.testTag("massDeleteAreasButton"),
                onClick = onConfirmDeleteSelected,
            ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            TextButton(
                modifier = Modifier.testTag("areaEditDone"),
                onClick = onCancelSelection,
            ) { Text("Done") }
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
                text = { Text("Invite member…") },
                onClick = { menuExpanded = false; onLongPressInvite() },
                modifier = Modifier.testTag("householdMenuInvite"),
            )
            DropdownMenuItem(
                text = { Text("Edit areas") },
                onClick = { menuExpanded = false; onLongPressSelectAreas() },
                modifier = Modifier.testTag("householdMenuSelectAreas"),
            )
        }
    }
}

/** Edit-areas mode: hand-rolled drag-reorder over a non-lazy Column.
 *  Households are small (typically <20 areas) so no LazyColumn needed.
 *  Eagerly mutates the in-memory list when the dragged row crosses a row
 *  boundary, then writes the new order to per-device DataStore on drop —
 *  reordering is intentionally local so each member can lay out areas
 *  however they like. */
@Composable
private fun ReorderableAreaList(
    areas: List<Area>,
    taskCount: (Area) -> Int,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onCommitReorder: (List<Area>) -> Unit,
) {
    var working by remember { mutableStateOf(areas) }
    LaunchedEffect(areas) {
        // Re-sync when upstream list identity changes (after refresh / delete).
        if (working.map { it.id } != areas.map { it.id }) working = areas
    }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val rowHeightPx = with(LocalDensity.current) { 56.dp.toPx() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(8.dp)
            .testTag("reorderableAreaList"),
    ) {
        working.forEach { area ->
            key(area.id) {
                val isDragging = area.id == draggingId
                Box(
                    modifier = Modifier
                        .zIndex(if (isDragging) 1f else 0f)
                        .offset {
                            IntOffset(0, if (isDragging) dragOffsetY.roundToInt() else 0)
                        },
                ) {
                    SelectableAreaCard(
                        area = area,
                        taskCount = taskCount(area),
                        checked = area.id in selected,
                        onToggle = { onToggle(area.id) },
                        isDragging = isDragging,
                        onDragStart = {
                            draggingId = area.id
                            dragOffsetY = 0f
                        },
                        onDrag = { delta ->
                            dragOffsetY += delta
                            val currentIdx = working.indexOfFirst { it.id == area.id }
                            if (currentIdx == -1) return@SelectableAreaCard
                            val moves = (dragOffsetY / rowHeightPx).toInt()
                            if (moves != 0) {
                                val targetIdx = (currentIdx + moves).coerceIn(0, working.lastIndex)
                                if (targetIdx != currentIdx) {
                                    val newList = working.toMutableList().apply {
                                        add(targetIdx, removeAt(currentIdx))
                                    }
                                    working = newList
                                    dragOffsetY -= (targetIdx - currentIdx) * rowHeightPx
                                }
                            }
                        },
                        onDragEnd = {
                            val toCommit = working
                            draggingId = null
                            dragOffsetY = 0f
                            onCommitReorder(toCommit)
                        },
                        onSendToTop = {
                            val idx = working.indexOfFirst { it.id == area.id }
                            if (idx > 0) {
                                working = working.toMutableList().apply { add(0, removeAt(idx)) }
                                onCommitReorder(working)
                            }
                        },
                        onSendToBottom = {
                            val idx = working.indexOfFirst { it.id == area.id }
                            if (idx < working.lastIndex) {
                                working = working.toMutableList().apply { add(removeAt(idx)) }
                                onCommitReorder(working)
                            }
                        },
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun SelectableAreaCard(
    area: Area,
    taskCount: Int,
    checked: Boolean,
    onToggle: () -> Unit,
    isDragging: Boolean = false,
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onSendToTop: () -> Unit = {},
    onSendToBottom: () -> Unit = {},
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("selectableAreaCard:${area.name}"),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isDragging -> MaterialTheme.colorScheme.tertiaryContainer
                checked -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.DragHandle,
                contentDescription = "Drag to reorder",
                modifier = Modifier
                    .padding(end = 4.dp)
                    .testTag("areaDragHandle:${area.name}")
                    .pointerInput(area.id) {
                        detectDragGestures(
                            onDragStart = { onDragStart() },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                        ) { change, drag ->
                            change.consume()
                            onDrag(drag.y)
                        }
                    },
            )
            androidx.compose.material3.Checkbox(
                checked = checked,
                onCheckedChange = { onToggle() },
                modifier = Modifier.testTag("selectableAreaCheckbox:${area.name}"),
            )
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(area.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (taskCount == 1) "1 task" else "$taskCount tasks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onSendToTop,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    contentDescription = "Send to top",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(
                onClick = onSendToBottom,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = "Send to bottom",
                    tint = MaterialTheme.colorScheme.primary,
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
    indicators: StatusIndicators,
    onSwipeRight: (Task) -> Unit,
    onSwipeLeft: (Task) -> Unit,
    onViewNotes: (Task) -> Unit,
    onAddTodo: (text: String, isPublic: Boolean, ownerId: String?) -> Unit,
    onToggleTodoDone: (TodoItem, done: Boolean) -> Unit,
    onDeleteTodo: (TodoItem) -> Unit,
    onReassignTask: (Task) -> Unit,
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
    val areaNamesById = remember(state.areas) { state.areas.associate { it.id to it.name } }
    val avatarVersionByUser = remember(state.members) {
        state.members.associate { it.id to it.avatarVersion }
    }

    val startOfToday = startOfTomorrow - 86_400_000L

    fun isDueToday(task: Task): Boolean {
        if (task.snoozedUntil != null && task.snoozedUntil > now) return false
        if (task.onDemand) return true   // on-demand always shows for the assignee
        val due = (task.lastDoneAt ?: 0L) + task.frequencyDays.toLong() * 86_400_000L
        return task.lastDoneAt == null || due < startOfTomorrow
    }

    val onPaused = state.pausedUntil != null && state.pausedUntil > now

    val myChores = remember(state.tasks, onPaused, me) {
        if (onPaused) emptyList()
        else state.tasks
            .filter { task -> task.assignedTo == null || task.assignedTo == me }
            .filter(::isDueToday)
            .filter { task ->
                // On-demand tasks only show for the explicit assignee, not unassigned.
                if (task.onDemand) task.assignedTo == me else true
            }
            .sortedByDescending { it.dirtiness(now) }
    }
    val myTodos = remember(state.todos, me, startOfToday) {
        state.todos.filter { it.ownerId == me && (it.doneAt == null || it.doneAt >= startOfToday) }
    }
    // Per-other-member section: their chores due today + their public open todos.
    // Skip members with nothing on their plate so the page doesn't fill with empty headers.
    val otherSections = remember(state.tasks, state.todos, state.members, onPaused, me, startOfToday) {
        if (onPaused) emptyList()
        else state.members
            .filter { it.id != me }
            .mapNotNull { member ->
                val theirChores = state.tasks
                    .filter { it.assignedTo == member.id }
                    .filter(::isDueToday)
                    .sortedByDescending { it.dirtiness(now) }
                val theirTodos = state.todos.filter {
                    it.ownerId == member.id && it.isPublic &&
                        (it.doneAt == null || it.doneAt >= startOfToday)
                }
                if (theirChores.isEmpty() && theirTodos.isEmpty()) null
                else Triple(member, theirChores, theirTodos)
            }
    }

    var showAddTodo by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp).testTag("todayList"),
    ) {
        // ── Your today ───────────────────────────────────────────────────
        item("yourHeader") {
            ReminderSectionHeader(
                title = "Your today",
                onAdd = { showAddTodo = true },
            )
        }
        items(myTodos, key = { "todo-${it.id}" }) { todo ->
            TodoRow(
                todo = todo,
                showOwner = false,
                ownerName = null,
                canEdit = true,
                onToggle = { done -> onToggleTodoDone(todo, done) },
                onDelete = { onDeleteTodo(todo) },
            )
        }
        if (myChores.isEmpty() && myTodos.isEmpty()) {
            item("noMine") {
                Text(
                    if (onPaused) "Vacation mode — nothing's due."
                    else "Nothing due today 🎉  Tap + to add a reminder.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        } else {
            items(myChores, key = { "task-${it.id}" }) { task ->
                TaskRow(
                    task = task,
                    areaName = areaNamesById[task.areaId],
                    indicators = indicators,
                    // Tapping a Today row reassigns it — useful when you can't
                    // do something today and want to hand it off.
                    onTap = { onReassignTask(task) },
                    onSwipeRight = { onSwipeRight(task) },
                    onSwipeLeft = { onSwipeLeft(task) },
                    onViewNotes = { onViewNotes(task) },
                    assigneeAvatarVersion = avatarVersionByUser[task.assignedTo] ?: 0,
                )
            }
        }

        // ── Other members' today ─────────────────────────────────────────
        otherSections.forEach { (member, theirChores, theirTodos) ->
            item("memberHeader-${member.id}") {
                MemberSectionHeader(
                    member = member,
                    title = "${member.displayName}'s today",
                )
            }
            items(theirTodos, key = { "todo-${it.id}" }) { todo ->
                TodoRow(
                    todo = todo,
                    showOwner = false,
                    ownerName = member.displayName,
                    canEdit = false,
                    onToggle = {},
                    onDelete = {},
                )
            }
            items(theirChores, key = { "task-${it.id}" }) { task ->
                TaskRow(
                    task = task,
                    areaName = areaNamesById[task.areaId],
                    indicators = indicators,
                    onTap = { onReassignTask(task) },
                    onSwipeRight = { onSwipeRight(task) },
                    onSwipeLeft = { onSwipeLeft(task) },
                    onViewNotes = { onViewNotes(task) },
                    assigneeAvatarVersion = avatarVersionByUser[task.assignedTo] ?: 0,
                )
            }
        }
    }

    if (showAddTodo) {
        AddTodoDialog(
            members = state.members,
            currentUserId = state.currentUserId,
            onDismiss = { showAddTodo = false },
            onConfirm = { text, isPublic, ownerId ->
                showAddTodo = false
                onAddTodo(text, isPublic, ownerId)
            },
        )
    }
}

@Composable
private fun MemberSectionHeader(
    member: Member,
    title: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .testTag("memberSection:${member.displayName}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AvatarBadge(
            userId = member.id,
            avatarVersion = member.avatarVersion,
            fallbackText = member.displayName.take(1).uppercase(),
            size = 24,
        )
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

@Composable
private fun ReminderSectionHeader(
    title: String,
    onAdd: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        if (onAdd != null) {
            IconButton(
                modifier = Modifier.testTag("addTodoButton"),
                onClick = onAdd,
            ) { Icon(Icons.Default.Add, contentDescription = "Add reminder") }
        }
    }
}

@Composable
private fun TodoRow(
    todo: TodoItem,
    showOwner: Boolean,
    ownerName: String?,
    canEdit: Boolean,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val done = todo.doneAt != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .testTag("todoRow:${todo.text}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Checkbox(
            checked = done,
            onCheckedChange = if (canEdit) { v -> onToggle(v) } else null,
            enabled = canEdit,
            modifier = Modifier.testTag("todoCheckbox:${todo.text}"),
        )
        Column(Modifier.weight(1f)) {
            Text(
                todo.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (done) androidx.compose.ui.text.style.TextDecoration.LineThrough
                else null,
            )
            if (showOwner && ownerName != null) {
                Text(
                    ownerName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!todo.isPublic && canEdit) {
            Icon(
                Icons.Default.Lock,
                contentDescription = "Private",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
        if (canEdit) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("todoDelete:${todo.text}"),
            ) { Icon(Icons.Default.Clear, contentDescription = "Delete") }
        }
    }
}

/** Compact picker for reassigning a chore to another household member.
 *  Tapping a Today-tab task row opens this — it's the "I can't do this today,
 *  you do it" affordance. The new assignee gets a push notification when the
 *  chore is due today. */
@Composable
private fun ReassignTaskDialog(
    task: Task,
    members: List<Member>,
    onDismiss: () -> Unit,
    onConfirm: (newAssignee: String?) -> Unit,
) {
    AlertDialog(
        modifier = Modifier.testTag("reassignTaskDialog:${task.name}"),
        onDismissRequest = onDismiss,
        title = { Text("Assign \"${task.name}\"") },
        text = {
            Column {
                members.forEach { member ->
                    val selected = member.id == task.assignedTo
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onConfirm(member.id) }
                            .padding(vertical = 10.dp, horizontal = 4.dp)
                            .testTag("reassignOption:${member.displayName}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AvatarBadge(
                            userId = member.id,
                            avatarVersion = member.avatarVersion,
                            fallbackText = member.displayName.take(1).uppercase(),
                            size = 28,
                        )
                        Spacer(Modifier.size(12.dp))
                        Text(
                            member.displayName,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                        if (selected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Currently assigned",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTodoDialog(
    members: List<Member>,
    currentUserId: String?,
    onDismiss: () -> Unit,
    onConfirm: (text: String, isPublic: Boolean, ownerId: String?) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(false) }
    val initialOwner = members.firstOrNull { it.id == currentUserId } ?: members.firstOrNull()
    var owner by remember { mutableStateOf(initialOwner) }
    var ownerExpanded by remember { mutableStateOf(false) }
    val canPickOwner = members.size > 1
    AlertDialog(
        modifier = Modifier.testTag("addTodoDialog"),
        onDismissRequest = onDismiss,
        title = { Text("Add a reminder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("What do you want to do?") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("todoTextField"),
                )
                if (canPickOwner) {
                    ExposedDropdownMenuBox(
                        expanded = ownerExpanded,
                        onExpandedChange = { ownerExpanded = it },
                        modifier = Modifier.testTag("todoOwnerPicker"),
                    ) {
                        OutlinedTextField(
                            value = if (owner?.id == currentUserId) "Me" else owner?.displayName.orEmpty(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Assign to") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(ownerExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = ownerExpanded,
                            onDismissRequest = { ownerExpanded = false },
                        ) {
                            members.forEach { member ->
                                DropdownMenuItem(
                                    text = {
                                        Text(if (member.id == currentUserId) "Me" else member.displayName)
                                    },
                                    onClick = { owner = member; ownerExpanded = false },
                                    modifier = Modifier.testTag("todoOwnerOption:${member.displayName}"),
                                )
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Public", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Visible to other household members",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = isPublic,
                        onCheckedChange = { isPublic = it },
                        modifier = Modifier.testTag("todoPublicToggle"),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                modifier = Modifier.testTag("todoConfirm"),
                onClick = {
                    val ownerIdForOther = owner?.id?.takeIf { it != currentUserId }
                    onConfirm(text.trim(), isPublic, ownerIdForOther)
                },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AreaCard(
    area: Area,
    tasks: List<Task>,
    indicators: StatusIndicators,
    collapsed: Boolean,
    onToggleCollapsed: () -> Unit,
    onAddTask: () -> Unit,
    onAddFromLibrary: () -> Unit,
    onEditArea: () -> Unit,
    onCopyArea: () -> Unit,
    onDeleteArea: () -> Unit,
    onEditTask: (Task) -> Unit,
    onMassDeleteTasks: (List<String>) -> Unit,
    onSwipeRightTask: (Task) -> Unit,
    onSwipeLeftTask: (Task) -> Unit,
    avatarVersionByUser: Map<String, Int> = emptyMap(),
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
                            onClick = onToggleCollapsed,
                            onLongClick = { menuExpanded = true },
                        )
                        .testTag("areaHeader:${area.name}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (collapsed) Icons.Default.ChevronRight else Icons.Default.ExpandMore,
                        contentDescription = if (collapsed) "Expand" else "Collapse",
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .testTag("areaCollapseChevron:${area.name}"),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(area.name, style = MaterialTheme.typography.titleMedium)
                        if (collapsed && tasks.isNotEmpty()) {
                            Text(
                                if (tasks.size == 1) "1 task" else "${tasks.size} tasks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
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
            if (!collapsed) {
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
                                indicators = indicators,
                                onTap = { onEditTask(task) },
                                onSwipeRight = { onSwipeRightTask(task) },
                                onSwipeLeft = { onSwipeLeftTask(task) },
                                assigneeAvatarVersion = avatarVersionByUser[task.assignedTo] ?: 0,
                            )
                            Spacer(Modifier.height(6.dp))
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TaskRow(
    task: Task,
    onTap: (() -> Unit)?,
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit,
    indicators: StatusIndicators = StatusIndicators(),
    onViewNotes: (() -> Unit)? = null,
    areaName: String? = null,
    assigneeAvatarVersion: Int = 0,
) {
    val now = System.currentTimeMillis()
    val freqMs = task.frequencyDays.toLong() * 86_400_000L
    val due = (task.lastDoneAt ?: 0L) + freqMs
    val dayMs = 86_400_000L
    val startOfToday = (now / dayMs) * dayMs
    val startOfTomorrow = startOfToday + dayMs

    val isOverdue = task.lastDoneAt == null || due < startOfToday
    val isDueToday = !isOverdue && due < startOfTomorrow
    val defaultColor = when {
        isOverdue -> Color(0xFFD32F2F)
        isDueToday -> Color(0xFFFBC02D)
        else -> Color(0xFF388E3C)
    }
    val customHex = when {
        isOverdue -> indicators.overdueColor
        isDueToday -> indicators.dueTodayColor
        else -> indicators.notDueColor
    }
    val statusColor = customHex.parseHexOrNull() ?: defaultColor
    val statusOverride = when {
        isOverdue -> indicators.overdue
        isDueToday -> indicators.dueToday
        else -> indicators.notDue
    }.takeIf { it.isNotBlank() }

    val swipeState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> { onSwipeRight(); false }
                SwipeToDismissBoxValue.EndToStart -> { onSwipeLeft(); false }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
        positionalThreshold = { distance -> distance * 0.3f },
    )
    val rowShape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    val snoozed = task.snoozedUntil != null && task.snoozedUntil > now

    SwipeToDismissBox(
        state = swipeState,
        modifier = Modifier
            .padding(vertical = 3.dp)
            .testTag("taskRow:${task.name}"),
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
                    .background(bg, rowShape)
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
        androidx.compose.material3.Surface(
            shape = rowShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .then(if (onTap != null) Modifier.clickable(onClick = onTap) else Modifier)
                    .padding(vertical = 10.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Status indicator: on-demand rotation icon, user-chosen
                // emoji/text override, or the default colored dot.
                if (task.onDemand) {
                    Icon(
                        Icons.Default.Loop,
                        contentDescription = "On demand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(16.dp)
                            .testTag("statusDot:${task.name}"),
                    )
                } else if (statusOverride != null) {
                    Text(
                        statusOverride,
                        modifier = Modifier.testTag("statusDot:${task.name}"),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(statusColor, CircleShape)
                            .testTag("statusDot:${task.name}"),
                    )
                }
                Spacer(Modifier.size(12.dp))
                // Title + tag row
                Column(Modifier.weight(1f)) {
                    Text(task.name, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.size(4.dp))
                    if (snoozed) {
                        val days = ((task.snoozedUntil!! - now) / dayMs).coerceAtLeast(0)
                        Text(
                            "snoozed · $days day${if (days == 1L) "" else "s"} left",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            areaName?.let { TagChip(it) }
                            if (task.onDemand) TagChip("on demand")
                            else TagChip("${task.frequencyDays}d")
                            task.assignedTo?.let { id ->
                                AvatarBadge(
                                    userId = id,
                                    avatarVersion = assigneeAvatarVersion,
                                    fallbackText = task.assignedToName?.take(1)?.uppercase().orEmpty().ifBlank { "?" },
                                    size = 20,
                                )
                            }
                            if (task.effortPoints > 1) {
                                TagChip("E:${task.effortPoints}")
                            }
                            if (!task.notes.isNullOrBlank()) {
                                TagChip(
                                    text = "notes",
                                    leadingIcon = Icons.AutoMirrored.Filled.Notes,
                                    testTag = "taskNotes:${task.name}",
                                    onClick = onViewNotes,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.size(8.dp))
                // Right column: last-done time + by
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatLastDone(task.lastDoneAt, now),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    task.lastDoneBy?.let { by ->
                        Text(
                            "by $by",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TagChip(
    text: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    testTag: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
    ) {
        if (leadingIcon != null) {
            Icon(
                leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.size(3.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

private fun formatLastDone(lastDoneAt: Long?, now: Long): String {
    if (lastDoneAt == null) return "N/A"
    val deltaDays = ((now - lastDoneAt) / 86_400_000L).coerceAtLeast(0)
    return when (deltaDays) {
        0L -> "today"
        1L -> "yesterday"
        in 2L..29L -> "${deltaDays}d ago"
        in 30L..364L -> "${deltaDays / 30}mo ago"
        else -> "${deltaDays / 365}y ago"
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
    // The Material3 DatePicker speaks UTC-midnight millis. To stay in sync we
    // compute "today" as UTC-midnight of the user's local date and format
    // labels in UTC — otherwise a negative-offset timezone shifts the visible
    // date back by one (today: 4/28 → labelled 4/27, etc.).
    val todayDay = remember(now) {
        val local = java.util.Calendar.getInstance()
        local.timeInMillis = now
        val y = local.get(java.util.Calendar.YEAR)
        val m = local.get(java.util.Calendar.MONTH)
        val d = local.get(java.util.Calendar.DAY_OF_MONTH)
        val utc = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        utc.clear(); utc.set(y, m, d)
        utc.timeInMillis
    }
    // Allow backdating up to a year.
    val earliestDay = todayDay - 365L * dayMs

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = todayDay,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis in earliestDay..todayDay
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
        val df = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
        df.timeZone = java.util.TimeZone.getTimeZone("UTC")
        df.format(java.util.Date(selectedDay))
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
                    // For "today" let the server stamp `now`. For backdated days,
                    // map UTC-midnight-of-picked-date back to local noon of the
                    // same calendar day — this keeps last_done_at on the day the
                    // user actually picked regardless of timezone offset.
                    val at = if (isToday) null else run {
                        val utc = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                        utc.timeInMillis = selectedDay
                        val y = utc.get(java.util.Calendar.YEAR)
                        val m = utc.get(java.util.Calendar.MONTH)
                        val d = utc.get(java.util.Calendar.DAY_OF_MONTH)
                        val local = java.util.Calendar.getInstance()
                        local.clear(); local.set(y, m, d, 12, 0, 0)
                        local.timeInMillis
                    }
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
    onDelete: (() -> Unit)?,
) {
    val now = System.currentTimeMillis()
    val isSnoozed = task.snoozedUntil != null && task.snoozedUntil > now
    var amount by remember { mutableStateOf("1") }
    var unit by remember { mutableStateOf(SnoozeUnit.DAYS) }
    var unitExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        modifier = Modifier.testTag("snoozeOrDeleteDialog:${task.name}"),
        onDismissRequest = onDismiss,
        title = { Text("\"${task.name}\"") },
        text = {
            if (isSnoozed) {
                val daysLeft = ((task.snoozedUntil!! - now) / 86_400_000L).coerceAtLeast(0)
                Text(
                    "Snoozed for $daysLeft day${if (daysLeft == 1L) "" else "s"} more. " +
                        "Tap unsnooze to bring it back now.",
                )
            } else {
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
            }
        },
        confirmButton = {
            if (isSnoozed) {
                TextButton(
                    modifier = Modifier.testTag("unsnoozeConfirm"),
                    // Snoozing to a past timestamp clears the active snooze:
                    // the server stores it but `snoozedUntil > now` is false,
                    // so the task immediately reappears.
                    onClick = { onSnooze(0L) },
                ) { Text("Unsnooze") }
            } else {
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
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("deleteTaskConfirm"),
                    ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
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

private data class SnoozeAction(val task: Task, val allowDelete: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskFormDialog(
    title: String,
    members: List<Member>,
    initialTask: Task? = null,
    confirmLabel: String = "Add",
    areaNameForLibrary: String? = null,
    repo: Repo? = null,
    areas: List<Area> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (name: String, frequencyDays: Int, assignedTo: String?, autoRotate: Boolean, effortPoints: Int, notes: String?, areaId: String?, onDemand: Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(initialTask?.name ?: "") }
    var freq by remember { mutableStateOf(initialTask?.frequencyDays?.toString() ?: "7") }
    // For an existing task, "no assigneeId" really means Unassigned — don't
    // fall back to the first member or we'll silently re-assign on edit. New
    // tasks default to the first member as a friendlier "assigned to me".
    val initialMember = if (initialTask != null) {
        initialTask.assignedTo?.let { id -> members.firstOrNull { it.id == id } }
    } else {
        members.firstOrNull()
    }
    var selectedMember by remember { mutableStateOf<Member?>(initialMember) }
    // Auto-rotate doesn't make sense without an assignee — without one, there's
    // no current holder to rotate from. Force off + disable the toggle when
    // unassigned.
    var autoRotate by remember { mutableStateOf(initialTask?.autoRotate ?: false) }
    if (selectedMember == null && autoRotate) autoRotate = false
    var onDemand by remember { mutableStateOf(initialTask?.onDemand ?: false) }
    var effortPoints by remember { mutableFloatStateOf(initialTask?.effortPoints?.toFloat() ?: 1f) }
    var notes by remember { mutableStateOf(initialTask?.notes ?: "") }
    val initialArea = initialTask?.let { t -> areas.firstOrNull { it.id == t.areaId } }
    var selectedArea by remember { mutableStateOf<Area?>(initialArea) }
    var assigneeExpanded by remember { mutableStateOf(false) }
    var areaExpanded by remember { mutableStateOf(false) }
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
                // Area picker only shown when editing — new tasks pick area
                // implicitly via the area card's "+ Add task" button.
                if (initialTask != null && areas.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = areaExpanded,
                        onExpandedChange = { areaExpanded = it },
                        modifier = Modifier.testTag("areaPicker"),
                    ) {
                        OutlinedTextField(
                            value = selectedArea?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Area") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(areaExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = areaExpanded,
                            onDismissRequest = { areaExpanded = false },
                        ) {
                            areas.forEach { area ->
                                DropdownMenuItem(
                                    text = { Text(area.name) },
                                    onClick = { selectedArea = area; areaExpanded = false },
                                    modifier = Modifier.testTag("areaOption:${area.name}"),
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("On demand")
                        Text(
                            "No schedule — rotates when completed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = onDemand,
                        onCheckedChange = { onDemand = it },
                        modifier = Modifier.testTag("onDemandToggle"),
                    )
                }
                if (!onDemand) {
                    OutlinedTextField(
                        freq,
                        { freq = it.filter { c -> c.isDigit() } },
                        label = { Text("Every N days") },
                        modifier = Modifier.testTag("taskFreqField"),
                    )
                }
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
                        val rotateEnabled = selectedMember != null
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Auto-rotate",
                                color = if (rotateEnabled) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            )
                            if (!rotateEnabled) {
                                Text(
                                    "Assign to a member to rotate",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Switch(
                            checked = autoRotate,
                            onCheckedChange = { autoRotate = it },
                            enabled = rotateEnabled,
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
                enabled = name.isNotBlank() && (onDemand || (freq.toIntOrNull() ?: 0) > 0),
                modifier = Modifier.testTag("addTaskConfirm"),
                onClick = {
                    onConfirm(
                        name.trim(),
                        if (onDemand) (freq.toIntOrNull() ?: 1) else freq.toInt(),
                        selectedMember?.id,
                        autoRotate,
                        effortPoints.roundToInt(),
                        notes.trim().ifBlank { null },
                        selectedArea?.id?.takeIf { it != initialTask?.areaId },
                        onDemand,
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
