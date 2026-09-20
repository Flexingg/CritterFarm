package com.critterfarm.data

import com.critterfarm.data.local.DailySummaryLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The challenge engine itself — weekly, monthly and event windows all run through
 * [ChallengeRules], so these tests exercise the maths once rather than once per window.
 */
class ChallengeRulesTest {

    private fun log(
        date: String,
        steps: Long = 0,
        workouts: Int = 0,
        hydrationFlOz: Double = 0.0,
        sleepHours: Double = 0.0,
        deficit: Double = 0.0,
        weightKg: Double? = null,
    ) = DailySummaryLogEntity(
        id = 0,
        date = date,
        steps = steps,
        caloriesBurned = 0.0,
        caloriesConsumed = 0.0,
        deficit = deficit,
        hydrationMl = Units.flOzToMl(hydrationFlOz),
        sleepMinutes = (sleepHours * 60).toLong(),
        workouts = workouts,
        weightKg = weightKg,
        xpEarned = 0,
        coinsEarned = 0,
        treatsEarned = 0,
        chestClaimed = false,
        syncedAt = 0L,
    )

    private val weeklySpec = ChallengeSpec(
        id = "test_weekly_workouts",
        title = "t", emoji = "t", blurb = "t",
        window = ChallengeWindow.WEEKLY,
        metric = Metric.WORKOUTS,
        goal = ChallengeGoal.TOTAL,
        target = 4.0,
        rewardCoins = 0, rewardTreats = 0, rewardSparks = 0,
    )

    private val monthlySpec = weeklySpec.copy(id = "test_monthly", window = ChallengeWindow.MONTHLY)

    // ------------------------------------------------------------------------ window edges ----

    @Test
    fun `weekly window is Monday-start and a day before or after is excluded`() {
        // 2026-09-16 is a Wednesday; the ISO week runs Mon 09-14 through Sun 09-20.
        val today = LocalDate.of(2026, 9, 16)
        val logs = listOf(
            log("2026-09-13", workouts = 9), // Sunday before — excluded
            log("2026-09-14", workouts = 1), // Monday — included
            log("2026-09-20", workouts = 1), // Sunday — included
            log("2026-09-21", workouts = 9), // Monday after — excluded
        )
        val progress = ChallengeRules.progressFor(weeklySpec, logs, today)
        assertEquals(2.0, progress.current, 0.0)
    }

    @Test
    fun `monthly window excludes the last day of the prior month and first day of the next`() {
        val today = LocalDate.of(2026, 9, 15)
        val logs = listOf(
            log("2026-08-31", workouts = 9),
            log("2026-09-01", workouts = 1),
            log("2026-09-30", workouts = 1),
            log("2026-10-01", workouts = 9),
        )
        val progress = ChallengeRules.progressFor(monthlySpec, logs, today)
        assertEquals(2.0, progress.current, 0.0)
    }

    // -------------------------------------------------------------------------- period keys ----

    @Test
    fun `ISO weekly period keys are stable within a week and differ across weeks`() {
        val monday = LocalDate.of(2026, 9, 14)
        val sunday = LocalDate.of(2026, 9, 20)
        val nextMonday = LocalDate.of(2026, 9, 21)
        val key = ChallengeRules.periodKeyFor(weeklySpec, monday)
        assertEquals(key, ChallengeRules.periodKeyFor(weeklySpec, sunday))
        assertTrue(key.matches(Regex("\\d{4}-W\\d{2}")))
        assertFalse(key == ChallengeRules.periodKeyFor(weeklySpec, nextMonday))
    }

    @Test
    fun `monthly period keys differ month to month`() {
        val sep = ChallengeRules.periodKeyFor(monthlySpec, LocalDate.of(2026, 9, 15))
        val oct = ChallengeRules.periodKeyFor(monthlySpec, LocalDate.of(2026, 10, 15))
        assertEquals("2026-09", sep)
        assertEquals("2026-10", oct)
        assertFalse(sep == oct)
    }

    @Test
    fun `event period key is the event id`() {
        val event = EventCatalog.ALL.first()
        val spec = weeklySpec.copy(window = ChallengeWindow.EVENT, eventId = event.id)
        assertEquals(event.id, ChallengeRules.periodKeyFor(spec, event.start))
    }

    // ----------------------------------------------------------------------------- goal maths ----

    @Test
    fun `TOTAL sums the metric across the window`() {
        val today = LocalDate.of(2026, 9, 16)
        val logs = listOf(log("2026-09-14", workouts = 2), log("2026-09-15", workouts = 1))
        val progress = ChallengeRules.progressFor(weeklySpec, logs, today)
        assertEquals(3.0, progress.current, 0.0)
        assertFalse(progress.completed)
    }

    @Test
    fun `DAYS counts days meeting the goal, not the summed total`() {
        val spec = weeklySpec.copy(metric = Metric.STEPS, goal = ChallengeGoal.DAYS, target = 2.0)
        val today = LocalDate.of(2026, 9, 16)
        val logs = listOf(
            log("2026-09-14", steps = GameGoals.STEPS), // met
            log("2026-09-15", steps = GameGoals.STEPS + 5_000), // met, but DAYS doesn't sum
            log("2026-09-16", steps = 10), // not met
        )
        val progress = ChallengeRules.progressFor(spec, logs, today)
        assertEquals(2.0, progress.current, 0.0)
        assertTrue(progress.completed)
    }

    @Test
    fun `a day is never counted twice even with a duplicate log row`() {
        val today = LocalDate.of(2026, 9, 16)
        val logs = listOf(log("2026-09-14", workouts = 1), log("2026-09-14", workouts = 1))
        val progress = ChallengeRules.progressFor(weeklySpec, logs, today)
        assertEquals(1.0, progress.current, 0.0)
    }

    @Test
    fun `ACTIVE_DAYS counts any logged activity regardless of metric`() {
        val spec = weeklySpec.copy(goal = ChallengeGoal.ACTIVE_DAYS, target = 3.0)
        val today = LocalDate.of(2026, 9, 16)
        val logs = listOf(
            log("2026-09-14", steps = 500),
            log("2026-09-15", hydrationFlOz = 20.0),
            log("2026-09-16", sleepHours = 3.0),
        )
        val progress = ChallengeRules.progressFor(spec, logs, today)
        assertEquals(3.0, progress.current, 0.0)
    }

    @Test
    fun `TOTAL ignores days with no data rather than treating them as zero-inflating a days count`() {
        val today = LocalDate.of(2026, 9, 16)
        // Only one real day of data — TOTAL should reflect just that, not average across empties.
        val logs = listOf(log("2026-09-14", workouts = 4))
        val progress = ChallengeRules.progressFor(weeklySpec, logs, today)
        assertEquals(4.0, progress.current, 0.0)
        assertTrue(progress.completed)
    }

    // --------------------------------------------------------------------------- daysLeft ----

    @Test
    fun `daysLeft is inclusive of today and zero on the final day`() {
        val monday = LocalDate.of(2026, 9, 14)
        val sunday = LocalDate.of(2026, 9, 20)
        assertEquals(6, ChallengeRules.progressFor(weeklySpec, emptyList(), monday).daysLeft)
        assertEquals(0, ChallengeRules.progressFor(weeklySpec, emptyList(), sunday).daysLeft)
    }

    // ---------------------------------------------------------------------------- fraction ----

    @Test
    fun `fraction is clamped to 0 to 1`() {
        val today = LocalDate.of(2026, 9, 16)
        val over = ChallengeRules.progressFor(weeklySpec, listOf(log("2026-09-14", workouts = 99)), today)
        assertEquals(1f, over.fraction, 0f)
        val empty = ChallengeRules.progressFor(weeklySpec, emptyList(), today)
        assertEquals(0f, empty.fraction, 0f)
    }

    @Test
    fun `an empty log set gives zero progress rather than crashing`() {
        val today = LocalDate.of(2026, 9, 16)
        val progress = ChallengeRules.progressFor(weeklySpec, emptyList(), today)
        assertEquals(0.0, progress.current, 0.0)
        assertFalse(progress.completed)
    }
}
