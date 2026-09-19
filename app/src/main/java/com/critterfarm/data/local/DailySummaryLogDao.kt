package com.critterfarm.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DailySummaryLogDao {
    @Query("SELECT * FROM daily_summary_logs ORDER BY date DESC")
    fun observeAll(): Flow<List<DailySummaryLogEntity>>

    @Query("SELECT * FROM daily_summary_logs WHERE date = :date LIMIT 1")
    fun observeByDate(date: String): Flow<DailySummaryLogEntity?>

    @Query("SELECT * FROM daily_summary_logs WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): DailySummaryLogEntity?

    @Query("SELECT MAX(syncedAt) FROM daily_summary_logs")
    suspend fun getMaxSyncedAt(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: DailySummaryLogEntity): Long
}
