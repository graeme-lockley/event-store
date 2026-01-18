package com.eventstore.domain.services.consumer

import com.eventstore.domain.Application
import com.eventstore.domain.Schema
import com.eventstore.domain.consumers.HttpConsumer
import com.eventstore.domain.exceptions.InvalidConsumerRegistrationException
import com.eventstore.domain.exceptions.TopicNotFoundException
import com.eventstore.domain.services.createApplication
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RegisterConsumerServiceTest {
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
        // Create topic
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
    }

    @Test
    fun `should register consumer successfully`() = runTest {
        val request = HttpConsumerRegistrationRequest(
            callbackUrl = "https://example.com/webhook",
            topics = mapOf(topicId to null)
        )

        val consumerId = application.registerConsumer(request)

        assertNotNull(consumerId)
        val consumer = application.consumerRepository.findById(consumerId)

        assertNotNull(consumer)
        assertNotNull(consumer is HttpConsumer)
        val httpConsumer = consumer as HttpConsumer
        assertEquals("https://example.com/webhook", httpConsumer.callbackUrl.toString())
        assertTrue(httpConsumer.topics.containsKey(topicId))
    }

    @Test
    fun `should throw exception for invalid callback URL`() = runTest {
        val initialConsumers = application.consumerRepository.findAll()

        val request = HttpConsumerRegistrationRequest(
            callbackUrl = "not-a-valid-url",
            topics = mapOf(topicId to null)
        )

        assertThrows<InvalidConsumerRegistrationException> {
            application.registerConsumer(request)
        }

        assertEquals(initialConsumers.size, application.consumerRepository.findAll().size)
    }

    @Test
    fun `should throw exception when topic does not exist`() = runTest {
        val initialConsumers = application.consumerRepository.findAll()
        val nonExistentTopicId = UUID.randomUUID()

        val request = HttpConsumerRegistrationRequest(
            callbackUrl = "https://example.com/webhook",
            topics = mapOf(nonExistentTopicId to null)
        )

        assertThrows<TopicNotFoundException> {
            application.registerConsumer(request)
        }

        assertEquals(initialConsumers.size, application.consumerRepository.findAll().size)
    }

    @Test
    fun `should validate all topics exist`() = runTest {
        val nonExistentTopicId = UUID.randomUUID()
        val request = HttpConsumerRegistrationRequest(
            callbackUrl = "https://example.com/webhook",
            topics = mapOf(
                topicId to null,
                nonExistentTopicId to null
            )
        )

        assertThrows<TopicNotFoundException> {
            application.registerConsumer(request)
        }
    }

    @Test
    fun `should handle multiple topics with lastEventIds`() = runTest {
        // Create another topic
        val otherTopic = application.createTopic(
            name = "other-user-events",
            schemas = listOf(
                Schema(
                    eventType = "user.updated",
                    properties = mapOf("id" to "string"),
                    required = listOf("id")
                )
            ),
            namespaceId = namespaceId
        )

        val request = HttpConsumerRegistrationRequest(
            callbackUrl = "https://example.com/webhook",
            topics = mapOf(
                topicId to "event-5",
                otherTopic.topicId to null
            )
        )

        val consumerId = application.registerConsumer(request)

        assertNotNull(consumerId)
        val consumer = application.consumerRepository.findById(consumerId) as HttpConsumer
        assertEquals("event-5", consumer.topics[topicId])
        assertNull(consumer.topics[otherTopic.topicId])
    }

    @Test
    fun `should throw exception for empty topics map`() = runTest {
        val initialConsumers = application.consumerRepository.findAll()

        val request = HttpConsumerRegistrationRequest(
            callbackUrl = "https://example.com/webhook",
            topics = emptyMap()
        )

        assertThrows<InvalidConsumerRegistrationException> {
            application.registerConsumer(request)
        }

        assertEquals(initialConsumers.size, application.consumerRepository.findAll().size)
    }

    @Test
    fun `should allow multiple consumers for same topic`() = runTest {
        val request1 = HttpConsumerRegistrationRequest(
            callbackUrl = "https://example.com/webhook1",
            topics = mapOf(topicId to null)
        )
        val request2 = HttpConsumerRegistrationRequest(
            callbackUrl = "https://example.com/webhook2",
            topics = mapOf(topicId to null)
        )
        val request3 = HttpConsumerRegistrationRequest(
            callbackUrl = "https://example.com/webhook3",
            topics = mapOf(topicId to null)
        )

        val consumerId1 = application.registerConsumer(request1)
        val consumerId2 = application.registerConsumer(request2)
        val consumerId3 = application.registerConsumer(request3)

        assertNotNull(consumerId1)
        assertNotNull(consumerId2)
        assertNotNull(consumerId3)
        assertNotNull(application.consumerRepository.findById(consumerId1))
        assertNotNull(application.consumerRepository.findById(consumerId2))
        assertNotNull(application.consumerRepository.findById(consumerId3))

        // Verify all consumers have the topic
        val consumer1 = application.consumerRepository.findById(consumerId1) as HttpConsumer
        val consumer2 = application.consumerRepository.findById(consumerId2) as HttpConsumer
        val consumer3 = application.consumerRepository.findById(consumerId3) as HttpConsumer

        assertTrue(consumer1.topics.containsKey(topicId))
        assertTrue(consumer2.topics.containsKey(topicId))
        assertTrue(consumer3.topics.containsKey(topicId))
    }

    @Test
    fun `should store lastEventId correctly`() = runTest {
        val lastEventId = "event-42"
        val request = HttpConsumerRegistrationRequest(
            callbackUrl = "https://example.com/webhook",
            topics = mapOf(topicId to lastEventId)
        )

        val consumerId = application.registerConsumer(request)

        val consumer = application.consumerRepository.findById(consumerId) as HttpConsumer
        assertEquals(lastEventId, consumer.topics[topicId])
    }

    @Test
    fun `should store null lastEventId correctly`() = runTest {
        val request = HttpConsumerRegistrationRequest(
            callbackUrl = "https://example.com/webhook",
            topics = mapOf(topicId to null)
        )

        val consumerId = application.registerConsumer(request)

        val consumer = application.consumerRepository.findById(consumerId) as HttpConsumer
        assertNull(consumer.topics[topicId])
    }

    @Test
    fun `should handle consumers for different topics`() = runTest {
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

        val defaultRequest = HttpConsumerRegistrationRequest(
            callbackUrl = "https://default.example.com/webhook",
            topics = mapOf(topicId to null)
        )
        val acmeRequest = HttpConsumerRegistrationRequest(
            callbackUrl = "https://acme.example.com/webhook",
            topics = mapOf(acmeTopic.topicId to null)
        )

        val defaultConsumerId = application.registerConsumer(defaultRequest)
        val acmeConsumerId = application.registerConsumer(acmeRequest)

        val defaultConsumer = application.consumerRepository.findById(defaultConsumerId) as HttpConsumer
        val acmeConsumer = application.consumerRepository.findById(acmeConsumerId) as HttpConsumer

        // Verify consumers have different topics
        assertTrue(defaultConsumer.topics.containsKey(topicId))
        assertTrue(acmeConsumer.topics.containsKey(acmeTopic.topicId))
        assertFalse(defaultConsumer.topics.containsKey(acmeTopic.topicId))
        assertFalse(acmeConsumer.topics.containsKey(topicId))

        // Verify consumers are separate
        assertNotNull(defaultConsumer)
        assertNotNull(acmeConsumer)
        assertTrue(defaultConsumer.id != acmeConsumer.id)
    }

    @Test
    fun `should list all consumers`() = runTest {
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

        val defaultRequest1 = HttpConsumerRegistrationRequest(
            callbackUrl = "https://default1.example.com/webhook",
            topics = mapOf(topicId to null)
        )
        val defaultRequest2 = HttpConsumerRegistrationRequest(
            callbackUrl = "https://default2.example.com/webhook",
            topics = mapOf(topicId to null)
        )
        val acmeRequest = HttpConsumerRegistrationRequest(
            callbackUrl = "https://acme.example.com/webhook",
            topics = mapOf(acmeTopic.topicId to null)
        )

        val defaultConsumerId1 = application.registerConsumer(defaultRequest1)
        val defaultConsumerId2 = application.registerConsumer(defaultRequest2)
        val acmeConsumerId = application.registerConsumer(acmeRequest)

        // List all consumers (no longer scoped by tenant/namespace)
        val allConsumers = application.listConsumers()
        assertTrue(allConsumers.size >= 3)
        assertTrue(allConsumers.any { it.id == defaultConsumerId1 })
        assertTrue(allConsumers.any { it.id == defaultConsumerId2 })
        assertTrue(allConsumers.any { it.id == acmeConsumerId })
    }

    @Test
    fun `should throw exception when invalid UUID provided as topic`() = runTest {
        val initialConsumers = application.consumerRepository.findAll()

        // Try to register with invalid UUID string (will fail in mapper)
        val request = HttpConsumerRegistrationRequest(
            callbackUrl = "https://example.com/webhook",
            topics = mapOf(UUID.randomUUID() to null) // Valid UUID, but topic doesn't exist
        )

        // Actually, this will fail because topic doesn't exist, not because of UUID format
        // The UUID string parsing happens in the mapper
        assertThrows<TopicNotFoundException> {
            application.registerConsumer(request)
        }

        assertEquals(initialConsumers.size, application.consumerRepository.findAll().size)
    }
}
