package com.eventstore.domain.services

import com.eventstore.domain.Schema
import com.eventstore.domain.Topic
import com.eventstore.domain.exceptions.TopicNotFoundException
import com.eventstore.domain.ports.outbound.TopicRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class GetTopicsServiceTest {

    private val topicRepository = mockk<TopicRepository>()
    private val service = GetTopicsService(topicRepository)

    @Test
    fun `should get all topics`() = runTest {
        val topics = listOf(
            Topic("user-events", 0L, emptyList()),
            Topic("order-events", 5L, emptyList())
        )

        coEvery { topicRepository.getAllTopics() } returns topics

        val result = service.execute()

        assertEquals(topics, result)
    }

    @Test
    fun `should get single topic by name`() = runTest {
        val topicName = "user-events"
        val topic = Topic(topicName, 5L, listOf(Schema(eventType = "user.created")))

        coEvery { topicRepository.getTopic(topicName) } returns topic

        val result = service.execute(topicName)

        assertEquals(topic, result)
    }

    @Test
    fun `should throw exception when topic not found`() = runTest {
        val topicName = "unknown-topic"

        coEvery { topicRepository.getTopic(topicName) } returns null

        assertThrows<TopicNotFoundException> {
            service.execute(topicName)
        }
    }

    @Test
    fun `should return empty list when no topics exist`() = runTest {
        coEvery { topicRepository.getAllTopics() } returns emptyList()

        val result = service.execute()

        assertEquals(emptyList(), result)
    }
}

