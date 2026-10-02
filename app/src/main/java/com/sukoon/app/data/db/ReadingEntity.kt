package com.sukoon.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// Mirrors GlucoseSource's GlucoseReading (data/source/GlucoseSource.kt); conversion lives in
// data/repository/ReadingMappers.kt, wired through GlucoseRepository (A2).
// Unique timestamp: sources that re-deliver (Nightscout backfill, a broadcast hitting both
// receivers) are deduped by the DB on insert (IGNORE), not by app code.
@Entity(tableName = "readings", indices = [Index(value = ["timestampMillis"], unique = true)])
data class ReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMillis: Long,
    val glucoseMgDl: Int,
    val trend: String, // TrendDirection.name
    val source: String, // SourceKind.name
)

@Dao
interface ReadingDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(reading: ReadingEntity)

    /** Leaving demo mode: simulated rows must never mix into real history, graphs, or AI context. */
    @Query("DELETE FROM readings WHERE source = :source")
    suspend fun deleteBySource(source: String)

    @Query("SELECT * FROM readings ORDER BY timestampMillis DESC LIMIT 1")
    fun latest(): Flow<ReadingEntity?>

    @Query("SELECT * FROM readings WHERE timestampMillis >= :sinceMillis ORDER BY timestampMillis ASC")
    fun since(sinceMillis: Long): Flow<List<ReadingEntity>>

    // Most-recent [limit] readings, newest first. Callers that want chronological order reverse it.
    @Query("SELECT * FROM readings ORDER BY timestampMillis DESC LIMIT :limit")
    fun latestN(limit: Int): Flow<List<ReadingEntity>>
}
