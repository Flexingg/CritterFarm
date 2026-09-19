package com.critterfarm.data

import com.critterfarm.data.local.DailySummaryLogEntity
import java.time.LocalDate
import java.util.Locale

/**
 * The six things the farm tracks, as a data-layer concept.
 *
 * The UI has its own [com.critterfarm.ui.model.GameZone] (which carries flavour: emoji, dormant
 * copy, Health Connect permissions). Keeping this list separate means the history maths never
 * depends on the UI layer, and the UI maps zone → metric in one place.
 */
enum class Metric(
    val label: String,
    val unit: String,
    /** Higher is always better here, except weight, which is a milestone rather than a score. */
    val higherIsBetter: Boolean,
) {
    STEPS("Steps", "steps", true),
    DEFICIT("Calorie deficit", "kcal", true),
    HYDRATION("Hydration", "fl oz", true),
    WORKOUTS("Workouts", "sessions", true),
    SLEEP("Sleep", "hours", true),
    WEIGHT("Weigh-in", "lb", false),
    ;

    /** The value for one day, or null when that day has nothing for this metric. */
    fun valueIn(log: DailySummaryLogEntity): Double? = when (this) {
        STEPS -> log.steps.takeIf { it > 0 }?.toDouble()
        DEFICIT -> log.deficit.coerceAtLeast(0.0).takeIf { it > 0.0 }
        HYDRATION -> Units.mlToFlOz(log.hydrationMl).takeIf { it > 0.0 }
        WORKOUTS -> log.workouts.takeIf { it > 0 }?.toDouble()
        SLEEP -> log.sleepMinutes.takeIf { it > 0 }?.let { it / 60.0 }
        WEIGHT -> log.weightKg?.let { Units.kgToLb(it) }
    }

    /** Human-readable rendering of one value, using the units the owner actually reads. */
    fun format(value: Double): String = when (this) {
        WORKOUTS -> "${value.toInt()} ${if (value == 1.0) "session" else "sessions"}"
        SLEEP -> String.format(Locale.US, "%.1f h", value)
        WEIGHT -> String.format(Locale.US, "%.1f lb", value)
        STEPS -> String.format(Locale.US, "%,d steps", value.toLong())
        DEFICIT -> String.format(Locale.US, "%,d kcal", value.toLong())
        HYDRATION -> String.format(Locale.US, "%.0f fl oz", value)
    }
}

/** Best/average/coverage for one metric across the whole stored history. */
data class MetricSummary(
    val metric: Metric,
    val best: Double,
    val bestDate: LocalDate?,
    val average: Double,
    val daysWithData: Int,
) {
    val hasData: Boolean get() = daysWithData > 0
}

/** History maths for a single metric, used by the tap-through detail view. */
object MetricHistory {

    /** Day-by-day values for the last [days] days, newest last. Missing days are null gaps. */
    fun series(
        logs: List<DailySummaryLogEntity>,
        metric: Metric,
        today: LocalDate = LocalDate.now(),
        days: Int = 14,
    ): List<Pair<LocalDate, Double?>> {
        val byDate = logs.associateBy { it.date }
        return (days - 1 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            date to byDate[date.toString()]?.let { metric.valueIn(it) }
        }
    }

    fun summary(logs: List<DailySummaryLogEntity>, metric: Metric): MetricSummary {
        val values = logs.mapNotNull { log ->
            metric.valueIn(log)?.let { value -> log to value }
        }
        if (values.isEmpty()) {
            return MetricSummary(metric, best = 0.0, bestDate = null, average = 0.0, daysWithData = 0)
        }
        val bestPair = if (metric.higherIsBetter) {
            values.maxBy { it.second }
        } else {
            values.minBy { it.second }
        }
        return MetricSummary(
            metric = metric,
            best = bestPair.second,
            bestDate = runCatching { LocalDate.parse(bestPair.first.date) }.getOrNull(),
            average = values.map { it.second }.average(),
            daysWithData = values.size,
        )
    }
}
