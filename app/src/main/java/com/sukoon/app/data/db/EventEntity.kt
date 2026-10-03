package com.sukoon.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// Timeline items logged in Sukoon or brought in from Health Connect (MyFitnessPal meals) (carb / insulin / finger-prick / activity / note), shown as pins on
// the glucose graph (A4). Quick-entry-first per docs/PLAN.md §11 — value/note are optional, type +
// timestamp are all that's required to log something. A finger-prick (FINGERSTICK, value = mg/dL
// from a blood meter) is a record only: it never changes sensor readings. Calibration (B10) is a
// separate, capped step (CalibrationEntity) that may read these.
@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMillis: Long,
    val type: String, // LogEventType.name
    val value: Double? = null, // grams of carbs, insulin units, meter mg/dL, or activity minutes
    val note: String? = null,
)

/** INSULIN = rapid-acting (bolus, e.g. Apidra); BASAL = long-acting (e.g. Toujeo). Stored by name, so adding one needs no migration. */
enum class LogEventType { CARB, INSULIN, BASAL, FINGERSTICK, ACTIVITY, NOTE }

/** Tolerant parse so a corrupt/future-build row can't crash the Logbook Flow (mirrors ReadingMappers.safeEnum). */
val EventEntity.logType: LogEventType
    get() = enumValues<LogEventType>().firstOrNull { it.name == type } ?: LogEventType.NOTE

@Dao
interface EventDao {
    @Insert
    suspend fun insert(event: EventEntity): Long

    @Update
    suspend fun update(event: EventEntity)

    @Delete
    suspend fun delete(event: EventEntity)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM events WHERE timestampMillis >= :sinceMillis ORDER BY timestampMillis ASC")
    fun since(sinceMillis: Long): Flow<List<EventEntity>>
}
