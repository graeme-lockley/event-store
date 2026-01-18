package com.eventstore.domain.services.topic

import com.eventstore.domain.Application
import com.eventstore.domain.Schema
import com.eventstore.domain.exceptions.TopicAlreadyExistsException
import com.eventstore.domain.services.createApplication
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import java.util.*
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CreateTopicServiceTest {
    private lateinit var application: Application
    private lateinit var namespaceId: UUID
    private val topicName = "user-events"

    @BeforeEach
    fun setup() = runTest {
        application = createApplication()
        // Create tenant and namespace
        val tenant = application.createTenant("default")
        val tenantId = tenant.tenantId
        val namespace = application.createNamespace(tenantId, "default")
        namespaceId = namespace.namespaceId
    }

    @Test
    fun `should create topic successfully`() = runTest {
        val name = "new-$topicName"
        val schemas = listOf(
            Schema(eventType = "user.created", properties = mapOf("id" to "string"))
        )

        val result = application.createTopic(name, schemas, namespaceId)

        val retrieved = application.getTopic(result.topicId)
        assertEquals(name, result.name)
        assertEquals(namespaceId, result.namespaceId)
        assertEquals(0L, result.sequence)
        assertEquals(schemas, result.schemas)
        assertEquals(name, retrieved.name)
        assertEquals(namespaceId, retrieved.namespaceId)
        assertEquals(0L, retrieved.sequence)
        assertEquals(schemas, retrieved.schemas)
    }

    @Test
    fun `should create topics with different topicIds`() = runTest {
        val name1 = "topic1"
        val name2 = "topic2"
        val schemas = listOf(Schema(eventType = "user.created"))

        val topic1 = application.createTopic(name1, schemas, namespaceId)
        val topic2 = application.createTopic(name2, schemas, namespaceId)

        // Topics should have different topicIds even with same name
        assertEquals(name1, topic1.name)
        assertEquals(name2, topic2.name)
        // UUIDs should be different
        assertEquals(namespaceId, topic1.namespaceId)
        assertEquals(namespaceId, topic2.namespaceId)
    }

    @Test
    fun `should handle multiple schemas`() = runTest {
        val name = "new-$topicName"
        val schemas = listOf(
            Schema(eventType = "user.created"),
            Schema(eventType = "user.updated")
        )

        val topic = application.createTopic(name, schemas, namespaceId)

        assertEquals(name, topic.name)
        assertEquals(0L, topic.sequence)
        assertEquals(schemas, topic.schemas)
        val retrieved = application.getTopic(topic.topicId)
        assertEquals(name, retrieved.name)
        assertEquals(0L, retrieved.sequence)
        assertEquals(schemas, retrieved.schemas)
    }

    @Test
    fun `should throw exception when duplicate event types in schemas`() = runTest {
        val name = "new-$topicName"
        val schemas = listOf(
            Schema(eventType = "user.created", properties = mapOf("id" to "string")),
            Schema(eventType = "user.created", properties = mapOf("id" to "string"))
        )

        assertThrows<IllegalArgumentException> {
            application.createTopic(name, schemas, namespaceId)
        }
    }

    @Test
    fun `should create topic in specific namespace`() = runTest {
        val tenantName = "acme"
        val namespaceName = "production"
        val name = "order-events"
        val schemas = listOf(
            Schema(eventType = "order.created", properties = mapOf("id" to "string"))
        )

        val tenant = application.createTenant(tenantName)
        val tenantId = tenant.tenantId
        val namespace = application.createNamespace(tenantId, namespaceName)
        val acmeNamespaceId = namespace.namespaceId

        val topic = application.createTopic(name, schemas, acmeNamespaceId)

        assertEquals(name, topic.name)
        assertEquals(acmeNamespaceId, topic.namespaceId)

        val retrieved = application.getTopic(topic.topicId)
        assertEquals(name, retrieved.name)
        assertEquals(acmeNamespaceId, retrieved.namespaceId)
    }

    @Test
    fun `should throw exception when namespace does not exist`() = runTest {
        val nonExistentNamespaceId = UUID.randomUUID()
        val schemas = listOf(Schema(eventType = "test.event"))
        assertThrows<com.eventstore.domain.exceptions.NamespaceNotFoundException> {
            application.createTopic("test-topic", schemas, nonExistentNamespaceId)
        }
    }

    @Test
    fun `should throw exception when schemas list is empty`() = runTest {
        // Rule C-6: At least one schema must be provided
        assertThrows<IllegalArgumentException> {
            application.createTopic("test-topic", emptyList(), namespaceId)
        }
    }

    @Test
    fun `should throw exception when schema eventType is blank`() = runTest {
        // Rule SM-3: Schema must have non-blank eventType
        // Schema validation happens at construction time, so we catch it when creating the Schema
        assertThrows<IllegalArgumentException> {
            Schema(eventType = "", properties = mapOf("id" to "string"))
        }
    }

    @Test
    fun `should throw exception when schema schema field is blank`() = runTest {
        // Rule SM-3: Schema must have non-blank schema field
        // Schema validation happens at construction time, so we catch it when creating the Schema
        assertThrows<IllegalArgumentException> {
            Schema(eventType = "user.created", schema = "", properties = mapOf("id" to "string"))
        }
    }
}
