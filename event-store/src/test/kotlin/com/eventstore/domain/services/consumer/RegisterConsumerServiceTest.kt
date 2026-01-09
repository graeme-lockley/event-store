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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RegisterConsumerServiceTest {
    private lateinit var application: Application
    private val topicName = "user-events"

    @BeforeEach
    fun setup() = runTest {
        application = createApplication()
        // Create tenant and namespace
        application.createTenant("default")
        application.createNamespace("default", "default")
        // Create topic
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
    }

    @Test
    fun `should register consumer successfully`() = runTest {
        val request = HttpConsumerRegistrationRequest(
            callbackUrl = "https://example.com/webhook",
            topics = mapOf(topicName to null)
        )

        val consumerId = application.registerConsumer(request, "default", "default")

        assertNotNull(consumerId)
        val consumer = application.consumerRepository.findById(consumerId)

        assertNotNull(consumer)
        assertNotNull(consumer is HttpConsumer)
        val httpConsumer = consumer as HttpConsumer
        assertEquals("https://example.com/webhook", httpConsumer.callbackUrl.toString())
    }

    @Test
    fun `should throw exception for invalid callback URL`() = runTest {
        val initialConsumers = application.consumerRepository.findAll()

        val request = HttpConsumerRegistrationRequest(
            callbackUrl = "not-a-valid-url",
            topics = mapOf(topicName to null)
        )

        assertThrows<InvalidConsumerRegistrationException> {
            application.registerConsumer(request, "default", "default")
        }

        assertEquals(initialConsumers.size, application.consumerRepository.findAll().size)
    }

    @Test
    fun `should throw exception when topic does not exist`() = runTest {
        val initialConsumers = application.consumerRepository.findAll()

        val request = HttpConsumerRegistrationRequest(
            callbackUrl = "https://example.com/webhook",
            topics = mapOf("unknown-topic" to null)
        )

        assertThrows<TopicNotFoundException> {
            application.registerConsumer(request, "default", "default")
        }

        assertEquals(initialConsumers.size, application.consumerRepository.findAll().size)
    }

    @Test
    fun `should validate all topics exist`() = runTest {
        val request = HttpConsumerRegistrationRequest(
            callbackUrl = "https://example.com/webhook",
            topics = mapOf(
                topicName to null,
                "unknown-topic" to null
            )
        )

        assertThrows<TopicNotFoundException> {
            application.registerConsumer(request, "default", "default")
        }
    }

    @Test
    fun `should handle multiple topics with lastEventIds`() = runTest {
        // Create another topic
        application.createTopic(
            name = "other-user-events",
            schemas = listOf(
                Schema(
                    eventType = "user.updated",
                    properties = mapOf("id" to "string"),
                    required = listOf("id")
                )
            ),
            tenantName = "default",
            namespaceName = "default"
        )

        val request = HttpConsumerRegistrationRequest(
            callbackUrl = "https://example.com/webhook",
            topics = mapOf(
                topicName to "$topicName-5",
                "other-user-events" to null
            )
        )

        val consumerId = application.registerConsumer(request, "default", "default")

        assertNotNull(consumerId)
    }

    @Test
    fun `should throw exception for empty topics map`() = runTest {
        val initialConsumers = application.consumerRepository.findAll()

        val request = HttpConsumerRegistrationRequest(
            callbackUrl = "https://example.com/webhook",
            topics = emptyMap()
        )

        assertThrows<InvalidConsumerRegistrationException> {
            application.registerConsumer(request, "default", "default")
        }

        assertEquals(initialConsumers.size, application.consumerRepository.findAll().size)
    }

    @Test
    fun `should allow multiple consumers for same topic`() = runTest {
        val request1 = HttpConsumerRegistrationRequest(
            callbackUrl = "https://example.com/webhook1",
            topics = mapOf(topicName to null)
        )
        val request2 = HttpConsumerRegistrationRequest(
            callbackUrl = "https://example.com/webhook2",
            topics = mapOf(topicName to null)
        )
        val request3 = HttpConsumerRegistrationRequest(
            callbackUrl = "https://example.com/webhook3",
            topics = mapOf(topicName to null)
        )

        val consumerId1 = application.registerConsumer(request1, "default", "default")
        val consumerId2 = application.registerConsumer(request2, "default", "default")
        val consumerId3 = application.registerConsumer(request3, "default", "default")

        assertNotNull(consumerId1)
        assertNotNull(consumerId2)
        assertNotNull(consumerId3)
        assertNotNull(application.consumerRepository.findById(consumerId1))
        assertNotNull(application.consumerRepository.findById(consumerId2))
        assertNotNull(application.consumerRepository.findById(consumerId3))

        // Verify all consumers have the qualified topic name
        val consumer1 = application.consumerRepository.findById(consumerId1) as HttpConsumer
        val consumer2 = application.consumerRepository.findById(consumerId2) as HttpConsumer
        val consumer3 = application.consumerRepository.findById(consumerId3) as HttpConsumer

        assertEquals("default/default/$topicName", consumer1.topics.keys.first())
        assertEquals("default/default/$topicName", consumer2.topics.keys.first())
        assertEquals("default/default/$topicName", consumer3.topics.keys.first())
    }

    @Test
    fun `should store lastEventId correctly`() = runTest {
        val lastEventId = "$topicName-42"
        val request = HttpConsumerRegistrationRequest(
            callbackUrl = "https://example.com/webhook",
            topics = mapOf(topicName to lastEventId)
        )

        val consumerId = application.registerConsumer(request, "default", "default")

        val consumer = application.consumerRepository.findById(consumerId) as HttpConsumer
        val qualifiedTopicName = "default/default/$topicName"
        assertEquals(lastEventId, consumer.topics[qualifiedTopicName])
    }

    @Test
    fun `should store null lastEventId correctly`() = runTest {
        val request = HttpConsumerRegistrationRequest(
            callbackUrl = "https://example.com/webhook",
            topics = mapOf(topicName to null)
        )

        val consumerId = application.registerConsumer(request, "default", "default")

        val consumer = application.consumerRepository.findById(consumerId) as HttpConsumer
        val qualifiedTopicName = "default/default/$topicName"
        assertNull(consumer.topics[qualifiedTopicName])
    }

    @Test
    fun `should scope consumers by tenant and namespace`() = runTest {
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

        val defaultRequest = HttpConsumerRegistrationRequest(
            callbackUrl = "https://default.example.com/webhook",
            topics = mapOf(topicName to null)
        )
        val acmeRequest = HttpConsumerRegistrationRequest(
            callbackUrl = "https://acme.example.com/webhook",
            topics = mapOf(topicName to null)
        )

        val defaultConsumerId = application.registerConsumer(defaultRequest, "default", "default")
        val acmeConsumerId = application.registerConsumer(acmeRequest, "acme", "production")

        val defaultConsumer = application.consumerRepository.findById(defaultConsumerId) as HttpConsumer
        val acmeConsumer = application.consumerRepository.findById(acmeConsumerId) as HttpConsumer

        // Verify consumers have different qualified topic names
        assertEquals("default/default/$topicName", defaultConsumer.topics.keys.first())
        assertEquals("acme/production/$topicName", acmeConsumer.topics.keys.first())

        // Verify consumers are separate
        assertNotNull(defaultConsumer)
        assertNotNull(acmeConsumer)
        assertTrue(defaultConsumer.id != acmeConsumer.id)
    }

    @Test
    fun `should list consumers by tenant and namespace`() = runTest {
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

        val defaultRequest1 = HttpConsumerRegistrationRequest(
            callbackUrl = "https://default1.example.com/webhook",
            topics = mapOf(topicName to null)
        )
        val defaultRequest2 = HttpConsumerRegistrationRequest(
            callbackUrl = "https://default2.example.com/webhook",
            topics = mapOf(topicName to null)
        )
        val acmeRequest = HttpConsumerRegistrationRequest(
            callbackUrl = "https://acme.example.com/webhook",
            topics = mapOf(topicName to null)
        )

        val defaultConsumerId1 = application.registerConsumer(defaultRequest1, "default", "default")
        val defaultConsumerId2 = application.registerConsumer(defaultRequest2, "default", "default")
        val acmeConsumerId = application.registerConsumer(acmeRequest, "acme", "production")

        // List consumers in default/default
        val defaultConsumers = application.listConsumers("default", "default")
        assertEquals(2, defaultConsumers.size)
        assertTrue(defaultConsumers.any { it.id == defaultConsumerId1 })
        assertTrue(defaultConsumers.any { it.id == defaultConsumerId2 })
        assertFalse(defaultConsumers.any { it.id == acmeConsumerId })

        // List consumers in acme/production
        val acmeConsumers = application.listConsumers("acme", "production")
        assertEquals(1, acmeConsumers.size)
        assertEquals(acmeConsumerId, acmeConsumers.first().id)
        assertFalse(acmeConsumers.any { it.id == defaultConsumerId1 })
        assertFalse(acmeConsumers.any { it.id == defaultConsumerId2 })
    }

    @Test
    fun `should throw exception when qualified topic name is provided`() = runTest {
        val initialConsumers = application.consumerRepository.findAll()

        // Try to register with qualified topic name (should fail)
        val request = HttpConsumerRegistrationRequest(
            callbackUrl = "https://example.com/webhook",
            topics = mapOf("default/default/$topicName" to null)
        )

        assertThrows<TopicNotFoundException> {
            application.registerConsumer(request, "default", "default")
        }

        assertEquals(initialConsumers.size, application.consumerRepository.findAll().size)
    }
}
