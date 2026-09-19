package com.critterfarm.data.local

import androidx.room.TypeConverter

class CritterMoodConverters {
    @TypeConverter
    fun fromMood(mood: CritterMood): String = mood.name

    @TypeConverter
    fun toMood(value: String): CritterMood = CritterMood.valueOf(value)
}
