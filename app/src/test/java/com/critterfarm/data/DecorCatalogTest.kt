package com.critterfarm.data

import com.critterfarm.data.local.DecorPlacementEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DecorCatalogTest {

    @Test
    fun `every decoration has a unique id`() {
        val ids = DecorCatalog.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `the catalogue offers a real long-term sink`() {
        // The farm is meant to be decorated over months, not bought out in a week.
        assertTrue("expected at least 10 items", DecorCatalog.ALL.size >= 10)
        assertEquals(100, DecorCatalog.ALL.minOf { it.price })
        assertEquals(1_500, DecorCatalog.ALL.maxOf { it.price })
    }

    @Test
    fun `every price is positive and rising with the sorted view`() {
        assertTrue(DecorCatalog.ALL.all { it.price > 0 })
        val sorted = DecorCatalog.BY_PRICE.map { it.price }
        assertEquals(sorted.sorted(), sorted)
    }

    @Test
    fun `every decoration has real user-facing copy`() {
        DecorCatalog.ALL.forEach { item ->
            assertTrue("${item.id} needs a name", item.name.isNotBlank())
            assertTrue("${item.id} needs a blurb", item.blurb.length > 15)
            assertTrue("${item.id} needs an emoji", item.emoji.isNotBlank())
        }
    }

    @Test
    fun `all three slots are represented`() {
        val slots = DecorCatalog.ALL.map { it.slot }.toSet()
        assertEquals(DecorSlot.entries.toSet(), slots)
    }

    @Test
    fun `lookup by id resolves every item and rejects nonsense`() {
        DecorCatalog.ALL.forEach { assertTrue(DecorCatalog.item(it.id) == it) }
        assertNull(DecorCatalog.item("decor_nonexistent"))
    }

    @Test
    fun `the grid is the size the scene draws`() {
        assertEquals(6, DecorCatalog.GRID_COLUMNS)
        assertEquals(4, DecorCatalog.GRID_ROWS)
        assertEquals(24, DecorCatalog.CELL_COUNT)
    }

    @Test
    fun `item counts group placements by decoration`() {
        val placements = listOf(
            DecorPlacementEntity(0, "decor_flowers", 1L),
            DecorPlacementEntity(5, "decor_flowers", 2L),
            DecorPlacementEntity(7, "decor_pond", 3L),
        )
        val counts = PlacementRules.countsByItem(placements)
        assertEquals(2, counts["decor_flowers"])
        assertEquals(1, counts["decor_pond"])
        assertNull(counts["decor_farmhouse"])
    }

    @Test
    fun `a placement is in range across the whole board and nowhere outside it`() {
        assertTrue(PlacementRules.isInRange(0))
        assertTrue(PlacementRules.isInRange(DecorCatalog.CELL_COUNT - 1))
        assertFalse(PlacementRules.isInRange(-1))
        assertFalse(PlacementRules.isInRange(DecorCatalog.CELL_COUNT))
        assertFalse(PlacementRules.isInRange(24))
        assertFalse(PlacementRules.isInRange(25))
    }
}
