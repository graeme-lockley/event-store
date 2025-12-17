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
        timestamp: java.time.Instant,
        tenantId: String? = null,
        namespaceId: String? = null
    ): Event

    suspend fun storeEvents(
        events: List<Event>,
        tenantId: String? = null,
        namespaceId: String? = null
    ): List<Event>

    suspend fun getEvent(
        topic: String,
        eventId: EventId,
        tenantId: String? = null,
        namespaceId: String? = null
    ): Event?
    suspend fun getEvents(
        topic: String,
        sinceEventId: EventId? = null,
        date: String? = null,
        limit: Int? = null,
        tenantId: String? = null,
        namespaceId: String? = null
    ): List<Event>

    suspend fun getLatestEventId(
        topic: String,
        tenantId: String? = null,
        namespaceId: String? = null
    ): EventId?
}

