package com.eventstore.domain.services

import com.eventstore.domain.Schema
import com.eventstore.domain.Topic
import com.eventstore.domain.exceptions.TopicNotFoundException
import com.eventstore.domain.ports.outbound.SchemaValidator
import com.eventstore.domain.ports.outbound.TopicRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UpdateTopicSchemasServiceTest {
    val topicName = "user-events"

    private lateinit var helper: PopulateEventStoreState
    private lateinit var service: UpdateTopicSchemasService

    @BeforeEach
    fun setup() = runBlocking {
        helper = createEventStore(topicName)
        service = UpdateTopicSchemasService(helper.topicRepository, helper.schemaValidator)
    }

    @Test
    fun `should successfully add new schemas`() = runTest {
        val currentTopic = helper.findTopic(topicName)!!
        val newSchemas = currentTopic.schemas + Schema(eventType = "user.deleted", properties = mapOf("id" to "string"))

        val result = service.execute(topicName, newSchemas)

        assertEquals(result.schemas, newSchemas)
        assertEquals(helper.findTopic(topicName)!!.schemas, newSchemas)
    }

    @Test
    fun `should successfully update existing schemas`() = runTest {
        val currentTopic = helper.findTopic(topicName)!!
        val newSchemas = currentTopic.schemas.filter { it.eventType != "user.created" } +
                Schema(eventType = "user.created", properties = mapOf("id" to "string", "email" to "string"))

        val result = service.execute(topicName, newSchemas)

        assertEquals(result.schemas, newSchemas)
        assertEquals(helper.findTopic(topicName)!!.schemas, newSchemas)
    }

    @Test
    fun `should throw an exception when there are duplicate event types in the schemas`() = runTest {
        val currentTopic = helper.findTopic(topicName)!!
        val newSchemas = currentTopic.schemas + currentTopic.schemas[0]

        assertThrows<IllegalArgumentException> {
            service.execute(topicName, newSchemas)
        }
    }

    @Test
    fun `should throw exception when topic does not exist`() = runTest {
        val topicName = "unknown-topic"

        assertNull(helper.findTopic(topicName))

        assertThrows<TopicNotFoundException> {
            service.execute(topicName, listOf())
        }
    }

    @Test
    fun `should throw exception when removing schemas`() = runTest {
        val currentTopic = helper.findTopic(topicName)!!
        val newSchemas = currentTopic.schemas.drop(1)

        assertThrows<IllegalArgumentException> {
            service.execute(topicName, newSchemas)
        }
    }
}

