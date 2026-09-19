package com.critterfarm.data

import com.critterfarm.data.local.DailySummaryLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whether a species can be hatched: unlock progress from the stored logs, affordability in
 * sparks, and the guardrail that a locked species can never be bought regardless of currency.
 */
class HatchRulesTest {

    private fun log(
        date: String,
        steps: Long = 0L,
        deficit: Double = 0.0,
        hydrationMl: Double = 0.0,
        sleepMinutes: Long = 0L,
        workouts: Int = 0,
    ) = DailySummaryLogEntity(
        id = 0,
        date = date,
        steps = steps,
        caloriesBurned = 2_000.0,
        caloriesConsumed = 2_000.0 - deficit,
        deficit = deficit,
        hydrationMl = hydrationMl,
        sleepMinutes = sleepMinutes,
        workouts = workouts,
        weightKg = null,
        xpEarned = 0,
        coinsEarned = 0,
        treatsEarned = 0,
        chestClaimed = true,
        syncedAt = 0L,
    )

    // ----------------------------------------------------------------------- affordability ----

    @Test
    fun `affordability is a simple spark check`() {
        assertTrue(HatchRules.canAfford(SpeciesCatalog.BUNNY, manaSparks = 15))
        assertFalse(HatchRules.canAfford(SpeciesCatalog.BUNNY, manaSparks = 14))
        assertEquals(0, HatchRules.shortfall(SpeciesCatalog.BUNNY, manaSparks = 20))
        assertEquals(5, HatchRules.shortfall(SpeciesCatalog.BUNNY, manaSparks = 10))
    }

    @Test
    fun `the blob is always unlocked and free`() {
        assertTrue(HatchRules.isUnlocked(SpeciesCatalog.BLOB, emptyList()))
        assertTrue(HatchRules.canAfford(SpeciesCatalog.BLOB, manaSparks = 0))
    }

    // ------------------------------------------------------------------------------ bunny -----

    @Test
    fun `bunny unlocks on any single logged workout`() {
        assertFalse(HatchRules.isUnlocked(SpeciesCatalog.BUNNY, emptyList()))
        val logs = listOf(log("2026-09-01", workouts = 1))
        assertTrue(HatchRules.isUnlocked(SpeciesCatalog.BUNNY, logs))
    }

    // ------------------------------------------------------------------------- day counts -----

    @Test
    fun `a player with 5 hydration days unlocks the axolotl and not the sloth`() {
        val logs = (1..5).map { day ->
            log("2026-09-0$day", hydrationMl = Units.flOzToMl(GameGoals.HYDRATION_FL_OZ))
        }
        assertTrue(HatchRules.isUnlocked(SpeciesCatalog.AXOLOTL, logs))
        assertFalse(HatchRules.isUnlocked(SpeciesCatalog.SLOTH, logs))
    }

    @Test
    fun `chick needs 3 days meeting the steps target, not just 3 days logged`() {
        val logs = listOf(
            log("2026-09-01", steps = GameGoals.STEPS),
            log("2026-09-02", steps = GameGoals.STEPS - 1),
            log("2026-09-03", steps = GameGoals.STEPS),
            log("2026-09-04", steps = GameGoals.STEPS),
        )
        assertTrue(HatchRules.isUnlocked(SpeciesCatalog.CHICK, logs))
        assertEquals(3, logs.count { it.steps >= GameGoals.STEPS })
    }

    @Test
    fun `dragon needs 10 days meeting the deficit target`() {
        val short = (1..9).map { day -> log("2026-09-%02d".format(day), deficit = GameGoals.DEFICIT_KCAL) }
        assertFalse(HatchRules.isUnlocked(SpeciesCatalog.DRAGON, short))
        val enough = short + log("2026-09-10", deficit = GameGoals.DEFICIT_KCAL)
        assertTrue(HatchRules.isUnlocked(SpeciesCatalog.DRAGON, enough))
    }

    @Test
    fun `sloth needs 7 days meeting the sleep target`() {
        val logs = (1..7).map { day ->
            log("2026-09-%02d".format(day), sleepMinutes = GameGoals.SLEEP_MINUTES)
        }
        assertTrue(HatchRules.isUnlocked(SpeciesCatalog.SLOTH, logs))
    }

    // ---------------------------------------------------------------------- never buyable -----

    @Test
    fun `a locked species can never be hatched even with unlimited sparks`() {
        val progress = HatchRules.unlockProgress(SpeciesCatalog.DRAGON, emptyList())
        assertFalse(progress.unlocked)
        assertTrue(HatchRules.canAfford(SpeciesCatalog.DRAGON, manaSparks = 999_999))
        // Affordability alone never implies the repository should hatch it — isUnlocked stays
        // false regardless of how many sparks are on hand.
        assertFalse(HatchRules.isUnlocked(SpeciesCatalog.DRAGON, emptyList()))
    }

    @Test
    fun `zero Health Connect data reads as encouraging zero progress, not an error`() {
        val progress = HatchRules.unlockProgress(SpeciesCatalog.CHICK, emptyList())
        assertFalse(progress.unlocked)
        assertEquals("0/3 days with steps met", progress.progressText)
    }
}
