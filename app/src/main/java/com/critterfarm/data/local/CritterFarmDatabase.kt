package com.critterfarm.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [CritterEntity::class, FarmInventoryEntity::class, DailySummaryLogEntity::class],
    version = 1,
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

        fun getInstance(context: Context): CritterFarmDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CritterFarmDatabase::class.java,
                    "critterfarm.db",
                ).build().also { instance = it }
            }
    }
}
