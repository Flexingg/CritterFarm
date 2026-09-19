package com.critterfarm.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Singleton row (always [SINGLETON_ID]) holding the farm's currencies and cosmetics. A
 * single row is enough because this is a solo-player local game with no multi-farm concept.
 */
@Entity(tableName = "farm_inventory")
data class FarmInventoryEntity(
    @PrimaryKey val id: Long = SINGLETON_ID,
    val coins: Int,
    val treats: Int,
    val manaSparks: Int,
    val seeds: Int,
    val ownedHatIds: String,
    val equippedHatId: String?,
) {
    companion object {
        const val SINGLETON_ID = 0L
    }
}
