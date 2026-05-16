package com.leetcode.tracker.data

import com.leetcode.tracker.domain.GoalEntity
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class UserRow(
    val id: UUID,
    val email: String,
    val passwordHash: String,
    val leetcodeUsername: String?,
)

class UserRepository {
    suspend fun create(email: String, passwordHash: String): UUID {
        val id = UUID.randomUUID()
        dbQuery {
            Users.insert {
                it[Users.id] = id
                it[Users.email] = email
                it[Users.passwordHash] = passwordHash
                it[Users.leetcodeUsername] = null
                it[Users.createdAt] = OffsetDateTime.now()
            }
            Streaks.insert {
                it[Streaks.userId] = id
                it[Streaks.currentStreakDays] = 0
                it[Streaks.longestStreakDays] = 0
                it[Streaks.lastActiveDate] = null
                it[Streaks.leetcodeCalendarStreak] = null
            }
        }
        return id
    }

    suspend fun findByEmail(email: String): UserRow? = dbQuery {
        Users.selectAll().where { Users.email eq email }.singleOrNull()?.let { r ->
            UserRow(
                id = r[Users.id],
                email = r[Users.email],
                passwordHash = r[Users.passwordHash],
                leetcodeUsername = r[Users.leetcodeUsername],
            )
        }
    }

    suspend fun findById(id: UUID): UserRow? = dbQuery {
        Users.selectAll().where { Users.id eq id }.singleOrNull()?.let { r ->
            UserRow(
                id = r[Users.id],
                email = r[Users.email],
                passwordHash = r[Users.passwordHash],
                leetcodeUsername = r[Users.leetcodeUsername],
            )
        }
    }

    suspend fun updateLeetcodeUsername(userId: UUID, username: String) {
        dbQuery {
            Users.update({ Users.id eq userId }) {
                it[Users.leetcodeUsername] = username
            }
        }
    }
}

class ProgressRepository {
    suspend fun upsertStats(
        userId: UUID,
        total: Int,
        easy: Int,
        medium: Int,
        hard: Int,
        ranking: Int?,
        reputation: Double?,
    ) {
        val now = OffsetDateTime.now()
        dbQuery {
            val updated = Stats.update({ Stats.userId eq userId }) {
                it[Stats.totalSolved] = total
                it[Stats.easySolved] = easy
                it[Stats.mediumSolved] = medium
                it[Stats.hardSolved] = hard
                it[Stats.ranking] = ranking
                it[Stats.reputation] = reputation
                it[Stats.lastSyncedAt] = now
            }
            if (updated == 0) {
                Stats.insert {
                    it[Stats.userId] = userId
                    it[Stats.totalSolved] = total
                    it[Stats.easySolved] = easy
                    it[Stats.mediumSolved] = medium
                    it[Stats.hardSolved] = hard
                    it[Stats.ranking] = ranking
                    it[Stats.reputation] = reputation
                    it[Stats.lastSyncedAt] = now
                }
            }
        }
    }

    suspend fun replacePatterns(userId: UUID, patternCounts: Map<String, Int>) {
        val now = OffsetDateTime.now()
        dbQuery {
            Patterns.deleteWhere { Patterns.userId eq userId }
            for ((key, count) in patternCounts) {
                Patterns.insert {
                    it[Patterns.id] = UUID.randomUUID()
                    it[Patterns.userId] = userId
                    it[Patterns.patternKey] = key
                    it[Patterns.solvedCount] = count
                    it[Patterns.lastSyncedAt] = now
                }
            }
        }
    }

    suspend fun upsertStreak(
        userId: UUID,
        current: Int,
        longest: Int,
        lastActive: LocalDate?,
        lcStreak: Int?,
    ) {
        dbQuery {
            Streaks.update({ Streaks.userId eq userId }) {
                it[Streaks.currentStreakDays] = current
                it[Streaks.longestStreakDays] = longest
                it[Streaks.lastActiveDate] = lastActive
                it[Streaks.leetcodeCalendarStreak] = lcStreak
            }
        }
    }

    suspend fun getStats(userId: UUID): StatsRow? = dbQuery {
        Stats.selectAll().where { Stats.userId eq userId }.singleOrNull()?.let { r ->
            StatsRow(
                total = r[Stats.totalSolved],
                easy = r[Stats.easySolved],
                medium = r[Stats.mediumSolved],
                hard = r[Stats.hardSolved],
                ranking = r[Stats.ranking],
                reputation = r[Stats.reputation],
                lastSyncedAt = r[Stats.lastSyncedAt],
            )
        }
    }

    suspend fun listPatterns(userId: UUID): List<PatternRow> = dbQuery {
        Patterns.selectAll().where { Patterns.userId eq userId }.map { r ->
            PatternRow(
                patternKey = r[Patterns.patternKey],
                solvedCount = r[Patterns.solvedCount],
                lastSyncedAt = r[Patterns.lastSyncedAt],
            )
        }
    }

    suspend fun getPatternCount(userId: UUID, patternKey: String): Int = dbQuery {
        Patterns.selectAll().where { (Patterns.userId eq userId) and (Patterns.patternKey eq patternKey) }
            .singleOrNull()?.get(Patterns.solvedCount) ?: 0
    }

    suspend fun getStreak(userId: UUID): StreakRow? = dbQuery {
        Streaks.selectAll().where { Streaks.userId eq userId }.singleOrNull()?.let { r ->
            StreakRow(
                current = r[Streaks.currentStreakDays],
                longest = r[Streaks.longestStreakDays],
                lastActive = r[Streaks.lastActiveDate],
                lcStreak = r[Streaks.leetcodeCalendarStreak],
            )
        }
    }

    suspend fun insertGoal(entity: GoalEntity) {
        dbQuery {
            Goals.insert {
                it[Goals.id] = entity.id
                it[Goals.userId] = entity.userId
                it[Goals.patternKey] = entity.patternKey
                it[Goals.targetTotalSolved] = entity.targetTotalSolved
                it[Goals.startingSolved] = entity.startingSolved
                it[Goals.deadline] = entity.deadline
                it[Goals.createdAt] = entity.createdAt
            }
        }
    }

    suspend fun listGoals(userId: UUID): List<GoalEntity> = dbQuery {
        Goals.selectAll().where { Goals.userId eq userId }.map { r ->
            GoalEntity(
                id = r[Goals.id],
                userId = r[Goals.userId],
                patternKey = r[Goals.patternKey],
                targetTotalSolved = r[Goals.targetTotalSolved],
                startingSolved = r[Goals.startingSolved],
                deadline = r[Goals.deadline],
                createdAt = r[Goals.createdAt],
            )
        }
    }
}

data class StatsRow(
    val total: Int,
    val easy: Int,
    val medium: Int,
    val hard: Int,
    val ranking: Int?,
    val reputation: Double?,
    val lastSyncedAt: OffsetDateTime?,
)

data class PatternRow(
    val patternKey: String,
    val solvedCount: Int,
    val lastSyncedAt: OffsetDateTime?,
)

data class StreakRow(
    val current: Int,
    val longest: Int,
    val lastActive: LocalDate?,
    val lcStreak: Int?,
)
