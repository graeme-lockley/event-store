package com.eventstore.domain.services

import com.eventstore.domain.Schema
import com.eventstore.domain.Topic
import com.eventstore.domain.exceptions.TopicAlreadyExistsException
import com.eventstore.infrastructure.external.JsonSchemaValidator
import com.eventstore.infrastructure.persistence.InMemoryTopicRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CreateTopicServiceTest {
    private val topicRepository = InMemoryTopicRepository()
    private val schemaValidator = JsonSchemaValidator()
    private val service = CreateTopicService(topicRepository, schemaValidator)

    @Test
    fun `should create topic successfully`() = runTest {
        val name = "user-events"
        val schemas = listOf(
            Schema(eventType = "user.created", properties = mapOf("id" to "string"))
        )
        val topic = Topic(name, 0L, schemas)

        val result = service.execute(name, schemas)

        assertEquals(topic, result)
        assertEquals(listOf(topic), topicRepository.getAllTopics())
    }

    @Test
    fun `should throw exception when topic already exists`() = runTest {
        val name = "user-events"
        val schemas = listOf(Schema(eventType = "user.created"))

        val topic = service.execute(name, schemas)

        assertThrows<TopicAlreadyExistsException> {
            service.execute(name, schemas)
        }

        assertEquals(listOf(topic), topicRepository.getAllTopics())
    }

    @Test
    fun `should handle multiple schemas`() = runTest {
        val name = "user-events"
        val schemas = listOf(
            Schema(eventType = "user.created"),
            Schema(eventType = "user.updated")
        )

        val topic = service.execute(name, schemas)

        assertEquals(Topic(name, 0L, schemas), topic)
        assertEquals(listOf(topic), topicRepository.getAllTopics())

        assertTrue(schemaValidator.hasSchema("user-events", "user.created"))
        assertTrue(schemaValidator.hasSchema("user-events", "user.updated"))
    }
}

