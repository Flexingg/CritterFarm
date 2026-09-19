package com.critterfarm.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Singleton row (always [SINGLETON_ID]) holding the farm's currencies, cosmetics and streak.
 * A single row is enough because this is a solo-player local game with no multi-farm concept.
 *
 * Currencies are earn-only in the game loop and are spent here: coins on cosmetics and streak
 * freezes, treats on feeding the critter, Mana Sparks on prestige cosmetics.
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
    /** Consecutive days the Daily Turn chest has been claimed. */
    val claimStreak: Int,
    /** Best streak ever reached — the number worth protecting. */
    val bestStreak: Int,
    /** Purchased Streak Freezes, consumed automatically when a day is missed. */
    val streakFreezes: Int,
    /** ISO date of the last claim, used to resolve streak continuity. */
    val lastClaimedDate: String?,
) {
    companion object {
        const val SINGLETON_ID = 0L

        /** A brand-new farm. Kept here so the repository and the migration agree on defaults. */
        fun empty(): FarmInventoryEntity = FarmInventoryEntity(
            id = SINGLETON_ID,
            coins = 0,
            treats = 0,
            manaSparks = 0,
            seeds = 0,
            ownedHatIds = "",
            equippedHatId = null,
            claimStreak = 0,
            bestStreak = 0,
            streakFreezes = 0,
            lastClaimedDate = null,
        )
    }
}
