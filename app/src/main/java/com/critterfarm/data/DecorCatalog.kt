package com.critterfarm.data

/**
 * Decoration categories. Slots exist so the shop can group items and so the scene can hint where
 * something belongs — they do not restrict placement, because telling a player their lantern
 * cannot stand in that particular square is the kind of rule that makes decorating feel like
 * paperwork.
 */
enum class DecorSlot {
    GROUND,
    WATER,
    STRUCTURE,
}

/**
 * One purchasable decoration.
 *
 * Decorations are the *only* thing coins buy besides hats and Streak Freezes, and they are purely
 * cosmetic on purpose: decorating the farm must never change what the game rewards, because a
 * pretty farm is not progress and the app refuses to blur that line.
 */
data class DecorItem(
    val id: String,
    val name: String,
    val emoji: String,
    val blurb: String,
    val price: Int,
    val slot: DecorSlot,
)

object DecorCatalog {
    /** The scene is a fixed 6x4 grid of cells, drawn around the critter. */
    const val GRID_COLUMNS = 6
    const val GRID_ROWS = 4
    const val CELL_COUNT = GRID_COLUMNS * GRID_ROWS

    /**
     * Eleven items from 100 to 1,500 coins, so decorating is a long-term sink rather than
     * something you finish in a week: 100 steps earn a single coin, so the farmhouse at the
     * bottom of this list is a quarter of a million steps of wandering.
     */
    val ALL: List<DecorItem> = listOf(
        DecorItem(
            "decor_flowers", "Wildflower Patch", "\uD83C\uDF3C",
            "A scruffy patch of colour. Cheap, cheerful, and it never needs watering.",
            100, DecorSlot.GROUND,
        ),
        DecorItem(
            "decor_haybale", "Hay Bale", "\uD83C\uDF3E",
            "Warm, dry and extremely sit-on-able. The critter's favourite nap spot.",
            150, DecorSlot.GROUND,
        ),
        DecorItem(
            "decor_lantern", "Farm Lantern", "\uD83C\uDFEE",
            "Keeps the evening chores from being a stumbling contest.",
            250, DecorSlot.STRUCTURE,
        ),
        DecorItem(
            "decor_logs", "Log Pile", "\uD83E\uDEB5",
            "Split and stacked. Proof that someone around here does the work.",
            300, DecorSlot.GROUND,
        ),
        DecorItem(
            "decor_pond", "Duck Pond", "\uD83D\uDCA7",
            "A proper pond, even if the ducks have not been consulted.",
            400, DecorSlot.WATER,
        ),
        DecorItem(
            "decor_beehive", "Bee Hive", "\uD83D\uDC1D",
            "Busy neighbours. They pay their rent in pollination.",
            500, DecorSlot.GROUND,
        ),
        DecorItem(
            "decor_scarecrow", "Scarecrow", "\uD83E\uDDD1\u200D\uD83C\uDF3E",
            "Does not scare crows, does make the place look officially farmed.",
            600, DecorSlot.STRUCTURE,
        ),
        DecorItem(
            "decor_appletree", "Apple Tree", "\uD83C\uDF33",
            "Shade in summer, snacks in autumn, somewhere to lean all year.",
            750, DecorSlot.GROUND,
        ),
        DecorItem(
            "decor_sunflowers", "Sunflower Row", "\uD83C\uDF3B",
            "A row of them, all facing the same way, showing off.",
            900, DecorSlot.GROUND,
        ),
        DecorItem(
            "decor_fountain", "Stone Fountain", "\u26F2",
            "Entirely unnecessary. That is the point of a fountain.",
            1_200, DecorSlot.WATER,
        ),
        DecorItem(
            "decor_farmhouse", "Little Farmhouse", "\uD83C\uDFE1",
            "The long game: a quarter of a million steps of wandering, made visible.",
            1_500, DecorSlot.STRUCTURE,
        ),
    )

    fun item(id: String): DecorItem? = ALL.firstOrNull { it.id == id }

    /** Cheapest first — the order the shop should offer them in. */
    val BY_PRICE: List<DecorItem> = ALL.sortedBy { it.price }
}
