package com.eventstore.domain.ports.outbound

import com.eventstore.domain.Event
import com.eventstore.domain.EventId

/**
 * Outbound port for event persistence operations.
 */
interface EventRepository {
    suspend fun storeEvent(
        topic: String,
        type: String,
        payload: Map<String, Any>,
        eventId: EventId,
        timestamp: java.time.Instant
    ): Event

    suspend fun storeEvents(events: List<Event>): List<Event>

    suspend fun getEvent(topic: String, eventId: EventId): Event?
    suspend fun getEvents(
        topic: String,
        sinceEventId: EventId? = null,
        date: String? = null,
        limit: Int? = null
    ): List<Event>

    suspend fun getLatestEventId(topic: String): EventId?
}

