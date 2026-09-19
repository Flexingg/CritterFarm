package com.critterfarm.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per calendar day (ISO `yyyy-MM-dd`, local time zone). Health totals are updated by
 * every delta sync; the reward fields (xpEarned, coinsEarned, treatsEarned) and
 * [chestClaimed] are only written once, by "Claim Daily Turn," to keep claiming idempotent.
 */
@Entity(tableName = "daily_summary_logs", indices = [Index(value = ["date"], unique = true)])
data class DailySummaryLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val steps: Long,
    val caloriesBurned: Double,
    val caloriesConsumed: Double,
    val deficit: Double,
    val hydrationMl: Double,
    val sleepMinutes: Long,
    val workouts: Int,
    val weightKg: Double?,
    val xpEarned: Int,
    val coinsEarned: Int,
    val treatsEarned: Int,
    val chestClaimed: Boolean,
    val syncedAt: Long,
)
