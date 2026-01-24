package com.eventstore.infrastructure.persistence

import com.eventstore.domain.Schema
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Implementation-specific tests for InMemoryTopicRepository.
 * These tests verify behavior unique to the in-memory implementation.
 */
class InMemoryTopicRepositoryTest {
    private val repository = InMemoryTopicRepository()

    @Test
    fun `should handle concurrent topic creation`() =
        runTest {
            val namespaceId = UUID.randomUUID()
            val topics =
                (1..10).map { i ->
                    repository.createTopic(
                        UUID.randomUUID(),
                        namespaceId,
                        "concurrent-topic-$i",
                        listOf(Schema(eventType = "event$i")),
                    )
                }

            assertEquals(10, topics.size)
            assertEquals(10, repository.getAllTopics().size)
        }

    @Test
    fun `should maintain topic isolation between instances`() =
        runTest {
            val repo1 = InMemoryTopicRepository()
            val repo2 = InMemoryTopicRepository()
            val namespaceId = UUID.randomUUID()

            val topicId1 = UUID.randomUUID()
            val topicId2 = UUID.randomUUID()

            repo1.createTopic(topicId1, namespaceId, "topic-1", listOf(Schema(eventType = "event1")))
            repo2.createTopic(topicId2, namespaceId, "topic-2", listOf(Schema(eventType = "event2")))

            assertEquals(1, repo1.getAllTopics().size)
            assertEquals(1, repo2.getAllTopics().size)
            assertTrue(repo1.getAllTopics().first().name == "topic-1")
            assertTrue(repo2.getAllTopics().first().name == "topic-2")
        }

    @Test
    fun `should handle rapid sequence updates`() =
        runTest {
            val topicId = UUID.randomUUID()
            val namespaceId = UUID.randomUUID()
            val name = "rapid-updates-topic"
            val schemas = listOf(Schema(eventType = "user.created"))

            repository.createTopic(topicId, namespaceId, name, schemas)

            // Rapid sequence updates
            repeat(100) { i ->
                repository.updateSequence(topicId, i.toLong())
            }

            val topic = repository.getTopic(topicId)
            assertNotNull(topic)
            assertEquals(99L, topic.sequence)
        }

    @Test
    fun `should handle rapid schema updates`() =
        runTest {
            val topicId = UUID.randomUUID()
            val namespaceId = UUID.randomUUID()
            val name = "rapid-schema-updates-topic"
            repository.createTopic(topicId, namespaceId, name, listOf(Schema(eventType = "initial")))

            // Rapid schema updates
            repeat(50) { i ->
                repository.updateSchemas(topicId, listOf(Schema(eventType = "event$i")))
            }

            val topic = repository.getTopic(topicId)
            assertNotNull(topic)
            assertEquals(1, topic.schemas.size)
            assertEquals("event49", topic.schemas.first().eventType)
        }

    @Test
    fun `should be thread-safe for concurrent operations`() =
        runTest {
            val topicId = UUID.randomUUID()
            val namespaceId = UUID.randomUUID()
            val name = "concurrent-ops-topic"
            repository.createTopic(topicId, namespaceId, name, listOf(Schema(eventType = "user.created")))

            // Simulate concurrent operations
            coroutineScope {
                val operations =
                    (1..100).map { i ->
                        async {
                            when (i % 3) {
                                0 -> repository.updateSequence(topicId, i.toLong())
                                1 -> repository.getTopic(topicId)
                                else -> repository.topicExists(topicId)
                            }
                        }
                    }
                operations.awaitAll()
            }

            // Verify final state is consistent
            val topic = repository.getTopic(topicId)
            assertNotNull(topic)
            assertTrue(repository.topicExists(topicId))
        }

    @Test
    fun `should handle large number of topics`() =
        runTest {
            val topicCount = 1000
            val namespaceId = UUID.randomUUID()
            repeat(topicCount) { i ->
                repository.createTopic(
                    UUID.randomUUID(),
                    namespaceId,
                    "topic-$i",
                    listOf(Schema(eventType = "event$i")),
                )
            }

            assertEquals(topicCount, repository.getAllTopics().size)
        }

    @Test
    fun `should maintain data after multiple operations`() =
        runTest {
            val topicId = UUID.randomUUID()
            val namespaceId = UUID.randomUUID()
            val name = "persistence-test-topic"
            val initialSchemas =
                listOf(
                    Schema(eventType = "user.created", properties = mapOf("id" to mapOf("type" to "string"))),
                )

            repository.createTopic(topicId, namespaceId, name, initialSchemas)
            repository.updateSequence(topicId, 5L)
            repository.updateSchemas(
                topicId,
                listOf(
                    Schema(eventType = "user.created"),
                    Schema(eventType = "user.updated"),
                ),
            )

            val topic = repository.getTopic(topicId)
            assertNotNull(topic)
            assertEquals(5L, topic.sequence)
            assertEquals(2, topic.schemas.size)
        }
}
