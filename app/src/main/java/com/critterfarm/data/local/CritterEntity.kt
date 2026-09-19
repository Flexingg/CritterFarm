package com.critterfarm.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** The critter's emotional read-out, driven by recent activity and hunger/happiness. */
enum class CritterMood {
    NEUTRAL,
    BOUNCING_HAPPY,
    SLUGGISH_TIRED,
    CELEBRATING,
}

@Entity(tableName = "critters")
data class CritterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val species: String,
    val stage: Int,
    val xp: Int,
    val level: Int,
    val happiness: Int,
    val hunger: Int,
    val mood: CritterMood,
    val lastFedAt: Long,
    val createdAt: Long,
)
