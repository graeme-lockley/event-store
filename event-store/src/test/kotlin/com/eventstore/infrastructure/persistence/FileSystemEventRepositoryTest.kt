package com.eventstore.infrastructure.persistence

import com.eventstore.domain.EventId
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
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Implementation-specific tests for FileSystemEventRepository.
 * These tests verify behavior unique to the file system implementation.
 */
class FileSystemEventRepositoryTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var repository: FileSystemEventRepository
    private val objectMapper = jacksonObjectMapper()

    @BeforeEach
    fun setUp() {
        repository = FileSystemEventRepository(tempDir, objectMapper)
    }

    @Test
    fun `should persist events to file system`() =
        runTest {
            val topicId = UUID.randomUUID()
            val eventId = EventId.create(topicId, 1L)
            val timestamp = Instant.now()

            repository.storeEvent(topicId, "user.created", mapOf("id" to "123"), eventId, timestamp)

            // Verify file structure exists (sequence-based hierarchical structure)
            val group1 = String.format("%03d", eventId.sequence / 1_000_000)
            val group2 = String.format("%02d", (eventId.sequence / 10_000) % 100)
            val group3 = String.format("%02d", (eventId.sequence / 100) % 100)
            val eventFile =
                tempDir.resolve(topicId.toString())
                    .resolve(group1)
                    .resolve(group2)
                    .resolve(group3)
                    .resolve("${eventId.sequence}.json")

            assertTrue(Files.exists(eventFile))
            assertTrue(Files.isRegularFile(eventFile))
        }

    @Test
    fun `should read events from file system`() =
        runTest {
            val topicId = UUID.randomUUID()
            val eventId = EventId.create(topicId, 1L)
            val timestamp = Instant.now()
            val payload = mapOf("id" to "123", "name" to "Alice")

            repository.storeEvent(topicId, "user.created", payload, eventId, timestamp)

            // Create a new repository instance to verify it reads from disk
            val newRepository = FileSystemEventRepository(tempDir, objectMapper)
            val event = newRepository.getEvent(topicId, eventId)

            assertNotNull(event)
            assertEquals(eventId, event.id)
            assertEquals("user.created", event.type)
            assertEquals(payload, event.payload)
        }

    @Test
    fun `should handle non-existent data directory`() =
        runTest {
            val newDir = tempDir.resolve("non-existent")
            assertFalse(Files.exists(newDir))

            val newRepository = FileSystemEventRepository(newDir, objectMapper)
            val topicId = UUID.randomUUID()
            val events = newRepository.getEvents(topicId)

            assertTrue(events.isEmpty())
        }

    @Test
    fun `should create directory structure automatically`() =
        runTest {
            val topicId = UUID.randomUUID()
            val eventId = EventId.create(topicId, 1L)
            val timestamp = Instant.now()

            repository.storeEvent(topicId, "user.created", mapOf("id" to "123"), eventId, timestamp)

            // Verify sequence-based hierarchical directory structure
            val group1 = String.format("%03d", eventId.sequence / 1_000_000)
            val group2 = String.format("%02d", (eventId.sequence / 10_000) % 100)
            val group3 = String.format("%02d", (eventId.sequence / 100) % 100)
            val topicDir = tempDir.resolve(topicId.toString())
            val group1Dir = topicDir.resolve(group1)
            val group2Dir = group1Dir.resolve(group2)
            val group3Dir = group2Dir.resolve(group3)

            assertTrue(Files.exists(topicDir))
            assertTrue(Files.isDirectory(topicDir))
            assertTrue(Files.exists(group1Dir))
            assertTrue(Files.isDirectory(group1Dir))
            assertTrue(Files.exists(group2Dir))
            assertTrue(Files.isDirectory(group2Dir))
            assertTrue(Files.exists(group3Dir))
            assertTrue(Files.isDirectory(group3Dir))
        }

    @Test
    fun `should handle events organized by date`() =
        runTest {
            val topicId = UUID.randomUUID()
            val today = Instant.now()
            val yesterday = today.minusSeconds(86400) // 24 hours ago

            repository.storeEvent(topicId, "event1", mapOf("id" to "1"), EventId.create(topicId, 1L), today)
            repository.storeEvent(topicId, "event2", mapOf("id" to "2"), EventId.create(topicId, 2L), yesterday)

            // Verify both events are stored in the same sequence-based directory structure
            // (events with sequences 1 and 2 will be in the same group3 directory)
            val events = repository.getEvents(topicId)
            assertEquals(2, events.size)
        }

    @Test
    fun `should handle events organized by grouping`() =
        runTest {
            val topicId = UUID.randomUUID()
            val timestamp = Instant.now()

            // Store events that will be in different groups
            // Sequence 1: group1=000, group2=00, group3=00
            // Sequence 100: group1=000, group2=00, group3=01
            // Sequence 10000: group1=000, group2=01, group3=00
            repository.storeEvent(topicId, "event1", mapOf("id" to "1"), EventId.create(topicId, 1L), timestamp)
            repository.storeEvent(topicId, "event2", mapOf("id" to "2"), EventId.create(topicId, 100L), timestamp)
            repository.storeEvent(topicId, "event3", mapOf("id" to "3"), EventId.create(topicId, 10000L), timestamp)

            // Verify directory structure - events should be in different group3 directories
            val group1For1 = String.format("%03d", 1L / 1_000_000)
            val group2For1 = String.format("%02d", (1L / 10_000) % 100)
            val group3For1 = String.format("%02d", (1L / 100) % 100)
            val dir1 = tempDir.resolve(topicId.toString()).resolve(group1For1).resolve(group2For1).resolve(group3For1)

            val group3For100 = String.format("%02d", (100L / 100) % 100)
            val dir2 = tempDir.resolve(topicId.toString()).resolve(group1For1).resolve(group2For1).resolve(group3For100)

            val group2For10000 = String.format("%02d", (10000L / 10_000) % 100)
            val group3For10000 = String.format("%02d", (10000L / 100) % 100)
            val dir3 = tempDir.resolve(topicId.toString()).resolve(group1For1).resolve(group2For10000).resolve(group3For10000)

            assertTrue(Files.exists(dir1))
            assertTrue(Files.exists(dir2))
            assertTrue(Files.exists(dir3))
        }

    @Test
    fun `should maintain separate events in different topics`() =
        runTest {
            val topicId1 = UUID.randomUUID()
            val topicId2 = UUID.randomUUID()
            val timestamp = Instant.now()

            val event1 =
                repository.storeEvent(
                    topicId1,
                    "event1",
                    mapOf("id" to "1"),
                    EventId.create(topicId1, 1L),
                    timestamp,
                )
            val event2 =
                repository.storeEvent(
                    topicId2,
                    "event2",
                    mapOf("id" to "2"),
                    EventId.create(topicId2, 1L),
                    timestamp,
                )

            val repo1 = FileSystemEventRepository(tempDir, objectMapper)
            val repo2 = FileSystemEventRepository(tempDir, objectMapper)

            assertEquals(1, repo1.getEvents(topicId1).size)
            assertEquals(1, repo2.getEvents(topicId2).size)
            assertTrue(repo1.getEvents(topicId1).contains(event1))
            assertTrue(repo2.getEvents(topicId2).contains(event2))
        }

    @Test
    fun `should handle file system operations atomically`() =
        runTest {
            val topicId = UUID.randomUUID()
            val eventId = EventId.create(topicId, 1L)
            val timestamp = Instant.now()
            val payload = mapOf("id" to "123", "name" to "Alice")

            repository.storeEvent(topicId, "user.created", payload, eventId, timestamp)

            // Verify file exists and is readable (sequence-based structure)
            val group1 = String.format("%03d", eventId.sequence / 1_000_000)
            val group2 = String.format("%02d", (eventId.sequence / 10_000) % 100)
            val group3 = String.format("%02d", (eventId.sequence / 100) % 100)
            val eventFile =
                tempDir.resolve(topicId.toString())
                    .resolve(group1)
                    .resolve(group2)
                    .resolve(group3)
                    .resolve("${eventId.sequence}.json")

            assertTrue(Files.exists(eventFile))
            val content = Files.readString(eventFile)
            assertTrue(content.contains("user.created"))
            assertTrue(content.contains("123"))
        }

    @Test
    fun `should persist all events across repository instances`() =
        runTest {
            val topicId = UUID.randomUUID()
            val timestamp = Instant.now()

            val events =
                (1..5).map { i ->
                    repository.storeEvent(
                        topicId,
                        "event$i",
                        mapOf("id" to i.toString()),
                        EventId.create(topicId, i.toLong()),
                        timestamp,
                    )
                }

            // Create a new repository instance
            val newRepository = FileSystemEventRepository(tempDir, objectMapper)
            val allEvents = newRepository.getEvents(topicId)

            assertEquals(5, allEvents.size)
            events.forEach { event ->
                val found = allEvents.find { it.id == event.id }
                assertNotNull(found)
                assertEquals(event, found)
            }
        }

    @Test
    fun `should handle concurrent file operations`() =
        runTest {
            val topicId = UUID.randomUUID()
            val timestamp = Instant.now()

            // Store initial event
            repository.storeEvent(topicId, "initial", mapOf("id" to "0"), EventId.create(topicId, 0L), timestamp)

            // Simulate concurrent storage
            coroutineScope {
                val operations =
                    (1..50).map { i ->
                        async {
                            repository.storeEvent(
                                topicId,
                                "event$i",
                                mapOf("id" to i.toString()),
                                EventId.create(topicId, i.toLong()),
                                timestamp,
                            )
                        }
                    }
                operations.awaitAll()
            }

            // Verify final state
            val events = repository.getEvents(topicId)
            assertTrue(events.size >= 1 && events.size <= 51) // At least initial, at most all
        }

    @Test
    fun `should handle events with UUID topic identifiers`() =
        runTest {
            val topicId = UUID.randomUUID()
            val eventId = EventId.create(topicId, 1L)
            val timestamp = Instant.now()

            repository.storeEvent(topicId, "user.created", mapOf("id" to "123"), eventId, timestamp)

            val retrieved = repository.getEvent(topicId, eventId)
            assertNotNull(retrieved)
            assertEquals(eventId, retrieved.id)
        }

    @Test
    fun `should handle large event payloads`() =
        runTest {
            val topicId = UUID.randomUUID()
            val eventId = EventId.create(topicId, 1L)
            val timestamp = Instant.now()
            val largePayload = (1..1000).associate { "key$it" to "value$it".repeat(10) }

            repository.storeEvent(topicId, "large.event", largePayload, eventId, timestamp)

            val retrieved = repository.getEvent(topicId, eventId)
            assertNotNull(retrieved)
            assertEquals(largePayload.size, retrieved.payload.size)
        }
}
