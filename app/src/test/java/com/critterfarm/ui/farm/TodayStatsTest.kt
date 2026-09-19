package com.critterfarm.ui.farm

import com.critterfarm.data.local.DailySummaryLogEntity
import com.critterfarm.ui.model.GameZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The stats rows exist to answer "where am I, and where do I need to be?" — so the assertions
 * below are mostly about the NUMBERS BEING PRESENT, including when nothing is linked yet.
 */
class TodayStatsTest {

    private fun log(
        steps: Long = 6_412L,
        deficit: Double = 312.0,
        hydrationMl: Double = 1_892.0,
        sleepMinutes: Long = 402L,
        workouts: Int = 1,
        weightKg: Double? = 96.4,
    ) = DailySummaryLogEntity(
        id = 0,
        date = "2026-09-19",
        steps = steps,
        caloriesBurned = 2_400.0,
        caloriesConsumed = 2_088.0,
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

    private val allDormant = GameZone.entries.toSet()

    @Test
    fun `all six zones always produce a row`() {
        val rows = buildStatTargets(log(), emptySet())
        assertEquals(6, rows.size)
        assertEquals(
            listOf(
                GameZone.PASTURE_ROAM,
                GameZone.GROWTH_SPARK,
                GameZone.FRESH_POND,
                GameZone.GYM_BARN,
                GameZone.COZY_BARN,
                GameZone.EVOLUTION_SCALE,
            ),
            rows.map { it.zone },
        )
    }

    @Test
    fun `steps row shows current and target with a to-go status`() {
        val row = buildStatTargets(log(steps = 6_412L), emptySet()).first()
        assertEquals("6,412", row.currentText)
        assertEquals("10,000 steps", row.targetText)
        assertEquals("3,588 steps to go", row.statusText)
        assertTrue(row.linked)
        assertFalse(row.met)
        assertEquals(0.6412f, row.progress, 0.001f)
    }

    @Test
    fun `goal met is recognised exactly at the target`() {
        val row = buildStatTargets(log(steps = 10_000L), emptySet()).first()
        assertTrue(row.met)
        assertEquals(StatsFormat.GOAL_MET, row.statusText)
        assertEquals(1.0f, row.progress, 0.0001f)
    }

    @Test
    fun `over-target clamp never overflows the bar`() {
        val row = buildStatTargets(log(steps = 23_000L), emptySet()).first()
        assertEquals(1.0f, row.progress, 0.0001f)
        assertEquals(StatsFormat.GOAL_MET, row.statusText)
    }

    @Test
    fun `water is reported in fl oz not millilitres`() {
        val row = buildStatTargets(log(hydrationMl = 1_892.0), emptySet())
            .first { it.zone == GameZone.FRESH_POND }
        assertEquals("64", row.currentText)
        assertEquals("100 fl oz", row.targetText)
        assertEquals("36 fl oz to go", row.statusText)
    }

    @Test
    fun `sleep renders as hours and minutes with a target`() {
        val row = buildStatTargets(log(sleepMinutes = 402L), emptySet())
            .first { it.zone == GameZone.COZY_BARN }
        assertEquals("6h 42m", row.currentText)
        assertEquals("8h 0m", row.targetText)
        assertEquals("1h 18m to go", row.statusText)
    }

    @Test
    fun `weight is reported in pounds and marks today as logged`() {
        val row = buildStatTargets(log(weightKg = 96.4), emptySet())
            .first { it.zone == GameZone.EVOLUTION_SCALE }
        assertEquals("212.5 lb", row.currentText)
        assertEquals("1 weigh-in today", row.targetText)
        assertEquals("Logged today", row.statusText)
        assertTrue(row.met)
    }

    @Test
    fun `missing weigh-in is honest rather than zero`() {
        val row = buildStatTargets(log(weightKg = null), emptySet())
            .first { it.zone == GameZone.EVOLUTION_SCALE }
        assertEquals(StatsFormat.NO_VALUE, row.currentText)
        assertEquals("No weigh-in yet", row.statusText)
        assertFalse(row.met)
    }

    @Test
    fun `workouts use correct singular and plural copy`() {
        val one = buildStatTargets(log(workouts = 1), emptySet())
            .first { it.zone == GameZone.GYM_BARN }
        assertEquals(StatsFormat.GOAL_MET, one.statusText)
        assertEquals("1 session", one.targetText)

        val zero = buildStatTargets(log(workouts = 0), emptySet())
            .first { it.zone == GameZone.GYM_BARN }
        assertEquals("1 session to go", zero.statusText)
    }

    @Test
    fun `a dormant zone still shows its target and asks to link`() {
        val rows = buildStatTargets(log(), allDormant)
        rows.forEach { row ->
            assertEquals("target must stay visible when dormant", true, row.targetText.isNotBlank())
            assertEquals(StatsFormat.NO_VALUE, row.currentText)
            assertEquals(StatsFormat.LINK_HEALTH, row.statusText)
            assertFalse(row.linked)
            assertEquals(0f, row.progress, 0.0001f)
        }
    }

    @Test
    fun `no log at all still renders every target`() {
        val rows = buildStatTargets(null, emptySet())
        assertEquals(6, rows.size)
        assertEquals("10,000 steps", rows.first().targetText)
        assertEquals(StatsFormat.NO_VALUE, rows.first().currentText)
        assertEquals("No data yet today", rows.first().statusText)
    }

    @Test
    fun `row exposes a screen reader sentence with all three facts`() {
        val row = buildStatTargets(log(steps = 6_412L), emptySet()).first()
        assertEquals("Pasture Roam: 6,412 of 10,000 steps, 3,588 steps to go", row.spokenText)
    }

    @Test
    fun `deficit row never reads as a negative`() {
        val row = buildStatTargets(log(deficit = -420.0), emptySet())
            .first { it.zone == GameZone.GROWTH_SPARK }
        assertEquals("0", row.currentText)
        assertEquals("500 kcal to go", row.statusText)
    }
}
