package com.critterfarm.ui.farm

import java.util.Locale

/**
 * Formatting and unit conversion for the farm's stat rows.
 *
 * Everything here is pure so it can be unit-tested without a device. Health Connect hands back
 * SI values (millilitres, kilograms, minutes); the farm's owner reads fl oz and lb, so every
 * conversion lives here and nowhere else.
 */
object StatsFormat {

    const val ML_PER_FL_OZ = 29.5735
    const val LB_PER_KG = 2.2046226

    // Daily targets — the "where you need to be" half of every row.
    const val STEPS_GOAL = 10_000L
    const val DEFICIT_GOAL_KCAL = 500.0
    const val HYDRATION_GOAL_FL_OZ = 100.0
    const val WORKOUTS_GOAL = 1
    const val SLEEP_GOAL_MINUTES = 480L

    /** Shown where a metric has no data yet. Never render a fake zero. */
    const val NO_VALUE = "—"

    const val LINK_HEALTH = "Link health to track"
    const val GOAL_MET = "Goal met!"

    fun steps(value: Long): String = String.format(Locale.US, "%,d", value)

    fun mlToFlOz(ml: Double): Double = ml / ML_PER_FL_OZ

    fun kgToLb(kg: Double): Double = kg * LB_PER_KG

    /** "64" — fl oz are read as whole numbers; the exact ml stays in the tooltip/secondary text. */
    fun flOzLabel(ml: Double): String = keep(value = mlToFlOz(ml), decimals = 0)

    /** "212.4" */
    fun weightLbLabel(kg: Double): String = keep(value = kgToLb(kg), decimals = 1)

    /** "6h 42m"; "45m" under an hour; "8h 0m" exactly on the hour. */
    fun sleepLabel(minutes: Long): String {
        val safe = minutes.coerceAtLeast(0L)
        val hours = safe / 60
        val mins = safe % 60
        return if (hours == 0L) "${mins}m" else "${hours}h ${mins}m"
    }

    /** Plain number for kcal deficits/values: "312". */
    fun kcalLabel(kcal: Double): String = keep(value = kcal.coerceAtLeast(0.0), decimals = 0)

    /** Bar fraction, clamped so an over-target day never overflows the track. */
    fun progress(current: Double, target: Double): Float {
        if (target <= 0.0) return 0f
        return (current / target).coerceIn(0.0, 1.0).toFloat()
    }

    fun progress(current: Long, target: Long): Float = progress(current.toDouble(), target.toDouble())

    fun goalMet(current: Double, target: Double): Boolean = target > 0.0 && current >= target

    fun goalMet(current: Long, target: Long): Boolean = goalMet(current.toDouble(), target.toDouble())

    /**
     * "3,588 to go" — the whole point of the redesign. Falls back to the goal-met copy rather
     * than showing a negative number.
     */
    fun toGoLabel(current: Double, target: Double, unit: String, thousands: Boolean = false): String {
        val remaining = target - current
        if (remaining <= 0.0) return GOAL_MET
        val shown = if (thousands) {
            steps(remaining.toLong())
        } else {
            keep(remaining, decimals = 0)
        }
        return "$shown $unit to go"
    }

    /** Whole-number metrics (steps, workouts) must never need a `.toDouble()` at the call site. */
    fun toGoLabel(current: Long, target: Long, unit: String, thousands: Boolean = false): String =
        toGoLabel(current.toDouble(), target.toDouble(), unit, thousands)

    /** "1 session" / "2 sessions" — units must not read like a robot wrote them. */
    fun plural(count: Int, singular: String, plural: String = "${singular}s"): String =
        if (count == 1) "1 $singular" else "$count $plural"

    /** Keeps a trailing ".0" off round numbers: 8.0 -> "8", 212.44 -> "212.4". */
    private fun keep(value: Double, decimals: Int): String {
        val factor = if (decimals == 0) 1.0 else Math.pow(10.0, decimals.toDouble())
        val rounded = Math.round(value * factor) / factor
        return if (rounded == Math.floor(rounded)) {
            String.format(Locale.US, "%,d", rounded.toLong())
        } else {
            String.format(Locale.US, "%,.${decimals}f", rounded)
        }
    }
}
