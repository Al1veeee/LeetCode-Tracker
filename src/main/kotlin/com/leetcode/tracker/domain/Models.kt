package com.leetcode.tracker.domain

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.UUID

@Serializable
data class UserRecord(
    val id: String,
    val email: String,
    val leetcodeUsername: String?,
)

@Serializable
data class AuthResponse(
    val token: String,
    val userId: String,
    val email: String,
)

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class BindLeetCodeRequest(
    val leetcodeUsername: String,
)

@Serializable
data class ErrorResponse(
    val error: String,
)

@Serializable
data class HealthResponse(
    val status: String,
)

@Serializable
data class LeetCodeBindResponse(
    val leetcodeUsername: String,
    val synced: Boolean,
)

@Serializable
data class UserStatsResponse(
    val leetcodeUsername: String?,
    val totalSolved: Int,
    val easySolved: Int,
    val mediumSolved: Int,
    val hardSolved: Int,
    val ranking: Int?,
    val reputation: Double?,
    val lastSyncedAt: String?,
)

@Serializable
data class PatternProgressResponse(
    val pattern: String,
    val displayName: String,
    val solvedCount: Int,
    val lastSyncedAt: String?,
)

@Serializable
data class StreakResponse(
    val currentStreakDays: Int,
    val longestStreakDays: Int,
    val lastActiveDate: String?,
    val leetcodeCalendarStreak: Int?,
)

@Serializable
data class CreateGoalRequest(
    val pattern: String,
    val targetTotalSolved: Int,
    val deadline: String,
)

@Serializable
data class GoalProgressResponse(
    val id: String,
    val pattern: String,
    val displayName: String,
    val targetTotalSolved: Int,
    val startingSolved: Int,
    val currentSolved: Int,
    val deadline: String,
    val remainingProblems: Int,
    val daysRemaining: Int,
    val dailyRequiredProblems: Double,
    val onTrack: Boolean,
)

data class GoalEntity(
    val id: UUID,
    val userId: UUID,
    val patternKey: String,
    val targetTotalSolved: Int,
    val startingSolved: Int,
    val deadline: LocalDate,
    val createdAt: java.time.OffsetDateTime,
)
