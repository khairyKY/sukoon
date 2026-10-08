package com.sukoon.app.data.repository

import com.sukoon.app.data.db.EventDao
import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * Thin CRUD wrapper over [EventDao] (A4, docs/track-a-plan.md). Unlike glucose readings, events
 * only ever originate from user input inside this app, so [EventEntity] doubles as the domain
 * type here — no separate entity/domain mapping layer needed.
 */
class LogbookRepository(private val eventDao: EventDao) {

    /** Logged events at or after [sinceMillis], chronological — backs the Logbook timeline and the graph's event pins. */
    fun eventsSince(sinceMillis: Long): Flow<List<EventEntity>> = eventDao.since(sinceMillis)

    /** Returns the new entry's id (a photo attached to it is filed under that id). */
    suspend fun log(type: LogEventType, value: Double? = null, note: String? = null, at: Instant = Instant.now()): Long =
        eventDao.insert(EventEntity(timestampMillis = at.toEpochMilli(), type = type.name, value = value, note = note))

    /** Rows changed: 0 when the entry was deleted meanwhile. */
    suspend fun update(event: EventEntity): Int = eventDao.update(event)

    suspend fun delete(event: EventEntity) = eventDao.delete(event)

    /** For entries other apps own (Health Connect meals): insert returns the new id so later edits find it. */
    suspend fun insert(event: EventEntity): Long = eventDao.insert(event)

    suspend fun deleteById(id: Long) = eventDao.deleteById(id)

    suspend fun byId(id: Long): EventEntity? = eventDao.byId(id)
}
