package com.eventstore.domain.services.event

import com.eventstore.domain.Event
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

        // Generate all event IDs first (atomic sequence increments)
        val timestamp = Instant.now()
        val events = mutableListOf<Event>()

        for (request in requests) {
            // Atomically get and increment sequence to prevent race conditions
            val nextSequence = topicRepository.getAndIncrementSequence(request.topic)
            val eventId = EventId.create(request.topic, nextSequence)
            val event = Event(eventId, timestamp, request.type, request.payload)
            events.add(event)
        }

        // Store all events in bulk for better atomicity
        val storedEvents = eventRepository.storeEvents(events)

        // Notify dispatcher that events have been published
        val topicsWithEvents = requests.map { it.topic }.toSet()
        eventDispatcher.notifyEventsPublished(topicsWithEvents)

        return storedEvents.map { it.id.value }
    }
}

