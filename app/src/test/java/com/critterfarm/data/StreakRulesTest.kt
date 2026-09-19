package com.critterfarm.data

import com.critterfarm.data.local.DailySummaryLogEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** Per-zone streaks must be right or the "🔥 N-day" chip lies to the player. */
class StreakRulesTest {

    private val today = LocalDate.of(2026, 9, 19)

    private fun stepsLog(date: String, steps: Long) = DailySummaryLogEntity(
        id = 0,
        date = date,
        steps = steps,
        caloriesBurned = 2_000.0,
        caloriesConsumed = 1_500.0,
        deficit = 0.0,
        hydrationMl = 0.0,
        sleepMinutes = 0,
        workouts = 0,
        weightKg = null,
        xpEarned = 0,
        coinsEarned = 0,
        treatsEarned = 0,
        chestClaimed = false,
        syncedAt = 0L,
    )

    private fun hydrationLog(date: String, hydrationFlOz: Double) = stepsLog(date, 0L)
        .copy(hydrationMl = Units.flOzToMl(hydrationFlOz))

    private val metGoal = GameGoals.STEPS

    @Test
    fun `current streak counts consecutive met days up to today`() {
        val logs = listOf(
            stepsLog("2026-09-17", metGoal),
            stepsLog("2026-09-18", metGoal),
            stepsLog("2026-09-19", metGoal),
        )
        assertEquals(3, StreakRules.currentStreak(logs, Metric.STEPS, today))
    }

    @Test
    fun `a streak ending yesterday is still current even though today has not been met`() {
        val logs = listOf(
            stepsLog("2026-09-17", metGoal),
            stepsLog("2026-09-18", metGoal),
            // Nothing logged for today (09-19) yet.
        )
        assertEquals(2, StreakRules.currentStreak(logs, Metric.STEPS, today))
    }

    @Test
    fun `today only extends the streak once it is actually met`() {
        val logs = listOf(
            stepsLog("2026-09-18", metGoal),
            stepsLog("2026-09-19", metGoal - 1), // today, not met
        )
        // Falls back to "ended yesterday" — still current, but not extended by today's miss.
        assertEquals(1, StreakRules.currentStreak(logs, Metric.STEPS, today))
    }

    @Test
    fun `a gap of two or more days resets the current streak to zero`() {
        val logs = listOf(
            stepsLog("2026-09-14", metGoal),
            stepsLog("2026-09-15", metGoal),
            // 09-16, 09-17, 09-18 missing — a gap of three days.
        )
        assertEquals(0, StreakRules.currentStreak(logs, Metric.STEPS, today))
    }

    @Test
    fun `best streak finds the longest historical run even if it is not current`() {
        val logs = listOf(
            stepsLog("2026-08-01", metGoal),
            stepsLog("2026-08-02", metGoal),
            stepsLog("2026-08-03", metGoal),
            stepsLog("2026-08-04", metGoal),
            // gap
            stepsLog("2026-09-19", metGoal),
        )
        assertEquals(4, StreakRules.bestStreak(logs, Metric.STEPS))
        assertEquals(1, StreakRules.currentStreak(logs, Metric.STEPS, today))
    }

    @Test
    fun `per-metric streaks are independent — a water streak ignores steps`() {
        val logs = listOf(
            stepsLog("2026-09-17", metGoal),
            stepsLog("2026-09-18", metGoal),
            // Today (09-19) hits water but not steps.
            stepsLog("2026-09-19", 0L).copy(hydrationMl = Units.flOzToMl(GameGoals.HYDRATION_FL_OZ)),
        )
        // Steps missed today, so the two-day chain from 09-17/09-18 is still current but not extended.
        assertEquals(2, StreakRules.currentStreak(logs, Metric.STEPS, today))
        // Water was only met today — an entirely different, shorter chain.
        assertEquals(1, StreakRules.currentStreak(logs, Metric.HYDRATION, today))
    }

    @Test
    fun `an empty history has no current or best streak`() {
        assertEquals(0, StreakRules.currentStreak(emptyList(), Metric.STEPS, today))
        assertEquals(0, StreakRules.bestStreak(emptyList(), Metric.STEPS))
    }
}
