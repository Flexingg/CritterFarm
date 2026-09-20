package com.critterfarm.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * One claimed challenge for one period. The composite key is what makes a claim idempotent per
 * (period, challenge) — weekly, monthly and event challenges all key off the same two columns, so
 * claiming "four workouts this week" for week 38 never touches week 39's claim, and the same
 * challenge id is claimable again once its next period starts.
 */
@Entity(tableName = "challenge_claims", primaryKeys = ["periodKey", "challengeId"])
data class ChallengeClaimEntity(
    val periodKey: String,
    val challengeId: String,
    val claimedAt: Long,
)

@Dao
interface ChallengeClaimDao {
    @Query("SELECT * FROM challenge_claims")
    fun observeAll(): Flow<List<ChallengeClaimEntity>>

    @Query("SELECT * FROM challenge_claims WHERE periodKey = :periodKey AND challengeId = :challengeId LIMIT 1")
    suspend fun getClaim(periodKey: String, challengeId: String): ChallengeClaimEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(claim: ChallengeClaimEntity)
}
