package com.eventstore.infrastructure.persistence

import com.eventstore.domain.Schema
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Implementation-specific tests for InMemoryTopicRepository.
 * These tests verify behavior unique to the in-memory implementation.
 */
class InMemoryTopicRepositoryTest {

    private val repository = InMemoryTopicRepository()

    @Test
    fun `should handle concurrent topic creation`() = runTest {
        val topics = (1..10).map { i ->
            repository.createTopic("concurrent-topic-$i", listOf(Schema(eventType = "event$i")))
        }

        assertEquals(10, topics.size)
        assertEquals(10, repository.getAllTopics().size)
    }

    @Test
    fun `should maintain topic isolation between instances`() = runTest {
        val repo1 = InMemoryTopicRepository()
        val repo2 = InMemoryTopicRepository()

        repo1.createTopic("topic-1", listOf(Schema(eventType = "event1")))
        repo2.createTopic("topic-2", listOf(Schema(eventType = "event2")))

        assertEquals(1, repo1.getAllTopics().size)
        assertEquals(1, repo2.getAllTopics().size)
        assert(repo1.getAllTopics().first().name == "topic-1")
        assert(repo2.getAllTopics().first().name == "topic-2")
    }

    @Test
    fun `should handle rapid sequence updates`() = runTest {
        val name = "rapid-updates-topic"
        val schemas = listOf(Schema(eventType = "user.created"))

        repository.createTopic(name, schemas)

        // Rapid sequence updates
        repeat(100) { i ->
            repository.updateSequence(name, i.toLong())
        }

        val topic = repository.getTopic(name)
        assert(topic != null)
        assertEquals(99L, topic!!.sequence)
    }

    @Test
    fun `should handle rapid schema updates`() = runTest {
        val name = "rapid-schema-updates-topic"
        repository.createTopic(name, listOf(Schema(eventType = "initial")))

        // Rapid schema updates
        repeat(50) { i ->
            repository.updateSchemas(name, listOf(Schema(eventType = "event$i")))
        }

        val topic = repository.getTopic(name)
        assert(topic != null)
        assertEquals(1, topic!!.schemas.size)
        assertEquals("event49", topic.schemas.first().eventType)
    }

    @Test
    fun `should be thread-safe for concurrent operations`() = runTest {
        val name = "concurrent-ops-topic"
        repository.createTopic(name, listOf(Schema(eventType = "user.created")))

        // Simulate concurrent operations
        coroutineScope {
            val operations = (1..100).map { i ->
                async {
                    when (i % 3) {
                        0 -> repository.updateSequence(name, i.toLong())
                        1 -> repository.getTopic(name)
                        else -> repository.topicExists(name)
                    }
                }
            }
            operations.awaitAll()
        }

        // Verify final state is consistent
        val topic = repository.getTopic(name)
        assert(topic != null)
        assert(repository.topicExists(name))
    }

    @Test
    fun `should handle large number of topics`() = runTest {
        val topicCount = 1000
        repeat(topicCount) { i ->
            repository.createTopic("topic-$i", listOf(Schema(eventType = "event$i")))
        }

        assertEquals(topicCount, repository.getAllTopics().size)
    }

    @Test
    fun `should maintain data after multiple operations`() = runTest {
        val name = "persistence-test-topic"
        val initialSchemas = listOf(
            Schema(eventType = "user.created", properties = mapOf("id" to mapOf("type" to "string")))
        )

        repository.createTopic(name, initialSchemas)
        repository.updateSequence(name, 5L)
        repository.updateSchemas(name, listOf(
            Schema(eventType = "user.created"),
            Schema(eventType = "user.updated")
        ))

        val topic = repository.getTopic(name)
        assert(topic != null)
        assertEquals(5L, topic!!.sequence)
        assertEquals(2, topic.schemas.size)
    }
}

