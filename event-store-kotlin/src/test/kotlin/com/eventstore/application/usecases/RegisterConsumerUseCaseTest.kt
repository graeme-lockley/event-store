package com.eventstore.application.usecases

import com.eventstore.application.repositories.ConsumerRepository
import com.eventstore.application.repositories.TopicRepository
import com.eventstore.domain.exceptions.InvalidConsumerRegistrationException
import com.eventstore.domain.exceptions.TopicNotFoundException
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RegisterConsumerUseCaseTest {
    
    private val consumerRepository = mockk<ConsumerRepository>(relaxed = true)
    private val topicRepository = mockk<TopicRepository>()
    private val useCase = RegisterConsumerUseCase(consumerRepository, topicRepository)
    
    @Test
    fun `should register consumer successfully`() = runTest {
        val request = ConsumerRegistrationRequest(
            callback = "https://example.com/webhook",
            topics = mapOf("user-events" to null)
        )
        
        coEvery { topicRepository.topicExists("user-events") } returns true
        
        val consumerId = useCase.execute(request)
        
        assertNotNull(consumerId)
        assertTrue(consumerId.isNotBlank())
        coVerify { consumerRepository.save(any()) }
    }
    
    @Test
    fun `should throw exception for invalid callback URL`() = runTest {
        val request = ConsumerRegistrationRequest(
            callback = "not-a-valid-url",
            topics = mapOf("user-events" to null)
        )
        
        assertThrows<InvalidConsumerRegistrationException> {
            useCase.execute(request)
        }
        
        coVerify(exactly = 0) { consumerRepository.save(any()) }
    }
    
    @Test
    fun `should throw exception when topic does not exist`() = runTest {
        val request = ConsumerRegistrationRequest(
            callback = "https://example.com/webhook",
            topics = mapOf("unknown-topic" to null)
        )
        
        coEvery { topicRepository.topicExists("unknown-topic") } returns false
        
        assertThrows<TopicNotFoundException> {
            useCase.execute(request)
        }
        
        coVerify(exactly = 0) { consumerRepository.save(any()) }
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
        
        coEvery { topicRepository.topicExists("user-events") } returns true
        coEvery { topicRepository.topicExists("unknown-topic") } returns false
        
        assertThrows<TopicNotFoundException> {
            useCase.execute(request)
        }
        
        coVerify(exactly = 0) { consumerRepository.save(any()) }
    }
    
    @Test
    fun `should handle multiple topics with lastEventIds`() = runTest {
        val request = ConsumerRegistrationRequest(
            callback = "https://example.com/webhook",
            topics = mapOf(
                "user-events" to "user-events-5",
                "order-events" to null
            )
        )
        
        coEvery { topicRepository.topicExists("user-events") } returns true
        coEvery { topicRepository.topicExists("order-events") } returns true
        
        val consumerId = useCase.execute(request)
        
        assertNotNull(consumerId)
        coVerify { consumerRepository.save(any()) }
    }
}

