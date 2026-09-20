package com.critterfarm.data

import java.time.LocalDate
import kotlin.random.Random

/**
 * Picks today's three quests from the pool. Seeded by the epoch day (a stable hash of the
 * calendar date) rather than wall-clock [Random], so the same day always yields the same three
 * quests, consecutive days differ, and there is nothing to persist — the rotation regenerates
 * itself from the date alone.
 */
object QuestsForDay {
    private const val QUESTS_PER_DAY = 3

    /**
     * [count] defaults to the original three so every existing caller and test is unaffected;
     * Barn Harmony's Working Farm tier onward passes a bigger number, which is a *capability*
     * (more quests to do), never a bigger reward for the same quest.
     */
    fun forDate(date: LocalDate, quests: List<Quest>, count: Int = QUESTS_PER_DAY): List<Quest> {
        if (quests.isEmpty()) return emptyList()
        val rng = Random(date.toEpochDay())
        return quests.shuffled(rng).take(count.coerceIn(0, quests.size))
    }
}
