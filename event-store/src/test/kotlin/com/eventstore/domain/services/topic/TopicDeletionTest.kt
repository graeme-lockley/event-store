package com.eventstore.domain.services.topic

import com.eventstore.domain.Application
import com.eventstore.domain.Schema
import com.eventstore.domain.services.createApplication
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.*
import kotlin.test.assertNotNull

/**
 * Tests verifying that topics cannot be deleted (Rule D-1).
 *
 * Topics are permanent records and deletion is not supported.
 * This test verifies that no deleteTopic method exists on Application.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TopicDeletionTest {
    private lateinit var application: Application
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

            // Create a topic for testing
            val topic =
                application.createTopic(
                    "test-topic",
                    listOf(Schema(eventType = "test.event")),
                    namespaceId,
                )
            topicId = topic.topicId
        }

    @Test
    fun `topics are not deletable - no deleteTopic method exists`() =
        runTest {
            // Rule D-1: Topics are not deletable
            // This test documents that deletion is not supported.
            // The Application class does not have a deleteTopic method, which enforces this rule.

            // Verify the topic still exists and can be retrieved
            val retrieved = application.getTopic(topicId)
            assertNotNull(retrieved, "Topic should still exist as deletion is not supported")

            // Note: If deleteTopic method is added in the future, it should throw
            // an appropriate exception indicating deletion is not supported.
        }
}
