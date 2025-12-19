package com.eventstore.domain.services.consumer

import com.eventstore.domain.services.PopulateEventStoreState
import com.eventstore.domain.services.createEventStore
import com.eventstore.domain.services.event.InMemoryEventDispatcher

import com.eventstore.domain.exceptions.ConsumerNotFoundException
import com.eventstore.infrastructure.factories.ConsumerFactoryImpl
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UnregisterConsumerServiceTest {
    val topicName = "user-events"

    private lateinit var helper: PopulateEventStoreState
    private lateinit var registerConsumerService: RegisterConsumerService
    private lateinit var unregisterConsumerService: UnregisterConsumerService

    @BeforeEach
    fun setup() = runBlocking {
        helper = createEventStore(topicName)
        val consumerFactory = ConsumerFactoryImpl()
        val eventDispatcher = InMemoryEventDispatcher()
        registerConsumerService = RegisterConsumerService(helper.consumerRepository, helper.topicRepository, consumerFactory, eventDispatcher)
        unregisterConsumerService = UnregisterConsumerService(helper.consumerRepository)
    }

    @Test
    fun `should unregister consumer successfully`() = runTest {
        val request = HttpConsumerRegistrationRequest(
            callbackUrl = "https://example.com/webhook",
            topics = mapOf(topicName to null)
        )

        val consumerId = registerConsumerService.execute(request, "default", "default")

        assertNotNull(helper.findConsumer(consumerId))
        unregisterConsumerService.execute(consumerId, "default", "default")

        assertNull(helper.findConsumer(consumerId))
    }

    @Test
    fun `should throw exception when consumer not found`() = runTest {
        val consumerId = "unknown-consumer"

        assertNull(helper.findConsumer(consumerId))

        assertThrows<ConsumerNotFoundException> {
            unregisterConsumerService.execute(consumerId, "default", "default")
        }
    }
}
