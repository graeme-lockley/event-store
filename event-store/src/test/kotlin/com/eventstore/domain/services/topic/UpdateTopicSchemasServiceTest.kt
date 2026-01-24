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
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UpdateTopicSchemasServiceTest {
    private lateinit var application: Application
    private val topicName = "user-events"
    private lateinit var namespaceId: UUID
    private lateinit var topicId: UUID

    @BeforeEach
    fun setup() =
        runTest {
            application = createApplication()
            // Create tenant and namespace
            val tenant = application.createTenant("default")
            val tenantId = tenant.tenantId
            val namespace = application.createNamespace(tenantId, "default")
            namespaceId = namespace.namespaceId
        }

    @Test
    fun `should successfully add new schemas`() =
        runTest {
            val initialSchemas =
                listOf(
                    Schema(eventType = "user.created", properties = mapOf("id" to "string")),
                )
            val topic = application.createTopic(topicName, initialSchemas, namespaceId)
            topicId = topic.topicId

            val newSchemas = initialSchemas + Schema(eventType = "user.deleted", properties = mapOf("id" to "string"))

            val result = application.updateTopicSchemas(topicId, newSchemas)

            assertEquals(newSchemas.size, result.schemas.size)
            assertTrue(result.schemas.any { it.eventType == "user.created" })
            assertTrue(result.schemas.any { it.eventType == "user.deleted" })

            val retrieved = application.getTopic(topicId)
            assertEquals(newSchemas.size, retrieved.schemas.size)
        }

    @Test
    fun `should successfully update existing schemas`() =
        runTest {
            val initialSchemas =
                listOf(
                    Schema(eventType = "user.created", properties = mapOf("id" to "string")),
                )
            val topic = application.createTopic(topicName, initialSchemas, namespaceId)
            topicId = topic.topicId

            val newSchemas =
                listOf(
                    Schema(eventType = "user.created", properties = mapOf("id" to "string", "email" to "string")),
                )

            val result = application.updateTopicSchemas(topicId, newSchemas)

            assertEquals(newSchemas.size, result.schemas.size)
            val updatedSchema = result.schemas.find { it.eventType == "user.created" }
            assertNotNull(updatedSchema)
            assertTrue(updatedSchema.properties.containsKey("email"))

            val retrieved = application.getTopic(topicId)
            assertEquals(newSchemas.size, retrieved.schemas.size)
        }

    @Test
    fun `should throw an exception when there are duplicate event types in the schemas`() =
        runTest {
            val initialSchemas =
                listOf(
                    Schema(eventType = "user.created", properties = mapOf("id" to "string")),
                )
            val topic = application.createTopic(topicName, initialSchemas, namespaceId)
            topicId = topic.topicId

            val newSchemas = initialSchemas + initialSchemas[0]

            assertThrows<IllegalArgumentException> {
                application.updateTopicSchemas(topicId, newSchemas)
            }
        }

    @Test
    fun `should throw exception when topic does not exist`() =
        runTest {
            val nonExistentTopicId = UUID.randomUUID()

            assertThrows<TopicNotFoundException> {
                application.updateTopicSchemas(nonExistentTopicId, listOf())
            }
        }

    @Test
    fun `should throw exception when removing schemas`() =
        runTest {
            val initialSchemas =
                listOf(
                    Schema(eventType = "user.created", properties = mapOf("id" to "string")),
                    Schema(eventType = "user.updated", properties = mapOf("id" to "string")),
                )
            val topic = application.createTopic(topicName, initialSchemas, namespaceId)
            topicId = topic.topicId

            val newSchemas = initialSchemas.drop(1)

            assertThrows<IllegalArgumentException> {
                application.updateTopicSchemas(topicId, newSchemas)
            }
        }

    @Test
    fun `should update schemas in specific namespace`() =
        runTest {
            val tenantName = "acme"
            val namespaceName = "production"
            val topicName = "order-events"
            val initialSchemas =
                listOf(
                    Schema(eventType = "order.created", properties = mapOf("id" to "string")),
                )

            val tenant = application.createTenant(tenantName)
            val tenantId = tenant.tenantId
            val namespace = application.createNamespace(tenantId, namespaceName)
            val topic = application.createTopic(topicName, initialSchemas, namespace.namespaceId)

            val newSchemas = initialSchemas + Schema(eventType = "order.cancelled", properties = mapOf("id" to "string"))

            val result = application.updateTopicSchemas(topic.topicId, newSchemas)

            assertEquals(newSchemas.size, result.schemas.size)
            assertTrue(result.schemas.any { it.eventType == "order.created" })
            assertTrue(result.schemas.any { it.eventType == "order.cancelled" })
        }

    @Test
    fun `should throw exception when schema eventType is blank`() =
        runTest {
            // Rule SM-3: Schema must have non-blank eventType
            // Schema validation happens at construction time, so we catch it when creating the Schema
            val initialSchemas =
                listOf(
                    Schema(eventType = "user.created", properties = mapOf("id" to "string")),
                )
            val topic = application.createTopic(topicName, initialSchemas, namespaceId)
            topicId = topic.topicId

            assertThrows<IllegalArgumentException> {
                Schema(eventType = "", properties = mapOf("id" to "string"))
            }
        }

    @Test
    fun `should throw exception when schema schema field is blank`() =
        runTest {
            // Rule SM-3: Schema must have non-blank schema field
            // Schema validation happens at construction time, so we catch it when creating the Schema
            val initialSchemas =
                listOf(
                    Schema(eventType = "user.created", properties = mapOf("id" to "string")),
                )
            val topic = application.createTopic(topicName, initialSchemas, namespaceId)
            topicId = topic.topicId

            assertThrows<IllegalArgumentException> {
                Schema(eventType = "user.created", schema = "", properties = mapOf("id" to "string"))
            }
        }
}
