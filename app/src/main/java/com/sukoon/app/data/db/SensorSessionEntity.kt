package com.sukoon.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "sensor_sessions")
data class SensorSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serial: String,
    val activatedAtMillis: Long,
    val warmupEndsAtMillis: Long,
    val expiresAtMillis: Long,
    val stoppedAtMillis: Long? = null,
)

@Dao
interface SensorSessionDao {
    @Insert
    suspend fun insert(session: SensorSessionEntity): Long

    @Update
    suspend fun update(session: SensorSessionEntity)

    @Query("SELECT * FROM sensor_sessions WHERE stoppedAtMillis IS NULL ORDER BY activatedAtMillis DESC LIMIT 1")
    fun active(): Flow<SensorSessionEntity?>
}
