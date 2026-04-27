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
)

@Serializable
data class CreateTaskRequest(
    val areaId: String,
    val name: String,
    val frequencyDays: Int,
    val assignedTo: String? = null,
    val autoRotate: Boolean = false,
    val effortPoints: Int = 1,
)

@Serializable
data class PatchAreaRequest(val name: String? = null, val icon: String? = null)

@Serializable
data class PatchTaskRequest(
    val name: String? = null,
    val frequencyDays: Int? = null,
    val assignedTo: String? = null,
    val autoRotate: Boolean? = null,
    val effortPoints: Int? = null,
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
    val doneAt: Long,
)

@Serializable
data class WorkloadEntry(
    val userId: String,
    val displayName: String,
    val effortPoints: Int,
)

@Serializable
data class Invite(val code: String, val expiresAt: Long)

@Serializable
data class Household(val id: String, val name: String, val createdAt: Long)

@Serializable
data class Member(val id: String, val displayName: String, val email: String)

@Serializable
data class HouseholdResponse(val household: Household, val members: List<Member>)

/** Computed dirtiness 0.0 (just done) → 1.0 (due) → >1.0 (overdue). */
fun Task.dirtiness(now: Long = System.currentTimeMillis()): Double {
    val last = lastDoneAt ?: return 1.0
    val window = frequencyDays.toLong() * 86_400_000L
    if (window <= 0) return 1.0
    return (now - last).toDouble() / window.toDouble()
}
