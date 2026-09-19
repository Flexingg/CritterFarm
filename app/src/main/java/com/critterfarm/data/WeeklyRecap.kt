package com.critterfarm.data

import com.critterfarm.data.local.DailySummaryLogEntity
import java.time.LocalDate
import java.util.Locale

/** Seven days of farm life, ready to be shown back to the player. */
data class WeekStats(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val daysLogged: Int,
    val totalSteps: Long,
    val totalWorkouts: Int,
    val totalWaterFlOz: Double,
    val totalSleepMinutes: Long,
    val metTargetDays: Int,
    val bestDayScore: Int,
    val coinsEarned: Int,
    val treatsEarned: Int,
    val sparksEarned: Int,
) {
    val isEmpty: Boolean get() = daysLogged == 0

    /** Average water per *logged* day — not per calendar day, which would punish a light week twice. */
    val waterPerLoggedDay: Double get() = if (daysLogged == 0) 0.0 else totalWaterFlOz / daysLogged
}

/**
 * One week compared with the one before it.
 *
 * [up] is false only when the number actually went down; [same] marks the flat case so the UI can
 * show "·" instead of pretending there was a direction.
 */
data class RecapDelta(
    val label: String,
    val thisWeek: String,
    val vsLastWeek: String,
    val up: Boolean,
    val same: Boolean = false,
)

/**
 * The weekly recap: facts about the last seven days, phrased as information rather than judgement.
 *
 * Deliberately built as pure functions over the stored logs so the awkward cases — an empty week,
 * a week with nothing logged, a first week with nothing to compare against — are unit-tested and
 * cannot turn into a screen that scolds someone for being ill.
 */
object WeeklyRecap {
    /** Sunday-start week containing [date] — the same convention the heatmap uses. */
    fun weekStart(date: LocalDate): LocalDate =
        date.minusDays((date.dayOfWeek.value % 7).toLong())

    fun statsFor(logs: List<DailySummaryLogEntity>, weekStart: LocalDate): WeekStats {
        val end = weekStart.plusDays(6)
        val startIso = weekStart.toString()
        val endIso = end.toString()
        // ISO yyyy-MM-dd strings sort lexicographically, so this range check is exact and needs no parsing.
        val week = logs.filter { it.date >= startIso && it.date <= endIso }

        var steps = 0L
        var workouts = 0
        var waterMl = 0.0
        var sleep = 0L
        var metDays = 0
        var best = 0
        var coins = 0
        var treats = 0
        var sparks = 0

        week.forEach { log ->
            steps += log.steps
            workouts += log.workouts
            waterMl += log.hydrationMl
            sleep += log.sleepMinutes
            val met = HistoryRules.metTargets(log)
            if (met >= 4) metDays++
            if (met > best) best = met
            coins += log.coinsEarned
            treats += log.treatsEarned
            sparks += GameRules.manaSparksFromDeficit(log.deficit, log.caloriesConsumed).manaSparks
        }

        return WeekStats(
            startDate = weekStart,
            endDate = end,
            daysLogged = week.size,
            totalSteps = steps,
            totalWorkouts = workouts,
            totalWaterFlOz = Units.mlToFlOz(waterMl),
            totalSleepMinutes = sleep,
            metTargetDays = metDays,
            bestDayScore = best,
            coinsEarned = coins,
            treatsEarned = treats,
            sparksEarned = sparks,
        )
    }

    private fun delta(label: String, current: Number, previous: Number, format: (Number) -> String): RecapDelta {
        val c = current.toDouble()
        val p = previous.toDouble()
        return RecapDelta(
            label = label,
            thisWeek = format(current),
            vsLastWeek = format(previous),
            up = c >= p,
            same = c == p,
        )
    }

    private fun stepsLabel(value: Number): String =
        String.format(Locale.US, "%,d", value.toLong())

    /**
     * A short, paste-anywhere summary for the share sheet. Plain text on purpose: no images, no
     * extra permissions, and it carries only the handful of numbers the player chose to share —
     * never a weight, never a health detail they did not ask to broadcast.
     */
    fun shareText(week: WeekStats): String = buildString {
        append("My week on the farm: ")
        append("${week.daysLogged}/7 days logged, ")
        append("${String.format(Locale.US, "%,d", week.totalSteps)} steps, ")
        append("${week.totalWorkouts} workout${if (week.totalWorkouts == 1) "" else "s"}, ")
        append("${week.totalWaterFlOz.toInt()} fl oz of water, ")
        append("${week.metTargetDays} goal day${if (week.metTargetDays == 1) "" else "s"}.")
        if (week.coinsEarned > 0) {
            append(" Earned ${String.format(Locale.US, "%,d", week.coinsEarned)} coins.")
        }
    }

    fun deltas(current: WeekStats, previous: WeekStats): List<RecapDelta> = listOf(
        delta("Steps", current.totalSteps, previous.totalSteps) { stepsLabel(it).replace(",", ",") },
        delta("Workouts", current.totalWorkouts, previous.totalWorkouts) { it.toInt().toString() },
        delta("Water", current.totalWaterFlOz, previous.totalWaterFlOz) {
            "${it.toInt()} fl oz"
        },
        delta("Sleep", current.totalSleepMinutes, previous.totalSleepMinutes) { minutes ->
            val h = minutes.toLong() / 60
            val m = minutes.toLong() % 60
            if (m == 0L) "${h}h" else "${h}h ${m}m"
        },
        delta("Target days", current.metTargetDays, previous.metTargetDays) { it.toInt().toString() },
        delta("Coins", current.coinsEarned, previous.coinsEarned) {
            String.format(Locale.US, "%,d", it.toInt())
        },
    )

    /**
     * Two to four friendly lines. Every branch is written so that a thin week still reads as an
     * honest, encouraging fact — there is deliberately no wording here that could make someone feel
     * judged for a week they did not manage. No mention of weight-loss speed, ever, and no advice
     * to eat less.
     */
    fun highlights(week: WeekStats): List<String> {
        val lines = mutableListOf<String>()

        lines += when (week.daysLogged) {
            0 -> "No days logged this week — the barn is open whenever you are ready."
            1 -> "1 day logged this week. Every chain starts with a single square."
            in 2..3 -> "${week.daysLogged} days logged this week — the habit is still breathing."
            in 4..6 -> "${week.daysLogged} of 7 days logged. That is a solid week by any measure."
            else -> "All 7 days logged. A full week on the farm, no gaps."
        }

        if (week.metTargetDays > 0) {
            lines += if (week.metTargetDays == 1) {
                "You hit most of your targets on 1 day — one is not nothing, it is a start."
            } else {
                "You hit most of your targets on ${week.metTargetDays} days."
            }
        }

        if (week.totalWorkouts > 0) {
            lines += if (week.totalWorkouts == 1) {
                "1 workout in the barn this week."
            } else {
                "${week.totalWorkouts} workouts in the barn this week."
            }
        }

        if (week.bestDayScore >= 4) {
            lines += "Your best day covered ${week.bestDayScore} of 6 targets."
        }

        if (week.totalSteps >= 35_000) {
            lines += "${String.format(Locale.US, "%,d", week.totalSteps)} steps wandered this week."
        }

        if (week.totalWaterFlOz >= 100.0) {
            lines += "About ${week.totalWaterFlOz.toInt()} fl oz of water logged."
        }

        if (week.coinsEarned > 0) {
            lines += "${String.format(Locale.US, "%,d", week.coinsEarned)} coins earned — spend them on something silly."
        }

        if (lines.size < 2) {
            lines += "Log a few days next week and this page fills itself in."
        }

        return lines.take(4)
    }
}
