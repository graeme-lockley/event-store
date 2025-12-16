package com.eventstore.domain.services

import com.eventstore.infrastructure.factories.ConsumerFactoryImpl
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class GetHealthStatusServiceTest {
    val topicName = "user-events"

    private lateinit var helper: PopulateEventStoreState

    @BeforeEach
    fun setup() = runBlocking {
        helper = createEventStore(topicName)
    }

    @Test
    fun `should return health status with consumer count and dispatchers`() = runTest {
        val request = HttpConsumerRegistrationRequest(
            callbackUrl = "https://example.com/webhook",
            topics = mapOf(topicName to null)
        )
        val consumerFactory = ConsumerFactoryImpl()
        val eventDispatcher = InMemoryEventDispatcher()
        val registerConsumerService = RegisterConsumerService(helper.consumerRepository, helper.topicRepository, consumerFactory, eventDispatcher)

        registerConsumerService.execute(request)
        registerConsumerService.execute(request)
        registerConsumerService.execute(request)

        val runningDispatchers = listOf("topic1", "topic2")
        val service = GetHealthStatusService(helper.consumerRepository) { runningDispatchers }

        val result = service.execute()

        assertEquals("healthy", result.status)
        assertEquals(3, result.consumers)
        assertEquals(runningDispatchers, result.runningDispatchers)
    }

    @Test
    fun `should return zero consumers when none exist`() = runTest {
        val runningDispatchers = listOf("topic1", "topic2")
        val service = GetHealthStatusService(helper.consumerRepository) { runningDispatchers }

        val result = service.execute()

        assertEquals("healthy", result.status)
        assertEquals(0, result.consumers)
        assertEquals(runningDispatchers, result.runningDispatchers)
    }

    @Test
    fun `should return empty dispatchers list when none running`() = runTest {
        val service = GetHealthStatusService(helper.consumerRepository) { emptyList() }

        val result = service.execute()

        assertEquals("healthy", result.status)
        assertEquals(0, result.consumers)
        assertEquals(emptyList(), result.runningDispatchers)
    }
}
