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
// Unique timestamp: the sensor re-delivers readings (each BLE packet repeats the last 15 min,
// pairing backfills 8 h from NFC) and the DB drops the repeats on insert (IGNORE), not app code.
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

    /** Saved readings strictly inside (from, to) — backs the save-interval spacing rule. */
    @Query("SELECT COUNT(*) FROM readings WHERE timestampMillis > :from AND timestampMillis < :to")
    suspend fun countBetween(from: Long, to: Long): Int

    @Query("SELECT * FROM readings ORDER BY timestampMillis DESC LIMIT 1")
    fun latest(): Flow<ReadingEntity?>

    @Query("SELECT * FROM readings WHERE timestampMillis >= :sinceMillis ORDER BY timestampMillis ASC")
    fun since(sinceMillis: Long): Flow<List<ReadingEntity>>

    @Query("SELECT * FROM readings WHERE timestampMillis >= :fromMillis AND timestampMillis < :toMillis ORDER BY timestampMillis ASC")
    fun between(fromMillis: Long, toMillis: Long): Flow<List<ReadingEntity>>

    // Most-recent [limit] readings, newest first. Callers that want chronological order reverse it.
    @Query("SELECT * FROM readings ORDER BY timestampMillis DESC LIMIT :limit")
    fun latestN(limit: Int): Flow<List<ReadingEntity>>
}
