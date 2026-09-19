package com.critterfarm.data

import com.critterfarm.data.local.DailySummaryLogEntity
import java.time.LocalDate

/** The chain for one metric: how many days it's alive right now, and the longest it's ever run. */
data class MetricStreak(
    val current: Int,
    val best: Int,
    val daysToNextBadge: Int? = null,
)

/**
 * Per-zone streaks, computed fresh from the stored logs every time — nothing is persisted, so a
 * streak can never drift from what the history actually shows.
 */
object StreakRules {

    /** Metrics with a 7-day streak badge (see [BadgeCatalog]) — used for [MetricStreak.daysToNextBadge]. */
    private val NEXT_STREAK_BADGE = mapOf(Metric.STEPS to 7, Metric.HYDRATION to 7)

    /**
     * Consecutive met days up to today or yesterday. Today only counts once it's actually met;
     * until then, a streak that ended yesterday is still "current" — it just hasn't been
     * extended yet. A gap of two or more days breaks the chain back to zero.
     */
    fun currentStreak(
        logs: List<DailySummaryLogEntity>,
        metric: Metric,
        today: LocalDate = LocalDate.now(),
    ): Int {
        val metDates = metDatesFor(logs, metric)
        var cursor = if (today in metDates) today else today.minusDays(1)
        if (cursor !in metDates) return 0
        var count = 0
        while (cursor in metDates) {
            count++
            cursor = cursor.minusDays(1)
        }
        return count
    }

    /** The longest run of consecutive met days anywhere in history, current chain included. */
    fun bestStreak(logs: List<DailySummaryLogEntity>, metric: Metric): Int {
        val dates = metDatesFor(logs, metric).sorted()
        if (dates.isEmpty()) return 0
        var best = 1
        var current = 1
        for (i in 1 until dates.size) {
            current = if (dates[i - 1].plusDays(1) == dates[i]) current + 1 else 1
            if (current > best) best = current
        }
        return best
    }

    fun streakFor(
        logs: List<DailySummaryLogEntity>,
        metric: Metric,
        today: LocalDate = LocalDate.now(),
    ): MetricStreak {
        val current = currentStreak(logs, metric, today)
        val nextBadgeAt = NEXT_STREAK_BADGE[metric]
        return MetricStreak(
            current = current,
            best = bestStreak(logs, metric),
            daysToNextBadge = nextBadgeAt?.let { (it - current).takeIf { days -> days > 0 } },
        )
    }

    private fun metDatesFor(logs: List<DailySummaryLogEntity>, metric: Metric): Set<LocalDate> =
        logs.filter { isMet(it, metric) }
            .mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
            .toSet()

    /** Same target each metric is scored against on the "Today on the farm" card. */
    private fun isMet(log: DailySummaryLogEntity, metric: Metric): Boolean {
        val value = metric.valueIn(log) ?: return false
        return when (metric) {
            Metric.STEPS -> value >= GameGoals.STEPS
            Metric.DEFICIT -> value >= GameGoals.DEFICIT_KCAL
            Metric.HYDRATION -> value >= GameGoals.HYDRATION_FL_OZ
            Metric.WORKOUTS -> value >= GameGoals.WORKOUTS
            Metric.SLEEP -> value >= GameGoals.SLEEP_MINUTES / 60.0
            // Weight is a milestone, not a race (see HistoryRules) — any logged weigh-in counts.
            Metric.WEIGHT -> true
        }
    }
}
