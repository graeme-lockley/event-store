package com.eventstore.application.usecases

import com.eventstore.application.repositories.ConsumerRepository
import com.eventstore.domain.exceptions.ConsumerNotFoundException
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertTrue

class UnregisterConsumerUseCaseTest {
    
    private val consumerRepository = mockk<ConsumerRepository>()
    private val useCase = UnregisterConsumerUseCase(consumerRepository)
    
    @Test
    fun `should unregister consumer successfully`() = runTest {
        val consumerId = "consumer-123"
        
        coEvery { consumerRepository.delete(consumerId) } returns true
        
        val result = useCase.execute(consumerId)
        
        assertTrue(result)
        coVerify { consumerRepository.delete(consumerId) }
    }
    
    @Test
    fun `should throw exception when consumer not found`() = runTest {
        val consumerId = "unknown-consumer"
        
        coEvery { consumerRepository.delete(consumerId) } returns false
        
        assertThrows<ConsumerNotFoundException> {
            useCase.execute(consumerId)
        }
        
        coVerify { consumerRepository.delete(consumerId) }
    }
}

