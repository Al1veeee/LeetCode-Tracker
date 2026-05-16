package com.leetcode.tracker.data

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

object Users : Table("users") {
    val id = uuid("id")
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val leetcodeUsername = varchar("leetcode_username", 64).nullable()
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}

object Stats : Table("stats") {
    val userId = uuid("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE).uniqueIndex()
    val totalSolved = integer("total_solved").default(0)
    val easySolved = integer("easy_solved").default(0)
    val mediumSolved = integer("medium_solved").default(0)
    val hardSolved = integer("hard_solved").default(0)
    val ranking = integer("ranking").nullable()
    val reputation = double("reputation").nullable()
    val lastSyncedAt = timestampWithTimeZone("last_synced_at").nullable()

    override val primaryKey = PrimaryKey(userId)
}

object Patterns : Table("patterns") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
    val patternKey = varchar("pattern_key", 64)
    val solvedCount = integer("solved_count").default(0)
    val lastSyncedAt = timestampWithTimeZone("last_synced_at").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(userId, patternKey)
    }
}

object Goals : Table("goals") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
    val patternKey = varchar("pattern_key", 64)
    val targetTotalSolved = integer("target_total_solved")
    val startingSolved = integer("starting_solved")
    val deadline = date("deadline")
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}

object Streaks : Table("streaks") {
    val userId = uuid("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
    val currentStreakDays = integer("current_streak_days").default(0)
    val longestStreakDays = integer("longest_streak_days").default(0)
    val lastActiveDate = date("last_active_date").nullable()
    val leetcodeCalendarStreak = integer("leetcode_calendar_streak").nullable()

    override val primaryKey = PrimaryKey(userId)
}
