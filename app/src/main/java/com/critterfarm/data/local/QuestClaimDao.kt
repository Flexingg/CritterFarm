package com.critterfarm.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestClaimDao {
    @Query("SELECT * FROM quest_claims WHERE date = :date")
    fun observeForDate(date: String): Flow<List<QuestClaimEntity>>

    @Query("SELECT * FROM quest_claims WHERE date = :date AND questId = :questId LIMIT 1")
    suspend fun getClaim(date: String, questId: String): QuestClaimEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(claim: QuestClaimEntity)
}
