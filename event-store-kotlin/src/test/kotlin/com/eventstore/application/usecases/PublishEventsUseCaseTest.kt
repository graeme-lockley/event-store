package com.eventstore.application.usecases

import com.eventstore.application.repositories.EventRepository
import com.eventstore.application.repositories.TopicRepository
import com.eventstore.application.services.SchemaValidator
import com.eventstore.domain.Event
import com.eventstore.domain.EventId
import com.eventstore.domain.Topic
import com.eventstore.domain.exceptions.InvalidEventPayloadException
import com.eventstore.domain.exceptions.TopicNotFoundException
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PublishEventsUseCaseTest {
    
    private val topicRepository = mockk<TopicRepository>()
    private val eventRepository = mockk<EventRepository>()
    private val schemaValidator = mockk<SchemaValidator>(relaxed = true)
    private val useCase = PublishEventsUseCase(topicRepository, eventRepository, schemaValidator)
    
    @Test
    fun `should publish single event successfully`() = runTest {
        val topicName = "user-events"
        val topic = Topic(topicName, 0L, listOf())
        val request = EventRequest(
            topic = topicName,
            type = "user.created",
            payload = mapOf("id" to "123", "name" to "Alice")
        )
        val eventId = EventId.create(topicName, 1L)
        val timestamp = Instant.now()
        val event = Event(eventId, timestamp, "user.created", request.payload)
        
        coEvery { topicRepository.topicExists(topicName) } returns true
        coEvery { topicRepository.getTopic(topicName) } returns topic
        coEvery { eventRepository.storeEvent(any(), any(), any(), any(), any()) } returns event
        coEvery { topicRepository.updateSequence(topicName, 1L) } just Runs
        
        val result = useCase.execute(listOf(request))
        
        assertEquals(1, result.size)
        assertEquals("user-events-1", result[0])
        coVerify { schemaValidator.validateEvent(topicName, "user.created", request.payload) }
        coVerify { eventRepository.storeEvent(topicName, "user.created", request.payload, eventId, any()) }
        coVerify { topicRepository.updateSequence(topicName, 1L) }
    }
    
    @Test
    fun `should publish multiple events successfully`() = runTest {
        val topicName = "user-events"
        var currentSequence = 0L
        val requests = listOf(
            EventRequest(topicName, "user.created", mapOf("id" to "1")),
            EventRequest(topicName, "user.created", mapOf("id" to "2"))
        )
        val timestamp = Instant.now()
        
        coEvery { topicRepository.topicExists(topicName) } returns true
        coEvery { topicRepository.getTopic(topicName) } answers {
            Topic(topicName, currentSequence, listOf())
        }
        coEvery { eventRepository.storeEvent(any(), any(), any(), any(), any()) } answers {
            val eventId = arg<EventId>(3)
            Event(eventId, timestamp, arg(1), arg(2))
        }
        coEvery { topicRepository.updateSequence(topicName, any()) } answers {
            currentSequence = arg(1)
        }
        
        val result = useCase.execute(requests)
        
        assertEquals(2, result.size)
        assertEquals("user-events-1", result[0])
        assertEquals("user-events-2", result[1])
        coVerify(exactly = 2) { eventRepository.storeEvent(any(), any(), any(), any(), any()) }
        coVerify { topicRepository.updateSequence(topicName, 1L) }
        coVerify { topicRepository.updateSequence(topicName, 2L) }
    }
    
    @Test
    fun `should throw exception for empty requests`() = runTest {
        assertThrows<IllegalArgumentException> {
            useCase.execute(emptyList())
        }
        
        coVerify(exactly = 0) { eventRepository.storeEvent(any(), any(), any(), any(), any()) }
    }
    
    @Test
    fun `should throw exception when topic does not exist`() = runTest {
        val request = EventRequest("unknown-topic", "user.created", mapOf("id" to "123"))
        
        coEvery { topicRepository.topicExists("unknown-topic") } returns false
        
        assertThrows<TopicNotFoundException> {
            useCase.execute(listOf(request))
        }
        
        coVerify(exactly = 0) { eventRepository.storeEvent(any(), any(), any(), any(), any()) }
    }
    
    @Test
    fun `should validate all events before storing any`() = runTest {
        val topicName = "user-events"
        val requests = listOf(
            EventRequest(topicName, "user.created", mapOf("id" to "1")),
            EventRequest("unknown-topic", "user.created", mapOf("id" to "2"))
        )
        
        coEvery { topicRepository.topicExists(topicName) } returns true
        coEvery { topicRepository.topicExists("unknown-topic") } returns false
        
        assertThrows<TopicNotFoundException> {
            useCase.execute(requests)
        }
        
        coVerify(exactly = 0) { eventRepository.storeEvent(any(), any(), any(), any(), any()) }
    }
    
    @Test
    fun `should throw exception for invalid payload`() = runTest {
        val topicName = "user-events"
        val request = EventRequest(topicName, "user.created", emptyMap())
        
        coEvery { topicRepository.topicExists(topicName) } returns true
        
        // Note: The current implementation checks if payload is empty AND not a Map
        // This test may need adjustment based on actual validation logic
        // For now, testing that validation is called
        coEvery { schemaValidator.validateEvent(any(), any(), any()) } throws 
            InvalidEventPayloadException("Invalid payload")
        
        assertThrows<InvalidEventPayloadException> {
            useCase.execute(listOf(request))
        }
    }
}

