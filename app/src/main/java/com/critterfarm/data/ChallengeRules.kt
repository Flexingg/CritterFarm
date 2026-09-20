package com.critterfarm.data

import com.critterfarm.data.local.DailySummaryLogEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

/** Where one challenge stands right now, for one specific period. */
data class ChallengeProgress(
    val spec: ChallengeSpec,
    val current: Double,
    val target: Double,
    val completed: Boolean,
    /** 0 on the final day of the window. */
    val daysLeft: Int,
    /** The claim key for this run of the challenge: an ISO week, a month, or an event id. */
    val periodKey: String,
) {
    val fraction: Float
        get() = if (target <= 0.0) 1f else (current / target).toFloat().coerceIn(0f, 1f)

    val progressText: String
        get() = when (spec.goal) {
            ChallengeGoal.TOTAL -> "${spec.metric.format(current)} / ${spec.metric.format(target)}"
            ChallengeGoal.DAYS, ChallengeGoal.ACTIVE_DAYS -> {
                val days = if (target == 1.0) "day" else "days"
                "${current.toInt()} / ${target.toInt()} $days"
            }
        }
}

/**
 * The challenge engine. Weekly, monthly and seasonal-event challenges are the exact same maths
 * below with a different [ChallengeSpec.window] — there is deliberately no per-window branch
 * anywhere except in [windowFor] and [periodKeyFor], which is the one place the window's
 * shape actually differs.
 */
object ChallengeRules {

    private const val ISO_WEEK_PREFIX_PAD = 2

    /**
     * The claim key for [spec] on [today]: a stable id for "this run" of the challenge, so
     * claiming is per-period and the same challenge becomes claimable again next period.
     */
    fun periodKeyFor(spec: ChallengeSpec, today: LocalDate): String = when (spec.window) {
        ChallengeWindow.WEEKLY -> {
            val weekYear = today.get(IsoFields.WEEK_BASED_YEAR)
            val week = today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
            "$weekYear-W${week.toString().padStart(ISO_WEEK_PREFIX_PAD, '0')}"
        }
        ChallengeWindow.MONTHLY -> "%04d-%02d".format(today.year, today.monthValue)
        ChallengeWindow.EVENT -> requireNotNull(spec.eventId) { "Event challenge with no eventId: ${spec.id}" }
    }

    /** The inclusive date range [spec] is scored over, given [today] falls somewhere inside it. */
    fun windowFor(spec: ChallengeSpec, today: LocalDate): Pair<LocalDate, LocalDate> = when (spec.window) {
        ChallengeWindow.WEEKLY -> {
            val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            start to start.plusDays(6)
        }
        ChallengeWindow.MONTHLY -> {
            val start = today.withDayOfMonth(1)
            start to today.withDayOfMonth(today.lengthOfMonth())
        }
        ChallengeWindow.EVENT -> {
            val event = requireNotNull(EventCatalog.byId(requireNotNull(spec.eventId))) {
                "Unknown event for challenge ${spec.id}: ${spec.eventId}"
            }
            event.start to event.end
        }
    }

    /** Scores [spec] against [logs] for the period containing [today]. */
    fun progressFor(spec: ChallengeSpec, logs: List<DailySummaryLogEntity>, today: LocalDate): ChallengeProgress {
        val (start, end) = windowFor(spec, today)
        val inWindow = logs
            .mapNotNull { log -> runCatching { LocalDate.parse(log.date) }.getOrNull()?.let { it to log } }
            .filter { (date, _) -> !date.isBefore(start) && !date.isAfter(end) }
            .distinctBy { (date, _) -> date }
            .map { (_, log) -> log }

        val current = when (spec.goal) {
            ChallengeGoal.TOTAL -> inWindow.sumOf { spec.metric.valueIn(it) ?: 0.0 }
            ChallengeGoal.DAYS -> inWindow.count { metGoal(spec.metric, it) }.toDouble()
            ChallengeGoal.ACTIVE_DAYS -> inWindow.count { hasAnyActivity(it) }.toDouble()
        }

        val daysLeft = ChronoUnit.DAYS.between(today, end).toInt().coerceAtLeast(0)

        return ChallengeProgress(
            spec = spec,
            current = current,
            target = spec.target,
            completed = current >= spec.target,
            daysLeft = daysLeft,
            periodKey = periodKeyFor(spec, today),
        )
    }

    /** True once a day's value for [metric] meets the same target the rest of the app scores against. */
    private fun metGoal(metric: Metric, log: DailySummaryLogEntity): Boolean {
        val value = metric.valueIn(log) ?: return false
        return when (metric) {
            Metric.STEPS -> value >= GameGoals.STEPS
            Metric.DEFICIT -> value >= GameGoals.DEFICIT_KCAL
            Metric.HYDRATION -> value >= GameGoals.HYDRATION_FL_OZ
            Metric.WORKOUTS -> value >= GameGoals.WORKOUTS
            Metric.SLEEP -> value >= GameGoals.SLEEP_MINUTES / 60.0
            // valueIn already returned non-null, so a weigh-in was logged for the day.
            Metric.WEIGHT -> true
        }
    }

    private fun hasAnyActivity(log: DailySummaryLogEntity): Boolean =
        Metric.entries.any { it.valueIn(log) != null }
}
