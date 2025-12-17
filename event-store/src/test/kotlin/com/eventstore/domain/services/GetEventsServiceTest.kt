package com.eventstore.domain.services

import com.eventstore.domain.services.event.GetEventsService

import com.eventstore.domain.exceptions.TopicNotFoundException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class GetEventsServiceTest {
    val topicName = "user-events"

    private lateinit var helper: PopulateEventStoreState
    private lateinit var service: GetEventsService

    @BeforeEach
    fun setup() = runBlocking {
        helper = createEventStore(topicName)
        service = GetEventsService(helper.eventRepository, helper.topicRepository)
    }


    @Test
    fun `should get events successfully`() = runTest {
        val events = helper.eventRepository.getEvents(topicName)

        val result = service.execute(topicName)

        assertEquals(events, result)
    }

    @Test
    fun `should throw exception when topic does not exist`() = runTest {
        val unknownTopicName = "unknown-${topicName}"

        assertFalse(helper.topicExists(unknownTopicName))
        assertThrows<TopicNotFoundException> {
            service.execute(unknownTopicName)
        }
    }

    @Test
    fun `should pass sinceEventId parameter`() = runTest {
        var events = helper.getEvents(topicName)

        while (events.isNotEmpty()) {
            val otherEvents = service.execute(topicName, events[0].id.toString())
            events = events.drop(1)

            assertEquals(events, otherEvents)
        }
    }

    @Test
    fun `should pass limit parameter`() = runTest {
        var events = helper.getEvents(topicName)

        while (events.isNotEmpty()) {
            val otherEvents = service.execute(topicName, events[0].id.toString(), limit = 2)
            events = events.drop(1)

            assertEquals(events.take(otherEvents.size), otherEvents)
        }
    }
}

