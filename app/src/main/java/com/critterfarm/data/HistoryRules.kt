package com.critterfarm.data

import com.critterfarm.data.local.DailySummaryLogEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One square of the farm's heatmap. */
data class DayCell(
    val date: LocalDate,
    val metTargets: Int,
) {
    val intensity: Int get() = HistoryRules.intensity(metTargets)
    val isFuture: Boolean get() = date.isAfter(LocalDate.now())
}

/** A personal best worth showing off. */
data class PersonalRecord(
    val emoji: String,
    val label: String,
    val value: String,
    val detail: String,
)

/**
 * History maths: turning stored daily logs into a heatmap, streaks and records.
 *
 * Pure and log-in only — the emulator can render it with no Health Connect data, and the unit
 * tests can assert on it without a device.
 */
object HistoryRules {

    private val DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d", Locale.US)

    /** Today's consistency score: how many of the six daily targets this log met (0..6). */
    fun metTargets(log: DailySummaryLogEntity): Int {
        var met = 0
        if (log.steps >= GameGoals.STEPS) met++
        if (log.deficit.coerceAtLeast(0.0) >= GameGoals.DEFICIT_KCAL) met++
        if (log.hydrationMl >= Units.flOzToMl(GameGoals.HYDRATION_FL_OZ)) met++
        if (log.workouts >= GameGoals.WORKOUTS) met++
        if (log.sleepMinutes >= GameGoals.SLEEP_MINUTES) met++
        if (log.weightKg != null) met++
        return met
    }

    /**
     * Heatmap colour bucket, 0..4 — the visual rhythm matters more than the exact number, so
     * 6 targets maps to 4 shades: nothing, a start, most, nearly all, perfect.
     */
    fun intensity(metTargets: Int): Int = when (metTargets.coerceIn(0, GameGoals.TARGET_COUNT)) {
        0 -> 0
        1, 2 -> 1
        3, 4 -> 2
        5 -> 3
        else -> 4
    }

    /** How many levels the intensity scale has, for legend rendering. */
    const val INTENSITY_LEVELS = 5

    /**
     * Dense day-by-day cells from [days] ago through to today, so gaps show as empty squares
     * rather than being skipped — the whole point of a consistency grid.
     */
    fun cells(logs: List<DailySummaryLogEntity>, today: LocalDate = LocalDate.now(), days: Int = 182): List<DayCell> {
        val byDate = logs.associateBy { it.date }
        return (days - 1 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            val log = byDate[date.toString()]
            DayCell(date = date, metTargets = log?.let { metTargets(it) } ?: 0)
        }
    }

    /**
     * Lays the dense cell list out as calendar weeks (columns), Sunday first, padding the first
     * week so every column is a genuine week rather than a ragged edge.
     */
    fun toWeeks(cells: List<DayCell>): List<List<DayCell?>> {
        if (cells.isEmpty()) return emptyList()
        val pad = cells.first().date.dayOfWeek.value % 7
        val padded: List<DayCell?> = List(pad) { null } + cells
        return padded.chunked(7)
    }

    /** Longest run of consecutive met-target days — the chain nobody wants to break. */
    fun longestConsistencyRun(logs: List<DailySummaryLogEntity>): Int {
        val dates = logs
            .filter { metTargets(it) > 0 }
            .mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
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

    /**
     * Personal bests. Weight is deliberately framed as a *milestone*, never a race — the app
     * must not reward losing fast.
     */
    fun records(logs: List<DailySummaryLogEntity>): List<PersonalRecord> {
        if (logs.isEmpty()) return emptyList()
        val records = mutableListOf<PersonalRecord>()

        logs.maxByOrNull { it.steps }?.takeIf { it.steps > 0 }?.let {
            records += PersonalRecord("👟", "Most steps in a day", formatThousands(it.steps), prettyDate(it.date))
        }
        logs.maxByOrNull { it.hydrationMl }?.takeIf { it.hydrationMl > 0 }?.let {
            records += PersonalRecord(
                "💧",
                "Most water logged",
                Metric.HYDRATION.format(Units.mlToFlOz(it.hydrationMl)),
                prettyDate(it.date),
            )
        }
        logs.maxByOrNull { it.sleepMinutes }?.takeIf { it.sleepMinutes > 0 }?.let {
            records += PersonalRecord("😴", "Longest sleep", sleepLabel(it.sleepMinutes), prettyDate(it.date))
        }
        logs.maxByOrNull { it.workouts }?.takeIf { it.workouts > 0 }?.let {
            records += PersonalRecord("🏋", "Most workouts in a day", "${it.workouts}", prettyDate(it.date))
        }
        val run = longestConsistencyRun(logs)
        if (run > 0) {
            records += PersonalRecord("🔥", "Longest streak of goal days", "$run days", "keep the chain")
        }
        val bestScore = logs.maxOfOrNull { metTargets(it) } ?: 0
        if (bestScore > 0) {
            records += PersonalRecord("⭐", "Best day", "$bestScore / ${GameGoals.TARGET_COUNT} targets", "all six is the ceiling")
        }
        logs.mapNotNull { it.weightKg }.minOrNull()?.let { lowest ->
            records += PersonalRecord(
                "⚖️",
                "Lowest weigh-in on record",
                "${"%.1f".format(Locale.US, lowest * Units.LB_PER_KG)} lb",
                "slow and steady wins",
            )
        }
        return records
    }

    private fun formatThousands(value: Long): String = String.format(Locale.US, "%,d", value)

    private fun sleepLabel(minutes: Long): String = "${minutes / 60}h ${minutes % 60}m"

    private fun prettyDate(iso: String): String =
        runCatching { LocalDate.parse(iso).format(DATE_FORMAT) }.getOrDefault(iso)
}
