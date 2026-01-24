package com.eventstore.infrastructure.persistence

import com.eventstore.domain.Schema
import com.eventstore.domain.exceptions.TopicAlreadyExistsException
import com.eventstore.domain.exceptions.TopicNotFoundException
import com.eventstore.domain.ports.outbound.TopicRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.test.*

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

        val implementations =
            listOf(
                "InMemoryTopicRepository" to {
                    RepoWithCleanup(InMemoryTopicRepository(), null)
                },
                "FileSystemTopicRepository" to {
                    // Create a unique subdirectory for each test to avoid conflicts
                    val tempDir = Files.createTempDirectory(sharedTempDir, "topic-repo-test")
                    val objectMapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
                    val repo = FileSystemTopicRepository(tempDir, objectMapper)
                    RepoWithCleanup(repo) { cleanupDirectory(tempDir) }
                },
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
                DynamicTest.dynamicTest("$name: should get topic by topicId") {
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
                        testUpdateSchemasNotFound(repoWithCleanup.repository)
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
                DynamicTest.dynamicTest("$name: should handle multiple sequence updates") {
                    val repoWithCleanup = factory()
                    try {
                        runTest { testSequenceUpdates(repoWithCleanup.repository) }
                    } finally {
                        repoWithCleanup.cleanup?.invoke()
                    }
                },
                DynamicTest.dynamicTest("$name: should handle multiple schema updates") {
                    val repoWithCleanup = factory()
                    try {
                        runTest { testSchemaUpdates(repoWithCleanup.repository) }
                    } finally {
                        repoWithCleanup.cleanup?.invoke()
                    }
                },
            )
        }
    }

    private suspend fun testCreateTopic(repository: TopicRepository) {
        val topicId = UUID.randomUUID()
        val namespaceId = UUID.randomUUID()
        val name = "test-topic"
        val schemas =
            listOf(
                Schema(eventType = "user.created", properties = mapOf("id" to mapOf("type" to "string"))),
            )

        val topic =
            repository.createTopic(
                topicId = topicId,
                namespaceId = namespaceId,
                name = name,
                schemas = schemas,
            )

        assertEquals(topicId, topic.topicId)
        assertEquals(namespaceId, topic.namespaceId)
        assertEquals(name, topic.name)
        assertEquals(0L, topic.sequence)
        assertEquals(schemas, topic.schemas)
        assertTrue(repository.topicExists(topicId))
    }

    private suspend fun testCreateDuplicateTopic(repository: TopicRepository) {
        val topicId = UUID.randomUUID()
        val namespaceId = UUID.randomUUID()
        val name = "duplicate-topic"
        val schemas = listOf(Schema(eventType = "user.created"))

        repository.createTopic(topicId, namespaceId, name, schemas)

        // Creating a topic with the same topicId should fail
        org.junit.jupiter.api.assertThrows<TopicAlreadyExistsException> {
            runTest {
                repository.createTopic(topicId, namespaceId, "different-name", schemas)
            }
        }
    }

    private suspend fun testGetTopic(repository: TopicRepository) {
        val topicId = UUID.randomUUID()
        val namespaceId = UUID.randomUUID()
        val name = "get-topic"
        val schemas =
            listOf(
                Schema(eventType = "user.created", properties = mapOf("id" to mapOf("type" to "string"))),
            )

        val created = repository.createTopic(topicId, namespaceId, name, schemas)
        val retrieved = repository.getTopic(topicId)

        assertNotNull(retrieved)
        assertEquals(created, retrieved)
    }

    private suspend fun testGetTopicNotFound(repository: TopicRepository) {
        val nonExistentTopicId = UUID.randomUUID()
        val retrieved = repository.getTopic(nonExistentTopicId)
        assertNull(retrieved)
    }

    private suspend fun testTopicExists(repository: TopicRepository) {
        val topicId = UUID.randomUUID()
        val namespaceId = UUID.randomUUID()
        val name = "exists-topic"
        val schemas = listOf(Schema(eventType = "user.created"))

        assertFalse(repository.topicExists(topicId))
        repository.createTopic(topicId, namespaceId, name, schemas)
        assertTrue(repository.topicExists(topicId))
    }

    private suspend fun testUpdateSequence(repository: TopicRepository) {
        val topicId = UUID.randomUUID()
        val namespaceId = UUID.randomUUID()
        val name = "sequence-topic"
        val schemas = listOf(Schema(eventType = "user.created"))

        repository.createTopic(topicId, namespaceId, name, schemas)
        repository.updateSequence(topicId, 42L)

        val topic = repository.getTopic(topicId)
        assertNotNull(topic)
        assertEquals(42L, topic.sequence)
    }

    private fun testUpdateSequenceNotFound(repository: TopicRepository) {
        val nonExistentTopicId = UUID.randomUUID()
        org.junit.jupiter.api.assertThrows<TopicNotFoundException> {
            runTest {
                repository.updateSequence(nonExistentTopicId, 1L)
            }
        }
    }

    private suspend fun testUpdateSchemas(repository: TopicRepository) {
        val topicId = UUID.randomUUID()
        val namespaceId = UUID.randomUUID()
        val name = "schemas-topic"
        val initialSchemas = listOf(Schema(eventType = "user.created"))
        val updatedSchemas =
            listOf(
                Schema(eventType = "user.created"),
                Schema(eventType = "user.updated"),
            )

        repository.createTopic(topicId, namespaceId, name, initialSchemas)
        val updated = repository.updateSchemas(topicId, updatedSchemas)

        assertEquals(updatedSchemas, updated.schemas)
        val topic = repository.getTopic(topicId)
        assertNotNull(topic)
        assertEquals(updatedSchemas, topic.schemas)
    }

    private fun testUpdateSchemasNotFound(repository: TopicRepository) {
        val nonExistentTopicId = UUID.randomUUID()
        org.junit.jupiter.api.assertThrows<TopicNotFoundException> {
            runTest {
                repository.updateSchemas(nonExistentTopicId, listOf(Schema(eventType = "user.created")))
            }
        }
    }

    private suspend fun testGetAllTopics(repository: TopicRepository) {
        val namespaceId = UUID.randomUUID()
        val topic1 =
            repository.createTopic(
                UUID.randomUUID(),
                namespaceId,
                "topic-1",
                listOf(Schema(eventType = "event1")),
            )
        val topic2 =
            repository.createTopic(
                UUID.randomUUID(),
                namespaceId,
                "topic-2",
                listOf(Schema(eventType = "event2")),
            )
        val topic3 =
            repository.createTopic(
                UUID.randomUUID(),
                namespaceId,
                "topic-3",
                listOf(Schema(eventType = "event3")),
            )

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
        val namespaceId = UUID.randomUUID()
        val topics =
            (1..5).map { i ->
                repository.createTopic(
                    UUID.randomUUID(),
                    namespaceId,
                    "topic-$i",
                    listOf(Schema(eventType = "event$i")),
                )
            }

        val allTopics = repository.getAllTopics()
        assertEquals(5, allTopics.size)

        topics.forEach { topic ->
            val retrieved = repository.getTopic(topic.topicId)
            assertNotNull(retrieved)
            assertEquals(topic, retrieved)
        }
    }

    private suspend fun testTopicWithEmptySchemas(repository: TopicRepository) {
        val topicId = UUID.randomUUID()
        val namespaceId = UUID.randomUUID()
        val name = "empty-schemas-topic"
        val topic = repository.createTopic(topicId, namespaceId, name, emptyList())

        assertEquals(emptyList(), topic.schemas)
        val retrieved = repository.getTopic(topicId)
        assertNotNull(retrieved)
        assertEquals(emptyList(), retrieved.schemas)
    }

    private suspend fun testTopicWithMultipleSchemas(repository: TopicRepository) {
        val topicId = UUID.randomUUID()
        val namespaceId = UUID.randomUUID()
        val name = "multiple-schemas-topic"
        val schemas =
            listOf(
                Schema(eventType = "user.created", properties = mapOf("id" to mapOf("type" to "string"))),
                Schema(eventType = "user.updated", properties = mapOf("id" to mapOf("type" to "string"))),
                Schema(eventType = "user.deleted", properties = mapOf("id" to mapOf("type" to "string"))),
            )

        val topic = repository.createTopic(topicId, namespaceId, name, schemas)
        assertEquals(3, topic.schemas.size)
        assertEquals(schemas, topic.schemas)

        val retrieved = repository.getTopic(topicId)
        assertNotNull(retrieved)
        assertEquals(schemas, retrieved.schemas)
    }

    private suspend fun testSequenceUpdates(repository: TopicRepository) {
        val topicId = UUID.randomUUID()
        val namespaceId = UUID.randomUUID()
        val name = "sequence-updates-topic"
        val schemas = listOf(Schema(eventType = "user.created"))

        repository.createTopic(topicId, namespaceId, name, schemas)

        // Update sequence multiple times
        repository.updateSequence(topicId, 10L)
        var topic = repository.getTopic(topicId)
        assertNotNull(topic)
        assertEquals(10L, topic.sequence)

        repository.updateSequence(topicId, 100L)
        topic = repository.getTopic(topicId)
        assertNotNull(topic)
        assertEquals(100L, topic.sequence)

        repository.updateSequence(topicId, 0L)
        topic = repository.getTopic(topicId)
        assertNotNull(topic)
        assertEquals(0L, topic.sequence)
    }

    private suspend fun testSchemaUpdates(repository: TopicRepository) {
        val topicId = UUID.randomUUID()
        val namespaceId = UUID.randomUUID()
        val name = "schema-updates-topic"
        val initialSchemas = listOf(Schema(eventType = "user.created"))

        repository.createTopic(topicId, namespaceId, name, initialSchemas)

        // Update schemas multiple times
        val schemas1 =
            listOf(
                Schema(eventType = "user.created"),
                Schema(eventType = "user.updated"),
            )
        repository.updateSchemas(topicId, schemas1)
        var topic = repository.getTopic(topicId)
        assertNotNull(topic)
        assertEquals(schemas1, topic.schemas)

        val schemas2 = listOf(Schema(eventType = "user.deleted"))
        repository.updateSchemas(topicId, schemas2)
        topic = repository.getTopic(topicId)
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
        } catch (_: Exception) {
            // Ignore cleanup errors - the temp directory will be cleaned up by the OS eventually
            // or by JUnit's @TempDir mechanism
        }
    }
}
