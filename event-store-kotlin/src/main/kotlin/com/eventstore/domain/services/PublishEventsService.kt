package com.eventstore.domain.services

import com.eventstore.domain.EventId
import com.eventstore.domain.exceptions.InvalidEventPayloadException
import com.eventstore.domain.exceptions.TopicNotFoundException
import com.eventstore.domain.ports.outbound.EventDispatcher
import com.eventstore.domain.ports.outbound.EventRepository
import com.eventstore.domain.ports.outbound.SchemaValidator
import com.eventstore.domain.ports.outbound.TopicRepository
import java.time.Instant

data class EventRequest(
    val topic: String,
    val type: String,
    val payload: Map<String, Any>
)

class PublishEventsService(
    private val topicRepository: TopicRepository,
    private val eventRepository: EventRepository,
    private val schemaValidator: SchemaValidator,
    private val eventDispatcher: EventDispatcher
) {
    suspend fun execute(requests: List<EventRequest>): List<String> {
        require(requests.isNotEmpty()) { "Events must be a non-empty array" }

        // Validate all events first
        for (request in requests) {
            // Validate topic exists
            if (!topicRepository.topicExists(request.topic)) {
                throw TopicNotFoundException(request.topic)
            }

            // Validate payload is a JSON object (Map)
            if (request.payload.isEmpty() && request.payload !is Map<*, *>) {
                throw InvalidEventPayloadException("Event payload must be a JSON object")
            }

            // Validate event against schema
            schemaValidator.validateEvent(request.topic, request.type, request.payload)
        }

        // Store all events
        val eventIds = mutableListOf<String>()
        val timestamp = Instant.now()

        for (request in requests) {
            val topic = topicRepository.getTopic(request.topic)
                ?: throw TopicNotFoundException(request.topic)

            val nextSequence = topic.nextSequence()
            val eventId = EventId.create(request.topic, nextSequence)

            eventRepository.storeEvent(
                topic = request.topic,
                type = request.type,
                payload = request.payload,
                eventId = eventId,
                timestamp = timestamp
            )

            // Update topic sequence
            topicRepository.updateSequence(request.topic, nextSequence)

            eventIds.add(eventId.value)
        }

        // Notify dispatcher that events have been published
        val topicsWithEvents = requests.map { it.topic }.toSet()
        eventDispatcher.notifyEventsPublished(topicsWithEvents)

        return eventIds
    }
}

