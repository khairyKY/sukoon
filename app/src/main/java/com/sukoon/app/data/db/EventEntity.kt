package com.sukoon.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// User-logged timeline items (carb / insulin / note), shown as pins on the glucose graph.
// Quick-entry-first per docs/PLAN.md §11 — value/note are optional, type + timestamp are all
// that's required to log something.
@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMillis: Long,
    val type: String, // "carb" | "insulin" | "note"
    val value: Double? = null, // grams of carbs, or insulin units
    val note: String? = null,
)

@Dao
interface EventDao {
    @Insert
    suspend fun insert(event: EventEntity)

    @Query("SELECT * FROM events WHERE timestampMillis >= :sinceMillis ORDER BY timestampMillis ASC")
    fun since(sinceMillis: Long): Flow<List<EventEntity>>
}
