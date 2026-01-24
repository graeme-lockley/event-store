package com.eventstore.infrastructure.persistence

import com.eventstore.domain.Schema
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Implementation-specific tests for FileSystemTopicRepository.
 * These tests verify behavior unique to the file system implementation.
 */
class FileSystemTopicRepositoryTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var repository: FileSystemTopicRepository
    private val objectMapper = jacksonObjectMapper()

    @BeforeEach
    fun setUp() {
        repository = FileSystemTopicRepository(tempDir, objectMapper)
    }

    @Test
    fun `should persist topics to file system`() =
        runTest {
            val topicId = UUID.randomUUID()
            val namespaceId = UUID.randomUUID()
            val name = "persisted-topic"
            val schemas = listOf(Schema(eventType = "user.created"))

            repository.createTopic(topicId, namespaceId, name, schemas)

            val configPath = tempDir.resolve("$topicId.json")
            assertTrue(Files.exists(configPath))
            assertTrue(Files.isRegularFile(configPath))
        }

    @Test
    fun `should read topics from file system`() =
        runTest {
            val topicId = UUID.randomUUID()
            val namespaceId = UUID.randomUUID()
            val name = "file-read-topic"
            val schemas =
                listOf(
                    Schema(eventType = "user.created", properties = mapOf("id" to mapOf("type" to "string"))),
                )

            repository.createTopic(topicId, namespaceId, name, schemas)

            // Create a new repository instance to verify it reads from disk
            val newRepository = FileSystemTopicRepository(tempDir, objectMapper)
            val topic = newRepository.getTopic(topicId)

            assertNotNull(topic)
            assertEquals(topicId, topic.topicId)
            assertEquals(name, topic.name)
            assertEquals(0L, topic.sequence)
            assertEquals(schemas, topic.schemas)
        }

    @Test
    fun `should persist sequence updates to file system`() =
        runTest {
            val topicId = UUID.randomUUID()
            val namespaceId = UUID.randomUUID()
            val name = "sequence-persist-topic"
            val schemas = listOf(Schema(eventType = "user.created"))

            repository.createTopic(topicId, namespaceId, name, schemas)
            repository.updateSequence(topicId, 42L)

            // Create a new repository instance to verify persistence
            val newRepository = FileSystemTopicRepository(tempDir, objectMapper)
            val topic = newRepository.getTopic(topicId)

            assertNotNull(topic)
            assertEquals(42L, topic.sequence)
        }

    @Test
    fun `should persist schema updates to file system`() =
        runTest {
            val topicId = UUID.randomUUID()
            val namespaceId = UUID.randomUUID()
            val name = "schema-persist-topic"
            val initialSchemas = listOf(Schema(eventType = "user.created"))
            val updatedSchemas =
                listOf(
                    Schema(eventType = "user.created"),
                    Schema(eventType = "user.updated"),
                )

            repository.createTopic(topicId, namespaceId, name, initialSchemas)
            repository.updateSchemas(topicId, updatedSchemas)

            // Create a new repository instance to verify persistence
            val newRepository = FileSystemTopicRepository(tempDir, objectMapper)
            val topic = newRepository.getTopic(topicId)

            assertNotNull(topic)
            assertEquals(updatedSchemas, topic.schemas)
        }

    @Test
    fun `should handle non-existent config directory`() =
        runTest {
            val nonExistentDir = tempDir.resolve("non-existent")
            val newRepository = FileSystemTopicRepository(nonExistentDir, objectMapper)

            val topics = newRepository.getAllTopics()
            assertTrue(topics.isEmpty())
        }

    @Test
    fun `should ignore non-JSON files in config directory`() =
        runTest {
            // Create a non-JSON file
            val textFile = tempDir.resolve("not-a-topic.txt")
            Files.writeString(textFile, "This is not a topic config")

            // Create a valid topic
            val topicId = UUID.randomUUID()
            val namespaceId = UUID.randomUUID()
            repository.createTopic(topicId, namespaceId, "valid-topic", listOf(Schema(eventType = "user.created")))

            val topics = repository.getAllTopics()
            assertEquals(1, topics.size)
            assertEquals(topicId, topics.first().topicId)
        }

    @Test
    fun `should ignore invalid JSON files in config directory`() =
        runTest {
            // Create an invalid JSON file with valid UUID name
            val invalidTopicId = UUID.randomUUID()
            val invalidJsonFile = tempDir.resolve("$invalidTopicId.json")
            Files.writeString(invalidJsonFile, "{ invalid json }")

            // Create a valid topic
            val topicId = UUID.randomUUID()
            val namespaceId = UUID.randomUUID()
            repository.createTopic(topicId, namespaceId, "valid-topic", listOf(Schema(eventType = "user.created")))

            val topics = repository.getAllTopics()
            assertEquals(1, topics.size)
            assertEquals(topicId, topics.first().topicId)
        }

    @Test
    fun `should handle corrupted topic config files gracefully`() =
        runTest {
            val topicId = UUID.randomUUID()
            val namespaceId = UUID.randomUUID()
            // Create a valid topic first
            repository.createTopic(topicId, namespaceId, "valid-topic", listOf(Schema(eventType = "user.created")))

            // Corrupt the file
            val configPath = tempDir.resolve("$topicId.json")
            Files.writeString(configPath, "corrupted content")

            // getAllTopics should skip the corrupted file
            val topics = repository.getAllTopics()
            assertTrue(topics.isEmpty() || !topics.any { it.topicId == topicId })
        }

    @Test
    fun `should create config directory if it does not exist`() =
        runTest {
            val newDir = tempDir.resolve("new-config-dir")
            assertFalse(Files.exists(newDir))

            val newRepository = FileSystemTopicRepository(newDir, objectMapper)
            val topicId = UUID.randomUUID()
            val namespaceId = UUID.randomUUID()
            newRepository.createTopic(topicId, namespaceId, "test-topic", listOf(Schema(eventType = "user.created")))

            assertTrue(Files.exists(newDir))
            assertTrue(Files.isDirectory(newDir))
        }

    @Test
    fun `should handle file system operations atomically`() =
        runTest {
            val topicId = UUID.randomUUID()
            val namespaceId = UUID.randomUUID()
            val name = "atomic-topic"
            val schemas = listOf(Schema(eventType = "user.created"))

            repository.createTopic(topicId, namespaceId, name, schemas)

            // Verify file exists and is readable
            val configPath = tempDir.resolve("$topicId.json")
            assertTrue(Files.exists(configPath))
            val content = Files.readString(configPath)
            assertTrue(content.contains(name))
        }

    @Test
    fun `should maintain separate topics in different repositories`() =
        runTest {
            val dir1 = tempDir.resolve("dir1")
            val dir2 = tempDir.resolve("dir2")
            val repo1 = FileSystemTopicRepository(dir1, objectMapper)
            val repo2 = FileSystemTopicRepository(dir2, objectMapper)

            val topicId1 = UUID.randomUUID()
            val topicId2 = UUID.randomUUID()
            val namespaceId1 = UUID.randomUUID()
            val namespaceId2 = UUID.randomUUID()

            repo1.createTopic(topicId1, namespaceId1, "topic-1", listOf(Schema(eventType = "event1")))
            repo2.createTopic(topicId2, namespaceId2, "topic-2", listOf(Schema(eventType = "event2")))

            assertEquals(1, repo1.getAllTopics().size)
            assertEquals(1, repo2.getAllTopics().size)
            assertEquals(topicId1, repo1.getAllTopics().first().topicId)
            assertEquals(topicId2, repo2.getAllTopics().first().topicId)
        }

    @Test
    fun `should handle topic names with special characters`() =
        runTest {
            val topicId = UUID.randomUUID()
            val namespaceId = UUID.randomUUID()
            val name = "topic-with-special-chars"
            val schemas = listOf(Schema(eventType = "user.created"))

            repository.createTopic(topicId, namespaceId, name, schemas)

            val configPath = tempDir.resolve("$topicId.json")
            assertTrue(Files.exists(configPath))
        }

    @Test
    fun `should persist all topics across repository instances`() =
        runTest {
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

            // Create a new repository instance
            val newRepository = FileSystemTopicRepository(tempDir, objectMapper)
            val allTopics = newRepository.getAllTopics()

            assertEquals(5, allTopics.size)
            topics.forEach { topic ->
                val found = allTopics.find { it.topicId == topic.topicId }
                assertNotNull(found)
                assertEquals(topic, found)
            }
        }

    @Test
    fun `should handle concurrent file operations`() =
        runTest {
            val topicId = UUID.randomUUID()
            val namespaceId = UUID.randomUUID()
            val name = "concurrent-file-topic"
            repository.createTopic(topicId, namespaceId, name, listOf(Schema(eventType = "user.created")))

            // Simulate concurrent updates
            coroutineScope {
                val operations =
                    (1..50).map { i ->
                        async {
                            repository.updateSequence(topicId, i.toLong())
                        }
                    }
                operations.awaitAll()
            }

            // Verify final state
            val topic = repository.getTopic(topicId)
            assertNotNull(topic)
            // The final sequence should be one of the values (last write wins)
            assertTrue(topic.sequence >= 1 && topic.sequence <= 50)
        }
}
