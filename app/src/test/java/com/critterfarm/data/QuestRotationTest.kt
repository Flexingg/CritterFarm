package com.critterfarm.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The daily rotation must be pure and deterministic — no wall-clock randomness allowed. */
class QuestRotationTest {

    private val pool = QuestCatalog.ALL

    @Test
    fun `the pool has no duplicate ids`() {
        assertEquals(pool.size, pool.map { it.id }.distinct().size)
    }

    @Test
    fun `every quest pays a positive reward`() {
        pool.forEach { quest ->
            assertTrue(
                "${quest.id} pays nothing",
                quest.rewardCoins > 0 || quest.rewardTreats > 0,
            )
        }
    }

    @Test
    fun `the pool has at least twelve quests spanning six metrics`() {
        assertTrue(pool.size >= 12)
        assertEquals(Metric.entries.toSet(), pool.map { it.metric }.toSet())
    }

    @Test
    fun `exactly three quests are returned for a day`() {
        val quests = QuestsForDay.forDate(LocalDate.of(2026, 9, 19), pool)
        assertEquals(3, quests.size)
    }

    @Test
    fun `the same date always yields the same three quests`() {
        val date = LocalDate.of(2026, 9, 19)
        val first = QuestsForDay.forDate(date, pool)
        val second = QuestsForDay.forDate(date, pool)
        assertEquals(first.map { it.id }, second.map { it.id })
    }

    @Test
    fun `a day never repeats a quest within its own three`() {
        val quests = QuestsForDay.forDate(LocalDate.of(2026, 9, 19), pool)
        assertEquals(quests.size, quests.map { it.id }.distinct().size)
    }

    @Test
    fun `three consecutive dates are not all identical`() {
        val base = LocalDate.of(2026, 9, 19)
        val day1 = QuestsForDay.forDate(base, pool).map { it.id }
        val day2 = QuestsForDay.forDate(base.plusDays(1), pool).map { it.id }
        val day3 = QuestsForDay.forDate(base.plusDays(2), pool).map { it.id }
        assertTrue(
            "three consecutive days should not all pick the exact same three quests",
            day1 != day2 || day2 != day3 || day1 != day3,
        )
    }
}
