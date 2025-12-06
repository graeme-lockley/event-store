package com.eventstore.infrastructure.persistence

import com.eventstore.application.repositories.EventRepository
import com.eventstore.domain.Event
import com.eventstore.domain.EventId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.format.DateTimeFormatter

class InMemoryEventRepository : EventRepository {
    // Map of topic name to list of events
    private val eventsByTopic = mutableMapOf<String, MutableList<Event>>()
    private val mutex = Mutex()

    override suspend fun storeEvent(
        topic: String,
        type: String,
        payload: Map<String, Any>,
        eventId: EventId,
        timestamp: Instant
    ): Event {
        return mutex.withLock {
            val event = Event(eventId, timestamp, type, payload)
            val events = eventsByTopic.getOrPut(topic) { mutableListOf() }
            events.add(event)
            event
        }
    }

    override suspend fun getEvent(topic: String, eventId: EventId): Event? {
        return mutex.withLock {
            eventsByTopic[topic]?.firstOrNull { it.id == eventId }
        }
    }

    override suspend fun getEvents(
        topic: String,
        sinceEventId: EventId?,
        date: String?,
        limit: Int?
    ): List<Event> {
        return mutex.withLock {
            val events = eventsByTopic[topic]?.toList() ?: return@withLock emptyList()

            var filtered = events.asSequence()

            // Filter by sinceEventId
            if (sinceEventId != null) {
                filtered = filtered.filter { event ->
                    compareEventIds(event.id, sinceEventId) > 0
                }
            }

            // Filter by date
            if (date != null) {
                filtered = filtered.filter { event ->
                    val eventDate = event.timestamp.atZone(java.time.ZoneId.systemDefault())
                        .format(DateTimeFormatter.ISO_LOCAL_DATE)
                    eventDate == date
                }
            }

            // Sort by event ID
            val sorted = filtered.sortedWith { a, b -> compareEventIds(a.id, b.id) }

            // Apply limit
            if (limit != null && limit > 0) {
                sorted.take(limit).toList()
            } else {
                sorted.toList()
            }
        }
    }

    override suspend fun getLatestEventId(topic: String): EventId? {
        val events = getEvents(topic)
        return events.lastOrNull()?.id
    }

    private fun compareEventIds(id1: EventId, id2: EventId): Int {
        // Compare topic names first
        if (id1.topic != id2.topic) {
            return id1.topic.compareTo(id2.topic)
        }

        // Compare sequence numbers
        return id1.sequence.compareTo(id2.sequence)
    }
}

