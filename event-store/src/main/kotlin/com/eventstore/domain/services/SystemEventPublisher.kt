package com.eventstore.domain.services

import com.eventstore.domain.Event
import com.eventstore.domain.EventId
import com.eventstore.domain.ports.outbound.EventDispatcher
import com.eventstore.domain.ports.outbound.EventRepository
import com.eventstore.domain.ports.outbound.SchemaValidator
import com.eventstore.domain.ports.outbound.TopicRepository
import com.eventstore.domain.tenants.SystemTopics
import java.time.Instant
import java.util.*

/**
 * Helper class for publishing system events (tenant and namespace events)
 * in the $system/$management namespace with schema validation.
 */
class SystemEventPublisher(
    private val eventRepository: EventRepository,
    private val topicRepository: TopicRepository,
    private val schemaValidator: SchemaValidator,
    private val eventDispatcher: EventDispatcher
) {
    suspend fun publishEvent(
        topicId: UUID,
        eventType: String,
        payload: Map<String, Any>,
        timestamp: Instant = Instant.now()
    ): Event {
        // Validate event payload against schema
        schemaValidator.validateEvent(topicId, eventType, payload)

        // Get sequence for the event
        val sequence = topicRepository.getAndIncrementSequence(topicId)

        // Create event
        val event = Event(
            id = EventId.create(
                topicId = topicId,
                sequence = sequence
            ),
            timestamp = timestamp,
            type = eventType,
            payload = payload
        )

        // Store and notify
        eventRepository.storeEvent(event)
        eventDispatcher.notifyEventPublished(topicId)

        return event
    }
}

