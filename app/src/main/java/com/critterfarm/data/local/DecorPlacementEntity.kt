package com.critterfarm.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * One placed decoration. The cell index is the primary key because a square can hold exactly one
 * thing — turning "occupied" into a database constraint instead of a rule the UI has to remember.
 */
@Entity(tableName = "decor_placements")
data class DecorPlacementEntity(
    @PrimaryKey val cellIndex: Int,
    val decorId: String,
    val placedAt: Long,
)

@Dao
interface DecorPlacementDao {
    @Query("SELECT * FROM decor_placements ORDER BY cellIndex")
    fun observeAll(): Flow<List<DecorPlacementEntity>>

    @Query("SELECT * FROM decor_placements ORDER BY cellIndex")
    suspend fun getAll(): List<DecorPlacementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(placement: DecorPlacementEntity)

    @Query("DELETE FROM decor_placements WHERE cellIndex = :cellIndex")
    suspend fun removeAt(cellIndex: Int)

    @Query("SELECT COUNT(*) FROM decor_placements")
    suspend fun count(): Int
}
