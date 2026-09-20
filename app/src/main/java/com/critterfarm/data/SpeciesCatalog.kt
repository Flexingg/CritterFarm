package com.critterfarm.data

import androidx.compose.ui.graphics.Color
import com.critterfarm.ui.theme.AuroraPurple
import com.critterfarm.ui.theme.AuroraTeal
import com.critterfarm.ui.theme.BerryPink
import com.critterfarm.ui.theme.BunnyCream
import com.critterfarm.ui.theme.DormantGray
import com.critterfarm.ui.theme.EmberOrange
import com.critterfarm.ui.theme.EmberRed
import com.critterfarm.ui.theme.PondBlueDeep
import com.critterfarm.ui.theme.SkyBlue
import com.critterfarm.ui.theme.SproutGreen
import com.critterfarm.ui.theme.SproutGreenDeep
import com.critterfarm.ui.theme.SunshineYellow
import com.critterfarm.ui.theme.TreatBrown

/**
 * One species in the barn: what it looks like, what it costs to hatch, and what earns the right
 * to hatch it. `unlockNote` is shown verbatim on a locked hatch card, so it must read as an
 * instruction ("do X to unlock"), not a status.
 *
 * [minHarmony] is a second, independent unlock gate on top of the usual log-based one (see
 * [HatchRules]): a nonzero value means the species also needs that much [HarmonyRules.harmony],
 * so no amount of Mana Sparks or logged behaviour can skip it early. Zero means no such gate.
 */
data class Species(
    val key: String,
    val displayName: String,
    val emoji: String,
    val blurb: String,
    val hatchCostSparks: Int,
    val primaryColor: Color,
    val accentColor: Color,
    val unlockNote: String,
    val minHarmony: Int = 0,
)

/**
 * The six critters a player can hatch. Cost and difficulty climb together on purpose: the
 * cheapest unlocks (one workout) also hatch cheap, and the hardest consistency asks (10 deficit
 * days) command the highest price — currency alone can never substitute for the behaviour gate,
 * see [HatchRules].
 */
object SpeciesCatalog {

    val BLOB = Species(
        key = "blob",
        displayName = "Blob",
        emoji = "🟢",
        blurb = "Everyone starts here — round, green, and always up for anything.",
        hatchCostSparks = 0,
        primaryColor = SproutGreen,
        accentColor = SproutGreenDeep,
        unlockNote = "Always available — the starter critter.",
    )

    val BUNNY = Species(
        key = "bunny",
        displayName = "Pasture Bunny",
        emoji = "🐰",
        blurb = "Quick on its feet and always up for a lap of the pasture.",
        hatchCostSparks = 15,
        primaryColor = BunnyCream,
        accentColor = BerryPink,
        unlockNote = "Log any 1 workout to unlock.",
    )

    val CHICK = Species(
        key = "chick",
        displayName = "Sunrise Chick",
        emoji = "🐥",
        blurb = "Up with the sun and always chasing the step goal.",
        hatchCostSparks = 25,
        primaryColor = SunshineYellow,
        accentColor = EmberOrange,
        unlockNote = "Meet the steps target on ${HatchRules.CHICK_DAYS} days to unlock.",
    )

    val AXOLOTL = Species(
        key = "axolotl",
        displayName = "Pond Axolotl",
        emoji = "🦎",
        blurb = "Never far from water, and always well hydrated.",
        hatchCostSparks = 40,
        primaryColor = SkyBlue,
        accentColor = PondBlueDeep,
        unlockNote = "Meet the hydration target on ${HatchRules.AXOLOTL_DAYS} days to unlock.",
    )

    val DRAGON = Species(
        key = "dragon",
        displayName = "Ember Drake",
        emoji = "🐉",
        blurb = "Burns hot — a safe, steady deficit is what feeds the flame.",
        hatchCostSparks = 60,
        primaryColor = EmberOrange,
        accentColor = EmberRed,
        unlockNote = "Meet the calorie deficit target on ${HatchRules.DRAGON_DAYS} days to unlock.",
    )

    val SLOTH = Species(
        key = "sloth",
        displayName = "Cozy Sloth",
        emoji = "🦥",
        blurb = "Slow, sleepy, and serious about a full night's rest.",
        hatchCostSparks = 80,
        primaryColor = TreatBrown,
        accentColor = DormantGray,
        unlockNote = "Meet the sleep target on ${HatchRules.SLOTH_DAYS} days to unlock.",
    )

    /**
     * The seventh critter, reserved for a fully-harmonious barn. Its unlock is not satisfiable
     * by logs alone — see [minHarmony] — so it is genuinely locked below Mythic harmony (10),
     * no matter how many Mana Sparks are stockpiled.
     */
    val PHOENIX = Species(
        key = "phoenix",
        displayName = "Aurora Phoenix",
        emoji = "🔥",
        blurb = "Legend says it only nests where every other critter is already thriving.",
        hatchCostSparks = 120,
        primaryColor = AuroraPurple,
        accentColor = AuroraTeal,
        unlockNote = "Reach Mythic Farm harmony (10) to unlock — sparks alone can't buy this one.",
        minHarmony = 10,
    )

    /** Cost/difficulty ladder order — also the order the Hatch section renders in. */
    val ALL: List<Species> = listOf(BLOB, BUNNY, CHICK, AXOLOTL, DRAGON, SLOTH, PHOENIX)

    fun bySpecies(key: String): Species? = ALL.firstOrNull { it.key == key }
}
