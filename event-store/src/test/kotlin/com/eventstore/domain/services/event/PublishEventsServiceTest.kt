package com.eventstore.domain.services.event

import com.eventstore.domain.Application
import com.eventstore.domain.EventId
import com.eventstore.domain.Schema
import com.eventstore.domain.exceptions.InvalidEventPayloadException
import com.eventstore.domain.exceptions.SchemaNotFoundException
import com.eventstore.domain.exceptions.SchemaValidationException
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
class PublishEventsServiceTest {
    private lateinit var application: Application
    private val topicName = "user-events"
    private lateinit var namespaceId: UUID
    private lateinit var topicId: UUID

    @BeforeEach
    fun setup() =
        runTest {
            application = createApplication()
            // Create tenant and namespace
            val tenant = application.createTenant("default")
            val tenantId = tenant.tenantId
            val namespace = application.createNamespace(tenantId, "default")
            namespaceId = namespace.namespaceId
            // Create topic with schema
            val topic =
                application.createTopic(
                    name = topicName,
                    schemas =
                        listOf(
                            Schema(
                                eventType = "user.created",
                                properties = mapOf("id" to "string", "name" to "string"),
                                required = listOf("id", "name"),
                            ),
                            Schema(
                                eventType = "user.updated",
                                properties = mapOf("id" to "string", "name" to "string"),
                                required = listOf("id"),
                            ),
                        ),
                    namespaceId = namespaceId,
                )
            topicId = topic.topicId
        }

    @Test
    fun `should publish single event successfully`() =
        runTest {
            val numberOfEvents = application.getEvents(topicId).size
            val requests =
                listOf(
                    EventRequest(topicId, "user.created", mapOf("id" to "123", "name" to "Alice")),
                )

            val result = application.publishEvents(requests)

            assertEquals(1, result.size)
            // EventId format is now: topicId/sequence
            val eventId = EventId.fromString(result[0])
            assertEquals(topicId, eventId.topicId)

            val events = application.getEvents(topicId)
            assertEquals(numberOfEvents + 1, events.size)
            assertEquals("user.created", events.last().type)
            assertEquals(mapOf("id" to "123", "name" to "Alice"), events.last().payload)
        }

    @Test
    fun `should publish multiple events successfully`() =
        runTest {
            val numberOfEvents = application.getEvents(topicId).size
            val requests =
                listOf(
                    EventRequest(topicId, "user.created", mapOf("id" to "1", "name" to "Alice")),
                    EventRequest(topicId, "user.created", mapOf("id" to "2", "name" to "Bob")),
                )

            val result = application.publishEvents(requests)

            assertEquals(2, result.size)
            // EventId format is now: topicId/sequence
            val eventId1 = EventId.fromString(result[0])
            val eventId2 = EventId.fromString(result[1])
            assertEquals(topicId, eventId1.topicId)
            assertEquals(topicId, eventId2.topicId)

            val events = application.getEvents(topicId)
            assertEquals(numberOfEvents + 2, events.size)
            assertEquals("user.created", events[numberOfEvents].type)
            assertEquals("user.created", events[numberOfEvents + 1].type)
            assertEquals(mapOf("id" to "1", "name" to "Alice"), events[numberOfEvents].payload)
            assertEquals(mapOf("id" to "2", "name" to "Bob"), events[numberOfEvents + 1].payload)
        }

    @Test
    fun `should throw exception for empty requests`() =
        runTest {
            assertThrows<IllegalArgumentException> {
                application.publishEvents(emptyList())
            }
        }

    @Test
    fun `should throw exception when topic does not exist`() =
        runTest {
            val nonExistentTopicId = UUID.randomUUID()
            val request = EventRequest(nonExistentTopicId, "user.created", mapOf("id" to "123", "name" to "Alice"))

            assertThrows<TopicNotFoundException> {
                application.publishEvents(listOf(request))
            }
        }

    @Test
    fun `should throw an exception when schema is unknown`() =
        runTest {
            val requests =
                listOf(
                    EventRequest(topicId, "user.removed", mapOf("id" to "123", "name" to "Alice")),
                )

            assertThrows<SchemaNotFoundException> {
                application.publishEvents(requests)
            }
        }

    @Test
    fun `should throw an exception when payload does not match schema`() =
        runTest {
            // age is not a valid field according to the schema
            assertThrows<SchemaValidationException> {
                application.publishEvents(
                    listOf(
                        EventRequest(topicId, "user.created", mapOf("id" to "123", "name" to "Fred", "age" to "27")),
                    ),
                )
            }

            // name is required according to the schema
            assertThrows<SchemaValidationException> {
                application.publishEvents(
                    listOf(
                        EventRequest(topicId, "user.created", mapOf("id" to "123")),
                    ),
                )
            }
        }

    @Test
    fun `should validate all events before storing any`() =
        runTest {
            val numberOfEvents = application.getEvents(topicId).size
            val nonExistentTopicId = UUID.randomUUID()

            val requests =
                listOf(
                    EventRequest(topicId, "user.created", mapOf("id" to "1", "name" to "Alice")),
                    EventRequest(nonExistentTopicId, "user.created", mapOf("id" to "2", "name" to "Bob")),
                )

            assertThrows<TopicNotFoundException> {
                application.publishEvents(requests)
            }

            // Verify no events were stored
            assertEquals(numberOfEvents, application.getEvents(topicId).size)
        }

    @Test
    fun `should throw exception for invalid payload`() =
        runTest {
            val request = EventRequest(topicId, "user.created", emptyMap())

            // Should throw SchemaValidationException for missing required field
            assertThrows<InvalidEventPayloadException> {
                application.publishEvents(listOf(request))
            }
        }

    @Test
    fun `should publish events to different topics`() =
        runTest {
            // Create another tenant and namespace with a topic
            val tenant = application.createTenant("acme")
            val tenantId = tenant.tenantId
            val acmeNamespace = application.createNamespace(tenantId, "production")
            val acmeTopic =
                application.createTopic(
                    name = topicName,
                    schemas =
                        listOf(
                            Schema(
                                eventType = "user.created",
                                properties = mapOf("id" to "string", "name" to "string"),
                                required = listOf("id", "name"),
                            ),
                        ),
                    namespaceId = acmeNamespace.namespaceId,
                )

            val defaultRequests =
                listOf(
                    EventRequest(topicId, "user.created", mapOf("id" to "1", "name" to "Default User")),
                )
            val acmeRequests =
                listOf(
                    EventRequest(acmeTopic.topicId, "user.created", mapOf("id" to "2", "name" to "Acme User")),
                )

            val defaultResult = application.publishEvents(defaultRequests)
            val acmeResult = application.publishEvents(acmeRequests)

            assertEquals(1, defaultResult.size)
            assertEquals(1, acmeResult.size)

            // Verify events are stored in correct topics
            val defaultEvents = application.getEvents(topicId)
            val acmeEvents = application.getEvents(acmeTopic.topicId)

            assertTrue(defaultEvents.any { it.payload["name"] == "Default User" })
            assertTrue(acmeEvents.any { it.payload["name"] == "Acme User" })
        }

    @Test
    fun `should increment event sequence correctly`() =
        runTest {
            val requests1 =
                listOf(
                    EventRequest(topicId, "user.created", mapOf("id" to "1", "name" to "Alice")),
                )
            val requests2 =
                listOf(
                    EventRequest(topicId, "user.created", mapOf("id" to "2", "name" to "Bob")),
                )
            val requests3 =
                listOf(
                    EventRequest(topicId, "user.created", mapOf("id" to "3", "name" to "Charlie")),
                )

            val result1 = application.publishEvents(requests1)
            val result2 = application.publishEvents(requests2)
            val result3 = application.publishEvents(requests3)

            val event1 = EventId.fromString(result1[0])
            val event2 = EventId.fromString(result2[0])
            val event3 = EventId.fromString(result3[0])

            assertEquals(event1.sequence + 1, event2.sequence)
            assertEquals(event2.sequence + 1, event3.sequence)
            assertEquals(topicId, event1.topicId)
            assertEquals(topicId, event2.topicId)
            assertEquals(topicId, event3.topicId)
        }
}
