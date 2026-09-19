package com.critterfarm.data

import com.critterfarm.data.local.DailySummaryLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeeklyRecapTest {

    private fun log(
        date: String,
        steps: Long = 0,
        deficit: Double = 0.0,
        hydrationMl: Double = 0.0,
        sleepMinutes: Long = 0,
        workouts: Int = 0,
        weightKg: Double? = null,
        coinsEarned: Int = 0,
        treatsEarned: Int = 0,
    ) = DailySummaryLogEntity(
        date = date,
        steps = steps,
        caloriesBurned = 2_400.0,
        caloriesConsumed = 2_000.0,
        deficit = deficit,
        hydrationMl = hydrationMl,
        sleepMinutes = sleepMinutes,
        workouts = workouts,
        weightKg = weightKg,
        xpEarned = 0,
        coinsEarned = coinsEarned,
        treatsEarned = treatsEarned,
        chestClaimed = false,
        syncedAt = 0L,
    )

    /** A day that hits all six targets — the "good day" the recap counts. */
    private fun perfectDay(date: String) = log(
        date = date,
        steps = GameGoals.STEPS,
        deficit = GameGoals.DEFICIT_KCAL,
        hydrationMl = Units.flOzToMl(GameGoals.HYDRATION_FL_OZ),
        sleepMinutes = GameGoals.SLEEP_MINUTES,
        workouts = GameGoals.WORKOUTS,
        weightKg = 96.0,
    )

    @Test
    fun `weeks start on Sunday`() {
        // 2026-09-19 is a Saturday; the week that contains it starts on Sunday the 13th.
        val saturday = LocalDate.of(2026, 9, 19)
        assertEquals(LocalDate.of(2026, 9, 13), WeeklyRecap.weekStart(saturday))
        // A Sunday is its own week start.
        assertEquals(LocalDate.of(2026, 9, 13), WeeklyRecap.weekStart(LocalDate.of(2026, 9, 13)))
    }

    @Test
    fun `only days inside the week are counted, and the edges are excluded`() {
        val weekStart = LocalDate.of(2026, 9, 13)
        val logs = listOf(
            log("2026-09-12", steps = 99_999),      // the day before  - must be ignored
            log("2026-09-13", steps = 1_000),       // first day       - counted
            log("2026-09-19", steps = 2_000),       // last day        - counted
            log("2026-09-20", steps = 88_888),      // the day after   - must be ignored
        )
        val stats = WeeklyRecap.statsFor(logs, weekStart)
        assertEquals(3_000L, stats.totalSteps)
        assertEquals(2, stats.daysLogged)
        assertEquals(LocalDate.of(2026, 9, 13), stats.startDate)
        assertEquals(LocalDate.of(2026, 9, 19), stats.endDate)
    }

    @Test
    fun `an empty week is empty rather than broken`() {
        val stats = WeeklyRecap.statsFor(emptyList(), LocalDate.of(2026, 9, 13))
        assertTrue(stats.isEmpty)
        assertEquals(0, stats.daysLogged)
        assertEquals(0L, stats.totalSteps)
        assertEquals(0.0, stats.waterPerLoggedDay, 0.0)
        assertEquals(0, stats.metTargetDays)
    }

    @Test
    fun `one perfect day counts as a goal day and scores six of six`() {
        val logs = listOf(perfectDay("2026-09-14"))
        val stats = WeeklyRecap.statsFor(logs, LocalDate.of(2026, 9, 13))
        assertEquals(1, stats.daysLogged)
        assertEquals(1, stats.metTargetDays)
        assertEquals(6, stats.bestDayScore)
        assertEquals(1, stats.totalWorkouts)
    }

    @Test
    fun `half the targets does not count as a goal day`() {
        // 3 of 6 is below the "4+ targets" bar used everywhere else in the app.
        val logs = listOf(
            log("2026-09-14", steps = GameGoals.STEPS, workouts = 1, sleepMinutes = GameGoals.SLEEP_MINUTES),
        )
        val stats = WeeklyRecap.statsFor(logs, LocalDate.of(2026, 9, 13))
        assertEquals(0, stats.metTargetDays)
        assertEquals(3, stats.bestDayScore)
    }

    @Test
    fun `water averages over logged days only`() {
        val logs = listOf(
            log("2026-09-14", hydrationMl = Units.flOzToMl(100.0)),
            log("2026-09-15", hydrationMl = Units.flOzToMl(60.0)),
            // five more days of the week with nothing logged do not dilute the average
        )
        val stats = WeeklyRecap.statsFor(logs, LocalDate.of(2026, 9, 13))
        assertEquals(2, stats.daysLogged)
        assertEquals(80.0, stats.waterPerLoggedDay, 0.5)
    }

    @Test
    fun `deltas point the right way, including flat weeks`() {
        val better = WeeklyRecap.statsFor(
            listOf(perfectDay("2026-09-14"), perfectDay("2026-09-15")),
            LocalDate.of(2026, 9, 13),
        )
        val worse = WeeklyRecap.statsFor(
            listOf(log("2026-09-07", steps = 100)),
            LocalDate.of(2026, 9, 6),
        )
        val deltas = WeeklyRecap.deltas(better, worse)
        assertEquals(6, deltas.size)
        val steps = deltas.first { it.label == "Steps" }
        assertTrue("a better week should read as up", steps.up)
        assertFalse(steps.same)

        // Nothing vs nothing must not be reported as a decline.
        val nothing = WeeklyRecap.statsFor(emptyList(), LocalDate.of(2026, 9, 6))
        val flat = WeeklyRecap.deltas(nothing, nothing).first { it.label == "Workouts" }
        assertTrue(flat.same)
        assertTrue(flat.up)
    }

    @Test
    fun `deltas label each metric with this week and last week`() {
        val week = WeeklyRecap.statsFor(
            listOf(log("2026-09-14", steps = 12_345, workouts = 2, hydrationMl = Units.flOzToMl(90.0))),
            LocalDate.of(2026, 9, 13),
        )
        val previous = WeeklyRecap.statsFor(
            listOf(log("2026-09-07", steps = 6_000, workouts = 1)),
            LocalDate.of(2026, 9, 6),
        )
        val deltas = WeeklyRecap.deltas(week, previous)
        val steps = deltas.first { it.label == "Steps" }
        assertEquals("12,345", steps.thisWeek)
        assertEquals("6,000", steps.vsLastWeek)
        val workouts = deltas.first { it.label == "Workouts" }
        assertEquals("2", workouts.thisWeek)
        assertEquals("1", workouts.vsLastWeek)
        val water = deltas.first { it.label == "Water" }
        assertEquals("90 fl oz", water.thisWeek)
    }

    @Test
    fun `an empty week still gets encouraging highlights`() {
        val stats = WeeklyRecap.statsFor(emptyList(), LocalDate.of(2026, 9, 13))
        val lines = WeeklyRecap.highlights(stats)
        assertTrue("expected at least two lines", lines.size >= 2)
        val joined = lines.joinToString(" ").lowercase()
        listOf("fail", "bad", "lazy", "should", "disappoint", "guilty", "shame").forEach { word ->
            assertFalse("highlights must not scold: found '$word'", joined.contains(word))
        }
        // And it must actually acknowledge the week rather than pretend it went fine.
        assertTrue(joined.contains("no days logged"))
    }

    @Test
    fun `a strong week is celebrated without mentioning weight loss speed`() {
        val logs = (13..19).map { perfectDay("2026-09-$it") }
        val stats = WeeklyRecap.statsFor(logs, LocalDate.of(2026, 9, 13))
        val lines = WeeklyRecap.highlights(stats)
        assertTrue(lines.isNotEmpty())
        assertTrue(lines.size <= 4)
        val joined = lines.joinToString(" ").lowercase()
        listOf("lose", "lost", "pounds", "lbs", "weight loss", "faster").forEach { word ->
            assertFalse("recap must not frame this as weight loss: found '$word'", joined.contains(word))
        }
        assertEquals(7, stats.daysLogged)
        assertEquals(7, stats.metTargetDays)
    }

    @Test
    fun `share text is short, plain and carries no weight`() {
        val logs = listOf(perfectDay("2026-09-14"), perfectDay("2026-09-15"))
        val stats = WeeklyRecap.statsFor(logs, LocalDate.of(2026, 9, 13))
        val text = WeeklyRecap.shareText(stats)
        assertTrue(text.startsWith("My week on the farm:"))
        assertTrue(text.contains("2/7 days logged"))
        assertTrue(text.contains("2 workouts"))
        assertTrue(text.contains("steps"))
        assertFalse("share text must not leak weight", text.lowercase().contains("kg"))
        assertFalse(text.lowercase().contains("lb"))
        assertTrue("share text should stay short", text.length < 200)
    }

    @Test
    fun `sparks are derived from the week's deficits, inside the safe band`() {
        // A 500 kcal deficit for three days should pay something, and a dangerous 1,600 kcal
        // deficit must not pay more than the safe cap allows.
        val safe = WeeklyRecap.statsFor(
            (13..15).map { log("2026-09-$it", deficit = 500.0) },
            LocalDate.of(2026, 9, 13),
        )
        val dangerous = WeeklyRecap.statsFor(
            (13..15).map { log("2026-09-$it", deficit = 1_600.0) },
            LocalDate.of(2026, 9, 13),
        )
        assertTrue(safe.sparksEarned > 0)
        assertTrue(
            "crash dieting must never out-earn a sane deficit",
            dangerous.sparksEarned <= safe.sparksEarned * 2,
        )
    }
}
