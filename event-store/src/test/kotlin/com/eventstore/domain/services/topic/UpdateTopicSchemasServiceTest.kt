package com.eventstore.domain.services.topic

import com.eventstore.domain.Application
import com.eventstore.domain.Schema
import com.eventstore.domain.exceptions.TopicNotFoundException
import com.eventstore.domain.services.createApplication
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UpdateTopicSchemasServiceTest {
    private lateinit var application: Application
    private val topicName = "user-events"

    @BeforeEach
    fun setup() = runTest {
        application = createApplication()
        // Create tenant and namespace
        application.createTenant("default")
        application.createNamespace("default", "default")
    }

    @Test
    fun `should successfully add new schemas`() = runTest {
        val initialSchemas = listOf(
            Schema(eventType = "user.created", properties = mapOf("id" to "string"))
        )
        application.createTopic(topicName, initialSchemas)

        val newSchemas = initialSchemas + Schema(eventType = "user.deleted", properties = mapOf("id" to "string"))

        val result = application.updateTopicSchemas(topicName, newSchemas)

        assertEquals(newSchemas.size, result.schemas.size)
        assertTrue(result.schemas.any { it.eventType == "user.created" })
        assertTrue(result.schemas.any { it.eventType == "user.deleted" })

        val retrieved = application.getTopic(topicName)
        assertEquals(newSchemas.size, retrieved.schemas.size)
    }

    @Test
    fun `should successfully update existing schemas`() = runTest {
        val initialSchemas = listOf(
            Schema(eventType = "user.created", properties = mapOf("id" to "string"))
        )
        application.createTopic(topicName, initialSchemas)

        val newSchemas = listOf(
            Schema(eventType = "user.created", properties = mapOf("id" to "string", "email" to "string"))
        )

        val result = application.updateTopicSchemas(topicName, newSchemas)

        assertEquals(newSchemas.size, result.schemas.size)
        val updatedSchema = result.schemas.find { it.eventType == "user.created" }
        assertNotNull(updatedSchema)
        assertTrue(updatedSchema.properties.containsKey("email"))

        val retrieved = application.getTopic(topicName)
        assertEquals(newSchemas.size, retrieved.schemas.size)
    }

    @Test
    fun `should throw an exception when there are duplicate event types in the schemas`() = runTest {
        val initialSchemas = listOf(
            Schema(eventType = "user.created", properties = mapOf("id" to "string"))
        )
        application.createTopic(topicName, initialSchemas)

        val newSchemas = initialSchemas + initialSchemas[0]

        assertThrows<IllegalArgumentException> {
            application.updateTopicSchemas(topicName, newSchemas)
        }
    }

    @Test
    fun `should throw exception when topic does not exist`() = runTest {
        val unknownTopic = "unknown-topic"

        assertThrows<TopicNotFoundException> {
            application.updateTopicSchemas(unknownTopic, listOf())
        }
    }

    @Test
    fun `should throw exception when removing schemas`() = runTest {
        val initialSchemas = listOf(
            Schema(eventType = "user.created", properties = mapOf("id" to "string")),
            Schema(eventType = "user.updated", properties = mapOf("id" to "string"))
        )
        application.createTopic(topicName, initialSchemas)

        val newSchemas = initialSchemas.drop(1)

        assertThrows<IllegalArgumentException> {
            application.updateTopicSchemas(topicName, newSchemas)
        }
    }

    @Test
    fun `should update schemas in specific tenant and namespace`() = runTest {
        val tenantName = "acme"
        val namespaceName = "production"
        val topicName = "order-events"
        val initialSchemas = listOf(
            Schema(eventType = "order.created", properties = mapOf("id" to "string"))
        )

        application.createTenant(tenantName)
        application.createNamespace(tenantName, namespaceName)
        application.createTopic(topicName, initialSchemas, tenantName, namespaceName)

        val newSchemas = initialSchemas + Schema(eventType = "order.cancelled", properties = mapOf("id" to "string"))

        val result = application.updateTopicSchemas(topicName, newSchemas, tenantName, namespaceName)

        assertEquals(newSchemas.size, result.schemas.size)
        assertTrue(result.schemas.any { it.eventType == "order.created" })
        assertTrue(result.schemas.any { it.eventType == "order.cancelled" })
    }
}
