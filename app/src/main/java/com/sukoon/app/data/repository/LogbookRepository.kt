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

    suspend fun log(type: LogEventType, value: Double? = null, note: String? = null, at: Instant = Instant.now()) {
        eventDao.insert(EventEntity(timestampMillis = at.toEpochMilli(), type = type.name, value = value, note = note))
    }

    suspend fun update(event: EventEntity) = eventDao.update(event)

    suspend fun delete(event: EventEntity) = eventDao.delete(event)
}
