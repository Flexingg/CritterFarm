package com.critterfarm.data

import com.critterfarm.data.local.DailySummaryLogEntity

/**
 * Scores a [Quest] against today's log. Nothing here is stored — progress is always recomputed
 * from [DailySummaryLogEntity], the same source the stats card reads, so a quest can never show a
 * number the farm itself disagrees with.
 */
object QuestRules {

    /** True once the quest's target is actually met — a null log or missing metric never is. */
    fun isComplete(quest: Quest, log: DailySummaryLogEntity?): Boolean {
        val value = log?.let { quest.metric.valueIn(it) } ?: return false
        return when (quest.compare) {
            QuestCompare.AT_LEAST -> value >= quest.target
            QuestCompare.AT_MOST -> value <= quest.target
        }
    }

    /** 0f..1f, for a progress bar. AT_MOST quests fill up as the value approaches the cap. */
    fun progress(quest: Quest, log: DailySummaryLogEntity?): Float {
        val value = log?.let { quest.metric.valueIn(it) } ?: return 0f
        return when (quest.compare) {
            QuestCompare.AT_LEAST -> {
                if (quest.target <= 0.0) return 1f
                (value / quest.target).toFloat().coerceIn(0f, 1f)
            }
            QuestCompare.AT_MOST -> {
                if (value <= quest.target) 1f else (quest.target / value).toFloat().coerceIn(0f, 1f)
            }
        }
    }
}
