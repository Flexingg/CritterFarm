package com.critterfarm.data

import com.critterfarm.data.local.DailySummaryLogEntity

/** How close a player is to unlocking a species, and whether it's unlocked yet. */
data class UnlockProgress(val unlocked: Boolean, val progressText: String)

/**
 * Whether a species may be hatched. Unlocking is entirely a function of behaviour recorded in
 * the stored daily logs (pure, no Room/Android) — a locked species can never be hatched, no
 * matter how many Mana Sparks are on hand. Sparks are only ever the extra cost on top.
 */
object HatchRules {

    const val CHICK_DAYS = 3
    const val AXOLOTL_DAYS = 5
    const val DRAGON_DAYS = 10
    const val SLOTH_DAYS = 7

    fun unlockProgress(species: Species, logs: List<DailySummaryLogEntity>): UnlockProgress =
        when (species.key) {
            SpeciesCatalog.BLOB.key -> UnlockProgress(unlocked = true, progressText = "Always available")

            SpeciesCatalog.BUNNY.key -> {
                val done = logs.any { it.workouts > 0 }
                UnlockProgress(done, if (done) "Unlocked" else "0/1 workouts logged")
            }

            SpeciesCatalog.CHICK.key -> daysProgress(
                count = logs.count { it.steps >= GameGoals.STEPS },
                needed = CHICK_DAYS,
                noun = "days with steps met",
            )

            SpeciesCatalog.AXOLOTL.key -> daysProgress(
                count = logs.count { it.hydrationMl >= Units.flOzToMl(GameGoals.HYDRATION_FL_OZ) },
                needed = AXOLOTL_DAYS,
                noun = "days with water met",
            )

            SpeciesCatalog.DRAGON.key -> daysProgress(
                count = logs.count { it.deficit.coerceAtLeast(0.0) >= GameGoals.DEFICIT_KCAL },
                needed = DRAGON_DAYS,
                noun = "days with deficit met",
            )

            SpeciesCatalog.SLOTH.key -> daysProgress(
                count = logs.count { it.sleepMinutes >= GameGoals.SLEEP_MINUTES },
                needed = SLOTH_DAYS,
                noun = "days with sleep met",
            )

            else -> UnlockProgress(unlocked = false, progressText = "Unknown species")
        }

    fun isUnlocked(species: Species, logs: List<DailySummaryLogEntity>): Boolean =
        unlockProgress(species, logs).unlocked

    fun canAfford(species: Species, manaSparks: Int): Boolean = manaSparks >= species.hatchCostSparks

    fun shortfall(species: Species, manaSparks: Int): Int =
        (species.hatchCostSparks - manaSparks).coerceAtLeast(0)

    private fun daysProgress(count: Int, needed: Int, noun: String): UnlockProgress =
        UnlockProgress(
            unlocked = count >= needed,
            progressText = "${count.coerceAtMost(needed)}/$needed $noun",
        )
}
