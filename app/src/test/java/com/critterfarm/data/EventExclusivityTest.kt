package com.critterfarm.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scarcity guarantee for event decorations: gone from the catalogue entirely outside the
 * window, and refused by [PlacementRules] even if a caller has the id and unlimited coins.
 */
class EventExclusivityTest {

    private val exclusiveItem = DecorCatalog.ALL.first { it.eventId != null }
    private val event = EventCatalog.byId(exclusiveItem.eventId!!)!!

    @Test
    fun `an exclusive item is available inside its event window`() {
        val available = DecorCatalog.availableOn(event.start).map { it.id }
        assertTrue(exclusiveItem.id in available)
    }

    @Test
    fun `an exclusive item is excluded the day before and the day after its window`() {
        val before = DecorCatalog.availableOn(event.start.minusDays(1)).map { it.id }
        val after = DecorCatalog.availableOn(event.end.plusDays(1)).map { it.id }
        assertFalse(exclusiveItem.id in before)
        assertFalse(exclusiveItem.id in after)
    }

    @Test
    fun `normal items are always available`() {
        val normal = DecorCatalog.ALL.filter { it.eventId == null }
        val outsideAnyEvent = event.end.plusDays(1)
        val available = DecorCatalog.availableOn(outsideAnyEvent).map { it.id }.toSet()
        normal.forEach { assertTrue("${it.id} should always be available", it.id in available) }
    }

    @Test
    fun `PlacementRules refuses an out-of-season event item even with unlimited coins`() {
        val outOfSeason = event.end.plusDays(1)
        val result = PlacementRules.validate(
            itemId = exclusiveItem.id,
            cellIndex = 0,
            coins = 1_000_000,
            occupiedBy = emptyMap(),
            today = outOfSeason,
        )
        assertTrue(result is PlaceResult.OutOfSeason)
        assertEquals(exclusiveItem.id, (result as PlaceResult.OutOfSeason).item.id)
    }

    @Test
    fun `PlacementRules allows the same item once its event is running`() {
        val result = PlacementRules.validate(
            itemId = exclusiveItem.id,
            cellIndex = 0,
            coins = 1_000_000,
            occupiedBy = emptyMap(),
            today = event.start,
        )
        assertTrue(result is PlaceResult.Placed)
    }

    @Test
    fun `an in-season event item still enforces the ordinary placement rules`() {
        // Cannot afford, even during the window.
        val cannotAfford = PlacementRules.validate(
            itemId = exclusiveItem.id,
            cellIndex = 0,
            coins = 0,
            occupiedBy = emptyMap(),
            today = event.start,
        )
        assertTrue(cannotAfford is PlaceResult.CannotAfford)
    }
}
