package com.eventstore.domain.services

import com.eventstore.domain.exceptions.InvalidConsumerRegistrationException
import com.eventstore.domain.exceptions.TopicNotFoundException
import com.eventstore.domain.ports.outbound.ConsumerRepository
import com.eventstore.domain.ports.outbound.TopicRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RegisterConsumerServiceTest {
    val topicName = "user-events"

    private lateinit var helper: PopulateEventStoreState
    private lateinit var service: RegisterConsumerService

    @BeforeEach
    fun setup() = runBlocking {
        helper = createEventStore(topicName)
        service = RegisterConsumerService(helper.consumerRepository, helper.topicRepository)
    }

    @Test
    fun `should register consumer successfully`() = runTest {
        val request = ConsumerRegistrationRequest(
            callback = "https://example.com/webhook",
            topics = mapOf(topicName to null)
        )

        val consumerId = service.execute(request)

        assertNotNull(consumerId)
        val consumer = helper.findConsumer(consumerId)

        assertNotNull(consumer)
        assertEquals("https://example.com/webhook", consumer.callback.toString())
    }

    @Test
    fun `should throw exception for invalid callback URL`() = runTest {
        val consumers = helper.findConsumers()

        val request = ConsumerRegistrationRequest(
            callback = "not-a-valid-url",
            topics = mapOf("user-events" to null)
        )

        assertThrows<InvalidConsumerRegistrationException> {
            service.execute(request)
        }

        assertEquals(consumers, helper.findConsumers())
    }

    @Test
    fun `should throw exception when topic does not exist`() = runTest {
        val consumers = helper.findConsumers()

        val request = ConsumerRegistrationRequest(
            callback = "https://example.com/webhook",
            topics = mapOf("unknown-topic" to null)
        )

        assertThrows<TopicNotFoundException> {
            service.execute(request)
        }

        assertEquals(consumers, helper.findConsumers())
    }

    @Test
    fun `should validate all topics exist`() = runTest {
        val request = ConsumerRegistrationRequest(
            callback = "https://example.com/webhook",
            topics = mapOf(
                "user-events" to null,
                "unknown-topic" to null
            )
        )

        assertThrows<TopicNotFoundException> {
            service.execute(request)
        }
    }

    @Test
    fun `should handle multiple topics with lastEventIds`() = runTest {
        val request = ConsumerRegistrationRequest(
            callback = "https://example.com/webhook",
            topics = mapOf(
                "user-events" to "user-events-5",
                "other-user-events" to null
            )
        )

        val consumerId = service.execute(request)

        assertNotNull(consumerId)
    }
}

