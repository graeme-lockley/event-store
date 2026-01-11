package com.eventstore.domain.services.health

import com.eventstore.domain.Application
import com.eventstore.domain.Schema
import com.eventstore.domain.services.consumer.HttpConsumerRegistrationRequest
import com.eventstore.domain.services.createApplication
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GetHealthStatusServiceTest {
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

    private suspend fun getInitialConsumerCount(): Int {
        return application.consumerRepository.count()
    }

    @Test
    fun `should return health status with consumer count and dispatchers`() = runTest {
        val initialConsumerCount = getInitialConsumerCount()

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

        application.registerConsumer(request1, "default", "default")
        application.registerConsumer(request2, "default", "default")
        application.registerConsumer(request3, "default", "default")

        // Publish an event to trigger dispatcher processing
        application.publishEvents(
            listOf(
                com.eventstore.domain.services.event.EventRequest(
                    topic = topicName,
                    type = "user.created",
                    payload = mapOf("id" to "1", "name" to "Alice"),
                    "default",
                    "default"
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
            topics = mapOf(topicName to null)
        )

        application.registerConsumer(request, "default", "default")

        val result = application.getHealthStatus()

        assertEquals("healthy", result.status)
        assertEquals(initialConsumerCount + 1, result.consumers)
        // Registering a consumer triggers ensureDispatchersRunning, so dispatcher is tracked
        assertTrue(result.runningDispatchers.contains("default/default/$topicName"))
    }
}
