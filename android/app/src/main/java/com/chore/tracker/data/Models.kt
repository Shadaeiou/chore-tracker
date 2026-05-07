package com.chore.tracker.data

import kotlinx.serialization.Serializable

@Serializable
data class AuthResponse(
    val token: String,
    val userId: String,
    val householdId: String,
)

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val displayName: String,
    val householdName: String? = null,
    val inviteCode: String? = null,
)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class Area(
    val id: String,
    val name: String,
    val icon: String? = null,
    val sortOrder: Int = 0,
    val createdAt: Long,
)

@Serializable
data class CreateAreaRequest(val name: String, val icon: String? = null, val sortOrder: Int = 0)

@Serializable
data class Task(
    val id: String,
    val areaId: String,
    val name: String,
    val frequencyDays: Int,
    val lastDoneAt: Long? = null,
    val lastDoneBy: String? = null,
    val createdAt: Long,
    val assignedTo: String? = null,
    val assignedToName: String? = null,
    val autoRotate: Boolean = false,
    val effortPoints: Int = 1,
    val snoozedUntil: Long? = null,
    val dueness: Double? = null,
    val notes: String? = null,
    val onDemand: Boolean = false,
    /** Captured original due (last_done_at + freq) when the chore was snoozed,
     *  so the next completion stamps last_done_at to this anchor instead of
     *  the real completion time — keeps weekly cadence on track. */
    val postponeAnchor: Long? = null,
)

@Serializable
data class CreateTaskRequest(
    val areaId: String,
    val name: String? = null,
    val frequencyDays: Int? = null,
    val assignedTo: String? = null,
    val autoRotate: Boolean = false,
    val effortPoints: Int? = null,
    val templateId: String? = null,
    val lastDoneAt: Long? = null,
    val notes: String? = null,
    val onDemand: Boolean = false,
)

@Serializable
data class TaskTemplate(
    val id: String,
    val name: String,
    val suggestedArea: String,
    val suggestedFrequencyDays: Int,
    val suggestedEffort: Int,
)

@Serializable
data class PatchAreaRequest(val name: String? = null, val icon: String? = null, val sortOrder: Int? = null)

@Serializable
data class PatchTaskRequest(
    val name: String? = null,
    val frequencyDays: Int? = null,
    val assignedTo: String? = null,
    val autoRotate: Boolean? = null,
    val effortPoints: Int? = null,
    val notes: String? = null,
    val areaId: String? = null,
    val onDemand: Boolean? = null,
)

@Serializable
data class DeviceTokenRequest(val token: String, val platform: String = "android")

@Serializable
data class ActivityEntry(
    val id: String,
    val taskId: String,
    val taskName: String,
    val areaName: String,
    val doneBy: String,
    val doneById: String? = null,
    val doneByAvatarVersion: Int = 0,
    val doneAt: Long,
    val notes: String? = null,
    val reactions: List<ActivityReaction> = emptyList(),
    val comments: List<ActivityComment> = emptyList(),
)

@Serializable
data class ActivityReaction(val userId: String, val emoji: String)

@Serializable
data class ActivityComment(
    val id: String,
    val userId: String,
    val displayName: String? = null,
    val avatarVersion: Int = 0,
    val text: String,
    val createdAt: Long,
)

@Serializable
data class ReactionRequest(val emoji: String)

@Serializable
data class CommentRequest(val text: String)

@Serializable
data class WorkloadEntry(
    val userId: String,
    val displayName: String,
    val effortPoints: Int,
)

@Serializable
data class Invite(val code: String, val expiresAt: Long)

@Serializable
data class Household(
    val id: String,
    val name: String,
    val createdAt: Long,
    val pausedUntil: Long? = null,
)

@Serializable
data class PatchHouseholdRequest(val pausedUntil: Long? = null)

@Serializable
data class RenameHouseholdRequest(val name: String)

@Serializable
data class CopyAreaRequest(val name: String)

@Serializable
data class SnoozeRequest(val until: Long)

@Serializable
data class CompleteRequest(
    val at: Long? = null,
    val notes: String? = null,
    val completedBy: String? = null,
)

@Serializable
data class Member(
    val id: String,
    val displayName: String,
    val email: String,
    val avatarVersion: Int = 0,
    val role: String = "member",
    val profileColor: String? = null,
)

@Serializable
data class UserProfile(
    val id: String,
    val email: String,
    val displayName: String,
    val avatar: String? = null,
    val avatarVersion: Int = 0,
    val role: String = "member",
    val profileColor: String? = null,
)

@Serializable
data class PatchProfileRequest(
    val displayName: String? = null,
    val avatar: String? = null,
    val profileColor: String? = null,
)

@Serializable
data class UserAvatarResponse(
    val avatar: String? = null,
    val avatarVersion: Int = 0,
    val profileColor: String? = null,
)

@Serializable
data class Reward(
    val id: String,
    val name: String,
    val emoji: String = "🏆",
    val effortCost: Int = 100,
    val createdBy: String,
    val createdAt: Long,
    val isActive: Boolean = true,
    val scope: String = "household",
    val ownerId: String? = null,
)

@Serializable
data class CreateRewardRequest(
    val name: String,
    val emoji: String = "🏆",
    val effortCost: Int = 100,
    val scope: String = "household",
)

@Serializable
data class HouseholdRewardSelected(
    val id: String,
    val name: String,
    val emoji: String,
    val effortCost: Int,
)

@Serializable
data class HouseholdRewardState(
    val selectedRewardId: String? = null,
    val selectedReward: HouseholdRewardSelected? = null,
    val pointsBaseline: Long = 0,
    val roundNumber: Int = 1,
    val nextPickerId: String? = null,
    val householdEarned: Long = 0,
    val roundPoints: Long = 0,
)

@Serializable
data class SelectHouseholdRewardRequest(
    val rewardId: String? = null,
)

@Serializable
data class HouseholdRewardWin(
    val id: String,
    val rewardId: String,
    val rewardName: String,
    val rewardEmoji: String,
    val cost: Int,
    val roundNumber: Int,
    val wonAt: Long,
    val claimedBy: String,
)

@Serializable
data class PersonalPoints(
    val earned: Long = 0,
    val redeemed: Long = 0,
    val available: Long = 0,
)

@Serializable
data class PersonalRedemption(
    val id: String,
    val rewardId: String,
    val rewardName: String,
    val rewardEmoji: String,
    val cost: Int,
    val redeemedAt: Long,
)

@Serializable
data class RedeemResponse(
    val id: String,
    val rewardId: String,
    val rewardName: String,
    val rewardEmoji: String,
    val cost: Int,
    val redeemedAt: Long,
    val available: Long,
)

@Serializable
data class RpsRound(
    val roundNumber: Int,
    val challengerChoice: String? = null,
    val opponentChoice: String? = null,
    val resolvedAt: Long? = null,
)

@Serializable
data class RpsGame(
    val id: String,
    val challengerId: String,
    val opponentId: String,
    val challengerScore: Int = 0,
    val opponentScore: Int = 0,
    val currentRound: Int = 1,
    val status: String = "in_progress",
    val winnerId: String? = null,
    val purpose: String = "pick_reward",
    val createdAt: Long = 0,
    val finishedAt: Long? = null,
    val rounds: List<RpsRound> = emptyList(),
)

@Serializable
data class CreateRpsGameRequest(
    val opponentId: String,
    val purpose: String = "pick_reward",
)

@Serializable
data class PlayRpsRequest(
    val choice: String,
)

@Serializable
data class PatchRewardRequest(
    val name: String? = null,
    val emoji: String? = null,
    val effortCost: Int? = null,
    val isActive: Boolean? = null,
)

@Serializable
data class RewardSettings(
    val pointRatio: Double = 1.0,
)

@Serializable
data class PatchRewardSettingsRequest(
    val pointRatio: Double,
)

@Serializable
data class EffortTotalEntry(
    val userId: String,
    val displayName: String,
    val effortPoints: Int,
)

@Serializable
data class TodoItem(
    val id: String,
    val ownerId: String,
    val text: String,
    val doneAt: Long? = null,
    val isPublic: Boolean = false,
    val createdAt: Long,
)

@Serializable
data class CreateTodoRequest(
    val text: String,
    val isPublic: Boolean = false,
    val ownerId: String? = null,
)

/** Toggling done state. Always sent with doneAt either set or null. */
@Serializable
data class MarkTodoDoneRequest(val doneAt: Long?)

/** Editing the text body / visibility. */
@Serializable
data class EditTodoRequest(val text: String, val isPublic: Boolean)

@Serializable
data class HouseholdResponse(val household: Household, val members: List<Member>)

/** Computed dirtiness 0.0 (just done) → 1.0 (due) → >1.0 (overdue).
 * Server-computed `dueness` wins when present (it accounts for pause/snooze).
 * On-demand tasks have no schedule, so they're always considered "not due". */
fun Task.dirtiness(now: Long = System.currentTimeMillis()): Double {
    if (onDemand) return 0.0
    dueness?.let { return it }
    val last = lastDoneAt ?: return 1.0
    val window = frequencyDays.toLong() * 86_400_000L
    if (window <= 0) return 1.0
    return (now - last).toDouble() / window.toDouble()
}
