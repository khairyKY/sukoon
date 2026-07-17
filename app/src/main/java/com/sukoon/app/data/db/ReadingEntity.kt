package com.sukoon.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// Mirrors GlucoseSource's GlucoseReading (data/source/GlucoseSource.kt); conversion lives in
// data/repository/ReadingMappers.kt, wired through GlucoseRepository (A2).
@Entity(tableName = "readings")
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

    @Query("SELECT * FROM readings ORDER BY timestampMillis DESC LIMIT 1")
    fun latest(): Flow<ReadingEntity?>

    @Query("SELECT * FROM readings WHERE timestampMillis >= :sinceMillis ORDER BY timestampMillis ASC")
    fun since(sinceMillis: Long): Flow<List<ReadingEntity>>

    // Most-recent [limit] readings, newest first. Callers that want chronological order reverse it.
    @Query("SELECT * FROM readings ORDER BY timestampMillis DESC LIMIT :limit")
    fun latestN(limit: Int): Flow<List<ReadingEntity>>
}
