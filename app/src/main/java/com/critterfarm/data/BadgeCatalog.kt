package com.critterfarm.data

enum class BadgeTier { BRONZE, SILVER, GOLD }

/** One trophy. The condition itself lives in [BadgeRules.progressFor], keyed by [id]. */
data class Badge(
    val id: String,
    val name: String,
    val emoji: String,
    val blurb: String,
    val tier: BadgeTier,
)

/**
 * Every badge the farm can award, all computed from history already on disk — no new Health
 * Connect permission ever unlocks one. Three tiers, each with a concrete headline number so a
 * locked card can always say exactly how close it is.
 */
object BadgeCatalog {
    val ALL: List<Badge> = listOf(
        // ------------------------------------------------------------------------ bronze ----
        Badge(
            id = "first_10k_steps",
            name = "First Big Walk",
            emoji = "👟",
            blurb = "Hit 10,000 steps in a single day.",
            tier = BadgeTier.BRONZE,
        ),
        Badge(
            id = "first_workout",
            name = "First Rep",
            emoji = "🏋",
            blurb = "Log your first workout.",
            tier = BadgeTier.BRONZE,
        ),
        Badge(
            id = "weighins_10",
            name = "Perfectly Weighed",
            emoji = "⚖️",
            blurb = "Log 10 weigh-ins.",
            tier = BadgeTier.BRONZE,
        ),
        Badge(
            id = "sleep_8h_5days",
            name = "Well Rested",
            emoji = "😴",
            blurb = "Sleep 8+ hours on 5 different days.",
            tier = BadgeTier.BRONZE,
        ),
        Badge(
            id = "workouts_10",
            name = "Warming Up",
            emoji = "💪",
            blurb = "Log 10 workouts, lifetime.",
            tier = BadgeTier.BRONZE,
        ),
        Badge(
            id = "days_logged_50",
            name = "Fifty Days In",
            emoji = "📅",
            blurb = "Log 50 days on the farm.",
            tier = BadgeTier.BRONZE,
        ),
        Badge(
            id = "full_week_logged",
            name = "Full Week",
            emoji = "🗓",
            blurb = "Log every day for a full week straight.",
            tier = BadgeTier.BRONZE,
        ),
        Badge(
            id = "consistent_bronze",
            name = "Consistency: Bronze",
            emoji = "🥉",
            blurb = "Hit 4+ of 6 daily targets on 15 days.",
            tier = BadgeTier.BRONZE,
        ),
        // ------------------------------------------------------------------------ silver ----
        Badge(
            id = "steps_100k_lifetime",
            name = "100K Club",
            emoji = "🥈",
            blurb = "Walk 100,000 lifetime steps.",
            tier = BadgeTier.SILVER,
        ),
        Badge(
            id = "perfect_days_5",
            name = "Five Perfect Days",
            emoji = "⭐",
            blurb = "Hit all 6 daily targets on 5 different days.",
            tier = BadgeTier.SILVER,
        ),
        Badge(
            id = "workouts_25",
            name = "Gym Regular",
            emoji = "🏆",
            blurb = "Log 25 workouts, lifetime.",
            tier = BadgeTier.SILVER,
        ),
        Badge(
            id = "hydration_streak_7",
            name = "Hydration Week",
            emoji = "💧",
            blurb = "Hit your water target 7 days in a row.",
            tier = BadgeTier.SILVER,
        ),
        Badge(
            id = "steps_streak_7",
            name = "Step Streak",
            emoji = "🔥",
            blurb = "Hit your step target 7 days in a row.",
            tier = BadgeTier.SILVER,
        ),
        Badge(
            id = "safe_deficit_20",
            name = "Steady Burn",
            emoji = "⚡",
            blurb = "Land a safe calorie deficit on 20 days.",
            tier = BadgeTier.SILVER,
        ),
        Badge(
            id = "consistent_silver",
            name = "Consistency: Silver",
            emoji = "🥈",
            blurb = "Hit 4+ of 6 daily targets on 30 days.",
            tier = BadgeTier.SILVER,
        ),
        // -------------------------------------------------------------------------- gold ----
        Badge(
            id = "steps_250k_lifetime",
            name = "Quarter Million Steps",
            emoji = "🥇",
            blurb = "Walk 250,000 lifetime steps.",
            tier = BadgeTier.GOLD,
        ),
        Badge(
            id = "claim_streak_30",
            name = "Thirty Days Strong",
            emoji = "🏅",
            blurb = "Claim the Daily Turn chest 30 days in a row.",
            tier = BadgeTier.GOLD,
        ),
        Badge(
            id = "perfect_days_20",
            name = "Flawless",
            emoji = "👑",
            blurb = "Hit all 6 daily targets on 20 different days.",
            tier = BadgeTier.GOLD,
        ),
        Badge(
            id = "consistent_gold",
            name = "Consistency: Gold",
            emoji = "🥇",
            blurb = "Hit 4+ of 6 daily targets on 60 days.",
            tier = BadgeTier.GOLD,
        ),
    )
}
