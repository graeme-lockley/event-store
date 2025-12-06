package com.eventstore.domain.services

import com.eventstore.domain.Schema
import com.eventstore.domain.Topic
import com.eventstore.domain.exceptions.TopicAlreadyExistsException
import com.eventstore.domain.ports.outbound.SchemaValidator
import com.eventstore.domain.ports.outbound.TopicRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class CreateTopicServiceTest {
    
    private val topicRepository = mockk<TopicRepository>()
    private val schemaValidator = mockk<SchemaValidator>(relaxed = true)
    private val service = CreateTopicService(topicRepository, schemaValidator)
    
    @Test
    fun `should create topic successfully`() = runTest {
        val name = "user-events"
        val schemas = listOf(
            Schema(eventType = "user.created", properties = mapOf("id" to "string"))
        )
        val topic = Topic(name, 0L, schemas)
        
        coEvery { topicRepository.topicExists(name) } returns false
        coEvery { topicRepository.createTopic(name, schemas) } returns topic
        
        val result = service.execute(name, schemas)
        
        assertEquals(topic, result)
        coVerify { schemaValidator.registerSchemas(name, schemas) }
        coVerify { topicRepository.createTopic(name, schemas) }
    }
    
    @Test
    fun `should throw exception when topic already exists`() = runTest {
        val name = "user-events"
        val schemas = listOf(Schema(eventType = "user.created"))
        
        coEvery { topicRepository.topicExists(name) } returns true
        
        assertThrows<TopicAlreadyExistsException> {
            service.execute(name, schemas)
        }
        
        coVerify(exactly = 0) { topicRepository.createTopic(any(), any()) }
    }
    
    @Test
    fun `should throw exception for blank eventType`() = runTest {
        // Schema constructor will throw, so we need to catch it during creation
        assertThrows<IllegalArgumentException> {
            Schema(eventType = "")
        }
    }
    
    @Test
    fun `should throw exception for blank schema`() = runTest {
        // Schema constructor will throw, so we need to catch it during creation
        assertThrows<IllegalArgumentException> {
            Schema(eventType = "user.created", schema = "")
        }
    }
    
    @Test
    fun `should handle multiple schemas`() = runTest {
        val name = "user-events"
        val schemas = listOf(
            Schema(eventType = "user.created"),
            Schema(eventType = "user.updated")
        )
        val topic = Topic(name, 0L, schemas)
        
        coEvery { topicRepository.topicExists(name) } returns false
        coEvery { topicRepository.createTopic(name, schemas) } returns topic
        
        val result = service.execute(name, schemas)
        
        assertEquals(topic, result)
        coVerify { schemaValidator.registerSchemas(name, schemas) }
    }
}

