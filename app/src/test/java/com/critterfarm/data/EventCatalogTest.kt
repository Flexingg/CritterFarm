package com.critterfarm.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The seasonal calendar: unique, well-ordered events, and the one guaranteed to be live now. */
class EventCatalogTest {

    @Test
    fun `there are at least four events`() {
        assertTrue(EventCatalog.ALL.size >= 4)
    }

    @Test
    fun `every event id is unique`() {
        val ids = EventCatalog.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `every event ends after it starts`() {
        EventCatalog.ALL.forEach { event ->
            assertTrue("${event.id} end must be after start", event.end.isAfter(event.start))
        }
    }

    @Test
    fun `an event is active on the day the emulator is seeded for`() {
        val seeded = LocalDate.of(2026, 9, 19)
        assertNotNull(EventCatalog.activeOn(seeded))
    }

    @Test
    fun `activeOn returns nothing outside every window`() {
        val quiet = EventCatalog.ALL.map { it.end.plusDays(1) }
            .firstOrNull { candidate -> EventCatalog.ALL.none { candidate in it.start..it.end } }
        assertNotNull("expected at least one date with nothing running", quiet)
        assertNull(EventCatalog.activeOn(quiet!!))
    }

    @Test
    fun `isActive agrees with activeOn`() {
        val event = EventCatalog.ALL.first()
        assertTrue(EventCatalog.isActive(event.id, event.start))
        assertTrue(EventCatalog.isActive(event.id, event.end))
        assertEquals(false, EventCatalog.isActive(event.id, event.end.plusDays(1)))
    }

    @Test
    fun `byId resolves every event and rejects nonsense`() {
        EventCatalog.ALL.forEach { assertEquals(it, EventCatalog.byId(it.id)) }
        assertNull(EventCatalog.byId("no_such_event"))
    }

    @Test
    fun `every event id referenced by decor exists in the calendar`() {
        DecorCatalog.ALL.mapNotNull { it.eventId }.forEach { eventId ->
            assertNotNull("decor references unknown event $eventId", EventCatalog.byId(eventId))
        }
    }

    @Test
    fun `every event id referenced by a challenge spec exists in the calendar`() {
        ChallengeCatalog.ALL.mapNotNull { it.eventId }.forEach { eventId ->
            assertNotNull("challenge references unknown event $eventId", EventCatalog.byId(eventId))
        }
    }

    @Test
    fun `at least three decorations are event-exclusive`() {
        assertTrue(DecorCatalog.ALL.count { it.eventId != null } >= 3)
    }
}
