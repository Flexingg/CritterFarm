package com.critterfarm.data

import com.critterfarm.data.local.DecorPlacementEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacementRulesTest {

    private fun occupied(vararg pairs: Pair<Int, String>): Map<Int, String> = pairs.toMap()

    @Test
    fun `an affordable square places and charges exactly one copy`() {
        val result = PlacementRules.validate("decor_flowers", 0, coins = 500, occupied())
        assertTrue(result is PlaceResult.Placed)
        result as PlaceResult.Placed
        assertEquals(400, result.coinsLeft)
        assertEquals(0, result.cellIndex)
    }

    @Test
    fun `the price is charged per copy, so two of the same item cost twice`() {
        val first = PlacementRules.validate("decor_haybale", 0, coins = 300, occupied()) as PlaceResult.Placed
        assertEquals(150, first.coinsLeft)
        // Same item, different square, second charge.
        val second = PlacementRules.validate(
            "decor_haybale",
            1,
            coins = first.coinsLeft,
            occupied(0 to "decor_haybale"),
        ) as PlaceResult.Placed
        assertEquals(0, second.coinsLeft)
    }

    @Test
    fun `exactly enough coins is enough`() {
        val result = PlacementRules.validate("decor_logs", 3, coins = 300, occupied())
        assertTrue(result is PlaceResult.Placed)
        assertEquals(0, (result as PlaceResult.Placed).coinsLeft)
    }

    @Test
    fun `one coin short is refused with the exact shortfall`() {
        val result = PlacementRules.validate("decor_scarecrow", 3, coins = 599, occupied())
        assertTrue(result is PlaceResult.CannotAfford)
        result as PlaceResult.CannotAfford
        assertEquals(1, result.shortfall)
        assertEquals(599, result.coins)
    }

    @Test
    fun `shortfall never goes negative`() {
        assertEquals(0, PlacementRules.shortfall(DecorCatalog.item("decor_flowers")!!, 9_999))
    }

    @Test
    fun `out of bounds is refused before anything is charged`() {
        listOf(-1, 24, 25, 999).forEach { cell ->
            val result = PlacementRules.validate("decor_flowers", cell, coins = 10_000, occupied())
            assertTrue("cell $cell should be out of range", result is PlaceResult.CellOutOfRange)
            assertEquals(cell, (result as PlaceResult.CellOutOfRange).cellIndex)
        }
    }

    @Test
    fun `an occupied square is refused and names what is standing there`() {
        val result = PlacementRules.validate(
            "decor_pond",
            7,
            coins = 10_000,
            occupied(7 to "decor_appletree"),
        )
        assertTrue(result is PlaceResult.CellOccupied)
        result as PlaceResult.CellOccupied
        assertEquals(7, result.cellIndex)
        assertEquals("decor_appletree", result.existingId)
    }

    @Test
    fun `an unknown item is refused`() {
        val result = PlacementRules.validate("decor_unicorn", 0, coins = 10_000, occupied())
        assertEquals(PlaceResult.UnknownItem, result)
    }

    @Test
    fun `the whole board can be filled and the next square is refused`() {
        // Start with plenty of coins and walk the entire grid.
        var coins = 1_000_000
        val placed = mutableMapOf<Int, String>()
        repeat(DecorCatalog.CELL_COUNT) { cell ->
            val result = PlacementRules.validate("decor_flowers", cell, coins, placed)
            assertTrue("cell $cell should place", result is PlaceResult.Placed)
            coins = (result as PlaceResult.Placed).coinsLeft
            placed[cell] = "decor_flowers"
        }
        assertEquals(DecorCatalog.CELL_COUNT, placed.size)
        // Every cell is taken now: placing anywhere is an occupied refusal, never a charge.
        val last = PlacementRules.validate("decor_flowers", 0, coins, placed)
        assertTrue(last is PlaceResult.CellOccupied)
    }

    @Test
    fun `a locked-out player cannot place even with the square free`() {
        val result = PlacementRules.validate("decor_farmhouse", 12, coins = 0, occupied())
        assertTrue(result is PlaceResult.CannotAfford)
        assertEquals(1_500, (result as PlaceResult.CannotAfford).shortfall)
    }

    @Test
    fun `counting placements tolerates an empty farm`() {
        assertEquals(emptyMap<String, Int>(), PlacementRules.countsByItem(emptyList()))
    }

    @Test
    fun `a placement entity keeps its cell and item`() {
        val entity = DecorPlacementEntity(cellIndex = 9, decorId = "decor_beehive", placedAt = 42L)
        assertEquals(9, entity.cellIndex)
        assertEquals("decor_beehive", entity.decorId)
        assertEquals(42L, entity.placedAt)
    }
}
