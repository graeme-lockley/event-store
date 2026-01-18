package com.eventstore.domain.services.consumer

import com.eventstore.domain.Application
import com.eventstore.domain.Schema
import com.eventstore.domain.exceptions.ConsumerNotFoundException
import com.eventstore.domain.services.createApplication
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import java.util.*
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UnregisterConsumerServiceTest {
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
    fun `should unregister consumer successfully`() = runTest {
        val request = HttpConsumerRegistrationRequest(
            callbackUrl = "https://example.com/webhook",
            topics = mapOf(topicId to null)
        )

        val consumerId = application.registerConsumer(request)

        assertNotNull(application.consumerRepository.findById(consumerId))
        application.unregisterConsumer(consumerId)

        assertNull(application.consumerRepository.findById(consumerId))
    }

    @Test
    fun `should throw exception when consumer not found`() = runTest {
        val consumerId = "unknown-consumer"

        assertNull(application.consumerRepository.findById(consumerId))

        assertThrows<ConsumerNotFoundException> {
            application.unregisterConsumer(consumerId)
        }
    }

    @Test
    fun `should throw exception when unregistering already unregistered consumer`() = runTest {
        val request = HttpConsumerRegistrationRequest(
            callbackUrl = "https://example.com/webhook",
            topics = mapOf(topicId to null)
        )

        val consumerId = application.registerConsumer(request)

        // First unregister should succeed
        application.unregisterConsumer(consumerId)
        assertNull(application.consumerRepository.findById(consumerId))

        // Second unregister should fail
        assertThrows<ConsumerNotFoundException> {
            application.unregisterConsumer(consumerId)
        }
    }
}
