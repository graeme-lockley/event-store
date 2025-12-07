package com.eventstore.domain.services

import com.eventstore.domain.EventId
import com.eventstore.domain.Schema
import com.eventstore.domain.exceptions.TopicNotFoundException
import com.eventstore.domain.ports.outbound.EventRepository
import com.eventstore.domain.ports.outbound.SchemaValidator
import com.eventstore.domain.ports.outbound.TopicRepository
import com.eventstore.infrastructure.external.JsonSchemaValidator
import com.eventstore.infrastructure.persistence.InMemoryEventRepository
import com.eventstore.infrastructure.persistence.InMemoryTopicRepository
import java.time.Instant

data class PopulateEventStoreState(
    val topicName: String = "user-events",
    val topicRepository: TopicRepository = InMemoryTopicRepository(),
    val eventRepository: EventRepository = InMemoryEventRepository(),
    val schemaValidator: SchemaValidator = JsonSchemaValidator()
) {
    suspend fun findTopic(topicName: String) =
        topicRepository.getAllTopics().find { it.name == topicName }

    fun hasSchema(topicName: String, eventType: String) =
        schemaValidator.hasSchema(topicName, eventType)

    suspend fun getEvents(topicName: String) =
        eventRepository.getEvents(topicName)

    suspend fun topicExists(topicName: String): Boolean =
        topicRepository.topicExists(topicName)
}

suspend fun populateEventStore(state: PopulateEventStoreState) {
    val topicSchemas = listOf(
        Schema(
            eventType = "user.created",
            properties = mapOf("id" to "string", "name" to "string"),
            required = listOf("id", "name")
        ),
        Schema(eventType = "user.updated", properties = mapOf("id" to "string", "name" to "string")),
    )

    state.topicRepository.createTopic(state.topicName, topicSchemas)
    state.schemaValidator.registerSchemas(state.topicName, topicSchemas)

    state.topicRepository.createTopic("other-user-events", topicSchemas)
    state.schemaValidator.registerSchemas("other-user-events", topicSchemas)

    val requests = listOf(
        EventRequest(state.topicName, "user.created", mapOf("id" to "1", "name" to "Alice")),
        EventRequest(state.topicName, "user.created", mapOf("id" to "2", "name" to "Bob")),
        EventRequest(state.topicName, "user.updated", mapOf("id" to "1", "name" to "Alice Smith")),
    )

    val timestamp = Instant.now()
    for (request in requests) {
        val topic = state.topicRepository.getTopic(request.topic)
            ?: throw TopicNotFoundException(request.topic)

        val nextSequence = topic.nextSequence()
        val eventId = EventId.create(request.topic, nextSequence)

        state.eventRepository.storeEvent(
            topic = request.topic,
            type = request.type,
            payload = request.payload,
            eventId = eventId,
            timestamp = timestamp
        )

        // Update topic sequence
        state.topicRepository.updateSequence(request.topic, nextSequence)
    }
}

suspend fun createEventStore(topicName: String = "user-events"): PopulateEventStoreState {
    val state = PopulateEventStoreState(topicName)
    populateEventStore(state)
    return state
}