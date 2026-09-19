package com.critterfarm.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CritterEntity::class,
        FarmInventoryEntity::class,
        DailySummaryLogEntity::class,
        QuestClaimEntity::class,
        DecorPlacementEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
@TypeConverters(CritterMoodConverters::class)
abstract class CritterFarmDatabase : RoomDatabase() {
    abstract fun critterDao(): CritterDao
    abstract fun farmInventoryDao(): FarmInventoryDao
    abstract fun dailySummaryLogDao(): DailySummaryLogDao
    abstract fun questClaimDao(): QuestClaimDao
    abstract fun decorPlacementDao(): DecorPlacementDao

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

        /**
         * v4 adds daily quests: a claims table keyed by (date, questId) so a quest can only ever
         * be paid out once per day. Purely additive — quests themselves are static data, not
         * stored — so nothing about the existing farm changes on upgrade.
         */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS quest_claims (
                        date TEXT NOT NULL,
                        questId TEXT NOT NULL,
                        claimedAt INTEGER NOT NULL,
                        PRIMARY KEY(date, questId)
                    )
                    """.trimIndent(),
                )
            }
        }

        /**
         * v5 adds decorations: one row per occupied square of the 6x4 farm scene, keyed by cell so
         * a square physically cannot hold two things. Purely additive — hats, coins, the barn and
         * every logged day are untouched, and a player's farm survives with nothing placed.
         */
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS decor_placements (
                        cellIndex INTEGER NOT NULL,
                        decorId TEXT NOT NULL,
                        placedAt INTEGER NOT NULL,
                        PRIMARY KEY(cellIndex)
                    )
                    """.trimIndent(),
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { instance = it }
            }
    }
}
