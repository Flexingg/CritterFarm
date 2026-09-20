package com.critterfarm.data

import com.critterfarm.data.local.CritterEntity
import com.critterfarm.data.local.CritterMood
import com.critterfarm.data.local.DailySummaryLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stage 3 (Mythic): level 25, 60 goal days, 120 sparks — and the ceiling, since there is no
 * stage 4. [EvolutionRulesTest] already pins down stages 1 and 2; this file is stage 3 plus the
 * clamp-not-throw guarantee the spec calls out explicitly.
 */
class MythicEvolutionTest {

    private fun critter(level: Int, stage: Int = 2) = CritterEntity(
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
        (1..count).map { day -> dayMeeting(dateFor(day), targetsMetPerDay) }

    /** Spreads more than 31 days across two months so a 60-day run is a valid calendar. */
    private fun dateFor(day: Int): String {
        val month = if (day <= 31) 1 else 2
        val dayOfMonth = if (day <= 31) day else day - 31
        return "2026-%02d-%02d".format(month, dayOfMonth)
    }

    // ------------------------------------------------------------------- stage 2 is stable ----

    @Test
    fun `a stage 2 critter stays stage 2 when only stage-2 needs are checked`() {
        val requirement = EvolutionRules.requirementFor(2, goalDays(21), critter(level = 12), manaSparks = 50)
        assertTrue(requirement.met)
        assertEquals(2, requirement.stage)
    }

    // ------------------------------------------------------------------------- stage 3 gate ----

    @Test
    fun `stage 3 needs level 25, 60 goal days and 120 sparks`() {
        val requirement = EvolutionRules.requirementFor(3, goalDays(60), critter(level = 25), manaSparks = 120)
        assertTrue(requirement.behaviorMet)
        assertTrue(requirement.met)
        assertEquals(25, requirement.minLevel)
        assertEquals(60, requirement.minGoalDays)
        assertEquals(120, requirement.sparkCost)
    }

    @Test
    fun `stage 3 is locked below level 25 even with days and sparks satisfied`() {
        val requirement = EvolutionRules.requirementFor(3, goalDays(60), critter(level = 24), manaSparks = 999)
        assertFalse(requirement.behaviorMet)
        assertFalse(requirement.met)
    }

    @Test
    fun `stage 3 is locked below 60 goal days even at a high level`() {
        val requirement = EvolutionRules.requirementFor(3, goalDays(59), critter(level = 30), manaSparks = 999)
        assertFalse(requirement.behaviorMet)
        assertFalse(requirement.met)
    }

    @Test
    fun `stage 3 is locked without 120 sparks even when the behaviour gate is satisfied`() {
        val requirement = EvolutionRules.requirementFor(3, goalDays(60), critter(level = 25), manaSparks = 119)
        assertTrue(requirement.behaviorMet)
        assertFalse(requirement.met)
    }

    @Test
    fun `met is only true when level, days and sparks all hold together`() {
        val allButLevel = EvolutionRules.requirementFor(3, goalDays(60), critter(level = 24), manaSparks = 120)
        assertFalse(allButLevel.met)

        val allButDays = EvolutionRules.requirementFor(3, goalDays(59), critter(level = 25), manaSparks = 120)
        assertFalse(allButDays.met)

        val allButSparks = EvolutionRules.requirementFor(3, goalDays(60), critter(level = 25), manaSparks = 0)
        assertFalse(allButSparks.met)

        val everything = EvolutionRules.requirementFor(3, goalDays(60), critter(level = 25), manaSparks = 120)
        assertTrue(everything.met)
    }

    @Test
    fun `stage 3 progress text names the stage`() {
        val requirement = EvolutionRules.requirementFor(3, emptyList(), critter(level = 1), manaSparks = 0)
        assertTrue(requirement.progressText.contains("Stage 3"))
    }

    // --------------------------------------------------------------------------- the ceiling ----

    @Test
    fun `stage 3 is the ceiling — stage 4 clamps rather than throwing`() {
        val requirement = EvolutionRules.requirementFor(4, goalDays(60), critter(level = 99, stage = 3), manaSparks = 9_999)
        assertFalse(requirement.behaviorMet)
        assertFalse(requirement.met)
        assertEquals(0, requirement.sparkCost)
    }

    @Test
    fun `stage names resolve for every real stage and clamp beyond the ceiling`() {
        assertEquals("Hatchling", StageNames.forStage(0))
        assertEquals("Awakened", StageNames.forStage(1))
        assertEquals("Ascendant", StageNames.forStage(2))
        assertEquals("Mythic", StageNames.forStage(3))
        assertEquals("Mythic", StageNames.forStage(4))
        assertEquals("Mythic", StageNames.forStage(99))
    }
}
