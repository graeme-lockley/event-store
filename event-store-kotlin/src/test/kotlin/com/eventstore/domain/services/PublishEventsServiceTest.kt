package com.eventstore.domain.services

import com.eventstore.domain.Event
import com.eventstore.domain.EventId
import com.eventstore.domain.Schema
import com.eventstore.domain.exceptions.SchemaNotFoundException
import com.eventstore.domain.exceptions.SchemaValidationException
import com.eventstore.domain.exceptions.TopicNotFoundException
import com.eventstore.infrastructure.external.JsonSchemaValidator
import com.eventstore.infrastructure.persistence.InMemoryEventRepository
import com.eventstore.infrastructure.persistence.InMemoryTopicRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PublishEventsServiceTest {
    private val topicRepository = InMemoryTopicRepository()
    private val eventRepository = InMemoryEventRepository()
    private val schemaValidator = JsonSchemaValidator()
    private val createTopicService = CreateTopicService(topicRepository, schemaValidator)
    private val service = PublishEventsService(topicRepository, eventRepository, schemaValidator)

    @Test
    fun `should publish single event successfully`() = runTest {
        val topicName = "user-events"
        val schemas = listOf(
            Schema(
                eventType = "user.created", properties = mapOf(
                    "id" to mapOf("type" to "string"), "name" to mapOf("type" to "string")
                )
            )
        )
        val requests = listOf(
            EventRequest(topicName, "user.created", mapOf("id" to "123", "name" to "Alice"))
        )
        val event = Event(EventId.create(topicName, 1L), Instant.now(), "user.created", requests[0].payload)

        createTopicService.execute(topicName, schemas)
        val result = service.execute(requests)

        assertEquals(1, result.size)
        assertEquals("user-events-1", result[0])

        val events = eventRepository.getEvents(topicName)
        assertEquals(1, events.size)
        assertEquals(event.copy(timestamp = events[0].timestamp), events[0])
    }

    @Test
    fun `should publish multiple events successfully`() = runTest {
        val topicName = "user-events"
        val schemas = listOf(
            Schema(eventType = "user.created", properties = mapOf("id" to mapOf("type" to "string")))
        )
        val requests = listOf(
            EventRequest(topicName, "user.created", mapOf("id" to "1")),
            EventRequest(topicName, "user.created", mapOf("id" to "2"))
        )

        createTopicService.execute(topicName, schemas)
        val result = service.execute(requests)

        assertEquals(2, result.size)
        assertEquals("user-events-1", result[0])
        assertEquals("user-events-2", result[1])

        val events = eventRepository.getEvents(topicName)
        assertEquals(2, events.size)
        assertEquals(
            Event(EventId.create(topicName, 1L), events[0].timestamp, "user.created", events[0].payload), events[0]
        )
        assertEquals(
            Event(EventId.create(topicName, 2L), events[1].timestamp, "user.created", events[1].payload), events[1]
        )
    }

    @Test
    fun `should throw exception for empty requests`() = runTest {
        assertThrows<IllegalArgumentException> {
            service.execute(emptyList())
        }
    }

    @Test
    fun `should throw exception when topic does not exist`() = runTest {
        assertFalse(topicRepository.topicExists("unknown-topic"))

        val request = EventRequest("unknown-topic", "user.created", mapOf("id" to "123"))

        assertThrows<TopicNotFoundException> {
            service.execute(listOf(request))
        }
    }

    @Test
    fun `should throw an exception when schema is unknown`() = runTest {
        val topicName = "user-events"
        val schemas = listOf(
            Schema(eventType = "user.created", properties = mapOf("id" to mapOf("type" to "string")))
        )
        val requests = listOf(
            EventRequest(topicName, "user.updated", mapOf("id" to "123", "name" to "Alice"))
        )

        createTopicService.execute(topicName, schemas)
        assertThrows<SchemaNotFoundException> {
            service.execute(requests)
        }
    }

    @Test
    fun `should throw an exception when payload does not match schema`() = runTest {
        val topicName = "user-events"
        val schemas = listOf(
            Schema(eventType = "user.created", properties = mapOf("id" to mapOf("type" to "string")))
        )
        val requests = listOf(
            // Payload contains "name" field which is not in schema - should be rejected
            EventRequest(topicName, "user.created", mapOf("id" to "123", "name" to "Alice"))
        )

        createTopicService.execute(topicName, schemas)
        assertThrows<SchemaValidationException> {
            service.execute(requests)
        }
    }

    @Test
    fun `should validate all events before storing any`() = runTest {
        val topicName = "user-events"
        val schemas = listOf(
            Schema(eventType = "user.created", properties = mapOf("id" to mapOf("type" to "string")))
        )
        createTopicService.execute(topicName, schemas)

        val requests = listOf(
            EventRequest(topicName, "user.created", mapOf("id" to "1")),
            EventRequest("unknown-topic", "user.created", mapOf("id" to "2"))
        )

        assertThrows<TopicNotFoundException> {
            service.execute(requests)
        }

        // Verify no events were stored
        val events = eventRepository.getEvents(topicName)
        assertEquals(0, events.size)
    }

    @Test
    fun `should throw exception for invalid payload`() = runTest {
        val topicName = "user-events"
        val schemas = listOf(
            Schema(
                eventType = "user.created",
                properties = mapOf("id" to mapOf("type" to "string")),
                required = listOf("id")
            )
        )
        createTopicService.execute(topicName, schemas)

        val request = EventRequest(topicName, "user.created", emptyMap())

        // Should throw SchemaValidationException for missing required field
        assertThrows<SchemaValidationException> {
            service.execute(listOf(request))
        }
    }
}
