package com.eventstore.domain.ports.outbound

import com.eventstore.domain.Event
import com.eventstore.domain.EventId
import java.util.*

/**
 * Outbound port for event persistence operations.
 */
interface EventRepository {
    suspend fun storeEvent(
        topicId: UUID,
        type: String,
        payload: Map<String, Any>,
        eventId: EventId,
        timestamp: java.time.Instant,
    ): Event

    suspend fun storeEvent(event: Event): Event

    suspend fun storeEvents(events: List<Event>): List<Event>

    suspend fun getEvent(
        topicId: UUID,
        eventId: EventId,
    ): Event?

    suspend fun getEvents(
        topicId: UUID,
        sinceEventId: EventId? = null,
        date: String? = null,
        limit: Int? = null,
    ): List<Event>

    suspend fun getLatestEventId(topicId: UUID): EventId?
}
