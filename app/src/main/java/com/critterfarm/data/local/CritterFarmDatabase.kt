package com.critterfarm.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CritterEntity::class, FarmInventoryEntity::class, DailySummaryLogEntity::class],
    version = 3,
    exportSchema = false,
)
@TypeConverters(CritterMoodConverters::class)
abstract class CritterFarmDatabase : RoomDatabase() {
    abstract fun critterDao(): CritterDao
    abstract fun farmInventoryDao(): FarmInventoryDao
    abstract fun dailySummaryLogDao(): DailySummaryLogDao

    companion object {
        @Volatile
        private var instance: CritterFarmDatabase? = null

        /**
         * v2 added the streak columns. They are additive with defaults, so an existing farm
         * (coins, critter, every logged day) survives the upgrade untouched. Destructive
         * migration would have wiped the player's history — never acceptable here.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE farm_inventory ADD COLUMN claimStreak INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE farm_inventory ADD COLUMN bestStreak INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE farm_inventory ADD COLUMN streakFreezes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE farm_inventory ADD COLUMN lastClaimedDate TEXT")
            }
        }

        /**
         * v3 adds the barn: `isActive` (exactly one critter shown on the farm) and `hatchedAt`.
         * Additive with defaults, same as v2 — the upgrading player's single existing critter is
         * flagged active and its hatch date backfilled from `createdAt` so the barn has something
         * sensible to show, rather than the epoch. Species is also lower-cased to match the new
         * catalogue's keys ("Blob" -> "blob"); pre-v1.3 saves only ever wrote that one value.
         * Destructive migration is never acceptable.
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE critters ADD COLUMN isActive INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE critters ADD COLUMN hatchedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE critters SET species = lower(species)")
                db.execSQL("UPDATE critters SET hatchedAt = createdAt")
                db.execSQL(
                    "UPDATE critters SET isActive = 1 WHERE id = (SELECT MIN(id) FROM critters)",
                )
            }
        }

        fun getInstance(context: Context): CritterFarmDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CritterFarmDatabase::class.java,
                    "critterfarm.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}
