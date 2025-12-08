package com.eventstore.domain.services

import com.eventstore.domain.Schema
import com.eventstore.domain.Topic
import com.eventstore.domain.exceptions.TopicAlreadyExistsException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CreateTopicServiceTest {
    val topicName = "user-events"

    private lateinit var helper: PopulateEventStoreState
    private lateinit var service: CreateTopicService

    @BeforeEach
    fun setup() = runBlocking {
        helper = createEventStore(topicName)
        service = CreateTopicService(helper.topicRepository, helper.schemaValidator)
    }

    @Test
    fun `should create topic successfully`() = runTest {
        val name = "new-${topicName}"
        val schemas = listOf(
            Schema(eventType = "user.created", properties = mapOf("id" to "string"))
        )

        val result = service.execute(name, schemas)

        val topic = Topic(name, 0L, schemas)
        assertEquals(topic, result)
        assertEquals(topic, helper.findTopic(name))
    }

    @Test
    fun `should throw exception when topic already exists`() = runTest {
        assertNotNull(helper.findTopic(topicName))
        assertThrows<TopicAlreadyExistsException> {
            service.execute(topicName, listOf(Schema(eventType = "user.created")))
        }
    }

    @Test
    fun `should handle multiple schemas`() = runTest {
        val name = "new-${topicName}"
        val schemas = listOf(
            Schema(eventType = "user.created"),
            Schema(eventType = "user.updated")
        )

        val topic = service.execute(name, schemas)

        assertEquals(Topic(name, 0L, schemas), topic)
        assertEquals(topic, helper.findTopic(name))

        assertTrue(helper.hasSchema(name, "user.created"))
        assertTrue(helper.hasSchema(name, "user.updated"))
    }

    @Test
    fun `should throw exception when duplicate event types in schemas`() = runTest {
        val name = "new-${topicName}"
        val schemas = listOf(
            Schema(eventType = "user.created", properties = mapOf("id" to "string")),
            Schema(eventType = "user.created", properties = mapOf("id" to "string"))
        )

        assertThrows<IllegalArgumentException> {
            service.execute(name, schemas)
        }
    }
}

