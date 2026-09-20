package com.critterfarm.data

import com.critterfarm.data.local.CritterEntity

/**
 * What a harmony tier unlocks. Every field here is a *capability* — more quests to do, more room
 * to decorate, more things that become hatchable/purchasable — never a multiplier on rewards.
 * That shape is deliberate: it should be impossible to express "earn more coins/sparks/treats"
 * through this type at all, which is what keeps a stronger barn from ever paying better than a
 * smaller one for the same real-world behaviour.
 */
data class HarmonyBonuses(
    val extraDailyQuests: Int = 0,
    val decorRows: Int = 0,
    val unlockedSpeciesIds: Set<String> = emptySet(),
    val unlockedHatIds: Set<String> = emptySet(),
)

data class HarmonyTier(
    val id: String,
    val name: String,
    val emoji: String,
    val blurb: String,
    val minHarmony: Int,
    val bonuses: HarmonyBonuses,
)

/**
 * Barn Harmony is the payoff for keeping every critter around, not just the active one: breadth
 * (more species hatched) and depth (deeper evolutions) both add to the same number, so a
 * stage-2 critter parked in the barn still pulls its weight.
 *
 * Deliberately derived, never stored — it is a pure function of the owned critters' stages, so
 * there is no schema to migrate and nothing to get out of sync.
 *
 * Tiers are cumulative going up the ladder: reaching Thriving Farm never takes back what Working
 * Farm granted. That is what makes every bonus here safe to be a capability rather than a
 * reward-multiplier — nothing is ever lost by evolving or hatching further.
 */
object HarmonyRules {

    val TIERS: List<HarmonyTier> = listOf(
        HarmonyTier(
            id = "quiet",
            name = "Quiet Barn",
            emoji = "🏡",
            blurb = "One critter, doing its best. Every farm starts here.",
            minHarmony = 1,
            bonuses = HarmonyBonuses(),
        ),
        HarmonyTier(
            id = "working",
            name = "Working Farm",
            emoji = "🌾",
            blurb = "A few critters pulling together — one more quest shows up each day.",
            minHarmony = 3,
            bonuses = HarmonyBonuses(extraDailyQuests = 1),
        ),
        HarmonyTier(
            id = "thriving",
            name = "Thriving Farm",
            emoji = "🌻",
            blurb = "The barn is full of life — the farm scene grows a whole extra row.",
            minHarmony = 6,
            bonuses = HarmonyBonuses(extraDailyQuests = 1, decorRows = 1),
        ),
        HarmonyTier(
            id = "mythic",
            name = "Mythic Farm",
            emoji = "✨",
            blurb = "Legendary company — the Aurora Phoenix and the Aurora Crown are within reach.",
            minHarmony = 10,
            bonuses = HarmonyBonuses(
                extraDailyQuests = 1,
                decorRows = 1,
                unlockedSpeciesIds = setOf(SpeciesCatalog.PHOENIX.key),
                unlockedHatIds = setOf(ShopCatalog.AURORA_CROWN.id),
            ),
        ),
    )

    /** A lone stage-0 starter is harmony 1; every stage deeper adds one more. */
    fun harmony(critters: List<CritterEntity>): Int = critters.sumOf { it.stage + 1 }

    /** The highest tier whose threshold is met. Harmony 0 (no critters) clamps to the first tier. */
    fun tierFor(harmony: Int): HarmonyTier = TIERS.lastOrNull { harmony >= it.minHarmony } ?: TIERS.first()

    /** The next tier up, or null once [harmony] has already reached the top one. */
    fun nextTier(harmony: Int): HarmonyTier? {
        val currentIndex = TIERS.indexOf(tierFor(harmony))
        return TIERS.getOrNull(currentIndex + 1)
    }

    fun progressToNext(harmony: Int): String {
        val next = nextTier(harmony) ?: return "top tier reached"
        return "$harmony / ${next.minHarmony} harmony"
    }
}
