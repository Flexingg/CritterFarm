package com.critterfarm.data

import com.critterfarm.data.local.CritterEntity
import com.critterfarm.data.local.CritterMood
import com.critterfarm.data.local.DailySummaryLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Evolution needs a behaviour gate (level + consistency) AND Mana Sparks. These tests pin down
 * the exact thresholds so the numbers never drift without a test noticing.
 */
class EvolutionRulesTest {

    private fun critter(level: Int, stage: Int = 0) = CritterEntity(
        name = "Test",
        species = "blob",
        stage = stage,
        xp = 0,
        level = level,
        happiness = 50,
        hunger = 50,
        mood = CritterMood.NEUTRAL,
        lastFedAt = 0,
        createdAt = 0,
    )

    /** A day that meets exactly [met] of the six daily targets, using steps as the lever. */
    private fun dayMeeting(date: String, met: Int) = DailySummaryLogEntity(
        id = 0,
        date = date,
        steps = if (met >= 1) GameGoals.STEPS else 0L,
        caloriesBurned = 2_000.0,
        caloriesConsumed = if (met >= 2) 2_000.0 - GameGoals.DEFICIT_KCAL else 2_000.0,
        deficit = if (met >= 2) GameGoals.DEFICIT_KCAL else 0.0,
        hydrationMl = if (met >= 3) Units.flOzToMl(GameGoals.HYDRATION_FL_OZ) else 0.0,
        sleepMinutes = if (met >= 4) GameGoals.SLEEP_MINUTES else 0L,
        workouts = if (met >= 5) GameGoals.WORKOUTS else 0,
        weightKg = if (met >= 6) 90.0 else null,
        xpEarned = 0,
        coinsEarned = 0,
        treatsEarned = 0,
        chestClaimed = true,
        syncedAt = 0L,
    )

    private fun goalDays(count: Int, targetsMetPerDay: Int = 4): List<DailySummaryLogEntity> =
        (1..count).map { dayMeeting("2026-01-%02d".format(it), targetsMetPerDay) }

    // ------------------------------------------------------------------------- stage 1 gate ----

    @Test
    fun `stage 1 is locked at low level even with days and sparks satisfied`() {
        val requirement = EvolutionRules.requirementFor(
            stage = 1,
            logs = goalDays(7),
            critter = critter(level = 4),
            manaSparks = 100,
        )
        assertFalse(requirement.behaviorMet)
        assertFalse(requirement.met)
    }

    @Test
    fun `stage 1 is locked with too few goal days even at a high level`() {
        val requirement = EvolutionRules.requirementFor(
            stage = 1,
            logs = goalDays(6),
            critter = critter(level = 10),
            manaSparks = 100,
        )
        assertFalse(requirement.behaviorMet)
        assertFalse(requirement.met)
    }

    @Test
    fun `stage 1 is locked without the sparks even when the behaviour gate is satisfied`() {
        val requirement = EvolutionRules.requirementFor(
            stage = 1,
            logs = goalDays(7),
            critter = critter(level = 5),
            manaSparks = 19,
        )
        assertTrue(requirement.behaviorMet)
        assertFalse(requirement.met)
    }

    @Test
    fun `stage 1 unlocks only when level, days and sparks are all satisfied`() {
        val requirement = EvolutionRules.requirementFor(
            stage = 1,
            logs = goalDays(7),
            critter = critter(level = 5),
            manaSparks = 20,
        )
        assertTrue(requirement.behaviorMet)
        assertTrue(requirement.met)
        assertEquals(5, requirement.minLevel)
        assertEquals(7, requirement.minGoalDays)
        assertEquals(20, requirement.sparkCost)
    }

    // ------------------------------------------------------------------------- stage 2 gate ----

    @Test
    fun `stage 2 needs level 12, 21 goal days and 50 sparks`() {
        val notReady = EvolutionRules.requirementFor(2, goalDays(21), critter(level = 11), manaSparks = 50)
        assertFalse(notReady.behaviorMet)

        val ready = EvolutionRules.requirementFor(2, goalDays(21), critter(level = 12), manaSparks = 50)
        assertTrue(ready.met)
        assertEquals(12, ready.minLevel)
        assertEquals(21, ready.minGoalDays)
        assertEquals(50, ready.sparkCost)
    }

    // -------------------------------------------------------------------------------- text ----

    @Test
    fun `requirement text mentions the stage`() {
        val requirement = EvolutionRules.requirementFor(1, emptyList(), critter(level = 1), manaSparks = 0)
        assertTrue(requirement.progressText.contains("Stage 1"))
    }

    // --------------------------------------------------------------------- day-count purity ----

    @Test
    fun `the day count only credits days meeting at least 4 of the 6 targets`() {
        val logs = listOf(
            dayMeeting("2026-01-01", met = 3),
            dayMeeting("2026-01-02", met = 4),
            dayMeeting("2026-01-03", met = 6),
        )
        val requirement = EvolutionRules.requirementFor(1, logs, critter(level = 5), manaSparks = 20)
        // Only the two days meeting >= 4 targets count, not the 3-target day.
        assertTrue(requirement.progressText.contains("2/7 goal days"))
    }
}
