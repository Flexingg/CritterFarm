package com.critterfarm.data

/** Which direction a quest's target reads. Most quests want more; a few want less. */
enum class QuestCompare { AT_LEAST, AT_MOST }

/**
 * One entry in the quest pool. [QuestsForDay] picks three of these per day; [QuestRules] scores
 * them against today's log. Nothing here is randomised — the pool is static data, so the same
 * quest id always means the same text, reward and target.
 */
data class Quest(
    val id: String,
    val text: String,
    val emoji: String,
    val rewardCoins: Int,
    val rewardTreats: Int,
    val metric: Metric,
    val target: Double,
    val compare: QuestCompare,
)

/**
 * The full quest pool, spanning all six metrics with rewards roughly proportional to difficulty.
 * [QuestsForDay] draws today's three from this list, so growing the pool only adds variety — it
 * never changes what a given quest id means.
 */
object QuestCatalog {
    val ALL: List<Quest> = listOf(
        Quest(
            id = "steps_6k",
            text = "Take a 6,000-step wander",
            emoji = "🚶",
            rewardCoins = 15,
            rewardTreats = 0,
            metric = Metric.STEPS,
            target = 6_000.0,
            compare = QuestCompare.AT_LEAST,
        ),
        Quest(
            id = "steps_8k",
            text = "Walk 8,000 steps",
            emoji = "👟",
            rewardCoins = 20,
            rewardTreats = 0,
            metric = Metric.STEPS,
            target = 8_000.0,
            compare = QuestCompare.AT_LEAST,
        ),
        Quest(
            id = "steps_10k",
            text = "Hit the 10,000-step target",
            emoji = "🏃",
            rewardCoins = 30,
            rewardTreats = 0,
            metric = Metric.STEPS,
            target = 10_000.0,
            compare = QuestCompare.AT_LEAST,
        ),
        Quest(
            id = "steps_12k",
            text = "Push past 12,000 steps",
            emoji = "🔥",
            rewardCoins = 45,
            rewardTreats = 0,
            metric = Metric.STEPS,
            target = 12_000.0,
            compare = QuestCompare.AT_LEAST,
        ),
        Quest(
            id = "steps_15k",
            text = "Go big: 15,000 steps",
            emoji = "🚀",
            rewardCoins = 60,
            rewardTreats = 0,
            metric = Metric.STEPS,
            target = 15_000.0,
            compare = QuestCompare.AT_LEAST,
        ),
        Quest(
            id = "water_64",
            text = "Sip 64 fl oz of water",
            emoji = "💧",
            rewardCoins = 15,
            rewardTreats = 0,
            metric = Metric.HYDRATION,
            target = 64.0,
            compare = QuestCompare.AT_LEAST,
        ),
        Quest(
            id = "water_80",
            text = "Reach 80 fl oz today",
            emoji = "💦",
            rewardCoins = 20,
            rewardTreats = 0,
            metric = Metric.HYDRATION,
            target = 80.0,
            compare = QuestCompare.AT_LEAST,
        ),
        Quest(
            id = "water_100",
            text = "Log 100 fl oz of water",
            emoji = "🌊",
            rewardCoins = 25,
            rewardTreats = 0,
            metric = Metric.HYDRATION,
            target = 100.0,
            compare = QuestCompare.AT_LEAST,
        ),
        Quest(
            id = "workout_1",
            text = "Get in one workout",
            emoji = "🏋",
            rewardCoins = 0,
            rewardTreats = 5,
            metric = Metric.WORKOUTS,
            target = 1.0,
            compare = QuestCompare.AT_LEAST,
        ),
        Quest(
            id = "workout_2",
            text = "Double up: two workouts",
            emoji = "💪",
            rewardCoins = 0,
            rewardTreats = 10,
            metric = Metric.WORKOUTS,
            target = 2.0,
            compare = QuestCompare.AT_LEAST,
        ),
        Quest(
            id = "workout_3",
            text = "Three workouts, one day",
            emoji = "🏆",
            rewardCoins = 0,
            rewardTreats = 15,
            metric = Metric.WORKOUTS,
            target = 3.0,
            compare = QuestCompare.AT_LEAST,
        ),
        Quest(
            id = "sleep_7",
            text = "Sleep at least 7 hours",
            emoji = "😴",
            rewardCoins = 20,
            rewardTreats = 0,
            metric = Metric.SLEEP,
            target = 7.0,
            compare = QuestCompare.AT_LEAST,
        ),
        Quest(
            id = "sleep_8",
            text = "Bank a full 8 hours of sleep",
            emoji = "🌙",
            rewardCoins = 30,
            rewardTreats = 0,
            metric = Metric.SLEEP,
            target = 8.0,
            compare = QuestCompare.AT_LEAST,
        ),
        Quest(
            id = "deficit_safe",
            text = "Land a safe calorie deficit",
            emoji = "⚡",
            rewardCoins = 25,
            rewardTreats = 0,
            metric = Metric.DEFICIT,
            target = GameGoals.DEFICIT_KCAL,
            compare = QuestCompare.AT_LEAST,
        ),
        Quest(
            id = "deficit_sane",
            text = "Keep the deficit sustainable",
            emoji = "🌿",
            rewardCoins = 15,
            rewardTreats = 0,
            metric = Metric.DEFICIT,
            target = 1_000.0,
            compare = QuestCompare.AT_MOST,
        ),
        Quest(
            id = "weighin_log",
            text = "Step on the scale",
            emoji = "⚖️",
            rewardCoins = 10,
            rewardTreats = 0,
            metric = Metric.WEIGHT,
            target = 0.0,
            compare = QuestCompare.AT_LEAST,
        ),
    )
}
