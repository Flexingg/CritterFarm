package com.critterfarm.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CritterDao {
    @Query("SELECT * FROM critters ORDER BY id")
    fun observeAll(): Flow<List<CritterEntity>>

    @Query("SELECT * FROM critters ORDER BY id")
    suspend fun getAll(): List<CritterEntity>

    @Query("SELECT * FROM critters WHERE isActive = 1 LIMIT 1")
    fun observeActive(): Flow<CritterEntity?>

    @Query("SELECT * FROM critters WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): CritterEntity?

    @Query("SELECT * FROM critters WHERE id = :id")
    suspend fun getById(id: Long): CritterEntity?

    @Insert
    suspend fun insert(critter: CritterEntity): Long

    @Update
    suspend fun update(critter: CritterEntity)

    @Query("UPDATE critters SET isActive = 0")
    suspend fun clearActive()

    @Query("UPDATE critters SET isActive = 1 WHERE id = :id")
    suspend fun setActive(id: Long)

    /** Makes exactly one critter active: a clear-all UPDATE, then a set-one UPDATE, atomically. */
    @Transaction
    suspend fun setActiveExclusive(id: Long) {
        clearActive()
        setActive(id)
    }
}
