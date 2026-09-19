package com.critterfarm.data

import com.critterfarm.data.local.CritterEntity
import com.critterfarm.data.local.CritterMood
import kotlin.math.pow
import kotlin.math.roundToInt

/** Result of converting a day's calorie deficit into Mana Sparks. */
data class DeficitReward(val manaSparks: Int, val supportiveMessage: String?)

/**
 * Pure game-balance math, kept free of Room/Android so it's trivial to reason about and test.
 * [GameRepository] is the only caller; it owns persistence, this owns the numbers.
 */
object GameRules {

    private const val BASE_XP_REQUIREMENT = 40.0
    private const val XP_CURVE_EXPONENT = 1.4

    /**
     * XP needed to advance *from* [level]. Superlinear exponent keeps early levels cheap
     * (level 1 costs 40 XP) while later ones cost noticeably more (level 10 costs ~1000 XP),
     * per the spec's "generous early, slower later" requirement.
     */
    fun xpRequiredForLevel(level: Int): Int =
        (BASE_XP_REQUIREMENT * level.toDouble().pow(XP_CURVE_EXPONENT)).roundToInt()

    /** Applies XP and resolves any resulting level-ups in one pass. */
    fun applyXp(critter: CritterEntity, xpGained: Int): CritterEntity {
        var xp = critter.xp + xpGained
        var level = critter.level
        while (xp >= xpRequiredForLevel(level)) {
            xp -= xpRequiredForLevel(level)
            level++
        }
        return critter.copy(xp = xp, level = level)
    }

    private const val STEPS_PER_COIN = 100
    fun coinsFromSteps(steps: Long): Int = (steps / STEPS_PER_COIN).toInt()

    private const val TREATS_PER_WORKOUT = 5
    fun treatsFromWorkouts(workoutCount: Int): Int = workoutCount * TREATS_PER_WORKOUT

    private const val MAX_SAFE_DEFICIT_KCAL = 1000.0
    private const val KCAL_PER_MANA_SPARK = 50.0
    private const val LOW_INTAKE_FLOOR_KCAL = 1.0
    private const val LOW_INTAKE_CEILING_KCAL = 1200.0

    /**
     * Converts a calorie deficit into Mana Sparks, clamped to a medically sane band so the
     * game never rewards crash-dieting. Deficits are flat-lined past [MAX_SAFE_DEFICIT_KCAL];
     * a very low (but nonzero, i.e. actually logged) intake gets a supportive message instead
     * of bonus loot.
     */
    fun manaSparksFromDeficit(deficitKcal: Double, caloriesConsumedKcal: Double?): DeficitReward {
        val clampedDeficit = deficitKcal.coerceIn(0.0, MAX_SAFE_DEFICIT_KCAL)
        val sparks = (clampedDeficit / KCAL_PER_MANA_SPARK).roundToInt()
        val veryLowIntake = caloriesConsumedKcal != null &&
            caloriesConsumedKcal in LOW_INTAKE_FLOOR_KCAL..LOW_INTAKE_CEILING_KCAL
        val message = if (veryLowIntake) {
            "Sprout noticed today's meals were very light. Fuel up gently tomorrow — " +
                "the farm rewards a safe deficit, never skipped meals."
        } else {
            null
        }
        return DeficitReward(manaSparks = sparks, supportiveMessage = message)
    }

    private const val ML_PER_HYDRATION_POINT = 250.0
    private const val MAX_HYDRATION_BOOST = 10

    fun hydrationMoodBoost(hydrationMl: Double): Int =
        (hydrationMl / ML_PER_HYDRATION_POINT).roundToInt().coerceIn(0, MAX_HYDRATION_BOOST)

    private const val CELEBRATION_STEP_THRESHOLD = 8000L
    private const val ACTIVE_STEP_THRESHOLD = 5000L
    private const val SLUGGISH_STEP_THRESHOLD = 1500L
    private const val HIGH_HUNGER_THRESHOLD = 70
    private const val LOW_HAPPINESS_THRESHOLD = 40
    private const val HAPPY_THRESHOLD = 60

    /** Derives the critter's emotional state from its stats and today's activity. */
    fun deriveMood(
        critter: CritterEntity,
        todaySteps: Long?,
        todayWorkouts: Int,
        chestClaimedToday: Boolean,
    ): CritterMood {
        val steps = todaySteps ?: 0L
        return when {
            chestClaimedToday && steps >= CELEBRATION_STEP_THRESHOLD -> CritterMood.CELEBRATING
            todayWorkouts > 0 && steps >= ACTIVE_STEP_THRESHOLD -> CritterMood.CELEBRATING
            critter.hunger >= HIGH_HUNGER_THRESHOLD ||
                (steps < SLUGGISH_STEP_THRESHOLD && critter.happiness < LOW_HAPPINESS_THRESHOLD) ->
                CritterMood.SLUGGISH_TIRED
            critter.happiness >= HAPPY_THRESHOLD -> CritterMood.BOUNCING_HAPPY
            else -> CritterMood.NEUTRAL
        }
    }
}
