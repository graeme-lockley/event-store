package com.eventstore.domain.services

import com.eventstore.domain.exceptions.ConsumerNotFoundException
import com.eventstore.domain.ports.outbound.ConsumerRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertTrue

class UnregisterConsumerServiceTest {

    private val consumerRepository = mockk<ConsumerRepository>()
    private val service = UnregisterConsumerService(consumerRepository)

    @Test
    fun `should unregister consumer successfully`() = runTest {
        val consumerId = "consumer-123"

        coEvery { consumerRepository.delete(consumerId) } returns true

        val result = service.execute(consumerId)

        assertTrue(result)
        coVerify { consumerRepository.delete(consumerId) }
    }

    @Test
    fun `should throw exception when consumer not found`() = runTest {
        val consumerId = "unknown-consumer"

        coEvery { consumerRepository.delete(consumerId) } returns false

        assertThrows<ConsumerNotFoundException> {
            service.execute(consumerId)
        }

        coVerify { consumerRepository.delete(consumerId) }
    }
}

