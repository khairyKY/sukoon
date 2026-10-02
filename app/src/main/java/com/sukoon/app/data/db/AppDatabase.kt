package com.sukoon.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ReadingEntity::class,
        SensorSessionEntity::class,
        EventEntity::class,
        CalibrationEntity::class,
    ],
    version = 2,
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
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }

        // v2: unique index on readings.timestampMillis (see ReadingEntity). Keep the first copy
        // of any duplicate timestamp so the index can be created on existing data.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM readings WHERE id NOT IN (SELECT MIN(id) FROM readings GROUP BY timestampMillis)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_readings_timestampMillis ON readings (timestampMillis)")
            }
        }
    }
}
