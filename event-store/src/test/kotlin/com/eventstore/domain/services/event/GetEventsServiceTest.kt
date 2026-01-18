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
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GetEventsServiceTest {
    private lateinit var application: Application
    private val topicName = "user-events"
    private lateinit var namespaceId: UUID
    private lateinit var topicId: UUID

    @BeforeEach
    fun setup() = runTest {
        application = createApplication()
        // Create tenant and namespace
        val tenant = application.createTenant("default")
        val tenantId = tenant.tenantId
        val namespace = application.createNamespace(tenantId, "default")
        namespaceId = namespace.namespaceId
        // Create topic with schema
        val topic = application.createTopic(
            name = topicName,
            schemas = listOf(
                Schema(
                    eventType = "user.created",
                    properties = mapOf("id" to "string", "name" to "string"),
                    required = listOf("id", "name")
                )
            ),
            namespaceId = namespaceId
        )
        topicId = topic.topicId
        // Publish some initial events
        application.publishEvents(
            listOf(
                EventRequest(topicId, "user.created", mapOf("id" to "1", "name" to "Alice")),
                EventRequest(topicId, "user.created", mapOf("id" to "2", "name" to "Bob")),
                EventRequest(topicId, "user.created", mapOf("id" to "3", "name" to "Charlie"))
            )
        )
    }

    @Test
    fun `should get events successfully`() = runTest {
        val result = application.getEvents(topicId)

        assertTrue(result.size >= 3)
        assertTrue(result.any { it.payload["name"] == "Alice" })
        assertTrue(result.any { it.payload["name"] == "Bob" })
        assertTrue(result.any { it.payload["name"] == "Charlie" })
    }

    @Test
    fun `should throw exception when topic does not exist`() = runTest {
        val nonExistentTopicId = UUID.randomUUID()

        assertThrows<TopicNotFoundException> {
            application.getEvents(nonExistentTopicId)
        }
    }

    @Test
    fun `should pass sinceEventId parameter`() = runTest {
        val allEvents = application.getEvents(topicId)
        assertTrue(allEvents.isNotEmpty())

        val firstEventId = allEvents[0].id.value
        val eventsAfterFirst = application.getEvents(topicId, sinceEventId = firstEventId)

        // Should return all events after the first one
        assertEquals(allEvents.size - 1, eventsAfterFirst.size)
        assertEquals(allEvents[1].id.value, eventsAfterFirst[0].id.value)
    }

    @Test
    fun `should pass limit parameter`() = runTest {
        val allEvents = application.getEvents(topicId)
        assertTrue(allEvents.size >= 2)

        val limitedEvents = application.getEvents(topicId, limit = 2)

        assertEquals(2, limitedEvents.size)
        assertEquals(allEvents[0].id.value, limitedEvents[0].id.value)
        assertEquals(allEvents[1].id.value, limitedEvents[1].id.value)
    }

    @Test
    fun `should get events for different topics`() = runTest {
        // Create another tenant and namespace with a topic
        val tenant = application.createTenant("acme")
        val tenantId = tenant.tenantId
        val acmeNamespace = application.createNamespace(tenantId, "production")
        val acmeTopic = application.createTopic(
            name = topicName,
            schemas = listOf(
                Schema(
                    eventType = "user.created",
                    properties = mapOf("id" to "string", "name" to "string"),
                    required = listOf("id", "name")
                )
            ),
            namespaceId = acmeNamespace.namespaceId
        )

        // Publish events to acme topic
        application.publishEvents(
            listOf(
                EventRequest(acmeTopic.topicId, "user.created", mapOf("id" to "10", "name" to "Acme User"))
            )
        )

        val defaultEvents = application.getEvents(topicId)
        val acmeEvents = application.getEvents(acmeTopic.topicId)

        // Verify events are scoped correctly by topic
        assertTrue(defaultEvents.any { it.payload["name"] == "Alice" })
        assertTrue(acmeEvents.any { it.payload["name"] == "Acme User" })
        assertTrue(!defaultEvents.any { it.payload["name"] == "Acme User" })
        assertTrue(!acmeEvents.any { it.payload["name"] == "Alice" })
    }

    @Test
    fun `should return empty list when sinceEventId is last event`() = runTest {
        val allEvents = application.getEvents(topicId)
        assertTrue(allEvents.isNotEmpty())

        val lastEventId = allEvents.last().id.value
        val eventsAfterLast = application.getEvents(topicId, sinceEventId = lastEventId)

        assertEquals(0, eventsAfterLast.size)
    }

    @Test
    fun `should combine sinceEventId and limit parameters`() = runTest {
        val allEvents = application.getEvents(topicId)
        assertTrue(allEvents.size >= 3)

        val firstEventId = allEvents[0].id.value
        val limitedEvents = application.getEvents(topicId, sinceEventId = firstEventId, limit = 1)

        assertEquals(1, limitedEvents.size)
        assertEquals(allEvents[1].id.value, limitedEvents[0].id.value)
    }
}
