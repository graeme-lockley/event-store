package com.eventstore.domain.services

import com.eventstore.domain.Event
import com.eventstore.domain.EventId
import com.eventstore.domain.exceptions.SchemaNotFoundException
import com.eventstore.domain.exceptions.SchemaValidationException
import com.eventstore.domain.exceptions.TopicNotFoundException
import com.eventstore.domain.ports.outbound.EventDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PublishEventsServiceTest {
    val topicName = "user-events"

    private lateinit var helper: PopulateEventStoreState
    private lateinit var service: PublishEventsService
    private val mockEventDispatcher = object : EventDispatcher {
        override suspend fun notifyEventsPublished(topics: Set<String>) {
            // No-op for tests
        }
    }

    @BeforeEach
    fun setup() = runBlocking {
        helper = createEventStore(topicName)
        service = PublishEventsService(helper.topicRepository, helper.eventRepository, helper.schemaValidator, mockEventDispatcher)
    }

    @Test
    fun `should publish single event successfully`() = runTest {
        val numberOfEvents = helper.getEvents(topicName).size
        val nextEventId = EventId.create(topicName, (numberOfEvents + 1).toLong())
        val requests = listOf(
            EventRequest(topicName, "user.created", mapOf("id" to "123", "name" to "Alice"))
        )
        val event = Event(nextEventId, Instant.now(), "user.created", requests[0].payload)

        val result = service.execute(requests)

        assertEquals(1, result.size)
        assertEquals(nextEventId.toString(), result[0])

        val events = helper.getEvents(topicName)
        assertEquals(numberOfEvents + 1, events.size)
        assertEquals(event.copy(timestamp = events[numberOfEvents].timestamp), events[numberOfEvents])
    }

    @Test
    fun `should publish multiple events successfully`() = runTest {
        val numberOfEvents = helper.getEvents(topicName).size
        val requests = listOf(
            EventRequest(topicName, "user.created", mapOf("id" to "1", "name" to "Alice")),
            EventRequest(topicName, "user.created", mapOf("id" to "2", "name" to "Bob"))
        )
        val event1 = EventId.create(topicName, (numberOfEvents + 1).toLong())
        val event2 = EventId.create(topicName, (numberOfEvents + 2).toLong())


        val result = service.execute(requests)

        assertEquals(2, result.size)

        assertEquals(event1.toString(), result[0])
        assertEquals(event2.toString(), result[1])

        val events = helper.getEvents(topicName)
        assertEquals(numberOfEvents + 2, events.size)

        assertEquals(
            Event(event1, events[numberOfEvents].timestamp, "user.created", requests[0].payload), events[numberOfEvents]
        )
        assertEquals(
            Event(event2, events[numberOfEvents + 1].timestamp, "user.created", requests[1].payload),
            events[numberOfEvents + 1]
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
        assertFalse(helper.topicExists("unknown-topic"))

        val request = EventRequest("unknown-topic", "user.created", mapOf("id" to "123"))

        assertThrows<TopicNotFoundException> {
            service.execute(listOf(request))
        }
    }

    @Test
    fun `should throw an exception when schema is unknown`() = runTest {
        val requests = listOf(
            EventRequest(topicName, "user.removed", mapOf("id" to "123"))
        )

        assertThrows<SchemaNotFoundException> {
            service.execute(requests)
        }
    }

    @Test
    fun `should throw an exception when payload does not match schema`() = runTest {
        assertThrows<SchemaValidationException> {
            // age is not a valid field according to the schema
            service.execute(
                listOf(
                    EventRequest(topicName, "user.created", mapOf("id" to "123", "name" to "Fred", "age" to "27"))
                )
            )
        }

        assertThrows<SchemaValidationException> {
            // name is required according to the schema
            service.execute(
                listOf(
                    EventRequest(topicName, "user.created", mapOf("id" to "123"))
                )
            )
        }
    }

    @Test
    fun `should validate all events before storing any`() = runTest {
        val numberOfEvents = helper.getEvents(topicName).size

        val requests = listOf(
            EventRequest(topicName, "user.created", mapOf("id" to "1", "name" to "Alice")),
            EventRequest("unknown-topic", "user.created", mapOf("id" to "2"))
        )

        assertThrows<TopicNotFoundException> {
            service.execute(requests)
        }

        assertEquals(numberOfEvents, helper.getEvents(topicName).size)
    }

    @Test
    fun `should throw exception for invalid payload`() = runTest {
        val request = EventRequest(topicName, "user.created", emptyMap())

        // Should throw SchemaValidationException for missing required field
        assertThrows<SchemaValidationException> {
            service.execute(listOf(request))
        }
    }
}
