package com.critterfarm.data

import com.critterfarm.data.local.DailySummaryLogEntity
import java.time.LocalDate

/** Where one badge stands: locked-with-progress or unlocked-forever. */
data class BadgeProgress(
    val badge: Badge,
    val unlocked: Boolean,
    val current: Int,
    val target: Int,
) {
    val progressText: String get() = "$current / $target"
}

/**
 * Turns stored logs into badge progress. Every condition is a plain count or sum over history,
 * so once a badge unlocks it can never re-lock — deleting today's data can't erase yesterday's
 * proof.
 */
object BadgeRules {

    fun progressFor(logs: List<DailySummaryLogEntity>, badge: Badge): BadgeProgress {
        val (current, target) = rawProgress(logs, badge.id)
        val capped = current.coerceAtMost(target)
        return BadgeProgress(badge = badge, unlocked = current >= target, current = capped, target = target)
    }

    fun unlockedCount(logs: List<DailySummaryLogEntity>): Int =
        BadgeCatalog.ALL.count { progressFor(logs, it).unlocked }

    private fun rawProgress(logs: List<DailySummaryLogEntity>, id: String): Pair<Int, Int> = when (id) {
        "first_10k_steps" -> (if (logs.any { it.steps >= GameGoals.STEPS }) 1 else 0) to 1
        "first_workout" -> (if (logs.any { it.workouts >= 1 }) 1 else 0) to 1
        "weighins_10" -> logs.count { it.weightKg != null } to 10
        "sleep_8h_5days" -> logs.count { it.sleepMinutes >= GameGoals.SLEEP_MINUTES } to 5
        "workouts_10" -> logs.sumOf { it.workouts } to 10
        "workouts_25" -> logs.sumOf { it.workouts } to 25
        "days_logged_50" -> logs.map { it.date }.distinct().size to 50
        "full_week_logged" -> longestRun(logs.map { it.date }) to 7
        "consistent_bronze" -> goodDays(logs) to 15
        "consistent_silver" -> goodDays(logs) to 30
        "consistent_gold" -> goodDays(logs) to 60
        "steps_100k_lifetime" -> logs.sumOf { it.steps }.coerceAtMost(100_000L).toInt() to 100_000
        "steps_250k_lifetime" -> logs.sumOf { it.steps }.coerceAtMost(250_000L).toInt() to 250_000
        "perfect_days_5" -> perfectDays(logs) to 5
        "perfect_days_20" -> perfectDays(logs) to 20
        "hydration_streak_7" -> StreakRules.bestStreak(logs, Metric.HYDRATION) to 7
        "steps_streak_7" -> StreakRules.bestStreak(logs, Metric.STEPS) to 7
        "safe_deficit_20" -> logs.count { it.deficit.coerceAtLeast(0.0) >= GameGoals.DEFICIT_KCAL } to 20
        "claim_streak_30" -> longestRun(logs.filter { it.chestClaimed }.map { it.date }) to 30
        else -> 0 to 1
    }

    private fun perfectDays(logs: List<DailySummaryLogEntity>): Int =
        logs.count { HistoryRules.metTargets(it) == GameGoals.TARGET_COUNT }

    private fun goodDays(logs: List<DailySummaryLogEntity>): Int =
        logs.count { HistoryRules.metTargets(it) >= 4 }

    /** Longest run of consecutive calendar days among the given ISO date strings. */
    private fun longestRun(isoDates: List<String>): Int {
        val dates = isoDates.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .distinct()
            .sorted()
        if (dates.isEmpty()) return 0
        var best = 1
        var current = 1
        for (i in 1 until dates.size) {
            current = if (dates[i - 1].plusDays(1) == dates[i]) current + 1 else 1
            if (current > best) best = current
        }
        return best
    }
}
