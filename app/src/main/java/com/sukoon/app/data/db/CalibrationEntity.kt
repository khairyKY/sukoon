package com.sukoon.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// Finger-prick calibration entries. slope/intercept are the fitted values AFTER this entry is
// applied — MUST be bounds-checked before insert (docs/PLAN.md §7/§8 calibration caps): an
// uncapped bad entry can silently skew all future readings and mask a real hypo. The cap
// enforcement belongs in the calibration flow (Phase 2), not here — this entity just stores
// the result of an already-validated fit.
@Entity(tableName = "calibrations")
data class CalibrationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMillis: Long,
    val fingerstickMgDl: Int,
    val sensorRawMgDl: Int,
    val slope: Double,
    val intercept: Double,
)

@Dao
interface CalibrationDao {
    @Insert
    suspend fun insert(calibration: CalibrationEntity)

    // i-Algorithm weights the most recent 4 entries most heavily — docs/PLAN.md §1/§3.
    @Query("SELECT * FROM calibrations ORDER BY timestampMillis DESC LIMIT 4")
    fun recentFour(): Flow<List<CalibrationEntity>>
}
