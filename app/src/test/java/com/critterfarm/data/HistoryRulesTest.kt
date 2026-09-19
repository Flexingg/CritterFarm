package com.critterfarm.data

import com.critterfarm.data.local.DailySummaryLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The heatmap, streaks and records must be right or the history screen lies to the player. */
class HistoryRulesTest {

    private val today = LocalDate.of(2026, 9, 19)

    private fun log(
        date: String,
        steps: Long = 0L,
        deficit: Double = 0.0,
        hydrationMl: Double = 0.0,
        sleepMinutes: Long = 0L,
        workouts: Int = 0,
        weightKg: Double? = null,
    ) = DailySummaryLogEntity(
        id = 0,
        date = date,
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
        chestClaimed = true,
        syncedAt = 0L,
    )

    private fun perfect(date: String) = log(
        date = date,
        steps = GameGoals.STEPS,
        deficit = GameGoals.DEFICIT_KCAL,
        hydrationMl = Units.flOzToMl(GameGoals.HYDRATION_FL_OZ),
        sleepMinutes = GameGoals.SLEEP_MINUTES,
        workouts = GameGoals.WORKOUTS,
        weightKg = 96.0,
    )

    // --------------------------------------------------------------------- target scoring ----

    @Test
    fun `a day with nothing logged scores zero`() {
        assertEquals(0, HistoryRules.metTargets(log("2026-09-19")))
    }

    @Test
    fun `a perfect day scores the full target count`() {
        assertEquals(GameGoals.TARGET_COUNT, HistoryRules.metTargets(perfect("2026-09-19")))
    }

    @Test
    fun `targets are met exactly at the goal, not just above it`() {
        assertEquals(1, HistoryRules.metTargets(log("2026-09-19", steps = GameGoals.STEPS)))
        assertEquals(0, HistoryRules.metTargets(log("2026-09-19", steps = GameGoals.STEPS - 1)))
        // 100 fl oz in ml, to the drop.
        val exactWater = log("2026-09-19", hydrationMl = Units.flOzToMl(100.0))
        assertEquals(1, HistoryRules.metTargets(exactWater))
    }

    @Test
    fun `a negative deficit never counts as a met target`() {
        assertEquals(0, HistoryRules.metTargets(log("2026-09-19", deficit = -800.0)))
    }

    // ------------------------------------------------------------------------- heatmap ------

    @Test
    fun `intensity buckets map the score to five shades`() {
        assertEquals(0, HistoryRules.intensity(0))
        assertEquals(1, HistoryRules.intensity(1))
        assertEquals(1, HistoryRules.intensity(2))
        assertEquals(2, HistoryRules.intensity(3))
        assertEquals(2, HistoryRules.intensity(4))
        assertEquals(3, HistoryRules.intensity(5))
        assertEquals(4, HistoryRules.intensity(6))
        // Out-of-range input can never paint an out-of-range colour.
        assertEquals(4, HistoryRules.intensity(99))
        assertEquals(0, HistoryRules.intensity(-3))
    }

    @Test
    fun `cells are dense day by day so gaps show as empty squares`() {
        val logs = listOf(perfect(today.toString()))
        val cells = HistoryRules.cells(logs, today = today, days = 10)
        assertEquals(10, cells.size)
        assertEquals(today, cells.last().date)
        assertEquals(GameGoals.TARGET_COUNT, cells.last().metTargets)
        // Every other day is a gap, not a skipped entry.
        assertEquals(0, cells.first().metTargets)
        assertEquals(today.minusDays(9), cells.first().date)
    }

    @Test
    fun `weeks pad the first week so every column is a real week`() {
        val cells = HistoryRules.cells(emptyList(), today = today, days = 14)
        val weeks = HistoryRules.toWeeks(cells)
        assertTrue(weeks.isNotEmpty())
        weeks.forEach { week -> assertEquals(7, week.size) }
        // The first cell is placed in its weekday slot, so the first week may start with nulls.
        val pad = cells.first().date.dayOfWeek.value % 7
        assertEquals(pad, weeks.first().takeWhile { it == null }.size)
    }

    // -------------------------------------------------------------------------- records -----

    @Test
    fun `longest run counts consecutive goal days and ignores gaps`() {
        val logs = listOf(
            perfect("2026-09-01"),
            perfect("2026-09-02"),
            perfect("2026-09-03"),
            // 09-04 missing
            perfect("2026-09-05"),
        )
        assertEquals(3, HistoryRules.longestConsistencyRun(logs))
    }

    @Test
    fun `records surface the best day for each metric`() {
        val logs = listOf(
            log("2026-09-10", steps = 4_000, sleepMinutes = 300, workouts = 1, hydrationMl = 1_000.0),
            perfect("2026-09-11"),
        )
        val records = HistoryRules.records(logs)
        assertTrue(records.any { it.label.contains("Most steps") && it.value == "10,000" })
        assertTrue(records.any { it.label.contains("Longest sleep") && it.value == "8h 0m" })
        assertTrue(records.any { it.label.contains("Best day") && it.value == "6 / 6 targets" })
    }

    @Test
    fun `records round water to whole fl oz instead of showing raw millilitres`() {
        // Regression: an integer-division slip once rendered "110.34 fl oz".
        val logs = listOf(log("2026-09-18", hydrationMl = 3_263.0))
        val record = HistoryRules.records(logs).first { it.label.contains("Most water") }
        assertEquals("110 fl oz", record.value)
    }

    @Test
    fun `an empty history produces no records`() {
        assertEquals(emptyList<Any>(), HistoryRules.records(emptyList()))
    }

    // ------------------------------------------------------------------ metric drill-down ----

    @Test
    fun `metric series returns one slot per day with gaps as null`() {
        val logs = listOf(log("2026-09-18", steps = 5_000))
        val series = MetricHistory.series(logs, Metric.STEPS, today = today, days = 3)
        assertEquals(3, series.size)
        assertEquals(5_000.0, series[1].second!!, 0.001)
        assertNull(series[0].second)
        assertNull(series[2].second)
    }

    @Test
    fun `metric summary reports best, average and coverage`() {
        val logs = listOf(
            log("2026-09-17", steps = 2_000),
            log("2026-09-18", steps = 8_000),
        )
        val summary = MetricHistory.summary(logs, Metric.STEPS)
        assertEquals(8_000.0, summary.best, 0.001)
        assertEquals(5_000.0, summary.average, 0.001)
        assertEquals(2, summary.daysWithData)
        assertEquals(LocalDate.of(2026, 9, 18), summary.bestDate)
    }

    @Test
    fun `lower is better for weight, so the best weigh-in is the lowest`() {
        val logs = listOf(
            log("2026-09-17", weightKg = 98.0),
            log("2026-09-18", weightKg = 95.0),
        )
        val summary = MetricHistory.summary(logs, Metric.WEIGHT)
        assertEquals(Units.kgToLb(95.0), summary.best, 0.01)
    }

    @Test
    fun `hydration is reported in fl oz not millilitres`() {
        val logs = listOf(log("2026-09-18", hydrationMl = Units.flOzToMl(64.0)))
        val summary = MetricHistory.summary(logs, Metric.HYDRATION)
        assertEquals(64.0, summary.best, 0.001)
        assertEquals("64 fl oz", Metric.HYDRATION.format(summary.best))
    }

    @Test
    fun `a metric with no data reports an empty summary instead of zeroed bests`() {
        val summary = MetricHistory.summary(emptyList(), Metric.SLEEP)
        assertEquals(0, summary.daysWithData)
        assertTrue(!summary.hasData)
        assertNull(summary.bestDate)
    }
}
