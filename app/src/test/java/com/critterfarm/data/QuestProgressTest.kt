package com.critterfarm.data

import com.critterfarm.data.local.DailySummaryLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Quest completion and progress must come straight from the log — nothing is stored. */
class QuestProgressTest {

    private fun log(
        steps: Long = 0L,
        deficit: Double = 0.0,
        hydrationMl: Double = 0.0,
        sleepMinutes: Long = 0L,
        workouts: Int = 0,
        weightKg: Double? = null,
    ) = DailySummaryLogEntity(
        id = 0,
        date = "2026-09-19",
        steps = steps,
        caloriesBurned = 2_000.0,
        caloriesConsumed = 2_000.0 - deficit,
        deficit = deficit,
        hydrationMl = hydrationMl,
        sleepMinutes = sleepMinutes,
        workouts = workouts,
        weightKg = weightKg,
        xpEarned = 0,
        coinsEarned = 0,
        treatsEarned = 0,
        chestClaimed = false,
        syncedAt = 0L,
    )

    private val steps8k = QuestCatalog.ALL.first { it.id == "steps_8k" }
    private val deficitSane = QuestCatalog.ALL.first { it.id == "deficit_sane" }

    @Test
    fun `an AT_LEAST quest is not complete below target`() {
        assertFalse(QuestRules.isComplete(steps8k, log(steps = 7_999)))
    }

    @Test
    fun `an AT_LEAST quest is complete exactly at target`() {
        assertTrue(QuestRules.isComplete(steps8k, log(steps = 8_000)))
        assertEquals(1f, QuestRules.progress(steps8k, log(steps = 8_000)), 0.0001f)
    }

    @Test
    fun `AT_LEAST progress is a plain fraction of target, clamped at 1`() {
        assertEquals(0.5f, QuestRules.progress(steps8k, log(steps = 4_000)), 0.0001f)
        assertEquals(1f, QuestRules.progress(steps8k, log(steps = 20_000)), 0.0001f)
    }

    @Test
    fun `an AT_MOST quest is complete at or under its cap`() {
        // deficit_sane caps at 1000 kcal
        assertTrue(QuestRules.isComplete(deficitSane, log(deficit = 1_000.0)))
        assertTrue(QuestRules.isComplete(deficitSane, log(deficit = 400.0)))
        assertFalse(QuestRules.isComplete(deficitSane, log(deficit = 1_001.0)))
    }

    @Test
    fun `AT_MOST progress is full once under the cap and shrinks once over it`() {
        assertEquals(1f, QuestRules.progress(deficitSane, log(deficit = 400.0)), 0.0001f)
        assertEquals(1f, QuestRules.progress(deficitSane, log(deficit = 1_000.0)), 0.0001f)
        // Double the cap should read as half-progress under the inverted formula.
        assertEquals(0.5f, QuestRules.progress(deficitSane, log(deficit = 2_000.0)), 0.0001f)
    }

    @Test
    fun `a quest is not complete when the metric has no data`() {
        assertFalse(QuestRules.isComplete(steps8k, log(steps = 0)))
        assertFalse(QuestRules.isComplete(deficitSane, log(deficit = 0.0)))
        assertEquals(0f, QuestRules.progress(steps8k, log(steps = 0)), 0.0001f)
    }

    @Test
    fun `a quest is not complete when there is no log at all`() {
        assertFalse(QuestRules.isComplete(steps8k, null))
        assertEquals(0f, QuestRules.progress(steps8k, null), 0.0001f)
    }

    @Test
    fun `a weigh-in quest completes as soon as any weight is logged`() {
        val quest = QuestCatalog.ALL.first { it.id == "weighin_log" }
        assertFalse(QuestRules.isComplete(quest, log(weightKg = null)))
        assertTrue(QuestRules.isComplete(quest, log(weightKg = 80.0)))
    }
}
