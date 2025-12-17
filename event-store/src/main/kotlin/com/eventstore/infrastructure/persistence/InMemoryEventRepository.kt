package com.eventstore.infrastructure.persistence

import com.eventstore.domain.Event
import com.eventstore.domain.EventId
import com.eventstore.domain.ports.outbound.EventRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.format.DateTimeFormatter

class InMemoryEventRepository : EventRepository {
    // Map of topic key to list of events
    private val eventsByTopic = mutableMapOf<String, MutableList<Event>>()
    private val mutex = Mutex()

    override suspend fun storeEvent(
        topic: String,
        type: String,
        payload: Map<String, Any>,
        eventId: EventId,
        timestamp: Instant,
        tenantId: String?,
        namespaceId: String?
    ): Event {
        return mutex.withLock {
            val event = Event(eventId, timestamp, type, payload)
            val key = topicKey(topic, tenantId, namespaceId, eventId)
            val events = eventsByTopic.getOrPut(key) { mutableListOf() }
            events.add(event)
            event
        }
    }

    override suspend fun storeEvents(
        events: List<Event>,
        tenantId: String?,
        namespaceId: String?
    ): List<Event> {
        if (events.isEmpty()) {
            return emptyList()
        }

        return mutex.withLock {
            val storedEvents = mutableListOf<Event>()
            try {
                for (event in events) {
                    val key = topicKey(event.id.topic, tenantId, namespaceId, event.id)
                    val eventsList = eventsByTopic.getOrPut(key) { mutableListOf() }
                    eventsList.add(event)
                    storedEvents.add(event)
                }
                storedEvents
            } catch (e: Exception) {
                // Rollback: remove events that were added
                for (event in storedEvents) {
                    val key = topicKey(event.id.topic, tenantId, namespaceId, event.id)
                    eventsByTopic[key]?.remove(event)
                }
                throw e
            }
        }
    }

    override suspend fun getEvent(
        topic: String,
        eventId: EventId,
        tenantId: String?,
        namespaceId: String?
    ): Event? {
        return mutex.withLock {
            val key = topicKey(topic, tenantId, namespaceId, eventId)
            eventsByTopic[key]?.firstOrNull { it.id == eventId }
        }
    }

    override suspend fun getEvents(
        topic: String,
        sinceEventId: EventId?,
        date: String?,
        limit: Int?,
        tenantId: String?,
        namespaceId: String?
    ): List<Event> {
        return mutex.withLock {
            val key = topicKey(topic, tenantId, namespaceId, sinceEventId)
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
        topic: String,
        tenantId: String?,
        namespaceId: String?
    ): EventId? {
        val events = getEvents(topic, tenantId = tenantId, namespaceId = namespaceId)
        return events.lastOrNull()?.id
    }

    private fun topicKey(topic: String, tenantId: String?, namespaceId: String?, eventId: EventId?): String {
        val resolvedTenant = tenantId ?: eventId?.tenantId
        val resolvedNamespace = namespaceId ?: eventId?.namespaceId

        return if (resolvedTenant != null && resolvedNamespace != null) {
            "$resolvedTenant/$resolvedNamespace/$topic"
        } else {
            topic
        }
    }

    private fun compareEventIds(id1: EventId, id2: EventId): Int {
        if (id1.topic != id2.topic) {
            return id1.topic.compareTo(id2.topic)
        }
        return id1.sequence.compareTo(id2.sequence)
    }
}

