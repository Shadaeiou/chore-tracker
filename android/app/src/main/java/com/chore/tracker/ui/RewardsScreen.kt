package com.chore.tracker.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chore.tracker.data.CreateRewardRequest
import com.chore.tracker.data.EffortTotalEntry
import com.chore.tracker.data.HouseholdState
import com.chore.tracker.data.PatchRewardRequest
import com.chore.tracker.data.Repo
import com.chore.tracker.data.Reward
import kotlinx.coroutines.launch

private data class SuggestedReward(val emoji: String, val name: String, val cost: Int)

private val DEFAULT_SUGGESTIONS = listOf(
    SuggestedReward("🍰", "Get dessert this week", 50),
    SuggestedReward("⏰", "1 hour alone time when needed", 75),
    SuggestedReward("🎬", "Choose the next movie night pick", 40),
    SuggestedReward("😴", "Sleep in one weekend morning", 60),
    SuggestedReward("🥡", "Skip cooking — takeout night of your choice", 55),
    SuggestedReward("🛁", "Solo 30-minute bath or spa time", 65),
    SuggestedReward("🗺️", "First pick on weekend activities", 45),
    SuggestedReward("📺", "Control the TV remote all evening", 35),
    SuggestedReward("🌙", "Skip bedtime routine (partner handles it)", 70),
    SuggestedReward("🎨", "One guilt-free hobby hour", 60),
    SuggestedReward("❤️", "Date night of your choosing", 100),
    SuggestedReward("☕", "Coffee in bed Saturday morning", 30),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardsScreen(
    state: HouseholdState,
    repo: Repo,
    snackbarHost: SnackbarHostState,
) {
    val scope = rememberCoroutineScope()

    val totalHouseholdPoints = state.effortTotals.sumOf { it.effortPoints.toLong() }

    val addedRewardNames = remember(state.rewards) { state.rewards.map { it.name }.toSet() }
    var dismissedSuggestions by remember { mutableStateOf(setOf<String>()) }
    val visibleSuggestions = remember(addedRewardNames, dismissedSuggestions) {
        DEFAULT_SUGGESTIONS.filter {
            it.name !in addedRewardNames && it.name !in dismissedSuggestions
        }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var addingSuggestion by remember { mutableStateOf<SuggestedReward?>(null) }
    var editingReward by remember { mutableStateOf<Reward?>(null) }

    val activeRewards = remember(state.rewards) { state.rewards.filter { it.isActive } }
    val nextReward = remember(activeRewards, totalHouseholdPoints) {
        activeRewards.filter { it.effortCost > totalHouseholdPoints }.minByOrNull { it.effortCost }
    }
    val progress = if (nextReward != null && nextReward.effortCost > 0) {
        (totalHouseholdPoints.toFloat() / nextReward.effortCost.toFloat()).coerceIn(0f, 1f)
    } else if (activeRewards.isNotEmpty()) 1f else 0f

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("rewardsScreen"),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        // ── Household effort header ──────────────────────────────────────
        item("header") {
            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("🏆", style = MaterialTheme.typography.headlineMedium)
                        Column {
                            Text(
                                "Household rewards",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            val memberLabel = when (state.effortTotals.size) {
                                0 -> "no members yet"
                                1 -> "1 member"
                                else -> "${state.effortTotals.size} members contributing"
                            }
                            Text(
                                "⚡ $totalHouseholdPoints pts total · $memberLabel",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    if (nextReward != null) {
                        Text(
                            "Progress toward: ${nextReward.emoji} ${nextReward.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp),
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
                                "$totalHouseholdPoints pts",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            )
                            Text(
                                "${nextReward.effortCost} pts needed",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            )
                        }
                    } else if (activeRewards.isNotEmpty()) {
                        Text(
                            "🎉 Your household has enough points for all rewards!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    } else {
                        Text(
                            "Add rewards below to start tracking progress",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // ── Household effort breakdown ───────────────────────────────────
        if (state.effortTotals.size > 1) {
            item("effortBreakdown") {
                Text(
                    "🏅 Household effort (all time)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                state.effortTotals.forEach { entry ->
                    val member = state.members.firstOrNull { it.id == entry.userId }
                    HouseholdEffortRow(entry = entry, member = member)
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
            }
        }

        // ── Active rewards ───────────────────────────────────────────────
        item("rewardsHeader") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "🎁 Household rewards",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.testTag("addRewardButton"),
                ) { Icon(Icons.Default.Add, contentDescription = "Add custom reward") }
            }
            Spacer(Modifier.height(4.dp))
        }

        if (activeRewards.isEmpty()) {
            item("noRewards") {
                Text(
                    "No rewards yet. Add one from the suggestions below or create your own!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
        } else {
            items(activeRewards, key = { "reward-${it.id}" }) { reward ->
                val canClaim = totalHouseholdPoints >= reward.effortCost
                RewardRow(
                    reward = reward,
                    canClaim = canClaim,
                    householdPoints = totalHouseholdPoints,
                    onEdit = { editingReward = reward },
                    onDelete = {
                        scope.launch {
                            runCatching { repo.api.deleteReward(reward.id) }
                                .onSuccess { repo.refresh() }
                                .onFailure { snackbarHost.showSnackbar("Delete failed: ${it.message}") }
                        }
                    },
                )
            }
        }

        // ── Suggested rewards ────────────────────────────────────────────
        if (visibleSuggestions.isNotEmpty()) {
            item("suggestionsHeader") {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text(
                    "✨ Suggested rewards",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Swipe to dismiss, tap ✚ to add",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }

            items(visibleSuggestions, key = { "suggestion-${it.name}" }) { suggestion ->
                SuggestionRow(
                    suggestion = suggestion,
                    onAdd = { addingSuggestion = suggestion },
                    onDismiss = { dismissedSuggestions = dismissedSuggestions + suggestion.name },
                )
            }
        }

        item("bottomSpacer") { Spacer(Modifier.height(24.dp)) }
    }

    // ── Add custom reward dialog ─────────────────────────────────────────
    if (showAddDialog) {
        RewardDialog(
            title = "Add reward",
            initialEmoji = "🏆",
            initialName = "",
            initialCost = 100,
            confirmLabel = "Add",
            onDismiss = { showAddDialog = false },
            onConfirm = { emoji, name, cost ->
                showAddDialog = false
                scope.launch {
                    runCatching { repo.api.createReward(CreateRewardRequest(name, emoji, cost)) }
                        .onSuccess { repo.refresh() }
                        .onFailure { snackbarHost.showSnackbar("Failed to add reward: ${it.message}") }
                }
            },
        )
    }

    // ── Add suggestion as reward ─────────────────────────────────────────
    addingSuggestion?.let { suggestion ->
        RewardDialog(
            title = "Add reward",
            initialEmoji = suggestion.emoji,
            initialName = suggestion.name,
            initialCost = suggestion.cost,
            confirmLabel = "Add",
            onDismiss = { addingSuggestion = null },
            onConfirm = { emoji, name, cost ->
                addingSuggestion = null
                scope.launch {
                    runCatching { repo.api.createReward(CreateRewardRequest(name, emoji, cost)) }
                        .onSuccess { repo.refresh() }
                        .onFailure { snackbarHost.showSnackbar("Failed to add reward: ${it.message}") }
                }
            },
        )
    }

    // ── Edit reward dialog ───────────────────────────────────────────────
    editingReward?.let { reward ->
        RewardDialog(
            title = "Edit reward",
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
                        .onSuccess { repo.refresh() }
                        .onFailure { snackbarHost.showSnackbar("Failed to save reward: ${it.message}") }
                }
            },
        )
    }
}

@Composable
private fun HouseholdEffortRow(
    entry: EffortTotalEntry,
    member: com.chore.tracker.data.Member?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
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
        Text(
            entry.displayName,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            "⚡ ${entry.effortPoints} pts",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RewardRow(
    reward: Reward,
    canClaim: Boolean,
    householdPoints: Long,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (canClaim)
                MaterialTheme.colorScheme.tertiaryContainer
            else
                MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
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
                val remaining = (reward.effortCost - householdPoints).coerceAtLeast(0)
                Text(
                    if (canClaim) "🎉 Household can claim this!"
                    else "🔒 $remaining more pts needed  (${reward.effortCost} total)",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (canClaim)
                        MaterialTheme.colorScheme.onTertiaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onEdit,
                modifier = Modifier.testTag("editReward:${reward.name}"),
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit reward",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("deleteReward:${reward.name}"),
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove reward",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestionRow(
    suggestion: SuggestedReward,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    val swipeState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart,
                SwipeToDismissBoxValue.StartToEnd -> { onDismiss(); false }
                else -> false
            }
        },
        positionalThreshold = { d -> d * 0.4f },
    )
    SwipeToDismissBox(
        state = swipeState,
        modifier = Modifier
            .padding(vertical = 3.dp)
            .testTag("suggestion:${suggestion.name}"),
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.errorContainer,
                        RoundedCornerShape(10.dp),
                    )
                    .padding(horizontal = 16.dp),
                contentAlignment = if (swipeState.dismissDirection == SwipeToDismissBoxValue.EndToStart)
                    Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Text("Dismiss", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(suggestion.emoji, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(suggestion.name, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "💡 ${suggestion.cost} pts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onAdd,
                    modifier = Modifier.testTag("addSuggestion:${suggestion.name}"),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add this reward",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
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
                    placeholder = { Text("e.g. Coffee in bed") },
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
