package com.eventstore.domain.services

import com.eventstore.domain.services.topic.GetTopicsService

import com.eventstore.domain.exceptions.TopicNotFoundException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class GetTopicsServiceTest {
    private lateinit var helper: PopulateEventStoreState
    private lateinit var service: GetTopicsService

    @BeforeEach
    fun setup() = runBlocking {
        helper = createEventStore()
        service = GetTopicsService(helper.topicRepository)
    }

    @Test
    fun `should get all topics`() = runTest {
        val result = service.execute()

        assertEquals(listOf("user-events", "other-user-events"), result.map { it.name })
    }

    @Test
    fun `should get single topic by name`() = runTest {
        val topicName = "user-events"

        val result = service.execute(topicName)

        assertEquals(topicName, result.name)
    }

    @Test
    fun `should throw exception when topic not found`() = runTest {
        val topicName = "unknown-topic"

        assertThrows<TopicNotFoundException> {
            service.execute(topicName)
        }
    }
}

