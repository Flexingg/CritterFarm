package com.critterfarm.data

import com.critterfarm.data.local.DailySummaryLogEntity
import java.time.LocalDate
import java.util.Locale

/**
 * One line the active critter "says" about recent activity. [fromStage] records which branch
 * produced it, purely so callers/tests can tell them apart — the UI just shows [text], prefixed
 * with the critter's own name.
 */
data class Insight(val text: String, val fromStage: Int)

/**
 * The critter's read of recent activity, deepening with its evolution stage: a Hatchling notices
 * nothing in particular, a Mythic critter has enough history to spot a pattern across two
 * different metrics. Pure and log-in only, so it needs no Health Connect permission to degrade
 * kindly, and it is deterministic for a given (stage, logs, today) so it is trivial to test.
 *
 * This is a companion noticing things, never a coach handing out food advice — see
 * `InsightRulesTest`'s forbidden-word guardrail, which every branch below must pass.
 */
object InsightRules {

    private const val WEEK_WINDOW_DAYS = 7L
    private const val ACTIVE_DAY_STEPS = 8_000L

    private const val STAGE_0_LINE =
        "Today feels like a good day to wander and see what happens."
    private const val FALLBACK_LINE =
        "Not much logged yet — the picture gets clearer with time, no rush at all."

    fun insightFor(stage: Int, logs: List<DailySummaryLogEntity>, today: LocalDate): Insight {
        val text = when {
            stage <= 0 -> STAGE_0_LINE
            stage == 1 -> todayFact(logs, today)
            stage == 2 -> weekPattern(logs, today)
            else -> crossMetricPattern(logs)
        }
        return Insight(text = text, fromStage = stage)
    }

    /** Stage 1: one fact about today, using the steps target as the lever (per the spec example). */
    private fun todayFact(logs: List<DailySummaryLogEntity>, today: LocalDate): String {
        val log = logs.firstOrNull { it.date == today.toString() }
        if (log == null || log.steps <= 0) return FALLBACK_LINE
        val remaining = GameGoals.STEPS - log.steps
        return if (remaining <= 0) {
            "${format(log.steps)} steps so far — today's step target is already met."
        } else {
            "${format(log.steps)} steps so far — ${format(remaining)} to go."
        }
    }

    /** Stage 2: a 7-day pattern — hydration goal days, counted over the trailing week only. */
    private fun weekPattern(logs: List<DailySummaryLogEntity>, today: LocalDate): String {
        val windowDates = (0 until WEEK_WINDOW_DAYS).map { today.minusDays(it).toString() }.toSet()
        val windowLogs = logs.filter { it.date in windowDates }
        if (windowLogs.isEmpty()) return FALLBACK_LINE
        val met = windowLogs.count { it.hydrationMl >= Units.flOzToMl(GameGoals.HYDRATION_FL_OZ) }
        return "You've hit your water goal $met of the last $WEEK_WINDOW_DAYS days."
    }

    /**
     * Stage 3: a cross-metric observation comparing sleep on active days (8,000+ steps) against
     * quieter ones. Deterministic — it only ever compares two averages — and always names both
     * metrics involved, never just one.
     */
    private fun crossMetricPattern(logs: List<DailySummaryLogEntity>): String {
        val active = logs.filter { it.steps >= ACTIVE_DAY_STEPS && it.sleepMinutes > 0 }
        val quiet = logs.filter { it.steps in 1 until ACTIVE_DAY_STEPS && it.sleepMinutes > 0 }
        if (active.isEmpty() || quiet.isEmpty()) return FALLBACK_LINE
        val activeAvg = active.map { it.sleepMinutes }.average()
        val quietAvg = quiet.map { it.sleepMinutes }.average()
        val threshold = format(ACTIVE_DAY_STEPS)
        return if (activeAvg >= quietAvg) {
            "Your sleep tends to be longest on days you walk $threshold+ steps."
        } else {
            "Your sleep tends to be shorter on days you walk $threshold+ steps."
        }
    }

    private fun format(value: Long): String = String.format(Locale.US, "%,d", value)
}
