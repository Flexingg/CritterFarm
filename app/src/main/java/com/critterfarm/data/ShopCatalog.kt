package com.critterfarm.data

/** Which purse an item is paid from. Currencies must stay distinct so each one has a job. */
enum class ShopCurrency { COINS, MANA_SPARKS }

/** Only cosmetics and streak insurance are purchasable — never health progress. */
enum class ShopItemKind { HAT, STREAK_FREEZE }

data class ShopItem(
    val id: String,
    val name: String,
    val emoji: String,
    val description: String,
    val price: Int,
    val currency: ShopCurrency,
    val kind: ShopItemKind,
    /** Nonzero only for a Barn-Harmony-gated cosmetic (see [HarmonyRules]) — still only decoration. */
    val minHarmony: Int = 0,
) {
    /** Repeatable items (streak freezes) can be bought again and again. */
    val repeatable: Boolean get() = kind == ShopItemKind.STREAK_FREEZE
}

/**
 * What the currencies are FOR.
 *
 * Coins are the abundant, everyday currency (100 steps = 1 coin), so they buy the frequent
 * small dopamine hits: a new hat every couple of days, and streak insurance when life happens.
 * Mana Sparks are scarce and earned only by a *safe* deficit, so they buy the prestige item.
 *
 * Deliberately absent: anything that buys health progress. A player cannot purchase a level,
 * an evolution, or a skipped workout — those have to be earned by behaviour. That keeps the
 * game honest about what it is teaching.
 */
object ShopCatalog {

    val STRAW_HAT = ShopItem(
        id = "hat_straw",
        name = "Straw Hat",
        emoji = "👒",
        description = "A field hat for a critter who roams the pasture.",
        price = 150,
        currency = ShopCurrency.COINS,
        kind = ShopItemKind.HAT,
    )

    val PARTY_HAT = ShopItem(
        id = "hat_party",
        name = "Party Hat",
        emoji = "🎉",
        description = "For hitting a goal. Wear it at 10,000 steps and beyond.",
        price = 300,
        currency = ShopCurrency.COINS,
        kind = ShopItemKind.HAT,
    )

    val BEANIE = ShopItem(
        id = "hat_beanie",
        name = "Cozy Beanie",
        emoji = "🧢",
        description = "Sleep-tracked critters love a warm head.",
        price = 500,
        currency = ShopCurrency.COINS,
        kind = ShopItemKind.HAT,
    )

    val COWBOY_HAT = ShopItem(
        id = "hat_cowboy",
        name = "Ranch Hat",
        emoji = "🤠",
        description = "The pasture is big. Somebody has to run it.",
        price = 900,
        currency = ShopCurrency.COINS,
        kind = ShopItemKind.HAT,
    )

    val CROWN = ShopItem(
        id = "hat_crown",
        name = "Golden Crown",
        emoji = "👑",
        description = "A long-term goal, earned in steps alone.",
        price = 2_000,
        currency = ShopCurrency.COINS,
        kind = ShopItemKind.HAT,
    )

    val HALO = ShopItem(
        id = "hat_halo",
        name = "Deficit Halo",
        emoji = "😇",
        description = "Reserved for safe, consistent deficits. Never for skipped meals.",
        price = 30,
        currency = ShopCurrency.MANA_SPARKS,
        kind = ShopItemKind.HAT,
    )

    val AURORA_CROWN = ShopItem(
        id = "hat_aurora_crown",
        name = "Aurora Crown",
        emoji = "🌈",
        description = "Woven from a Mythic-harmony barn. Every critter you own helped make this.",
        price = 3_000,
        currency = ShopCurrency.COINS,
        kind = ShopItemKind.HAT,
        minHarmony = 10,
    )

    val STREAK_FREEZE = ShopItem(
        id = "streak_freeze",
        name = "Streak Freeze",
        emoji = "🧊",
        description = "Miss a day and the chain survives. Life happens; the streak need not.",
        price = 250,
        currency = ShopCurrency.COINS,
        kind = ShopItemKind.STREAK_FREEZE,
    )

    val HATS: List<ShopItem> = listOf(STRAW_HAT, PARTY_HAT, BEANIE, COWBOY_HAT, CROWN, HALO, AURORA_CROWN)

    val ALL: List<ShopItem> = HATS + STREAK_FREEZE

    fun item(id: String): ShopItem? = ALL.firstOrNull { it.id == id }
}
