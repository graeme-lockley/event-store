package com.eventstore.domain.services.health

import com.eventstore.domain.Application
import com.eventstore.domain.Schema
import com.eventstore.domain.services.consumer.HttpConsumerRegistrationRequest
import com.eventstore.domain.services.createApplication
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GetHealthStatusServiceTest {
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

    private suspend fun getInitialConsumerCount(): Int {
        return application.consumerRepository.count()
    }

    @Test
    fun `should return health status with consumer count and dispatchers`() = runTest {
        val initialConsumerCount = getInitialConsumerCount()

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

        application.registerConsumer(request1)
        application.registerConsumer(request2)
        application.registerConsumer(request3)

        // Publish an event to trigger dispatcher processing
        application.publishEvents(
            listOf(
                com.eventstore.domain.services.event.EventRequest(
                    topicId = topicId,
                    type = "user.created",
                    payload = mapOf("id" to "1", "name" to "Alice")
                )
            )
        )

        val result = application.getHealthStatus()

        assertEquals("healthy", result.status)
        assertEquals(initialConsumerCount + 3, result.consumers)
        // Dispatchers should include the topic that was processed
        assertTrue(result.runningDispatchers.isNotEmpty())
    }

    @Test
    fun `should return initial consumer count when no new consumers exist`() = runTest {
        val initialConsumerCount = getInitialConsumerCount()
        val result = application.getHealthStatus()

        assertEquals("healthy", result.status)
        assertEquals(initialConsumerCount, result.consumers)
        // Bootstrap creates system consumers which trigger dispatchers
        assertTrue(result.runningDispatchers.isNotEmpty())
    }

    @Test
    fun `should include dispatchers for registered consumers`() = runTest {
        val initialConsumerCount = getInitialConsumerCount()

        // Create a consumer - this triggers ensureDispatchersRunning which adds to processedTopics
        val request = HttpConsumerRegistrationRequest(
            callbackUrl = "https://example.com/webhook",
            topics = mapOf(topicId to null)
        )

        application.registerConsumer(request)

        val result = application.getHealthStatus()

        assertEquals("healthy", result.status)
        assertEquals(initialConsumerCount + 1, result.consumers)
        // Registering a consumer triggers ensureDispatchersRunning, so dispatcher is tracked
        assertTrue(result.runningDispatchers.contains(topicId.toString()))
    }
}
