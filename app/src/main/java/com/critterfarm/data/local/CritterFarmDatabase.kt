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
    version = 2,
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

        fun getInstance(context: Context): CritterFarmDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CritterFarmDatabase::class.java,
                    "critterfarm.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
