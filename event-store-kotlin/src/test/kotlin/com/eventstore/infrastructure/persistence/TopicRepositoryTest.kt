package com.eventstore.infrastructure.persistence

import com.eventstore.domain.Schema
import com.eventstore.domain.Topic
import com.eventstore.domain.exceptions.TopicAlreadyExistsException
import com.eventstore.domain.exceptions.TopicNotFoundException
import com.eventstore.domain.ports.outbound.TopicRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Parameterized tests for TopicRepository implementations.
 * These tests verify common behavior that should be consistent across all implementations.
 */
class TopicRepositoryTest {

    @TempDir
    lateinit var sharedTempDir: Path

    @TestFactory
    fun `test repository implementations`(): List<DynamicTest> {
        data class RepoWithCleanup(val repository: TopicRepository, val cleanup: (() -> Unit)?)

        val implementations = listOf(
            "InMemoryTopicRepository" to {
                RepoWithCleanup(InMemoryTopicRepository(), null)
            },
            "FileSystemTopicRepository" to {
                // Create a unique subdirectory for each test to avoid conflicts
                val tempDir = Files.createTempDirectory(sharedTempDir, "topic-repo-test")
                val objectMapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
                val repo = FileSystemTopicRepository(tempDir, objectMapper)
                RepoWithCleanup(repo) { cleanupDirectory(tempDir) }
            }
        )

        return implementations.flatMap { (name, factory) ->
            listOf(
                DynamicTest.dynamicTest("$name: should create topic successfully") {
                    val repoWithCleanup = factory()
                    try {
                        runTest { testCreateTopic(repoWithCleanup.repository) }
                    } finally {
                        repoWithCleanup.cleanup?.invoke()
                    }
                },
                DynamicTest.dynamicTest("$name: should throw exception when creating duplicate topic") {
                    val repoWithCleanup = factory()
                    try {
                        runTest { testCreateDuplicateTopic(repoWithCleanup.repository) }
                    } finally {
                        repoWithCleanup.cleanup?.invoke()
                    }
                },
                DynamicTest.dynamicTest("$name: should get topic by name") {
                    val repoWithCleanup = factory()
                    try {
                        runTest { testGetTopic(repoWithCleanup.repository) }
                    } finally {
                        repoWithCleanup.cleanup?.invoke()
                    }
                },
                DynamicTest.dynamicTest("$name: should return null when topic not found") {
                    val repoWithCleanup = factory()
                    try {
                        runTest { testGetTopicNotFound(repoWithCleanup.repository) }
                    } finally {
                        repoWithCleanup.cleanup?.invoke()
                    }
                },
                DynamicTest.dynamicTest("$name: should check if topic exists") {
                    val repoWithCleanup = factory()
                    try {
                        runTest { testTopicExists(repoWithCleanup.repository) }
                    } finally {
                        repoWithCleanup.cleanup?.invoke()
                    }
                },
                DynamicTest.dynamicTest("$name: should update topic sequence") {
                    val repoWithCleanup = factory()
                    try {
                        runTest { testUpdateSequence(repoWithCleanup.repository) }
                    } finally {
                        repoWithCleanup.cleanup?.invoke()
                    }
                },
                DynamicTest.dynamicTest("$name: should throw exception when updating sequence for non-existent topic") {
                    val repoWithCleanup = factory()
                    try {
                        runTest { testUpdateSequenceNotFound(repoWithCleanup.repository) }
                    } finally {
                        repoWithCleanup.cleanup?.invoke()
                    }
                },
                DynamicTest.dynamicTest("$name: should update topic schemas") {
                    val repoWithCleanup = factory()
                    try {
                        runTest { testUpdateSchemas(repoWithCleanup.repository) }
                    } finally {
                        repoWithCleanup.cleanup?.invoke()
                    }
                },
                DynamicTest.dynamicTest("$name: should throw exception when updating schemas for non-existent topic") {
                    val repoWithCleanup = factory()
                    try {
                        runTest { testUpdateSchemasNotFound(repoWithCleanup.repository) }
                    } finally {
                        repoWithCleanup.cleanup?.invoke()
                    }
                },
                DynamicTest.dynamicTest("$name: should get all topics") {
                    val repoWithCleanup = factory()
                    try {
                        runTest { testGetAllTopics(repoWithCleanup.repository) }
                    } finally {
                        repoWithCleanup.cleanup?.invoke()
                    }
                },
                DynamicTest.dynamicTest("$name: should return empty list when no topics exist") {
                    val repoWithCleanup = factory()
                    try {
                        runTest { testGetAllTopicsEmpty(repoWithCleanup.repository) }
                    } finally {
                        repoWithCleanup.cleanup?.invoke()
                    }
                },
                DynamicTest.dynamicTest("$name: should handle multiple topics") {
                    val repoWithCleanup = factory()
                    try {
                        runTest { testMultipleTopics(repoWithCleanup.repository) }
                    } finally {
                        repoWithCleanup.cleanup?.invoke()
                    }
                },
                DynamicTest.dynamicTest("$name: should handle topic with empty schemas") {
                    val repoWithCleanup = factory()
                    try {
                        runTest { testTopicWithEmptySchemas(repoWithCleanup.repository) }
                    } finally {
                        repoWithCleanup.cleanup?.invoke()
                    }
                },
                DynamicTest.dynamicTest("$name: should handle topic with multiple schemas") {
                    val repoWithCleanup = factory()
                    try {
                        runTest { testTopicWithMultipleSchemas(repoWithCleanup.repository) }
                    } finally {
                        repoWithCleanup.cleanup?.invoke()
                    }
                },
                DynamicTest.dynamicTest("$name: should handle sequence updates correctly") {
                    val repoWithCleanup = factory()
                    try {
                        runTest { testSequenceUpdates(repoWithCleanup.repository) }
                    } finally {
                        repoWithCleanup.cleanup?.invoke()
                    }
                },
                DynamicTest.dynamicTest("$name: should handle schema updates correctly") {
                    val repoWithCleanup = factory()
                    try {
                        runTest { testSchemaUpdates(repoWithCleanup.repository) }
                    } finally {
                        repoWithCleanup.cleanup?.invoke()
                    }
                }
            )
        }
    }

    private suspend fun testCreateTopic(repository: TopicRepository) {
        val name = "test-topic"
        val schemas = listOf(
            Schema(eventType = "user.created", properties = mapOf("id" to mapOf("type" to "string")))
        )

        val topic = repository.createTopic(name, schemas)

        assertEquals(name, topic.name)
        assertEquals(0L, topic.sequence)
        assertEquals(schemas, topic.schemas)
        assertTrue(repository.topicExists(name))
    }

    private suspend fun testCreateDuplicateTopic(repository: TopicRepository) {
        val name = "duplicate-topic"
        val schemas = listOf(Schema(eventType = "user.created"))

        repository.createTopic(name, schemas)

        org.junit.jupiter.api.assertThrows<TopicAlreadyExistsException> {
            runTest {
                repository.createTopic(name, schemas)
            }
        }
    }

    private suspend fun testGetTopic(repository: TopicRepository) {
        val name = "get-topic"
        val schemas = listOf(
            Schema(eventType = "user.created", properties = mapOf("id" to mapOf("type" to "string")))
        )

        val created = repository.createTopic(name, schemas)
        val retrieved = repository.getTopic(name)

        assertNotNull(retrieved)
        assertEquals(created, retrieved)
    }

    private suspend fun testGetTopicNotFound(repository: TopicRepository) {
        val retrieved = repository.getTopic("non-existent-topic")
        assertNull(retrieved)
    }

    private suspend fun testTopicExists(repository: TopicRepository) {
        val name = "exists-topic"
        val schemas = listOf(Schema(eventType = "user.created"))

        assertFalse(repository.topicExists(name))
        repository.createTopic(name, schemas)
        assertTrue(repository.topicExists(name))
    }

    private suspend fun testUpdateSequence(repository: TopicRepository) {
        val name = "sequence-topic"
        val schemas = listOf(Schema(eventType = "user.created"))

        repository.createTopic(name, schemas)
        repository.updateSequence(name, 42L)

        val topic = repository.getTopic(name)
        assertNotNull(topic)
        assertEquals(42L, topic.sequence)
    }

    private suspend fun testUpdateSequenceNotFound(repository: TopicRepository) {
        org.junit.jupiter.api.assertThrows<TopicNotFoundException> {
            runTest {
                repository.updateSequence("non-existent-topic", 1L)
            }
        }
    }

    private suspend fun testUpdateSchemas(repository: TopicRepository) {
        val name = "schemas-topic"
        val initialSchemas = listOf(Schema(eventType = "user.created"))
        val updatedSchemas = listOf(
            Schema(eventType = "user.created"),
            Schema(eventType = "user.updated")
        )

        repository.createTopic(name, initialSchemas)
        val updated = repository.updateSchemas(name, updatedSchemas)

        assertEquals(updatedSchemas, updated.schemas)
        val topic = repository.getTopic(name)
        assertNotNull(topic)
        assertEquals(updatedSchemas, topic.schemas)
    }

    private suspend fun testUpdateSchemasNotFound(repository: TopicRepository) {
        org.junit.jupiter.api.assertThrows<TopicNotFoundException> {
            runTest {
                repository.updateSchemas("non-existent-topic", listOf(Schema(eventType = "user.created")))
            }
        }
    }

    private suspend fun testGetAllTopics(repository: TopicRepository) {
        val topic1 = repository.createTopic("topic-1", listOf(Schema(eventType = "event1")))
        val topic2 = repository.createTopic("topic-2", listOf(Schema(eventType = "event2")))
        val topic3 = repository.createTopic("topic-3", listOf(Schema(eventType = "event3")))

        val allTopics = repository.getAllTopics()

        assertEquals(3, allTopics.size)
        assertTrue(allTopics.contains(topic1))
        assertTrue(allTopics.contains(topic2))
        assertTrue(allTopics.contains(topic3))
    }

    private suspend fun testGetAllTopicsEmpty(repository: TopicRepository) {
        val allTopics = repository.getAllTopics()
        assertTrue(allTopics.isEmpty())
    }

    private suspend fun testMultipleTopics(repository: TopicRepository) {
        val topics = (1..5).map { i ->
            repository.createTopic("topic-$i", listOf(Schema(eventType = "event$i")))
        }

        val allTopics = repository.getAllTopics()
        assertEquals(5, allTopics.size)

        topics.forEach { topic ->
            val retrieved = repository.getTopic(topic.name)
            assertNotNull(retrieved)
            assertEquals(topic, retrieved)
        }
    }

    private suspend fun testTopicWithEmptySchemas(repository: TopicRepository) {
        val name = "empty-schemas-topic"
        val topic = repository.createTopic(name, emptyList())

        assertEquals(emptyList<Schema>(), topic.schemas)
        val retrieved = repository.getTopic(name)
        assertNotNull(retrieved)
        assertEquals(emptyList<Schema>(), retrieved.schemas)
    }

    private suspend fun testTopicWithMultipleSchemas(repository: TopicRepository) {
        val name = "multiple-schemas-topic"
        val schemas = listOf(
            Schema(eventType = "user.created", properties = mapOf("id" to mapOf("type" to "string"))),
            Schema(eventType = "user.updated", properties = mapOf("id" to mapOf("type" to "string"))),
            Schema(eventType = "user.deleted", properties = mapOf("id" to mapOf("type" to "string")))
        )

        val topic = repository.createTopic(name, schemas)
        assertEquals(3, topic.schemas.size)
        assertEquals(schemas, topic.schemas)

        val retrieved = repository.getTopic(name)
        assertNotNull(retrieved)
        assertEquals(schemas, retrieved.schemas)
    }

    private suspend fun testSequenceUpdates(repository: TopicRepository) {
        val name = "sequence-updates-topic"
        val schemas = listOf(Schema(eventType = "user.created"))

        repository.createTopic(name, schemas)

        // Update sequence multiple times
        repository.updateSequence(name, 10L)
        var topic = repository.getTopic(name)
        assertNotNull(topic)
        assertEquals(10L, topic.sequence)

        repository.updateSequence(name, 100L)
        topic = repository.getTopic(name)
        assertNotNull(topic)
        assertEquals(100L, topic.sequence)

        repository.updateSequence(name, 0L)
        topic = repository.getTopic(name)
        assertNotNull(topic)
        assertEquals(0L, topic.sequence)
    }

    private suspend fun testSchemaUpdates(repository: TopicRepository) {
        val name = "schema-updates-topic"
        val initialSchemas = listOf(Schema(eventType = "user.created"))

        repository.createTopic(name, initialSchemas)

        // Update schemas multiple times
        val schemas1 = listOf(
            Schema(eventType = "user.created"),
            Schema(eventType = "user.updated")
        )
        repository.updateSchemas(name, schemas1)
        var topic = repository.getTopic(name)
        assertNotNull(topic)
        assertEquals(schemas1, topic.schemas)

        val schemas2 = listOf(Schema(eventType = "user.deleted"))
        repository.updateSchemas(name, schemas2)
        topic = repository.getTopic(name)
        assertNotNull(topic)
        assertEquals(schemas2, topic.schemas)
    }

    /**
     * Recursively delete a directory and all its contents.
     */
    private fun cleanupDirectory(dir: Path) {
        try {
            if (Files.exists(dir)) {
                Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .forEach { Files.deleteIfExists(it) }
            }
        } catch (e: Exception) {
            // Ignore cleanup errors - the temp directory will be cleaned up by the OS eventually
            // or by JUnit's @TempDir mechanism
        }
    }
}

