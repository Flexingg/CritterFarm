package com.critterfarm.data

import com.critterfarm.data.local.DailySummaryLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Badge thresholds must land exactly on target, and once unlocked, must stay unlocked. */
class BadgeRulesTest {

    private fun log(
        date: String,
        steps: Long = 0L,
        workouts: Int = 0,
        weightKg: Double? = null,
    ) = DailySummaryLogEntity(
        id = 0,
        date = date,
        steps = steps,
        caloriesBurned = 2_000.0,
        caloriesConsumed = 1_500.0,
        deficit = 0.0,
        hydrationMl = 0.0,
        sleepMinutes = 0,
        workouts = workouts,
        weightKg = weightKg,
        xpEarned = 0,
        coinsEarned = 0,
        treatsEarned = 0,
        chestClaimed = false,
        syncedAt = 0L,
    )

    private val weighinsBadge = BadgeCatalog.ALL.first { it.id == "weighins_10" }
    private val workouts10Badge = BadgeCatalog.ALL.first { it.id == "workouts_10" }
    private val first10kBadge = BadgeCatalog.ALL.first { it.id == "first_10k_steps" }

    private fun weighInLogs(count: Int) =
        (1..count).map { log("2026-08-%02d".format(it), weightKg = 70.0) }

    @Test
    fun `the pool has at least sixteen badges across three tiers`() {
        assertTrue(BadgeCatalog.ALL.size >= 16)
        assertEquals(BadgeTier.entries.toSet(), BadgeCatalog.ALL.map { it.tier }.toSet())
    }

    @Test
    fun `a count-based badge is locked one below target and unlocked exactly at target`() {
        val justUnder = BadgeRules.progressFor(weighInLogs(9), weighinsBadge)
        assertFalse(justUnder.unlocked)
        assertEquals(9, justUnder.current)

        val exact = BadgeRules.progressFor(weighInLogs(10), weighinsBadge)
        assertTrue(exact.unlocked)
        assertEquals(10, exact.current)
    }

    @Test
    fun `progress never exceeds the target even with far more history than needed`() {
        val progress = BadgeRules.progressFor(weighInLogs(40), weighinsBadge)
        assertTrue(progress.unlocked)
        assertEquals(10, progress.current)
        assertEquals(10, progress.target)
    }

    @Test
    fun `a one-shot badge unlocks exactly at its single-day trigger`() {
        val notYet = BadgeRules.progressFor(listOf(log("2026-09-19", steps = 9_999)), first10kBadge)
        assertFalse(notYet.unlocked)

        val unlocked = BadgeRules.progressFor(listOf(log("2026-09-19", steps = 10_000)), first10kBadge)
        assertTrue(unlocked.unlocked)
    }

    @Test
    fun `a badge unlocked by an old day stays unlocked no matter what comes after`() {
        val logs = listOf(log("2020-01-01", steps = 10_000)) +
            (1..5).map { log("2026-09-%02d".format(it), steps = 100) }
        val progress = BadgeRules.progressFor(logs, first10kBadge)
        assertTrue(progress.unlocked)
    }

    @Test
    fun `lifetime sums accumulate across every day, not just one`() {
        val logs = (1..10).map { log("2026-08-%02d".format(it), workouts = 1) }
        val progress = BadgeRules.progressFor(logs, workouts10Badge)
        assertTrue(progress.unlocked)
        assertEquals(10, progress.current)
    }

    @Test
    fun `unlockedCount agrees with the individual badge results`() {
        val logs = weighInLogs(10) + listOf(log("2026-09-19", steps = 10_000))
        val expected = BadgeCatalog.ALL.count { BadgeRules.progressFor(logs, it).unlocked }
        assertEquals(expected, BadgeRules.unlockedCount(logs))
        assertTrue(BadgeRules.unlockedCount(logs) >= 2)
    }

    @Test
    fun `an empty history unlocks nothing but still reports every badge`() {
        assertEquals(0, BadgeRules.unlockedCount(emptyList()))
        BadgeCatalog.ALL.forEach { badge ->
            val progress = BadgeRules.progressFor(emptyList(), badge)
            assertFalse(progress.unlocked)
            assertEquals(0, progress.current)
        }
    }
}
