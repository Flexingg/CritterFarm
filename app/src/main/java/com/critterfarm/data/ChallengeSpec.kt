package com.critterfarm.data

/** How wide a challenge's window is. A window is just a parameter — the engine below is the same. */
enum class ChallengeWindow { WEEKLY, MONTHLY, EVENT }

/** How a challenge's [ChallengeSpec.target] is scored against the logs in its window. */
enum class ChallengeGoal {
    /** Sum the metric across every logged day in the window, e.g. "4 workouts this week". */
    TOTAL,

    /** Count days where the metric met its [GameGoals] target, e.g. "5 days at your water goal". */
    DAYS,

    /** Count days with any activity logged at all, regardless of which metric. */
    ACTIVE_DAYS,
}

/**
 * One challenge, static data. [ChallengeWindow] decides the date range, [ChallengeGoal] decides
 * how the metric is scored — the same [ChallengeRules] evaluates every combination, so a weekly
 * workout count and a seasonal-event workout count are the same code path with different dates.
 */
data class ChallengeSpec(
    val id: String,
    val title: String,
    val emoji: String,
    val blurb: String,
    val window: ChallengeWindow,
    /**
     * The metric this challenge is scored against. Unused for [ChallengeGoal.ACTIVE_DAYS], which
     * counts a day if *any* metric has data — the field is still required so every spec reads the
     * same shape, and by convention those specs point at [Metric.STEPS].
     */
    val metric: Metric,
    val goal: ChallengeGoal,
    val target: Double,
    val rewardCoins: Int,
    val rewardTreats: Int,
    val rewardSparks: Int,
    /** Set only for [ChallengeWindow.EVENT] challenges — the event whose window this borrows. */
    val eventId: String? = null,
)
