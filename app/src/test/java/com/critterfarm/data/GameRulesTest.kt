package com.critterfarm.data

import com.critterfarm.data.local.CritterEntity
import com.critterfarm.data.local.CritterMood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameRulesTest {

    private fun critter(
        xp: Int = 0,
        level: Int = 1,
        happiness: Int = 50,
        hunger: Int = 50,
        mood: CritterMood = CritterMood.NEUTRAL,
    ) = CritterEntity(
        name = "Sprout the Blob",
        species = "Blob",
        stage = 0,
        xp = xp,
        level = level,
        happiness = happiness,
        hunger = hunger,
        mood = mood,
        lastFedAt = 0L,
        createdAt = 0L,
    )

    // --- xpRequiredForLevel / applyXp -----------------------------------------------------

    @Test
    fun `xp requirement grows with level`() {
        val level1 = GameRules.xpRequiredForLevel(1)
        val level5 = GameRules.xpRequiredForLevel(5)
        val level10 = GameRules.xpRequiredForLevel(10)

        assertEquals(40, level1)
        assertTrue("level 5 should cost more than level 1", level5 > level1)
        assertTrue("level 10 should cost more than level 5", level10 > level5)
    }

    @Test
    fun `applyXp does not level up when below the threshold`() {
        val result = GameRules.applyXp(critter(xp = 0, level = 1), xpGained = 10)

        assertEquals(1, result.level)
        assertEquals(10, result.xp)
    }

    @Test
    fun `applyXp levels up exactly once when xp meets the threshold`() {
        val requirement = GameRules.xpRequiredForLevel(1)
        val result = GameRules.applyXp(critter(xp = 0, level = 1), xpGained = requirement)

        assertEquals(2, result.level)
        assertEquals(0, result.xp)
    }

    @Test
    fun `applyXp resolves multiple level-ups in one call`() {
        val hugeGain = GameRules.xpRequiredForLevel(1) +
            GameRules.xpRequiredForLevel(2) +
            GameRules.xpRequiredForLevel(3) +
            5
        val result = GameRules.applyXp(critter(xp = 0, level = 1), xpGained = hugeGain)

        assertEquals(4, result.level)
        assertEquals(5, result.xp)
    }

    @Test
    fun `applyXp carries over existing xp before adding new xp`() {
        val result = GameRules.applyXp(critter(xp = 5, level = 1), xpGained = 3)

        assertEquals(1, result.level)
        assertEquals(8, result.xp)
    }

    // --- coinsFromSteps ---------------------------------------------------------------------

    @Test
    fun `coinsFromSteps converts every 100 steps into one coin`() {
        assertEquals(0, GameRules.coinsFromSteps(0))
        assertEquals(0, GameRules.coinsFromSteps(99))
        assertEquals(1, GameRules.coinsFromSteps(100))
        assertEquals(2, GameRules.coinsFromSteps(250))
        assertEquals(100, GameRules.coinsFromSteps(10_000))
    }

    // --- treatsFromWorkouts -------------------------------------------------------------------

    @Test
    fun `treatsFromWorkouts pays 5 treats per workout`() {
        assertEquals(0, GameRules.treatsFromWorkouts(0))
        assertEquals(5, GameRules.treatsFromWorkouts(1))
        assertEquals(15, GameRules.treatsFromWorkouts(3))
    }

    // --- manaSparksFromDeficit (the crash-diet guardrail) ------------------------------------

    @Test
    fun `manaSparksFromDeficit pays zero for a zero or negative deficit`() {
        assertEquals(0, GameRules.manaSparksFromDeficit(0.0, 2000.0).manaSparks)
        assertEquals(0, GameRules.manaSparksFromDeficit(-500.0, 2000.0).manaSparks)
    }

    @Test
    fun `manaSparksFromDeficit scales linearly within the safe band`() {
        assertEquals(5, GameRules.manaSparksFromDeficit(250.0, 2000.0).manaSparks)
        assertEquals(10, GameRules.manaSparksFromDeficit(500.0, 2000.0).manaSparks)
        assertEquals(20, GameRules.manaSparksFromDeficit(1000.0, 2000.0).manaSparks)
    }

    @Test
    fun `manaSparksFromDeficit flat-lines past the medically sane ceiling`() {
        val atCeiling = GameRules.manaSparksFromDeficit(1000.0, 2000.0).manaSparks
        val wayOverCeiling = GameRules.manaSparksFromDeficit(5000.0, 2000.0).manaSparks

        assertEquals("a crash-diet deficit must not out-earn the safe ceiling", atCeiling, wayOverCeiling)
    }

    @Test
    fun `manaSparksFromDeficit adds a supportive message for very low but logged intake`() {
        val reward = GameRules.manaSparksFromDeficit(800.0, 800.0)

        assertNotNull("a very low logged intake should get supportive copy, not silence", reward.supportiveMessage)
    }

    @Test
    fun `manaSparksFromDeficit does not shame a normal intake`() {
        val reward = GameRules.manaSparksFromDeficit(500.0, 2000.0)

        assertNull(reward.supportiveMessage)
    }

    @Test
    fun `manaSparksFromDeficit does not flag missing intake data as low intake`() {
        val missingData = GameRules.manaSparksFromDeficit(500.0, null)
        val zeroReading = GameRules.manaSparksFromDeficit(500.0, 0.0)

        assertNull("null means unread, not a zero-calorie day", missingData.supportiveMessage)
        assertNull("a literal 0.0 reading is most likely absent data, not a fast", zeroReading.supportiveMessage)
    }

    // --- hydrationMoodBoost -------------------------------------------------------------------

    @Test
    fun `hydrationMoodBoost scales then clamps to the maximum`() {
        assertEquals(0, GameRules.hydrationMoodBoost(0.0))
        assertEquals(1, GameRules.hydrationMoodBoost(250.0))
        assertEquals(10, GameRules.hydrationMoodBoost(2500.0))
        assertEquals(10, GameRules.hydrationMoodBoost(10_000.0))
    }

    // --- deriveMood -----------------------------------------------------------------------

    @Test
    fun `deriveMood celebrates a claimed chest with a big step day`() {
        val mood = GameRules.deriveMood(
            critter(happiness = 50, hunger = 50),
            todaySteps = 8500,
            todayWorkouts = 0,
            chestClaimedToday = true,
        )

        assertEquals(CritterMood.CELEBRATING, mood)
    }

    @Test
    fun `deriveMood celebrates an active workout day even without claiming`() {
        val mood = GameRules.deriveMood(
            critter(happiness = 50, hunger = 50),
            todaySteps = 6000,
            todayWorkouts = 1,
            chestClaimedToday = false,
        )

        assertEquals(CritterMood.CELEBRATING, mood)
    }

    @Test
    fun `deriveMood is sluggish when hunger is high`() {
        val mood = GameRules.deriveMood(
            critter(happiness = 80, hunger = 90),
            todaySteps = 6000,
            todayWorkouts = 0,
            chestClaimedToday = false,
        )

        assertEquals(CritterMood.SLUGGISH_TIRED, mood)
    }

    @Test
    fun `deriveMood is sluggish when inactive and unhappy`() {
        val mood = GameRules.deriveMood(
            critter(happiness = 20, hunger = 30),
            todaySteps = 100,
            todayWorkouts = 0,
            chestClaimedToday = false,
        )

        assertEquals(CritterMood.SLUGGISH_TIRED, mood)
    }

    @Test
    fun `deriveMood is bouncing happy for a content critter`() {
        val mood = GameRules.deriveMood(
            critter(happiness = 75, hunger = 20),
            todaySteps = 3000,
            todayWorkouts = 0,
            chestClaimedToday = false,
        )

        assertEquals(CritterMood.BOUNCING_HAPPY, mood)
    }

    @Test
    fun `deriveMood falls back to neutral otherwise`() {
        val mood = GameRules.deriveMood(
            critter(happiness = 50, hunger = 40),
            todaySteps = 3000,
            todayWorkouts = 0,
            chestClaimedToday = false,
        )

        assertEquals(CritterMood.NEUTRAL, mood)
    }

    @Test
    fun `deriveMood treats a null steps reading as zero rather than crashing`() {
        val mood = GameRules.deriveMood(
            critter(happiness = 20, hunger = 30),
            todaySteps = null,
            todayWorkouts = 0,
            chestClaimedToday = false,
        )

        assertEquals(CritterMood.SLUGGISH_TIRED, mood)
    }
}
