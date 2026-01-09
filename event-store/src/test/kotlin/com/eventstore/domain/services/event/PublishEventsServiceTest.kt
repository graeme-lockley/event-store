package com.eventstore.domain.services.event

import com.eventstore.domain.Application
import com.eventstore.domain.Event
import com.eventstore.domain.EventId
import com.eventstore.domain.Schema
import com.eventstore.domain.exceptions.SchemaNotFoundException
import com.eventstore.domain.exceptions.SchemaValidationException
import com.eventstore.domain.exceptions.TopicNotFoundException
import com.eventstore.domain.services.createApplication
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PublishEventsServiceTest {
    private lateinit var application: Application
    private val topicName = "user-events"

    @BeforeEach
    fun setup() = runTest {
        application = createApplication()
        // Create tenant and namespace
        application.createTenant("default")
        application.createNamespace("default", "default")
        // Create topic with schema
        application.createTopic(
            name = topicName,
            schemas = listOf(
                Schema(
                    eventType = "user.created",
                    properties = mapOf("id" to "string", "name" to "string"),
                    required = listOf("id", "name")
                ),
                Schema(
                    eventType = "user.updated",
                    properties = mapOf("id" to "string", "name" to "string"),
                    required = listOf("id")
                )
            ),
            tenantName = "default",
            namespaceName = "default"
        )
    }

    @Test
    fun `should publish single event successfully`() = runTest {
        val numberOfEvents = application.getEvents(topicName).size
        val requests = listOf(
            EventRequest(topicName, "user.created", mapOf("id" to "123", "name" to "Alice"))
        )

        val result = application.publishEvents(requests)

        assertEquals(1, result.size)
        assertTrue(result[0].startsWith("$topicName-"))

        val events = application.getEvents(topicName)
        assertEquals(numberOfEvents + 1, events.size)
        assertEquals("user.created", events.last().type)
        assertEquals(mapOf("id" to "123", "name" to "Alice"), events.last().payload)
    }

    @Test
    fun `should publish multiple events successfully`() = runTest {
        val numberOfEvents = application.getEvents(topicName).size
        val requests = listOf(
            EventRequest(topicName, "user.created", mapOf("id" to "1", "name" to "Alice")),
            EventRequest(topicName, "user.created", mapOf("id" to "2", "name" to "Bob"))
        )

        val result = application.publishEvents(requests)

        assertEquals(2, result.size)
        assertTrue(result[0].startsWith("$topicName-"))
        assertTrue(result[1].startsWith("$topicName-"))

        val events = application.getEvents(topicName)
        assertEquals(numberOfEvents + 2, events.size)
        assertEquals("user.created", events[numberOfEvents].type)
        assertEquals("user.created", events[numberOfEvents + 1].type)
        assertEquals(mapOf("id" to "1", "name" to "Alice"), events[numberOfEvents].payload)
        assertEquals(mapOf("id" to "2", "name" to "Bob"), events[numberOfEvents + 1].payload)
    }

    @Test
    fun `should throw exception for empty requests`() = runTest {
        assertThrows<IllegalArgumentException> {
            application.publishEvents(emptyList())
        }
    }

    @Test
    fun `should throw exception when topic does not exist`() = runTest {
        val request = EventRequest("unknown-topic", "user.created", mapOf("id" to "123", "name" to "Alice"))

        assertThrows<TopicNotFoundException> {
            application.publishEvents(listOf(request))
        }
    }

    @Test
    fun `should throw an exception when schema is unknown`() = runTest {
        val requests = listOf(
            EventRequest(topicName, "user.removed", mapOf("id" to "123", "name" to "Alice"))
        )

        assertThrows<SchemaNotFoundException> {
            application.publishEvents(requests)
        }
    }

    @Test
    fun `should throw an exception when payload does not match schema`() = runTest {
        // age is not a valid field according to the schema
        assertThrows<SchemaValidationException> {
            application.publishEvents(
                listOf(
                    EventRequest(topicName, "user.created", mapOf("id" to "123", "name" to "Fred", "age" to "27"))
                )
            )
        }

        // name is required according to the schema
        assertThrows<SchemaValidationException> {
            application.publishEvents(
                listOf(
                    EventRequest(topicName, "user.created", mapOf("id" to "123"))
                )
            )
        }
    }

    @Test
    fun `should validate all events before storing any`() = runTest {
        val numberOfEvents = application.getEvents(topicName).size

        val requests = listOf(
            EventRequest(topicName, "user.created", mapOf("id" to "1", "name" to "Alice")),
            EventRequest("unknown-topic", "user.created", mapOf("id" to "2", "name" to "Bob"))
        )

        assertThrows<TopicNotFoundException> {
            application.publishEvents(requests)
        }

        // Verify no events were stored
        assertEquals(numberOfEvents, application.getEvents(topicName).size)
    }

    @Test
    fun `should throw exception for invalid payload`() = runTest {
        val request = EventRequest(topicName, "user.created", emptyMap())

        // Should throw SchemaValidationException for missing required field
        assertThrows<SchemaValidationException> {
            application.publishEvents(listOf(request))
        }
    }

    @Test
    fun `should publish events with tenant and namespace scoping`() = runTest {
        // Create another tenant and namespace
        application.createTenant("acme")
        application.createNamespace("acme", "production")
        application.createTopic(
            name = topicName,
            schemas = listOf(
                Schema(
                    eventType = "user.created",
                    properties = mapOf("id" to "string", "name" to "string"),
                    required = listOf("id", "name")
                )
            ),
            tenantName = "acme",
            namespaceName = "production"
        )

        val defaultRequests = listOf(
            EventRequest(topicName, "user.created", mapOf("id" to "1", "name" to "Default User"), tenantId = "default", namespaceId = "default")
        )
        val acmeRequests = listOf(
            EventRequest(topicName, "user.created", mapOf("id" to "2", "name" to "Acme User"), tenantId = "acme", namespaceId = "production")
        )

        val defaultResult = application.publishEvents(defaultRequests)
        val acmeResult = application.publishEvents(acmeRequests)

        assertEquals(1, defaultResult.size)
        assertEquals(1, acmeResult.size)

        // Verify events are stored in correct tenant/namespace context
        val defaultEvents = application.getEvents(topicName, tenantName = "default", namespaceName = "default")
        val acmeEvents = application.getEvents(topicName, tenantName = "acme", namespaceName = "production")

        assertTrue(defaultEvents.any { it.payload["name"] == "Default User" })
        assertTrue(acmeEvents.any { it.payload["name"] == "Acme User" })
    }

    @Test
    fun `should increment event sequence correctly`() = runTest {
        val requests1 = listOf(
            EventRequest(topicName, "user.created", mapOf("id" to "1", "name" to "Alice"))
        )
        val requests2 = listOf(
            EventRequest(topicName, "user.created", mapOf("id" to "2", "name" to "Bob"))
        )
        val requests3 = listOf(
            EventRequest(topicName, "user.created", mapOf("id" to "3", "name" to "Charlie"))
        )

        val result1 = application.publishEvents(requests1)
        val result2 = application.publishEvents(requests2)
        val result3 = application.publishEvents(requests3)

        val event1 = EventId(result1[0])
        val event2 = EventId(result2[0])
        val event3 = EventId(result3[0])

        assertEquals(event1.sequence + 1, event2.sequence)
        assertEquals(event2.sequence + 1, event3.sequence)
    }
}
