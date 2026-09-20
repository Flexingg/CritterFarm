package com.critterfarm.data

import com.critterfarm.data.local.CritterEntity
import com.critterfarm.data.local.DailySummaryLogEntity

/**
 * What's needed to reach [stage], and how close the active critter is right now.
 *
 * [behaviorMet] is the consistency gate alone (level + goal days) — money can never buy it.
 * [met] is the full gate, [behaviorMet] AND enough Mana Sparks, which is what actually unlocks
 * the Evolve button. Keeping them separate lets the repository tell "not consistent enough" apart
 * from "consistent enough, just short on sparks".
 */
data class EvolutionRequirement(
    val stage: Int,
    val minLevel: Int,
    val minGoalDays: Int,
    val sparkCost: Int,
    val behaviorMet: Boolean,
    val met: Boolean,
    val progressText: String,
)

/**
 * Evolution needs both a behaviour gate and Mana Sparks — consistency alone never spends a spark
 * on the player's behalf, and sparks alone can never buy a stage. Pure and log-in only, so it's
 * trivial to unit test and the UI can show progress with zero Health Connect permissions.
 */
object EvolutionRules {

    /** A day only counts toward evolution if it met at least this many of the six daily targets. */
    const val GOAL_TARGET_THRESHOLD = 4

    /** The last real stage — there is no stage 4, and [requirementFor] clamps rather than throws. */
    const val MAX_STAGE = 3

    private data class StageRule(val minLevel: Int, val minGoalDays: Int, val sparkCost: Int)

    private val STAGE_RULES = mapOf(
        1 to StageRule(minLevel = 5, minGoalDays = 7, sparkCost = 20),
        2 to StageRule(minLevel = 12, minGoalDays = 21, sparkCost = 50),
        3 to StageRule(minLevel = 25, minGoalDays = 60, sparkCost = 120),
    )

    fun requirementFor(
        stage: Int,
        logs: List<DailySummaryLogEntity>,
        critter: CritterEntity,
        manaSparks: Int,
    ): EvolutionRequirement {
        val rule = STAGE_RULES[stage]
            ?: return EvolutionRequirement(
                stage = stage,
                minLevel = 0,
                minGoalDays = 0,
                sparkCost = 0,
                behaviorMet = false,
                met = false,
                progressText = "No further evolution beyond stage $MAX_STAGE.",
            )

        val goalDays = logs.count { HistoryRules.metTargets(it) >= GOAL_TARGET_THRESHOLD }
        val behaviorMet = critter.level >= rule.minLevel && goalDays >= rule.minGoalDays
        val met = behaviorMet && manaSparks >= rule.sparkCost
        val progressText = "Stage $stage · Level ${critter.level}/${rule.minLevel} · " +
            "$goalDays/${rule.minGoalDays} goal days · ${rule.sparkCost} sparks"

        return EvolutionRequirement(
            stage = stage,
            minLevel = rule.minLevel,
            minGoalDays = rule.minGoalDays,
            sparkCost = rule.sparkCost,
            behaviorMet = behaviorMet,
            met = met,
            progressText = progressText,
        )
    }
}

/**
 * The one place a stage number becomes a word — the barn, the farm header and the evolution
 * dialog all read through here so "Stage 3" and "Mythic" can never drift apart.
 */
object StageNames {
    private val NAMES = listOf("Hatchling", "Awakened", "Ascendant", "Mythic")

    /** Clamps rather than throws: any stage past the ceiling still reads as the top name. */
    fun forStage(stage: Int): String = NAMES.getOrElse(stage) { NAMES.last() }
}
