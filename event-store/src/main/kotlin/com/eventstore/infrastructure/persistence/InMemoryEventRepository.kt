package com.eventstore.infrastructure.persistence

import com.eventstore.domain.Event
import com.eventstore.domain.EventId
import com.eventstore.domain.ports.outbound.EventRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*

class InMemoryEventRepository : EventRepository {
    // Map of topic key (topicId UUID as string) to list of events
    private val eventsByTopic = mutableMapOf<String, MutableList<Event>>()
    private val mutex = Mutex()

    override suspend fun storeEvent(
        topicId: UUID,
        type: String,
        payload: Map<String, Any>,
        eventId: EventId,
        timestamp: Instant
    ): Event {
        return mutex.withLock {
            val event = Event(eventId, timestamp, type, payload)
            val key = topicKey(topicId)
            val events = eventsByTopic.getOrPut(key) { mutableListOf() }
            events.add(event)
            event
        }
    }

    override suspend fun storeEvent(event: Event): Event {
        return mutex.withLock {
            val key = topicKey(event.id.topicId)
            val events = eventsByTopic.getOrPut(key) { mutableListOf() }
            events.add(event)
            event
        }
    }

    override suspend fun storeEvents(
        events: List<Event>
    ): List<Event> {
        if (events.isEmpty()) {
            return emptyList()
        }

        return mutex.withLock {
            val storedEvents = mutableListOf<Event>()
            try {
                for (event in events) {
                    val key = topicKey(event.id.topicId)
                    val eventsList = eventsByTopic.getOrPut(key) { mutableListOf() }
                    eventsList.add(event)
                    storedEvents.add(event)
                }
                storedEvents
            } catch (e: Exception) {
                // Rollback: remove events that were added
                for (event in storedEvents) {
                    val key = topicKey(event.id.topicId)
                    eventsByTopic[key]?.remove(event)
                }
                throw e
            }
        }
    }

    override suspend fun getEvent(
        topicId: UUID,
        eventId: EventId
    ): Event? {
        return mutex.withLock {
            val key = topicKey(topicId)
            eventsByTopic[key]?.firstOrNull { it.id == eventId }
        }
    }

    override suspend fun getEvents(
        topicId: UUID,
        sinceEventId: EventId?,
        date: String?,
        limit: Int?
    ): List<Event> {
        return mutex.withLock {
            val key = topicKey(topicId)
            val events = eventsByTopic[key]?.toList() ?: return@withLock emptyList()

            var filtered = events.asSequence()

            if (sinceEventId != null) {
                filtered = filtered.filter { event ->
                    compareEventIds(event.id, sinceEventId) > 0
                }
            }

            if (date != null) {
                filtered = filtered.filter { event ->
                    val eventDate = event.timestamp.atZone(java.time.ZoneId.systemDefault())
                        .format(DateTimeFormatter.ISO_LOCAL_DATE)
                    eventDate == date
                }
            }

            val sorted = filtered.sortedWith { a, b -> compareEventIds(a.id, b.id) }

            if (limit != null && limit > 0) {
                sorted.take(limit).toList()
            } else {
                sorted.toList()
            }
        }
    }

    override suspend fun getLatestEventId(
        topicId: UUID
    ): EventId? {
        val events = getEvents(topicId)
        return events.lastOrNull()?.id
    }

    private fun topicKey(topicId: UUID): String {
        return topicId.toString()
    }

    private fun compareEventIds(id1: EventId, id2: EventId): Int {
        val topicComparison = id1.topicId.compareTo(id2.topicId)
        return if (topicComparison != 0) {
            topicComparison
        } else {
            id1.sequence.compareTo(id2.sequence)
        }
    }
}

