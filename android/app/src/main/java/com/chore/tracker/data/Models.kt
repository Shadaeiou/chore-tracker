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
    val householdName: String,
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
    val createdAt: Long,
)

@Serializable
data class CreateTaskRequest(
    val areaId: String,
    val name: String,
    val frequencyDays: Int,
)

/** Computed dirtiness 0.0 (just done) → 1.0 (due) → >1.0 (overdue). */
fun Task.dirtiness(now: Long = System.currentTimeMillis()): Double {
    val last = lastDoneAt ?: return 1.0
    val window = frequencyDays.toLong() * 86_400_000L
    if (window <= 0) return 1.0
    return (now - last).toDouble() / window.toDouble()
}
