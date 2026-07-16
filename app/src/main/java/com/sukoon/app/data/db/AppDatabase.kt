package com.sukoon.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ReadingEntity::class,
        SensorSessionEntity::class,
        EventEntity::class,
        CalibrationEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun readingDao(): ReadingDao
    abstract fun sensorSessionDao(): SensorSessionDao
    abstract fun eventDao(): EventDao
    abstract fun calibrationDao(): CalibrationDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sukoon.db",
                ).build().also { instance = it }
            }
    }
}
