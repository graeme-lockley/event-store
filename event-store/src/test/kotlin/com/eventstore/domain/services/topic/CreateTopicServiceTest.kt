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
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CreateTopicServiceTest {
    private lateinit var application: Application
    private val topicName = "user-events"

    @BeforeEach
    fun setup() = runTest {
        application = createApplication()
        // Create tenant and namespace
        val tenant = application.createTenant("default")
        val tenantId = tenant.tenantId
        application.createNamespace(tenantId, "default")
    }

    @Test
    fun `should create topic successfully`() = runTest {
        val name = "new-$topicName"
        val schemas = listOf(
            Schema(eventType = "user.created", properties = mapOf("id" to "string"))
        )

        val result = application.createTopic(name, schemas)

        val retrieved = application.getTopic(name)
        assertEquals(name, result.name)
        assertEquals(0L, result.sequence)
        assertEquals(schemas, result.schemas)
        assertEquals(name, retrieved.name)
        assertEquals(0L, retrieved.sequence)
        assertEquals(schemas, retrieved.schemas)
    }

    @Test
    fun `should throw exception when topic already exists`() = runTest {
        application.createTopic(topicName, listOf(Schema(eventType = "user.created")))

        assertThrows<TopicAlreadyExistsException> {
            application.createTopic(topicName, listOf(Schema(eventType = "user.created")))
        }
    }

    @Test
    fun `should handle multiple schemas`() = runTest {
        val name = "new-$topicName"
        val schemas = listOf(
            Schema(eventType = "user.created"),
            Schema(eventType = "user.updated")
        )

        val topic = application.createTopic(name, schemas)

        assertEquals(name, topic.name)
        assertEquals(0L, topic.sequence)
        assertEquals(schemas, topic.schemas)
        val retrieved = application.getTopic(name)
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
            application.createTopic(name, schemas)
        }
    }

    @Test
    fun `should create topic in specific tenant and namespace`() = runTest {
        val tenantName = "acme"
        val namespaceName = "production"
        val name = "order-events"
        val schemas = listOf(
            Schema(eventType = "order.created", properties = mapOf("id" to "string"))
        )

        val tenant = application.createTenant(tenantName)
        val tenantId = tenant.tenantId
        application.createNamespace(tenantId, namespaceName)

        val topic = application.createTopic(name, schemas, tenantName, namespaceName)

        assertEquals(name, topic.name)
        assertEquals(tenantName, topic.tenantName)
        assertEquals(namespaceName, topic.namespaceName)

        val retrieved = application.getTopic(name, tenantName, namespaceName)
        assertEquals(name, retrieved.name)
        assertEquals(tenantName, retrieved.tenantName)
        assertEquals(namespaceName, retrieved.namespaceName)
    }

    @Test
    fun `should throw exception when tenant does not exist`() = runTest {
        assertThrows<com.eventstore.domain.exceptions.TenantNameNotFoundException> {
            application.createTopic("test-topic", emptyList(), "nonexistent-tenant", "default")
        }
    }

    @Test
    fun `should throw exception when namespace does not exist`() = runTest {
        application.createTenant("acme")

        assertThrows<com.eventstore.domain.exceptions.NamespaceNotFoundException> {
            application.createTopic("test-topic", emptyList(), "acme", "nonexistent-namespace")
        }
    }
}
