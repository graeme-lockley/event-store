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
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GetTopicsServiceTest {
    private lateinit var application: Application

    @BeforeEach
    fun setup() = runTest {
        application = createApplication()
        // Create tenant and namespace
        val tenant = application.createTenant("default")
        val tenantId = tenant.tenantId
        application.createNamespace(tenantId, "default")
    }

    @Test
    fun `should get all topics in namespace`() = runTest {
        val topic1 = "user-events"
        val topic2 = "order-events"

        application.createTopic(topic1, listOf(Schema(eventType = "user.created")))
        application.createTopic(topic2, listOf(Schema(eventType = "order.created")))

        val result = application.listTopics()

        assertTrue(result.size >= 2)
        assertTrue(result.any { it.name == topic1 })
        assertTrue(result.any { it.name == topic2 })
    }

    @Test
    fun `should get single topic by name`() = runTest {
        val topicName = "user-events"
        val schemas = listOf(Schema(eventType = "user.created"))

        application.createTopic(topicName, schemas)

        val result = application.getTopic(topicName)

        assertEquals(topicName, result.name)
        assertEquals(schemas, result.schemas)
    }

    @Test
    fun `should throw exception when topic not found`() = runTest {
        val topicName = "unknown-topic"

        assertThrows<TopicNotFoundException> {
            application.getTopic(topicName)
        }
    }

    @Test
    fun `should get topics scoped by tenant and namespace`() = runTest {
        val tenant1 = "acme"
        val namespace1 = "production"
        val tenant2 = "corp"
        val namespace2 = "staging"
        val topicName = "events"

        val t1 = application.createTenant(tenant1)
        application.createNamespace(t1.tenantId, namespace1)
        val t2 = application.createTenant(tenant2)
        application.createNamespace(t2.tenantId, namespace2)

        application.createTopic(topicName, listOf(Schema(eventType = "event.created")), tenant1, namespace1)
        application.createTopic(topicName, listOf(Schema(eventType = "event.created")), tenant2, namespace2)

        val tenant1Topics = application.listTopics(tenant1, namespace1)
        val tenant2Topics = application.listTopics(tenant2, namespace2)
        val defaultTopics = application.listTopics()

        assertTrue(tenant1Topics.any { it.name == topicName && it.tenantName == tenant1 })
        assertTrue(tenant2Topics.any { it.name == topicName && it.tenantName == tenant2 })
        assertTrue(!defaultTopics.any { it.name == topicName })
    }

    @Test
    fun `should get topic from specific tenant and namespace`() = runTest {
        val tenantName = "acme"
        val namespaceName = "production"
        val topicName = "order-events"

        val tenant = application.createTenant(tenantName)
        application.createNamespace(tenant.tenantId, namespaceName)
        application.createTopic(topicName, listOf(Schema(eventType = "order.created")), tenantName, namespaceName)

        val result = application.getTopic(topicName, tenantName, namespaceName)

        assertEquals(topicName, result.name)
        assertEquals(tenantName, result.tenantName)
        assertEquals(namespaceName, result.namespaceName)
    }
}
