package com.critterfarm.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The catalogue backing the barn — six species, a real cost ladder, no blanks. */
class SpeciesCatalogTest {

    @Test
    fun `every species key is unique`() {
        val keys = SpeciesCatalog.ALL.map { it.key }
        assertEquals(keys.size, keys.distinct().size)
    }

    @Test
    fun `the blob is the free starter`() {
        assertEquals(0, SpeciesCatalog.BLOB.hatchCostSparks)
        assertEquals("blob", SpeciesCatalog.BLOB.key)
    }

    @Test
    fun `every species resolves by key`() {
        SpeciesCatalog.ALL.forEach { species ->
            assertEquals(species, SpeciesCatalog.bySpecies(species.key))
        }
    }

    @Test
    fun `an unknown key resolves to nothing`() {
        assertEquals(null, SpeciesCatalog.bySpecies("does_not_exist"))
    }

    @Test
    fun `hatch costs strictly increase down the ladder`() {
        val costs = SpeciesCatalog.ALL.map { it.hatchCostSparks }
        val sorted = costs.sorted()
        assertEquals(sorted, costs)
        assertEquals(costs.size, costs.distinct().size)
    }

    @Test
    fun `every species has non-blank user-facing copy`() {
        SpeciesCatalog.ALL.forEach { species ->
            assertTrue("${species.key} needs a display name", species.displayName.isNotBlank())
            assertTrue("${species.key} needs an emoji", species.emoji.isNotBlank())
            assertTrue("${species.key} needs a blurb", species.blurb.isNotBlank())
            assertTrue("${species.key} needs an unlock note", species.unlockNote.isNotBlank())
        }
    }
}
