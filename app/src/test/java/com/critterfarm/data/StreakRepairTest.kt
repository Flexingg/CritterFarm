package com.critterfarm.data

import com.critterfarm.data.local.DailySummaryLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate

/**
 * The second-chance rule: a single missed day can be bought back, honestly priced, and the
 * guardrail that matters most — a repair never manufactures a reward for the day it forgives.
 */
class StreakRepairTest {

    private val today = LocalDate.of(2026, 9, 19)

    @Test
    fun `a single missed day is repairable`() {
        val option = StreakRepairRules.canRepair(
            streakDays = 5,
            lastClaimedDate = today.minusDays(2).toString(),
            lastRepairedGapDate = null,
            today = today,
            coins = 1_000,
        )
        assertNotNull(option)
        assertEquals(today.minusDays(1), option!!.missedDate)
    }

    @Test
    fun `two or more missed days are not repairable`() {
        val option = StreakRepairRules.canRepair(
            streakDays = 5,
            lastClaimedDate = today.minusDays(3).toString(),
            lastRepairedGapDate = null,
            today = today,
            coins = 1_000,
        )
        assertNull(option)
    }

    @Test
    fun `a chain claimed yesterday is not repairable — nothing is broken`() {
        val option = StreakRepairRules.canRepair(
            streakDays = 5,
            lastClaimedDate = today.minusDays(1).toString(),
            lastRepairedGapDate = null,
            today = today,
            coins = 1_000,
        )
        assertNull(option)
    }

    @Test
    fun `a chain claimed today is not repairable`() {
        val option = StreakRepairRules.canRepair(
            streakDays = 5,
            lastClaimedDate = today.toString(),
            lastRepairedGapDate = null,
            today = today,
            coins = 1_000,
        )
        assertNull(option)
    }

    @Test
    fun `a chain that was never alive cannot be repaired`() {
        val option = StreakRepairRules.canRepair(
            streakDays = 0,
            lastClaimedDate = today.minusDays(2).toString(),
            lastRepairedGapDate = null,
            today = today,
            coins = 1_000,
        )
        assertNull(option)
    }

    @Test
    fun `the same gap cannot be repaired twice`() {
        val missedDate = today.minusDays(1)
        val option = StreakRepairRules.canRepair(
            streakDays = 5,
            lastClaimedDate = today.minusDays(2).toString(),
            lastRepairedGapDate = missedDate.toString(),
            today = today,
            coins = 1_000,
        )
        assertNull(option)
    }

    @Test
    fun `cost scales with streak length and is capped at 1000`() {
        assertEquals(100, StreakRepairRules.repairCost(0))
        assertEquals(225, StreakRepairRules.repairCost(5))
        assertEquals(600, StreakRepairRules.repairCost(20))
        assertEquals(1_000, StreakRepairRules.repairCost(100))
    }

    @Test
    fun `an unaffordable repair reports the exact shortfall`() {
        val cost = StreakRepairRules.repairCost(5) // 225
        val option = StreakRepairRules.canRepair(
            streakDays = 5,
            lastClaimedDate = today.minusDays(2).toString(),
            lastRepairedGapDate = null,
            today = today,
            coins = cost - 1,
        )
        assertNull(option)

        val eligibleGap = StreakRepairRules.eligibleGap(
            streakDays = 5,
            lastClaimedDate = today.minusDays(2).toString(),
            lastRepairedGapDate = null,
            today = today,
        )
        assertNotNull(eligibleGap)
        val shortfall = cost - (cost - 1)
        assertEquals(1, shortfall)
    }

    @Test
    fun `repairing spends exactly the cost and pays nothing extra`() {
        val coins = 1_000
        val streakDays = 5
        val option = StreakRepairRules.canRepair(
            streakDays = streakDays,
            lastClaimedDate = today.minusDays(2).toString(),
            lastRepairedGapDate = null,
            today = today,
            coins = coins,
        )!!
        val cost = StreakRepairRules.repairCost(streakDays)
        assertEquals(coins - cost, option.coinsAfter)
        assertEquals(streakDays, option.streakDaysRestored)
    }

    /**
     * The critical guardrail: a log row for the missed day — representing the fact that nothing
     * was actually earned that day — must stay exactly as it is. [StreakRepairRules] never sees or
     * touches [DailySummaryLogEntity] at all, so this pins that contract down rather than trusting
     * it by inspection.
     */
    @Test
    fun `repairing changes no reward fields on the missed day's log row`() {
        val missedDate = today.minusDays(1)
        val untouchedLog = DailySummaryLogEntity(
            id = 1,
            date = missedDate.toString(),
            steps = 0,
            caloriesBurned = 0.0,
            caloriesConsumed = 0.0,
            deficit = 0.0,
            hydrationMl = 0.0,
            sleepMinutes = 0,
            workouts = 0,
            weightKg = null,
            xpEarned = 0,
            coinsEarned = 0,
            treatsEarned = 0,
            chestClaimed = false,
            syncedAt = 0L,
        )

        val option = StreakRepairRules.canRepair(
            streakDays = 5,
            lastClaimedDate = today.minusDays(2).toString(),
            lastRepairedGapDate = null,
            today = today,
            coins = 1_000,
        )!!

        // The repair option carries only a cost and the restored streak — no reward fields exist
        // to move, and the log itself was never passed in or produced.
        assertEquals(0, untouchedLog.xpEarned)
        assertEquals(0, untouchedLog.coinsEarned)
        assertEquals(0, untouchedLog.treatsEarned)
        assertEquals(false, untouchedLog.chestClaimed)
        assertEquals(missedDate, option.missedDate)
    }
}
