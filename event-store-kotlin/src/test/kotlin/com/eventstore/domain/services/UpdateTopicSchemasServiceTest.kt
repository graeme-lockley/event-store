package com.eventstore.domain.services

import com.eventstore.domain.Schema
import com.eventstore.domain.Topic
import com.eventstore.domain.exceptions.TopicNotFoundException
import com.eventstore.domain.ports.outbound.SchemaValidator
import com.eventstore.domain.ports.outbound.TopicRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class UpdateTopicSchemasServiceTest {

    private val topicRepository = mockk<TopicRepository>()
    private val schemaValidator = mockk<SchemaValidator>(relaxed = true)
    private val service = UpdateTopicSchemasService(topicRepository, schemaValidator)

    @Test
    fun `should update schemas successfully`() = runTest {
        val topicName = "user-events"
        val currentTopic = Topic(
            topicName, 5L, listOf(
                Schema(eventType = "user.created")
            )
        )
        val newSchemas = listOf(
            Schema(eventType = "user.created"),
            Schema(eventType = "user.updated")
        )
        val updatedTopic = Topic(topicName, 5L, newSchemas)

        coEvery { topicRepository.getTopic(topicName) } returns currentTopic
        coEvery { topicRepository.updateSchemas(topicName, newSchemas) } returns updatedTopic

        val result = service.execute(topicName, newSchemas)

        assertEquals(updatedTopic, result)
        coVerify { schemaValidator.registerSchemas(topicName, newSchemas) }
    }

    @Test
    fun `should throw exception when topic does not exist`() = runTest {
        val topicName = "unknown-topic"
        val newSchemas = listOf(Schema(eventType = "user.created"))

        coEvery { topicRepository.getTopic(topicName) } returns null

        assertThrows<TopicNotFoundException> {
            service.execute(topicName, newSchemas)
        }

        coVerify(exactly = 0) { topicRepository.updateSchemas(any(), any()) }
    }

    @Test
    fun `should throw exception when removing schemas`() = runTest {
        val topicName = "user-events"
        val currentTopic = Topic(
            topicName, 5L, listOf(
                Schema(eventType = "user.created"),
                Schema(eventType = "user.updated")
            )
        )
        val newSchemas = listOf(
            Schema(eventType = "user.created")
            // user.updated is missing
        )

        coEvery { topicRepository.getTopic(topicName) } returns currentTopic

        assertThrows<IllegalArgumentException> {
            service.execute(topicName, newSchemas)
        }

        coVerify(exactly = 0) { topicRepository.updateSchemas(any(), any()) }
    }

    @Test
    fun `should throw exception for blank eventType`() = runTest {
        // Schema constructor will throw, so we need to catch it during creation
        assertThrows<IllegalArgumentException> {
            Schema(eventType = "")
        }
    }
}

