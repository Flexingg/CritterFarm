package com.critterfarm.data

import com.critterfarm.data.local.CritterEntity
import com.critterfarm.data.local.CritterMood
import java.time.LocalDate
import java.time.temporal.ChronoUnit
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

    // ---------------------------------------------------------------- daily turn streaks ----

    private const val STREAK_TIER_1 = 3
    private const val STREAK_TIER_2 = 7
    private const val STREAK_TIER_3 = 30

    /**
     * The multiplier a claim earns from the current streak. The chain is what compounds —
     * a single big day cannot buy it, which is exactly the habit the app is trying to build.
     */
    fun claimMultiplier(streakDays: Int): Double = when {
        streakDays >= STREAK_TIER_3 -> 3.0
        streakDays >= STREAK_TIER_2 -> 2.0
        streakDays >= STREAK_TIER_1 -> 1.5
        else -> 1.0
    }

    fun applyMultiplier(base: Int, multiplier: Double): Int =
        (base * multiplier).roundToInt()

    /** What claiming today does to the streak, and whether it cost a freeze. */
    data class StreakOutcome(val streak: Int, val freezesLeft: Int, val freezeUsed: Boolean)

    /**
     * Resolves the streak for a claim on [today].
     *
     * - claimed yesterday → streak grows
     * - claimed today → idempotent, no growth (the chest is already open)
     * - missed a day → a Streak Freeze is consumed if one is owned, otherwise the chain resets
     *
     * Freezes protect the *chain*, never the health maths: they cannot grant rewards that a
     * 10,000-step day did not earn.
     */
    fun nextStreak(
        previousStreak: Int,
        lastClaimedDate: String?,
        today: LocalDate,
        freezesAvailable: Int,
    ): StreakOutcome {
        if (lastClaimedDate == null) {
            return StreakOutcome(streak = 1, freezesLeft = freezesAvailable, freezeUsed = false)
        }
        val last = runCatching { LocalDate.parse(lastClaimedDate) }.getOrNull()
            ?: return StreakOutcome(streak = 1, freezesLeft = freezesAvailable, freezeUsed = false)
        val gapDays = ChronoUnit.DAYS.between(last, today)
        return when {
            gapDays <= 0L -> StreakOutcome(previousStreak.coerceAtLeast(1), freezesAvailable, false)
            gapDays == 1L -> StreakOutcome(previousStreak + 1, freezesAvailable, false)
            freezesAvailable > 0 -> StreakOutcome(previousStreak + 1, freezesAvailable - 1, freezeUsed = true)
            else -> StreakOutcome(streak = 1, freezesLeft = 0, freezeUsed = false)
        }
    }

    // ------------------------------------------------------------------------- feeding ----

    private const val HAPPINESS_PER_TREAT = 15
    private const val HUNGER_RELIEF_PER_TREAT = 40

    data class FeedOutcome(val happiness: Int, val hunger: Int)

    /** One treat: happier, less hungry, both clamped to 0..100. */
    fun feed(happiness: Int, hunger: Int): FeedOutcome = FeedOutcome(
        happiness = (happiness + HAPPINESS_PER_TREAT).coerceIn(0, 100),
        hunger = (hunger - HUNGER_RELIEF_PER_TREAT).coerceIn(0, 100),
    )

    // ------------------------------------------------------------------- inventory bits ----

    /** `ownedHatIds` is stored as a comma-separated string; keep the parsing in one place. */
    fun parseOwnedHatIds(csv: String): Set<String> =
        csv.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    fun serializeOwnedHatIds(ids: Set<String>): String = ids.sorted().joinToString(",")

    /** Cosmetics are the only thing currency buys, so affordability is a simple purse check. */
    fun canAfford(item: ShopItem, coins: Int, manaSparks: Int): Boolean = when (item.currency) {
        ShopCurrency.COINS -> coins >= item.price
        ShopCurrency.MANA_SPARKS -> manaSparks >= item.price
    }
}
