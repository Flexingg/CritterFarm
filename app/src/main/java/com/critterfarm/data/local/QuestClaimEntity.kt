package com.critterfarm.data.local

import androidx.room.Entity

/**
 * One claimed quest for one day. The composite key is what makes a claim idempotent per
 * (day, quest) — claiming the same quest twice on the same day is a no-op at the database level,
 * exactly like [DailySummaryLogEntity.chestClaimed] for the Daily Turn chest.
 */
@Entity(tableName = "quest_claims", primaryKeys = ["date", "questId"])
data class QuestClaimEntity(
    val date: String,
    val questId: String,
    val claimedAt: Long,
)
