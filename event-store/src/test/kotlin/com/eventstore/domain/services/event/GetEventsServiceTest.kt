package com.eventstore.domain.services.event

import com.eventstore.domain.Application
import com.eventstore.domain.Schema
import com.eventstore.domain.exceptions.TopicNotFoundException
import com.eventstore.domain.services.createApplication
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GetEventsServiceTest {
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
                )
            ),
            tenantName = "default",
            namespaceName = "default"
        )
        // Publish some initial events
        application.publishEvents(
            listOf(
                EventRequest(topicName, "user.created", mapOf("id" to "1", "name" to "Alice"), "default", "default"),
                EventRequest(topicName, "user.created", mapOf("id" to "2", "name" to "Bob"), "default", "default"),
                EventRequest(
                    topicName,
                    "user.created",
                    mapOf("id" to "3", "name" to "Charlie"),
                    "default",
                    "default"
                )
            )
        )
    }

    @Test
    fun `should get events successfully`() = runTest {
        val result = application.getEvents(topicName)

        assertTrue(result.size >= 3)
        assertTrue(result.any { it.payload["name"] == "Alice" })
        assertTrue(result.any { it.payload["name"] == "Bob" })
        assertTrue(result.any { it.payload["name"] == "Charlie" })
    }

    @Test
    fun `should throw exception when topic does not exist`() = runTest {
        val unknownTopicName = "unknown-topic"

        assertThrows<TopicNotFoundException> {
            application.getEvents(unknownTopicName)
        }
    }

    @Test
    fun `should pass sinceEventId parameter`() = runTest {
        val allEvents = application.getEvents(topicName)
        assertTrue(allEvents.isNotEmpty())

        val firstEventId = allEvents[0].id.value
        val eventsAfterFirst = application.getEvents(topicName, sinceEventId = firstEventId)

        // Should return all events after the first one
        assertEquals(allEvents.size - 1, eventsAfterFirst.size)
        assertEquals(allEvents[1].id.value, eventsAfterFirst[0].id.value)
    }

    @Test
    fun `should pass limit parameter`() = runTest {
        val allEvents = application.getEvents(topicName)
        assertTrue(allEvents.size >= 2)

        val limitedEvents = application.getEvents(topicName, limit = 2)

        assertEquals(2, limitedEvents.size)
        assertEquals(allEvents[0].id.value, limitedEvents[0].id.value)
        assertEquals(allEvents[1].id.value, limitedEvents[1].id.value)
    }

    @Test
    fun `should get events with tenant and namespace scoping`() = runTest {
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

        // Publish events to acme namespace
        application.publishEvents(
            listOf(
                EventRequest(
                    topicName,
                    "user.created",
                    mapOf("id" to "10", "name" to "Acme User"),
                    tenantId = "acme",
                    namespaceId = "production"
                )
            )
        )

        val defaultEvents = application.getEvents(topicName, tenantName = "default", namespaceName = "default")
        val acmeEvents = application.getEvents(topicName, tenantName = "acme", namespaceName = "production")

        // Verify events are scoped correctly
        assertTrue(defaultEvents.any { it.payload["name"] == "Alice" })
        assertTrue(acmeEvents.any { it.payload["name"] == "Acme User" })
        assertTrue(!defaultEvents.any { it.payload["name"] == "Acme User" })
        assertTrue(!acmeEvents.any { it.payload["name"] == "Alice" })
    }

    @Test
    fun `should return empty list when sinceEventId is last event`() = runTest {
        val allEvents = application.getEvents(topicName)
        assertTrue(allEvents.isNotEmpty())

        val lastEventId = allEvents.last().id.value
        val eventsAfterLast = application.getEvents(topicName, sinceEventId = lastEventId)

        assertEquals(0, eventsAfterLast.size)
    }

    @Test
    fun `should combine sinceEventId and limit parameters`() = runTest {
        val allEvents = application.getEvents(topicName, tenantName = "default", namespaceName = "default")
        assertTrue(allEvents.size >= 3)

        val firstEventId = allEvents[0].id.value
        val limitedEvents = application.getEvents(topicName, sinceEventId = firstEventId, limit = 1, tenantName = "default", namespaceName = "default")

        assertEquals(1, limitedEvents.size)
        assertEquals(allEvents[1].id.value, limitedEvents[0].id.value)
    }
}
