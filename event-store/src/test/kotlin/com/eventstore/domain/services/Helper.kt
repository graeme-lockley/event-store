package com.eventstore.domain.services

import ch.qos.logback.classic.LoggerContext
import com.eventstore.Config
import com.eventstore.domain.Application
import com.eventstore.domain.EventId
import com.eventstore.domain.Schema
import com.eventstore.domain.exceptions.TopicNotFoundException
import com.eventstore.domain.ports.outbound.ConsumerRepository
import com.eventstore.domain.ports.outbound.EventRepository
import com.eventstore.domain.ports.outbound.SchemaValidator
import com.eventstore.domain.ports.outbound.TopicRepository
import com.eventstore.domain.services.event.EventRequest
import com.eventstore.infrastructure.external.JsonSchemaValidator
import com.eventstore.infrastructure.persistence.InMemoryConsumerRepository
import com.eventstore.infrastructure.persistence.InMemoryEventRepository
import com.eventstore.infrastructure.persistence.InMemoryTopicRepository
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.*
import ch.qos.logback.classic.Level as LogbackLevel

data class PopulateEventStoreState(
    val topicName: String = "user-events",
    val topicRepository: TopicRepository = InMemoryTopicRepository(),
    val eventRepository: EventRepository = InMemoryEventRepository(),
    val consumerRepository: ConsumerRepository = InMemoryConsumerRepository(),
    val schemaValidator: SchemaValidator = JsonSchemaValidator(),
) {
    var topicId: UUID? = null
    var namespaceId: UUID? = null

    suspend fun findTopic(topicId: UUID) = topicRepository.getTopic(topicId)

    fun hasSchema(
        topicId: UUID,
        eventType: String,
    ) = schemaValidator.hasSchema(topicId, eventType)

    suspend fun getEvents(topicId: UUID) = eventRepository.getEvents(topicId)

    suspend fun topicExists(topicId: UUID): Boolean = topicRepository.topicExists(topicId)

    suspend fun findConsumer(consumerId: String) = consumerRepository.findById(consumerId)

    suspend fun findConsumers() = consumerRepository.findAll()
}

suspend fun populateEventStore(state: PopulateEventStoreState) {
    val topicSchemas =
        listOf(
            Schema(
                eventType = "user.created",
                properties = mapOf("id" to "string", "name" to "string"),
                required = listOf("id", "name"),
            ),
            Schema(eventType = "user.updated", properties = mapOf("id" to "string", "name" to "string")),
        )

    val namespaceId = UUID.randomUUID()
    val topicId = UUID.randomUUID()
    state.topicId = topicId
    state.namespaceId = namespaceId

    state.topicRepository.createTopic(
        topicId,
        namespaceId,
        state.topicName,
        topicSchemas,
    )
    state.schemaValidator.registerSchemas(topicId, topicSchemas)

    val otherTopicId = UUID.randomUUID()
    state.topicRepository.createTopic(
        otherTopicId,
        namespaceId,
        "other-user-events",
        topicSchemas,
    )
    state.schemaValidator.registerSchemas(otherTopicId, topicSchemas)

    val requests =
        listOf(
            EventRequest(topicId, "user.created", mapOf("id" to "1", "name" to "Alice")),
            EventRequest(topicId, "user.created", mapOf("id" to "2", "name" to "Bob")),
            EventRequest(topicId, "user.updated", mapOf("id" to "1", "name" to "Alice Smith")),
        )

    val timestamp = Instant.now()
    for (request in requests) {
        val topic =
            state.topicRepository.getTopic(request.topicId)
                ?: throw TopicNotFoundException(request.topicId.toString())

        val nextSequence = topic.nextSequence()
        val eventId = EventId.create(request.topicId, nextSequence)

        state.eventRepository.storeEvent(
            topicId = request.topicId,
            type = request.type,
            payload = request.payload,
            eventId = eventId,
            timestamp = timestamp,
        )

        // Update topic sequence
        state.topicRepository.updateSequence(request.topicId, nextSequence)
    }
}

suspend fun createEventStore(topicName: String = "user-events"): PopulateEventStoreState {
    val state = PopulateEventStoreState(topicName)
    populateEventStore(state)
    return state
}

fun createApplication(): Application {
    val config =
        Config(
            port = 0,
            dataDir = "./data",
            configDir = "./config",
            maxBodyBytes = 1024,
            rateLimitPerMinute = 10,
            authEnabled = false,
            silent = true,
        )

    // Configure logging levels based on silent mode - must happen BEFORE creating Application
    // because Application's init block runs bootstrap which logs at INFO level
    if (config.silent) {
        try {
            val loggerFactory = LoggerFactory.getILoggerFactory()
            if (loggerFactory is LoggerContext) {
                val bootstrapLogger = loggerFactory.getLogger("com.eventstore.infrastructure.bootstrap.BootstrapServiceImpl")
                bootstrapLogger.level = LogbackLevel.ERROR
                val ktorLogger = loggerFactory.getLogger("ktor.application")
                ktorLogger.level = LogbackLevel.ERROR
                val ktorTestLogger = loggerFactory.getLogger("ktor.test")
                ktorTestLogger.level = LogbackLevel.ERROR
            }
        } catch (e: Exception) {
            // If logger configuration fails, silently continue - tests should still work
            // This might happen if logback is not being used as the SLF4J implementation
        }
    }

    val application =
        Application(
            bootstrap = true,
            config = config,
        )

    return application
}

class InMemoryEventDispatcher : com.eventstore.domain.ports.outbound.EventDispatcher {
    val events = mutableListOf<Set<UUID>>()
    val ensuredTopics = mutableListOf<Set<UUID>>()

    override suspend fun notifyEventPublished(topicId: UUID) {
        events.add(setOf(topicId))
    }

    override suspend fun notifyEventsPublished(topicIds: Set<UUID>) {
        events.add(topicIds)
    }

    override suspend fun ensureDispatchersRunning(topicIds: Set<UUID>) {
        ensuredTopics.add(topicIds)
    }
}
