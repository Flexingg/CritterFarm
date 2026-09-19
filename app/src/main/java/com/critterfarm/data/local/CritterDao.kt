package com.critterfarm.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CritterDao {
    @Query("SELECT * FROM critters ORDER BY id LIMIT 1")
    fun observeCritter(): Flow<CritterEntity?>

    @Query("SELECT * FROM critters ORDER BY id LIMIT 1")
    suspend fun getCritter(): CritterEntity?

    @Insert
    suspend fun insert(critter: CritterEntity): Long

    @Update
    suspend fun update(critter: CritterEntity)
}
