package com.sukoon.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// User-logged timeline items (carb / insulin / activity / note), shown as pins on the glucose
// graph (A4). Quick-entry-first per docs/PLAN.md §11 — value/note are optional, type + timestamp
// are all that's required to log something. Finger-prick BG is deliberately NOT a loggable type
// here — it goes through CalibrationEntity, which applies the capped calibration math (B10); a
// bare uncapped finger-prick log would bypass that safety cap.
@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMillis: Long,
    val type: String, // LogEventType.name
    val value: Double? = null, // grams of carbs, insulin units, or activity minutes
    val note: String? = null,
)

enum class LogEventType { CARB, INSULIN, ACTIVITY, NOTE }

/** Tolerant parse so a corrupt/future-build row can't crash the Logbook Flow (mirrors ReadingMappers.safeEnum). */
val EventEntity.logType: LogEventType
    get() = enumValues<LogEventType>().firstOrNull { it.name == type } ?: LogEventType.NOTE

@Dao
interface EventDao {
    @Insert
    suspend fun insert(event: EventEntity)

    @Update
    suspend fun update(event: EventEntity)

    @Delete
    suspend fun delete(event: EventEntity)

    @Query("SELECT * FROM events WHERE timestampMillis >= :sinceMillis ORDER BY timestampMillis ASC")
    fun since(sinceMillis: Long): Flow<List<EventEntity>>
}
