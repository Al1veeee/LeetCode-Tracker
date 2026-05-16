package com.leetcode.tracker.application

import com.leetcode.tracker.client.LeetCodeGraphqlClient
import com.leetcode.tracker.data.ProgressRepository
import com.leetcode.tracker.domain.PatternCatalog
import java.util.UUID

class ProgressSyncService(
    private val client: LeetCodeGraphqlClient,
    private val progress: ProgressRepository,
) {
    suspend fun sync(userId: UUID, leetcodeUsername: String) {
        val snap = client.fetchUserSnapshot(leetcodeUsername)
        val patternCounts = PatternCatalog.mapLeetCodeTags(snap.tagSolvedBySlug)

        progress.upsertStats(
            userId = userId,
            total = snap.totalSolved,
            easy = snap.easySolved,
            medium = snap.mediumSolved,
            hard = snap.hardSolved,
            ranking = snap.ranking,
            reputation = snap.reputation,
        )
        progress.replacePatterns(userId, patternCounts)

        val lastActive = snap.activityByUtcDate.filterValues { it > 0 }.keys.maxOrNull()
        val prev = progress.getStreak(userId)
        val longest = maxOf(
            prev?.longest ?: 0,
            snap.longestStreakDays,
            snap.computedDailyStreak,
        )
        progress.upsertStreak(
            userId = userId,
            current = snap.computedDailyStreak,
            longest = longest,
            lastActive = lastActive,
            lcStreak = snap.leetcodeCalendarStreak,
        )
    }
}
