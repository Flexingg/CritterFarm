package com.critterfarm.data

import com.critterfarm.data.local.DailySummaryLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The critter's "says" line: it deepens with stage, degrades kindly with no data, and — the hard
 * guardrail — never reads as food advice, at any stage or branch.
 */
class InsightRulesTest {

    private val today = LocalDate.of(2026, 9, 19)

    private fun log(
        date: String,
        steps: Long = 0L,
        hydrationMl: Double = 0.0,
        sleepMinutes: Long = 0L,
    ) = DailySummaryLogEntity(
        id = 0,
        date = date,
        steps = steps,
        caloriesBurned = 2_000.0,
        caloriesConsumed = 1_500.0,
        deficit = 500.0,
        hydrationMl = hydrationMl,
        sleepMinutes = sleepMinutes,
        workouts = 0,
        weightKg = null,
        xpEarned = 0,
        coinsEarned = 0,
        treatsEarned = 0,
        chestClaimed = true,
        syncedAt = 0L,
    )

    // ---------------------------------------------------------------------------- stage 0 ----

    @Test
    fun `the stage-0 line is cheerful and mentions no numbers`() {
        val insight = InsightRules.insightFor(0, emptyList(), today)
        assertTrue(insight.text.none { it.isDigit() })
        assertEquals(0, insight.fromStage)
    }

    // ---------------------------------------------------------------------------- stage 1 ----

    @Test
    fun `the stage-1 line contains today's actual step count`() {
        val logs = listOf(log(today.toString(), steps = 6_412))
        val insight = InsightRules.insightFor(1, logs, today)
        assertTrue(insight.text.contains("6,412"))
    }

    @Test
    fun `the stage-1 line degrades kindly with no data today`() {
        val insight = InsightRules.insightFor(1, emptyList(), today)
        assertFalse(insight.text.contains("0 steps"))
    }

    // ---------------------------------------------------------------------------- stage 2 ----

    @Test
    fun `the stage-2 line counts met-goal days over the last 7 days only`() {
        val hydrationGoal = Units.flOzToMl(GameGoals.HYDRATION_FL_OZ)
        val logs = (0..6).map { offset -> log(today.minusDays(offset.toLong()).toString(), hydrationMl = hydrationGoal) } +
            // Outside the 7-day window — must not be counted.
            log(today.minusDays(7).toString(), hydrationMl = hydrationGoal)
        val insight = InsightRules.insightFor(2, logs, today)
        assertTrue(insight.text.contains("7 of the last 7 days"))
    }

    @Test
    fun `a day outside the 7-day window is excluded from the stage-2 count`() {
        val hydrationGoal = Units.flOzToMl(GameGoals.HYDRATION_FL_OZ)
        val logs = listOf(
            log(today.toString(), hydrationMl = hydrationGoal),
            log(today.minusDays(10).toString(), hydrationMl = hydrationGoal),
        )
        val insight = InsightRules.insightFor(2, logs, today)
        assertTrue(insight.text.contains("1 of the last 7 days"))
    }

    // ---------------------------------------------------------------------------- stage 3 ----

    @Test
    fun `the stage-3 line is deterministic and references two different metrics`() {
        val logs = listOf(
            log("2026-08-01", steps = 9_000, sleepMinutes = 480),
            log("2026-08-02", steps = 8_500, sleepMinutes = 470),
            log("2026-08-03", steps = 2_000, sleepMinutes = 300),
            log("2026-08-04", steps = 1_000, sleepMinutes = 310),
        )
        val first = InsightRules.insightFor(3, logs, today)
        val second = InsightRules.insightFor(3, logs, today)
        assertEquals(first.text, second.text)
        assertTrue(first.text.contains("sleep"))
        assertTrue(first.text.contains("steps"))
    }

    // ------------------------------------------------------------------------- empty logs ----

    @Test
    fun `empty logs return an encouraging line at every stage`() {
        for (stage in 0..3) {
            val insight = InsightRules.insightFor(stage, emptyList(), today)
            assertTrue("stage $stage should read as encouraging", insight.text.isNotBlank())
            assertFalse("stage $stage should not error or go blank", insight.text.contains("null"))
        }
    }

    // -------------------------------------------------------------------- forbidden words ----

    private val forbidden = listOf(
        "eat", "less", "cut", "calorie deficit", "should", "must", "cheat", "guilt", "fail",
    )

    private fun assertClean(text: String) {
        val lower = text.lowercase()
        forbidden.forEach { word ->
            assertFalse("insight text contained forbidden word '$word': $text", lower.contains(word))
        }
    }

    @Test
    fun `every reachable branch at every stage passes the forbidden-word guardrail`() {
        val hydrationGoal = Units.flOzToMl(GameGoals.HYDRATION_FL_OZ)

        // Stage 0: the only branch.
        assertClean(InsightRules.insightFor(0, emptyList(), today).text)
        assertClean(InsightRules.insightFor(0, listOf(log(today.toString(), steps = 12_000)), today).text)

        // Stage 1: no data, some data, target already met.
        assertClean(InsightRules.insightFor(1, emptyList(), today).text)
        assertClean(InsightRules.insightFor(1, listOf(log(today.toString(), steps = 3_000)), today).text)
        assertClean(InsightRules.insightFor(1, listOf(log(today.toString(), steps = GameGoals.STEPS)), today).text)
        assertClean(InsightRules.insightFor(1, listOf(log(today.toString(), steps = GameGoals.STEPS + 500)), today).text)

        // Stage 2: no window data, zero-met, some-met, all-met.
        assertClean(InsightRules.insightFor(2, emptyList(), today).text)
        assertClean(InsightRules.insightFor(2, listOf(log(today.toString())), today).text)
        assertClean(
            InsightRules.insightFor(
                2,
                listOf(log(today.toString(), hydrationMl = hydrationGoal)),
                today,
            ).text,
        )
        assertClean(
            InsightRules.insightFor(
                2,
                (0..6).map { offset -> log(today.minusDays(offset.toLong()).toString(), hydrationMl = hydrationGoal) },
                today,
            ).text,
        )

        // Stage 3: no data, one-sided data (fallback), both buckets present (both directions).
        assertClean(InsightRules.insightFor(3, emptyList(), today).text)
        assertClean(
            InsightRules.insightFor(
                3,
                listOf(log("2026-08-01", steps = 9_000, sleepMinutes = 480)),
                today,
            ).text,
        )
        assertClean(
            InsightRules.insightFor(
                3,
                listOf(
                    log("2026-08-01", steps = 9_000, sleepMinutes = 480),
                    log("2026-08-02", steps = 2_000, sleepMinutes = 300),
                ),
                today,
            ).text,
        )
        assertClean(
            InsightRules.insightFor(
                3,
                listOf(
                    log("2026-08-01", steps = 9_000, sleepMinutes = 300),
                    log("2026-08-02", steps = 2_000, sleepMinutes = 480),
                ),
                today,
            ).text,
        )
    }
}
