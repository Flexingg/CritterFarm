package com.critterfarm.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The v1.2 sink economy: what currency buys, what the streak pays, and what feeding does.
 * These are the numbers that decide whether the grind feels worth it, so they are pinned down.
 */
class GameEconomyTest {

    private val today = LocalDate.of(2026, 9, 19)

    // ------------------------------------------------------------------ streak multiplier ----

    @Test
    fun `streak multiplier tiers`() {
        assertEquals(1.0, GameRules.claimMultiplier(0), 0.001)
        assertEquals(1.0, GameRules.claimMultiplier(2), 0.001)
        assertEquals(1.5, GameRules.claimMultiplier(3), 0.001)
        assertEquals(1.5, GameRules.claimMultiplier(6), 0.001)
        assertEquals(2.0, GameRules.claimMultiplier(7), 0.001)
        assertEquals(2.0, GameRules.claimMultiplier(29), 0.001)
        assertEquals(3.0, GameRules.claimMultiplier(30), 0.001)
    }

    @Test
    fun `multiplier scales rewards and rounds`() {
        assertEquals(150, GameRules.applyMultiplier(100, 1.5))
        assertEquals(200, GameRules.applyMultiplier(100, 2.0))
        assertEquals(100, GameRules.applyMultiplier(100, 1.0))
    }

    // ----------------------------------------------------------------------- streak flow ----

    @Test
    fun `first ever claim starts a streak of one`() {
        val outcome = GameRules.nextStreak(0, null, today, freezesAvailable = 0)
        assertEquals(1, outcome.streak)
        assertFalse(outcome.freezeUsed)
    }

    @Test
    fun `claiming the next day grows the streak`() {
        val outcome = GameRules.nextStreak(4, today.minusDays(1).toString(), today, 0)
        assertEquals(5, outcome.streak)
    }

    @Test
    fun `claiming twice in one day does not inflate the streak`() {
        val outcome = GameRules.nextStreak(4, today.toString(), today, 0)
        assertEquals(4, outcome.streak)
        assertFalse(outcome.freezeUsed)
    }

    @Test
    fun `a missed day spends a freeze and keeps the chain`() {
        val outcome = GameRules.nextStreak(9, today.minusDays(3).toString(), today, freezesAvailable = 2)
        assertEquals(10, outcome.streak)
        assertTrue(outcome.freezeUsed)
        assertEquals(1, outcome.freezesLeft)
    }

    @Test
    fun `a missed day with no freeze resets the chain`() {
        val outcome = GameRules.nextStreak(9, today.minusDays(2).toString(), today, freezesAvailable = 0)
        assertEquals(1, outcome.streak)
        assertFalse(outcome.freezeUsed)
    }

    // ---------------------------------------------------------------------------- feeding ----

    @Test
    fun `feeding raises happiness and relieves hunger`() {
        val outcome = GameRules.feed(happiness = 40, hunger = 70)
        assertEquals(55, outcome.happiness)
        assertEquals(30, outcome.hunger)
    }

    @Test
    fun `feeding clamps at the caps`() {
        val outcome = GameRules.feed(happiness = 95, hunger = 10)
        assertEquals(100, outcome.happiness)
        assertEquals(0, outcome.hunger)
    }

    // --------------------------------------------------------------------- hat inventory ----

    @Test
    fun `owned hats survive a serialize and parse round trip`() {
        val owned = setOf("hat_crown", "hat_straw", "hat_beanie")
        val csv = GameRules.serializeOwnedHatIds(owned)
        assertEquals("hat_beanie,hat_crown,hat_straw", csv)
        assertEquals(owned, GameRules.parseOwnedHatIds(csv))
    }

    @Test
    fun `empty inventory parses to no hats`() {
        assertEquals(emptySet<String>(), GameRules.parseOwnedHatIds(""))
    }

    // ------------------------------------------------------------------------ shop prices ----

    @Test
    fun `coins pay for coin items and sparks for spark items`() {
        assertTrue(GameRules.canAfford(ShopCatalog.STRAW_HAT, coins = 150, manaSparks = 0))
        assertFalse(GameRules.canAfford(ShopCatalog.STRAW_HAT, coins = 149, manaSparks = 999))
        assertTrue(GameRules.canAfford(ShopCatalog.HALO, coins = 0, manaSparks = 30))
        assertFalse(GameRules.canAfford(ShopCatalog.HALO, coins = 99_999, manaSparks = 29))
    }

    @Test
    fun `every catalogue id is unique and resolvable`() {
        val ids = ShopCatalog.ALL.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        ids.forEach { assertEquals(it, ShopCatalog.item(it)?.id) }
    }

    @Test
    fun `streak freezes are the only repeatable purchase`() {
        assertTrue(ShopCatalog.STREAK_FREEZE.repeatable)
        assertTrue(ShopCatalog.HATS.none { it.repeatable })
    }

    /**
     * The guardrail this whole economy exists to protect: nothing purchasable may improve
     * health progress. If someone adds such an item later, this test should fail loudly.
     */
    @Test
    fun `the catalogue only sells cosmetics and streak insurance`() {
        ShopCatalog.ALL.forEach { item ->
            assertTrue(
                "unexpected purchasable kind: ${item.kind}",
                item.kind == ShopItemKind.HAT || item.kind == ShopItemKind.STREAK_FREEZE,
            )
        }
    }
}
