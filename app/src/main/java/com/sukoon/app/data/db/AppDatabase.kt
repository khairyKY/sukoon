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
    version = 6,
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
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build().also { instance = it }
            }

        // v2: unique index on readings.timestampMillis (see ReadingEntity). Keep the first copy
        // of any duplicate timestamp so the index can be created on existing data.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM readings WHERE id NOT IN (SELECT MIN(id) FROM readings GROUP BY timestampMillis)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_readings_timestampMillis ON readings (timestampMillis)")
            }
        }

        // v3: events remember their source app and a meal's nutrients (MyFitnessPal brings them).
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN source TEXT")
                db.execSQL("ALTER TABLE events ADD COLUMN mealType INTEGER")
                listOf("fiber", "sugar", "protein", "fat", "kcal").forEach { db.execSQL("ALTER TABLE events ADD COLUMN $it REAL") }
            }
        }

        // v5: a rapid dose knows its meal, so two meals at the same time (MyFitnessPal stamps its own) can't swap doses.
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN mealId INTEGER")
                db.execSQL("ALTER TABLE events ADD COLUMN timeSet INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN summary INTEGER NOT NULL DEFAULT 0")
            }
        }

        // v4: where an insulin dose went (injection site rotation).
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN site TEXT")
            }
        }
    }
}
