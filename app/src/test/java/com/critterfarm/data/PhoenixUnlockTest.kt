package com.critterfarm.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Aurora Phoenix: a species whose unlock is Barn Harmony alone, not logs — so it must stay
 * locked below the Mythic tier no matter how many Mana Sparks are stockpiled.
 */
class PhoenixUnlockTest {

    @Test
    fun `the phoenix is locked below Mythic harmony even with unlimited sparks`() {
        assertFalse(HatchRules.isUnlocked(SpeciesCatalog.PHOENIX, emptyList(), harmony = 9))
        assertTrue(HatchRules.canAfford(SpeciesCatalog.PHOENIX, manaSparks = 999_999))
        // Affordability alone never implies unlocked — the harmony gate stands regardless.
        assertFalse(HatchRules.isUnlocked(SpeciesCatalog.PHOENIX, emptyList(), harmony = 9))
    }

    @Test
    fun `logs alone can never unlock the phoenix`() {
        val heavilyLoggedButLowHarmony = (1..1000).map { day ->
            com.critterfarm.data.local.DailySummaryLogEntity(
                id = 0,
                date = "2026-01-%03d".format(day),
                steps = GameGoals.STEPS * 2,
                caloriesBurned = 3_000.0,
                caloriesConsumed = 1_000.0,
                deficit = GameGoals.DEFICIT_KCAL,
                hydrationMl = Units.flOzToMl(GameGoals.HYDRATION_FL_OZ),
                sleepMinutes = GameGoals.SLEEP_MINUTES,
                workouts = 5,
                weightKg = 90.0,
                xpEarned = 0,
                coinsEarned = 0,
                treatsEarned = 0,
                chestClaimed = true,
                syncedAt = 0L,
            )
        }
        assertFalse(HatchRules.isUnlocked(SpeciesCatalog.PHOENIX, heavilyLoggedButLowHarmony, harmony = 0))
    }

    @Test
    fun `the phoenix unlocks at exactly harmony 10`() {
        assertTrue(HatchRules.isUnlocked(SpeciesCatalog.PHOENIX, emptyList(), harmony = 10))
        assertTrue(HatchRules.isUnlocked(SpeciesCatalog.PHOENIX, emptyList(), harmony = 11))
        assertFalse(HatchRules.isUnlocked(SpeciesCatalog.PHOENIX, emptyList(), harmony = 9))
    }

    @Test
    fun `the phoenix's harmony requirement matches the Mythic tier threshold`() {
        val mythic = HarmonyRules.TIERS.first { it.id == "mythic" }
        assertEquals(mythic.minHarmony, SpeciesCatalog.PHOENIX.minHarmony)
    }

    @Test
    fun `hatch cost is 120 sparks`() {
        assertEquals(120, SpeciesCatalog.PHOENIX.hatchCostSparks)
    }

    @Test
    fun `the Mythic tier's unlock set names the phoenix and the Aurora Crown`() {
        val mythic = HarmonyRules.TIERS.first { it.id == "mythic" }
        assertEquals(setOf(SpeciesCatalog.PHOENIX.key), mythic.bonuses.unlockedSpeciesIds)
        assertEquals(setOf(ShopCatalog.AURORA_CROWN.id), mythic.bonuses.unlockedHatIds)
    }
}
