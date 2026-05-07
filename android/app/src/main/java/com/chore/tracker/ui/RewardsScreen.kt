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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.chore.tracker.data.CreateRewardRequest
import com.chore.tracker.data.EffortTotalEntry
import com.chore.tracker.data.HouseholdRewardSelected
import com.chore.tracker.data.HouseholdRewardState
import com.chore.tracker.data.HouseholdState
import com.chore.tracker.data.PatchRewardRequest
import com.chore.tracker.data.PersonalPoints
import com.chore.tracker.data.Repo
import com.chore.tracker.data.Reward
import com.chore.tracker.data.SelectHouseholdRewardRequest
import kotlinx.coroutines.launch

private enum class RewardSubTab { HOUSEHOLD, PERSONAL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardsScreen(
    state: HouseholdState,
    repo: Repo,
    snackbarHost: SnackbarHostState,
    onOpenRps: () -> Unit = {},
) {
    var subTab by remember { mutableStateOf(RewardSubTab.HOUSEHOLD) }

    Column(modifier = Modifier.fillMaxSize().testTag("rewardsScreen")) {
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            SegmentedButton(
                selected = subTab == RewardSubTab.HOUSEHOLD,
                onClick = { subTab = RewardSubTab.HOUSEHOLD },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
                modifier = Modifier.testTag("rewardsTab:household"),
            ) { Text("🏠 Household") }
            SegmentedButton(
                selected = subTab == RewardSubTab.PERSONAL,
                onClick = { subTab = RewardSubTab.PERSONAL },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
                modifier = Modifier.testTag("rewardsTab:personal"),
            ) { Text("👤 Personal") }
        }
        Spacer(Modifier.height(8.dp))

        when (subTab) {
            RewardSubTab.HOUSEHOLD -> HouseholdRewardsTab(
                state = state, repo = repo, snackbarHost = snackbarHost, onOpenRps = onOpenRps,
            )
            RewardSubTab.PERSONAL -> PersonalRewardsTab(
                state = state, repo = repo, snackbarHost = snackbarHost,
            )
        }
    }
}

// ─── HOUSEHOLD TAB ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HouseholdRewardsTab(
    state: HouseholdState,
    repo: Repo,
    snackbarHost: SnackbarHostState,
    onOpenRps: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val rewardState = state.rewardState
    val selected = rewardState.selectedReward
    val activeRewards = remember(state.rewards) { state.rewards.filter { it.isActive } }
    val nextPickerName = state.members.firstOrNull { it.id == rewardState.nextPickerId }?.displayName

    var showAddDialog by remember { mutableStateOf(false) }
    var editingReward by remember { mutableStateOf<Reward?>(null) }
    var showSelectDialog by remember { mutableStateOf(false) }

    val canPick = rewardState.nextPickerId == null || rewardState.nextPickerId == state.currentUserId

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        item("currentRound") {
            CurrentRoundCard(
                rewardState = rewardState,
                selected = selected,
                nextPickerName = nextPickerName,
                canPick = canPick,
                onPickReward = { showSelectDialog = true },
                onClaim = {
                    scope.launch {
                        runCatching { repo.api.claimHouseholdReward() }
                            .onSuccess {
                                repo.refreshRewardsAndRps()
                                snackbarHost.showSnackbar("🎉 Reward claimed! Round ${rewardState.roundNumber} won.")
                            }
                            .onFailure { snackbarHost.showSnackbar("Claim failed: ${it.message}") }
                    }
                },
                onPlayRps = onOpenRps,
            )
            Spacer(Modifier.height(16.dp))
        }

        if (state.effortTotals.size > 1) {
            item("breakdownHeader") {
                Text(
                    "🏅 Household effort (all time)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
            }
            items(state.effortTotals, key = { "effort-${it.userId}" }) { entry ->
                val member = state.members.firstOrNull { it.id == entry.userId }
                HouseholdEffortRow(entry = entry, member = member)
            }
            item("breakdownDivider") {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
            }
        }

        item("rewardsHeader") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "🎁 Household reward wishlist",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.testTag("addRewardButton"),
                ) { Icon(Icons.Default.Add, contentDescription = "Add household reward") }
            }
            Text(
                "Any household member can add or edit. Pick one above to make it the round goal.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }

        if (activeRewards.isEmpty()) {
            item("noRewards") {
                Text(
                    "No household rewards yet — add one to start earning toward shared goals.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
        } else {
            items(activeRewards, key = { "h-reward-${it.id}" }) { reward ->
                HouseholdRewardRow(
                    reward = reward,
                    isSelected = reward.id == rewardState.selectedRewardId,
                    onSelect = {
                        if (!canPick) return@HouseholdRewardRow
                        scope.launch {
                            runCatching {
                                repo.api.selectHouseholdReward(SelectHouseholdRewardRequest(reward.id))
                            }
                                .onSuccess { repo.refreshRewardsAndRps() }
                                .onFailure { snackbarHost.showSnackbar("Select failed: ${it.message}") }
                        }
                    },
                    onEdit = { editingReward = reward },
                    onDelete = {
                        scope.launch {
                            runCatching { repo.api.deleteReward(reward.id) }
                                .onSuccess { repo.refreshRewardsAndRps() }
                                .onFailure { snackbarHost.showSnackbar("Delete failed: ${it.message}") }
                        }
                    },
                )
            }
        }

        item("bottomSpacer") { Spacer(Modifier.height(24.dp)) }
    }

    if (showAddDialog) {
        RewardDialog(
            title = "Add household reward",
            initialEmoji = "🏆",
            initialName = "",
            initialCost = 100,
            confirmLabel = "Add",
            onDismiss = { showAddDialog = false },
            onConfirm = { emoji, name, cost ->
                showAddDialog = false
                scope.launch {
                    runCatching {
                        repo.api.createReward(CreateRewardRequest(name, emoji, cost, scope = "household"))
                    }
                        .onSuccess { repo.refreshRewardsAndRps() }
                        .onFailure { snackbarHost.showSnackbar("Failed: ${it.message}") }
                }
            },
        )
    }

    editingReward?.let { reward ->
        RewardDialog(
            title = "Edit household reward",
            initialEmoji = reward.emoji,
            initialName = reward.name,
            initialCost = reward.effortCost,
            confirmLabel = "Save",
            onDismiss = { editingReward = null },
            onConfirm = { emoji, name, cost ->
                editingReward = null
                scope.launch {
                    runCatching {
                        repo.api.patchReward(reward.id, PatchRewardRequest(name, emoji, cost))
                    }
                        .onSuccess { repo.refreshRewardsAndRps() }
                        .onFailure { snackbarHost.showSnackbar("Failed: ${it.message}") }
                }
            },
        )
    }

    if (showSelectDialog) {
        SelectHouseholdRewardDialog(
            rewards = activeRewards,
            currentSelectedId = rewardState.selectedRewardId,
            onDismiss = { showSelectDialog = false },
            onClear = {
                showSelectDialog = false
                scope.launch {
                    runCatching { repo.api.selectHouseholdReward(SelectHouseholdRewardRequest(null)) }
                        .onSuccess { repo.refreshRewardsAndRps() }
                        .onFailure { snackbarHost.showSnackbar("Failed: ${it.message}") }
                }
            },
            onPick = { reward ->
                showSelectDialog = false
                scope.launch {
                    runCatching {
                        repo.api.selectHouseholdReward(SelectHouseholdRewardRequest(reward.id))
                    }
                        .onSuccess { repo.refreshRewardsAndRps() }
                        .onFailure { snackbarHost.showSnackbar("Failed: ${it.message}") }
                }
            },
        )
    }
}

@Composable
private fun CurrentRoundCard(
    rewardState: HouseholdRewardState,
    selected: HouseholdRewardSelected?,
    nextPickerName: String?,
    canPick: Boolean,
    onPickReward: () -> Unit,
    onClaim: () -> Unit,
    onPlayRps: () -> Unit,
) {
    val progress = if (selected != null && selected.effortCost > 0) {
        (rewardState.roundPoints.toFloat() / selected.effortCost.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val canClaim = selected != null && rewardState.roundPoints >= selected.effortCost

    Spacer(Modifier.height(8.dp))
    Card(
        modifier = Modifier.fillMaxWidth().testTag("currentRoundCard"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🏆", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Round ${rewardState.roundNumber}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        "⚡ ${rewardState.roundPoints} pts this round  ·  ${rewardState.householdEarned} all-time",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
                IconButton(
                    onClick = onPlayRps,
                    modifier = Modifier.testTag("openRpsButton"),
                ) {
                    Icon(
                        Icons.Default.Casino,
                        contentDescription = "Play rock-paper-scissors",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (selected == null) {
                Text(
                    "🎯 No reward selected yet",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "Points will pool as they're earned. Pick one to set the goal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
                if (rewardState.nextPickerId != null && !canPick) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "🎲 ${nextPickerName ?: "Someone"} won the last RPS — they pick this round's reward.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onPickReward,
                        enabled = canPick,
                        modifier = Modifier.testTag("pickRewardButton"),
                    ) { Text(if (canPick) "Pick reward" else "Waiting on picker") }
                    OutlinedButton(
                        onClick = onPlayRps,
                        modifier = Modifier.testTag("playRpsCtaButton"),
                    ) { Text("🪨📄✂️") }
                }
            } else {
                Text(
                    "🎯 ${selected.emoji} ${selected.name}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(12.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    strokeCap = StrokeCap.Round,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "${rewardState.roundPoints} pts",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                    Text(
                        "${selected.effortCost} pts goal",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onClaim,
                        enabled = canClaim,
                        modifier = Modifier.testTag("claimRewardButton"),
                    ) { Text(if (canClaim) "🎉 Claim reward!" else "Keep earning") }
                    OutlinedButton(
                        onClick = onPickReward,
                        enabled = canPick,
                        modifier = Modifier.testTag("changeRewardButton"),
                    ) { Text("Change") }
                }
            }
        }
    }
}

@Composable
private fun HouseholdRewardRow(
    reward: Reward,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onSelect() }
            .testTag("householdReward:${reward.name}"),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.tertiaryContainer
            else
                MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(reward.emoji, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    reward.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    if (isSelected) "🎯 Active goal · ${reward.effortCost} pts"
                    else "${reward.effortCost} pts · tap to set as goal",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected)
                        MaterialTheme.colorScheme.onTertiaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit, modifier = Modifier.testTag("editReward:${reward.name}")) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.testTag("deleteReward:${reward.name}")) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun SelectHouseholdRewardDialog(
    rewards: List<Reward>,
    currentSelectedId: String?,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onPick: (Reward) -> Unit,
) {
    AlertDialog(
        modifier = Modifier.testTag("selectHouseholdRewardDialog"),
        onDismissRequest = onDismiss,
        title = { Text("Pick household reward") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (rewards.isEmpty()) {
                    Text("Add a reward first to set as the round goal.")
                } else {
                    rewards.forEach { reward ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (reward.id == currentSelectedId)
                                MaterialTheme.colorScheme.tertiaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(reward) }
                                .testTag("selectReward:${reward.name}"),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(reward.emoji, style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(reward.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "${reward.effortCost} pts",
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
            TextButton(onClick = onClear) { Text("Clear selection") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// ─── PERSONAL TAB ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonalRewardsTab(
    state: HouseholdState,
    repo: Repo,
    snackbarHost: SnackbarHostState,
) {
    val scope = rememberCoroutineScope()
    val points = state.personalPoints
    val activeRewards = remember(state.personalRewards) { state.personalRewards.filter { it.isActive } }
    val nextReward = remember(activeRewards, points.available) {
        activeRewards.filter { it.effortCost > points.available }.minByOrNull { it.effortCost }
    }
    val progress = if (nextReward != null && nextReward.effortCost > 0) {
        (points.available.toFloat() / nextReward.effortCost.toFloat()).coerceIn(0f, 1f)
    } else if (activeRewards.isNotEmpty()) 1f else 0f

    var showAddDialog by remember { mutableStateOf(false) }
    var editingReward by remember { mutableStateOf<Reward?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        item("personalHeader") {
            Spacer(Modifier.height(8.dp))
            PersonalPointsCard(points = points, nextReward = nextReward, progress = progress)
            Spacer(Modifier.height(16.dp))
        }

        item("personalRewardsHeader") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "🎁 My personal rewards",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.testTag("addPersonalRewardButton"),
                ) { Icon(Icons.Default.Add, contentDescription = "Add personal reward") }
            }
            Text(
                "Only you can see and redeem these. They draw from your personal point balance.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }

        if (activeRewards.isEmpty()) {
            item("noPersonalRewards") {
                Text(
                    "Add a personal reward to spend your own points on.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
        } else {
            items(activeRewards, key = { "p-reward-${it.id}" }) { reward ->
                val canRedeem = points.available >= reward.effortCost
                PersonalRewardRow(
                    reward = reward,
                    canRedeem = canRedeem,
                    available = points.available,
                    onRedeem = {
                        scope.launch {
                            runCatching { repo.api.redeemPersonalReward(reward.id) }
                                .onSuccess {
                                    repo.refreshRewardsAndRps()
                                    snackbarHost.showSnackbar("Redeemed: ${reward.emoji} ${reward.name}")
                                }
                                .onFailure { snackbarHost.showSnackbar("Redeem failed: ${it.message}") }
                        }
                    },
                    onEdit = { editingReward = reward },
                    onDelete = {
                        scope.launch {
                            runCatching { repo.api.deleteReward(reward.id) }
                                .onSuccess { repo.refreshRewardsAndRps() }
                                .onFailure { snackbarHost.showSnackbar("Delete failed: ${it.message}") }
                        }
                    },
                )
            }
        }

        item("bottomSpacer") { Spacer(Modifier.height(24.dp)) }
    }

    if (showAddDialog) {
        RewardDialog(
            title = "Add personal reward",
            initialEmoji = "🎁",
            initialName = "",
            initialCost = 50,
            confirmLabel = "Add",
            onDismiss = { showAddDialog = false },
            onConfirm = { emoji, name, cost ->
                showAddDialog = false
                scope.launch {
                    runCatching {
                        repo.api.createReward(CreateRewardRequest(name, emoji, cost, scope = "personal"))
                    }
                        .onSuccess { repo.refreshRewardsAndRps() }
                        .onFailure { snackbarHost.showSnackbar("Failed: ${it.message}") }
                }
            },
        )
    }

    editingReward?.let { reward ->
        RewardDialog(
            title = "Edit personal reward",
            initialEmoji = reward.emoji,
            initialName = reward.name,
            initialCost = reward.effortCost,
            confirmLabel = "Save",
            onDismiss = { editingReward = null },
            onConfirm = { emoji, name, cost ->
                editingReward = null
                scope.launch {
                    runCatching {
                        repo.api.patchReward(reward.id, PatchRewardRequest(name, emoji, cost))
                    }
                        .onSuccess { repo.refreshRewardsAndRps() }
                        .onFailure { snackbarHost.showSnackbar("Failed: ${it.message}") }
                }
            },
        )
    }
}

@Composable
private fun PersonalPointsCard(
    points: PersonalPoints,
    nextReward: Reward?,
    progress: Float,
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("personalPointsCard"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("👤", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "My personal balance",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        "⚡ ${points.available} pts available  ·  ${points.earned} earned · ${points.redeemed} spent",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (nextReward != null) {
                Text(
                    "Next: ${nextReward.emoji} ${nextReward.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(12.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    strokeCap = StrokeCap.Round,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "${points.available} pts",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                    )
                    Text(
                        "${nextReward.effortCost} pts needed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonalRewardRow(
    reward: Reward,
    canRedeem: Boolean,
    available: Long,
    onRedeem: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("personalReward:${reward.name}"),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (canRedeem)
                MaterialTheme.colorScheme.tertiaryContainer
            else
                MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(reward.emoji, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    reward.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                val remaining = (reward.effortCost - available).coerceAtLeast(0)
                Text(
                    if (canRedeem) "💸 ${reward.effortCost} pts — tap redeem"
                    else "🔒 $remaining more pts needed (${reward.effortCost} total)",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (canRedeem)
                        MaterialTheme.colorScheme.onTertiaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (canRedeem) {
                FilledTonalButton(
                    onClick = onRedeem,
                    modifier = Modifier.testTag("redeemReward:${reward.name}"),
                ) { Text("Redeem") }
                Spacer(Modifier.width(4.dp))
            }
            IconButton(onClick = onEdit, modifier = Modifier.testTag("editPersonalReward:${reward.name}")) {
                Icon(
                    Icons.Default.Edit, contentDescription = "Edit",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.testTag("deletePersonalReward:${reward.name}")) {
                Icon(
                    Icons.Default.Delete, contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

// ─── SHARED HELPERS ──────────────────────────────────────────────────────────

@Composable
private fun HouseholdEffortRow(
    entry: EffortTotalEntry,
    member: com.chore.tracker.data.Member?,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val ringColor = member?.profileColor?.let { hex ->
            runCatching {
                val s = hex.removePrefix("#")
                if (s.length == 6) Color(android.graphics.Color.parseColor("#$s")) else null
            }.getOrNull()
        }
        AvatarBadge(
            userId = member?.id,
            avatarVersion = member?.avatarVersion ?: 0,
            fallbackText = entry.displayName.take(1).uppercase(),
            size = 32,
            ringColor = ringColor,
        )
        Text(entry.displayName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            "⚡ ${entry.effortPoints} pts",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun RewardDialog(
    title: String,
    initialEmoji: String,
    initialName: String,
    initialCost: Int,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (emoji: String, name: String, cost: Int) -> Unit,
) {
    var emoji by remember { mutableStateOf(initialEmoji) }
    var name by remember { mutableStateOf(initialName) }
    var costText by remember { mutableStateOf(initialCost.toString()) }
    val cost = costText.toIntOrNull() ?: 0
    val valid = name.isNotBlank() && cost > 0

    AlertDialog(
        modifier = Modifier.testTag("rewardDialog"),
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { emoji = it.take(4) },
                    label = { Text("Emoji") },
                    singleLine = true,
                    modifier = Modifier.width(100.dp).testTag("rewardEmojiField"),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Reward name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("rewardNameField"),
                )
                OutlinedTextField(
                    value = costText,
                    onValueChange = { costText = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("Points required") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("rewardCostField"),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = { onConfirm(emoji.ifBlank { "🏆" }, name.trim(), cost) },
                modifier = Modifier.testTag("rewardConfirm"),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
