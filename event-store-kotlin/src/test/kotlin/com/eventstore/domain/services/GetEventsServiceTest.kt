package com.eventstore.domain.services

import com.eventstore.domain.Event
import com.eventstore.domain.EventId
import com.eventstore.domain.exceptions.TopicNotFoundException
import com.eventstore.domain.ports.outbound.EventRepository
import com.eventstore.domain.ports.outbound.TopicRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import kotlin.test.assertEquals

class GetEventsServiceTest {

    private val eventRepository = mockk<EventRepository>()
    private val topicRepository = mockk<TopicRepository>()
    private val service = GetEventsService(eventRepository, topicRepository)

    @Test
    fun `should get events successfully`() = runTest {
        val topicName = "user-events"
        val events = listOf(
            Event(EventId.create(topicName, 1L), Instant.now(), "user.created", mapOf("id" to "1")),
            Event(EventId.create(topicName, 2L), Instant.now(), "user.created", mapOf("id" to "2"))
        )

        coEvery { topicRepository.topicExists(topicName) } returns true
        coEvery { eventRepository.getEvents(topicName, null, null, null) } returns events

        val result = service.execute(topicName)

        assertEquals(events, result)
    }

    @Test
    fun `should throw exception when topic does not exist`() = runTest {
        val topicName = "unknown-topic"

        coEvery { topicRepository.topicExists(topicName) } returns false

        assertThrows<TopicNotFoundException> {
            service.execute(topicName)
        }

        coVerify(exactly = 0) { eventRepository.getEvents(any(), any(), any(), any()) }
    }

    @Test
    fun `should pass sinceEventId parameter`() = runTest {
        val topicName = "user-events"
        val sinceEventId = "user-events-5"
        val events = emptyList<Event>()

        coEvery { topicRepository.topicExists(topicName) } returns true
        coEvery { eventRepository.getEvents(topicName, EventId(sinceEventId), null, null) } returns events

        val result = service.execute(topicName, sinceEventId = sinceEventId)

        assertEquals(events, result)
        coVerify { eventRepository.getEvents(topicName, EventId(sinceEventId), null, null) }
    }

    @Test
    fun `should pass date parameter`() = runTest {
        val topicName = "user-events"
        val date = "2025-01-15"
        val events = emptyList<Event>()

        coEvery { topicRepository.topicExists(topicName) } returns true
        coEvery { eventRepository.getEvents(topicName, null, date, null) } returns events

        val result = service.execute(topicName, date = date)

        assertEquals(events, result)
        coVerify { eventRepository.getEvents(topicName, null, date, null) }
    }

    @Test
    fun `should pass limit parameter`() = runTest {
        val topicName = "user-events"
        val limit = 10
        val events = emptyList<Event>()

        coEvery { topicRepository.topicExists(topicName) } returns true
        coEvery { eventRepository.getEvents(topicName, null, null, limit) } returns events

        val result = service.execute(topicName, limit = limit)

        assertEquals(events, result)
        coVerify { eventRepository.getEvents(topicName, null, null, limit) }
    }
}

