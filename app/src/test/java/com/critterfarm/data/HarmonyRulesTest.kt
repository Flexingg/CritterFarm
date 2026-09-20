package com.critterfarm.data

import com.critterfarm.data.local.CritterEntity
import com.critterfarm.data.local.CritterMood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Barn Harmony: breadth (species) and depth (evolution) both count toward one number, and every
 * tier it unlocks is a capability, never a reward multiplier.
 */
class HarmonyRulesTest {

    private fun critter(stage: Int) = CritterEntity(
        name = "Test",
        species = "blob",
        stage = stage,
        xp = 0,
        level = 1,
        happiness = 50,
        hunger = 50,
        mood = CritterMood.NEUTRAL,
        lastFedAt = 0,
        createdAt = 0,
    )

    // -------------------------------------------------------------------------- the sum ----

    @Test
    fun `a lone stage-0 starter is harmony one`() {
        assertEquals(1, HarmonyRules.harmony(listOf(critter(stage = 0))))
    }

    @Test
    fun `harmony sums stage plus one across every owned critter`() {
        val critters = listOf(critter(stage = 2), critter(stage = 2))
        assertEquals(6, HarmonyRules.harmony(critters))
    }

    @Test
    fun `breadth and depth both count`() {
        // Two stage-0 starters and one stage-2 critter: 1 + 1 + 3 = 5.
        val critters = listOf(critter(stage = 0), critter(stage = 0), critter(stage = 2))
        assertEquals(5, HarmonyRules.harmony(critters))
    }

    // ---------------------------------------------------------------------------- tierFor ----

    @Test
    fun `tierFor returns the right tier at exactly each threshold and one below it`() {
        assertEquals("quiet", HarmonyRules.tierFor(1).id)
        assertEquals("quiet", HarmonyRules.tierFor(2).id)
        assertEquals("working", HarmonyRules.tierFor(3).id)
        assertEquals("working", HarmonyRules.tierFor(5).id)
        assertEquals("thriving", HarmonyRules.tierFor(6).id)
        assertEquals("thriving", HarmonyRules.tierFor(9).id)
        assertEquals("mythic", HarmonyRules.tierFor(10).id)
        assertEquals("mythic", HarmonyRules.tierFor(999).id)
    }

    @Test
    fun `harmony zero clamps to the first tier`() {
        assertEquals(HarmonyRules.TIERS.first(), HarmonyRules.tierFor(0))
    }

    // --------------------------------------------------------------------------- nextTier ----

    @Test
    fun `nextTier points at the next rung up`() {
        assertEquals("working", HarmonyRules.nextTier(1)?.id)
        assertEquals("thriving", HarmonyRules.nextTier(3)?.id)
        assertEquals("mythic", HarmonyRules.nextTier(6)?.id)
    }

    @Test
    fun `nextTier is null at the top`() {
        assertNull(HarmonyRules.nextTier(10))
        assertNull(HarmonyRules.nextTier(999))
    }

    // --------------------------------------------------------------------- progressToNext ----

    @Test
    fun `progress to next reads sensibly`() {
        assertEquals("7 / 10 harmony", HarmonyRules.progressToNext(7))
        assertEquals("1 / 3 harmony", HarmonyRules.progressToNext(1))
    }

    @Test
    fun `progress to next at the top tier does not name a target`() {
        assertEquals("top tier reached", HarmonyRules.progressToNext(10))
    }

    // ------------------------------------------------------------------- catalogue shape ----

    @Test
    fun `tiers are ordered ascending by threshold`() {
        val thresholds = HarmonyRules.TIERS.map { it.minHarmony }
        assertEquals(thresholds.sorted(), thresholds)
    }

    @Test
    fun `every tier id is unique`() {
        val ids = HarmonyRules.TIERS.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    /**
     * The guardrail this whole system exists to protect: no tier may grant a reward-multiplying
     * bonus. [HarmonyBonuses] only has capability fields (more quests, more room, more unlocks),
     * so pinning down the exact expected value for every tier is itself the proof — there is no
     * multiplier field to accidentally set.
     */
    @Test
    fun `no tier grants any reward-multiplying bonus`() {
        val expected = mapOf(
            "quiet" to HarmonyBonuses(),
            "working" to HarmonyBonuses(extraDailyQuests = 1),
            "thriving" to HarmonyBonuses(extraDailyQuests = 1, decorRows = 1),
            "mythic" to HarmonyBonuses(
                extraDailyQuests = 1,
                decorRows = 1,
                unlockedSpeciesIds = setOf("phoenix"),
                unlockedHatIds = setOf("hat_aurora_crown"),
            ),
        )
        HarmonyRules.TIERS.forEach { tier ->
            assertEquals("bonus mismatch for ${tier.id}", expected[tier.id], tier.bonuses)
        }
    }

    @Test
    fun `the base tier grants nothing extra`() {
        val quiet = HarmonyRules.tierFor(1)
        assertFalse(quiet.bonuses.extraDailyQuests > 0)
        assertFalse(quiet.bonuses.decorRows > 0)
        assertTrue(quiet.bonuses.unlockedSpeciesIds.isEmpty())
        assertTrue(quiet.bonuses.unlockedHatIds.isEmpty())
    }
}
