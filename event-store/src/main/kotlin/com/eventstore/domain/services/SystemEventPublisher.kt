package com.eventstore.domain.services

import com.eventstore.domain.Event
import com.eventstore.domain.EventId
import com.eventstore.domain.ports.outbound.EventDispatcher
import com.eventstore.domain.ports.outbound.EventRepository
import com.eventstore.domain.ports.outbound.SchemaValidator
import com.eventstore.domain.ports.outbound.TopicRepository
import com.eventstore.domain.tenants.SystemTopics
import java.time.Instant

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
        topic: String,
        eventType: String,
        payload: Map<String, Any>,
        timestamp: Instant = Instant.now()
    ): Event {
        // Validate event payload against schema
        schemaValidator.validateEvent(topic, eventType, payload)

        // Get sequence for the event
        val sequence = topicRepository.getAndIncrementSequence(
            topicName = topic,
            tenantName = SystemTopics.SYSTEM_TENANT_ID,
            namespaceName = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )

        // Create event
        val event = Event(
            id = EventId.create(
                topic = topic,
                sequence = sequence,
                tenantId = SystemTopics.SYSTEM_TENANT_ID,
                namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
            ),
            timestamp = timestamp,
            type = eventType,
            payload = payload
        )

        // Store and notify
        eventRepository.storeEvents(listOf(event))
        eventDispatcher.notifyEventsPublished(setOf(event.id.qualifiedTopic))

        return event
    }
}

