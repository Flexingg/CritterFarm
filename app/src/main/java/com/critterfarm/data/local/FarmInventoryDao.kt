package com.critterfarm.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FarmInventoryDao {
    @Query("SELECT * FROM farm_inventory WHERE id = :id")
    fun observeInventory(id: Long): Flow<FarmInventoryEntity?>

    @Query("SELECT * FROM farm_inventory WHERE id = :id")
    suspend fun getInventory(id: Long): FarmInventoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(inventory: FarmInventoryEntity)
}
