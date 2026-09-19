package com.critterfarm.data

/**
 * The single source of truth for every daily target in the game.
 *
 * Both the stats UI and the history heatmap score against these numbers, so they must live in
 * exactly one place — if the steps goal ever changes, the rows and the heatmap change together.
 */
object GameGoals {
    const val STEPS = 10_000L
    const val DEFICIT_KCAL = 500.0
    const val HYDRATION_FL_OZ = 100.0
    const val WORKOUTS = 1
    const val SLEEP_MINUTES = 480L

    /** A weigh-in counts as a met target for the day it is logged. */
    const val WEIGH_IN_PER_DAY = 1

    /** Everything the day is scored against — used by the heatmap and the records list. */
    const val TARGET_COUNT = 6
}
