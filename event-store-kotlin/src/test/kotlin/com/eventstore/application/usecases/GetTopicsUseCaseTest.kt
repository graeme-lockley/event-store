package com.eventstore.application.usecases

import com.eventstore.application.repositories.TopicRepository
import com.eventstore.domain.Schema
import com.eventstore.domain.Topic
import com.eventstore.domain.exceptions.TopicNotFoundException
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class GetTopicsUseCaseTest {
    
    private val topicRepository = mockk<TopicRepository>()
    private val useCase = GetTopicsUseCase(topicRepository)
    
    @Test
    fun `should get all topics`() = runTest {
        val topics = listOf(
            Topic("user-events", 0L, emptyList()),
            Topic("order-events", 5L, emptyList())
        )
        
        coEvery { topicRepository.getAllTopics() } returns topics
        
        val result = useCase.execute()
        
        assertEquals(topics, result)
    }
    
    @Test
    fun `should get single topic by name`() = runTest {
        val topicName = "user-events"
        val topic = Topic(topicName, 5L, listOf(Schema(eventType = "user.created")))
        
        coEvery { topicRepository.getTopic(topicName) } returns topic
        
        val result = useCase.execute(topicName)
        
        assertEquals(topic, result)
    }
    
    @Test
    fun `should throw exception when topic not found`() = runTest {
        val topicName = "unknown-topic"
        
        coEvery { topicRepository.getTopic(topicName) } returns null
        
        assertThrows<TopicNotFoundException> {
            useCase.execute(topicName)
        }
    }
    
    @Test
    fun `should return empty list when no topics exist`() = runTest {
        coEvery { topicRepository.getAllTopics() } returns emptyList()
        
        val result = useCase.execute()
        
        assertEquals(emptyList(), result)
    }
}

