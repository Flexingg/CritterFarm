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

    fun forDate(date: LocalDate, quests: List<Quest>): List<Quest> {
        if (quests.isEmpty()) return emptyList()
        val rng = Random(date.toEpochDay())
        return quests.shuffled(rng).take(QUESTS_PER_DAY.coerceAtMost(quests.size))
    }
}
