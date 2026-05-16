package com.leetcode.tracker.application

import com.leetcode.tracker.domain.GoalEntity
import com.leetcode.tracker.domain.GoalProgressResponse
import com.leetcode.tracker.domain.PatternCatalog
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

object GoalProgressCalculator {
    fun build(goal: GoalEntity, currentSolved: Int): GoalProgressResponse {
        val today = LocalDate.now()
        val remaining = (goal.targetTotalSolved - currentSolved).coerceAtLeast(0)

        val rawDaysUntilDeadline = ChronoUnit.DAYS.between(today, goal.deadline).toInt()
        val daysRemaining = rawDaysUntilDeadline.coerceAtLeast(0)

        val scheduleDays = when {
            goal.deadline < today -> 0
            else -> rawDaysUntilDeadline + 1
        }.coerceAtLeast(1)

        val dailyRequired = when {
            remaining == 0 -> 0.0
            goal.deadline < today -> Double.POSITIVE_INFINITY
            else -> ceil(remaining.toDouble() / scheduleDays.toDouble())
        }

        val onTrack = when {
            remaining == 0 -> true
            goal.deadline < today -> false
            else -> remaining <= scheduleDays
        }

        return GoalProgressResponse(
            id = goal.id.toString(),
            pattern = goal.patternKey,
            displayName = PatternCatalog.displayName(goal.patternKey),
            targetTotalSolved = goal.targetTotalSolved,
            startingSolved = goal.startingSolved,
            currentSolved = currentSolved,
            deadline = goal.deadline.toString(),
            remainingProblems = remaining,
            daysRemaining = daysRemaining,
            dailyRequiredProblems = if (dailyRequired.isInfinite()) 0.0 else dailyRequired,
            onTrack = onTrack,
        )
    }
}
