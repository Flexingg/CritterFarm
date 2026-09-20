package com.critterfarm.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * A single-use offer to undo one missed day, priced honestly: it buys back the *chain*, never the
 * work that would have earned rewards on that day. [GameRepository.repairStreak] is the only
 * caller; everything here is pure so the guardrail (no retroactive rewards) can be pinned down in
 * a unit test rather than trusted by inspection.
 */
data class RepairOption(
    val cost: Int,
    val missedDate: LocalDate,
    val coinsAfter: Int,
    val streakDaysRestored: Int,
)

object StreakRepairRules {

    private const val BASE_COST = 100
    private const val COST_PER_STREAK_DAY = 25
    private const val MAX_COST = 1_000

    /** How many days were missed between [lastClaimedDate] and [today]. Zero if never claimed. */
    fun gapDays(lastClaimedDate: String?, today: LocalDate): Long {
        val last = lastClaimedDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return 0L
        val daysSince = ChronoUnit.DAYS.between(last, today)
        return (daysSince - 1).coerceAtLeast(0L)
    }

    /** 100 + 25 coins per day of streak protected, capped at 1,000 — a long chain costs more to save. */
    fun repairCost(streakDays: Int): Int =
        (BASE_COST + COST_PER_STREAK_DAY * streakDays.coerceAtLeast(0)).coerceAtMost(MAX_COST)

    /**
     * The date that would be repaired, ignoring affordability — null when repairing does not
     * apply at all: more than one day was missed, the chain was never alive, or this exact gap
     * was already repaired once. Split out from [canRepair] so a caller can tell "not eligible"
     * apart from "eligible but cannot pay."
     */
    fun eligibleGap(
        streakDays: Int,
        lastClaimedDate: String?,
        lastRepairedGapDate: String?,
        today: LocalDate,
    ): LocalDate? {
        if (streakDays <= 0) return null
        val last = lastClaimedDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        if (gapDays(lastClaimedDate, today) != 1L) return null

        val missedDate = last.plusDays(1)
        if (lastRepairedGapDate == missedDate.toString()) return null
        return missedDate
    }

    /**
     * The repair offer, or null when repairing does not apply or the player cannot afford it.
     */
    fun canRepair(
        streakDays: Int,
        lastClaimedDate: String?,
        lastRepairedGapDate: String?,
        today: LocalDate,
        coins: Int,
    ): RepairOption? {
        val missedDate = eligibleGap(streakDays, lastClaimedDate, lastRepairedGapDate, today) ?: return null
        val cost = repairCost(streakDays)
        if (coins < cost) return null

        return RepairOption(
            cost = cost,
            missedDate = missedDate,
            coinsAfter = coins - cost,
            streakDaysRestored = streakDays,
        )
    }
}
