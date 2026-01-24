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
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GetTopicsServiceTest {
    private lateinit var application: Application
    private lateinit var namespaceId: UUID

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
    fun `should get all topics in namespace`() =
        runTest {
            val topic1 = "user-events"
            val topic2 = "order-events"

            val topic1Obj = application.createTopic(topic1, listOf(Schema(eventType = "user.created")), namespaceId)
            val topic2Obj = application.createTopic(topic2, listOf(Schema(eventType = "order.created")), namespaceId)

            val result = application.listTopics(namespaceId)

            assertTrue(result.size >= 2)
            assertTrue(result.any { it.topicId == topic1Obj.topicId })
            assertTrue(result.any { it.topicId == topic2Obj.topicId })
        }

    @Test
    fun `should get single topic by topicId`() =
        runTest {
            val topicName = "user-events"
            val schemas = listOf(Schema(eventType = "user.created"))

            val topic = application.createTopic(topicName, schemas, namespaceId)

            val result = application.getTopic(topic.topicId)

            assertEquals(topicName, result.name)
            assertEquals(topic.topicId, result.topicId)
            assertEquals(namespaceId, result.namespaceId)
            assertEquals(schemas, result.schemas)
        }

    @Test
    fun `should throw exception when topic not found`() =
        runTest {
            val nonExistentTopicId = UUID.randomUUID()

            assertThrows<TopicNotFoundException> {
                application.getTopic(nonExistentTopicId)
            }
        }

    @Test
    fun `should get topics scoped by namespace`() =
        runTest {
            val tenant1 = "acme"
            val namespace1Name = "production"
            val tenant2 = "corp"
            val namespace2Name = "staging"
            val topicName = "events"

            val t1 = application.createTenant(tenant1)
            val namespace1 = application.createNamespace(t1.tenantId, namespace1Name)
            val t2 = application.createTenant(tenant2)
            val namespace2 = application.createNamespace(t2.tenantId, namespace2Name)

            val topic1 = application.createTopic(topicName, listOf(Schema(eventType = "event.created")), namespace1.namespaceId)
            val topic2 = application.createTopic(topicName, listOf(Schema(eventType = "event.created")), namespace2.namespaceId)

            val namespace1Topics = application.listTopics(namespace1.namespaceId)
            val namespace2Topics = application.listTopics(namespace2.namespaceId)
            val allTopics = application.listTopics()

            assertTrue(namespace1Topics.any { it.topicId == topic1.topicId && it.namespaceId == namespace1.namespaceId })
            assertTrue(namespace2Topics.any { it.topicId == topic2.topicId && it.namespaceId == namespace2.namespaceId })
            assertTrue(allTopics.any { it.topicId == topic1.topicId })
            assertTrue(allTopics.any { it.topicId == topic2.topicId })
        }

    @Test
    fun `should get topic from specific namespace`() =
        runTest {
            val tenantName = "acme"
            val namespaceName = "production"
            val topicName = "order-events"

            val tenant = application.createTenant(tenantName)
            val namespace = application.createNamespace(tenant.tenantId, namespaceName)
            val topic = application.createTopic(topicName, listOf(Schema(eventType = "order.created")), namespace.namespaceId)

            val result = application.getTopic(topic.topicId)

            assertEquals(topicName, result.name)
            assertEquals(namespace.namespaceId, result.namespaceId)
            assertEquals(topic.topicId, result.topicId)
        }
}
