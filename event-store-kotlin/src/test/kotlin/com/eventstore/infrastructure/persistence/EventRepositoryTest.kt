package com.eventstore.infrastructure.persistence

import com.eventstore.domain.EventId
import com.eventstore.domain.ports.outbound.EventRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Parameterized tests for EventRepository implementations.
 * These tests verify common behavior that should be consistent across all implementations.
 */
class EventRepositoryTest {

    @TempDir
    lateinit var sharedTempDir: Path

    @TestFactory
    fun `test repository implementations`(): List<DynamicTest> {
        data class RepoWithCleanup(val repository: EventRepository, val cleanup: (() -> Unit)?)

        val implementations = listOf(
            "InMemoryEventRepository" to {
                RepoWithCleanup(InMemoryEventRepository(), null)
            },
            "FileSystemEventRepository" to {
                // Create a unique subdirectory for each test to avoid conflicts
                val tempDir = Files.createTempDirectory(sharedTempDir, "event-repo-test")
                val objectMapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
                val repo = FileSystemEventRepository(tempDir, objectMapper)
                RepoWithCleanup(repo) { cleanupDirectory(tempDir) }
            }
        )

        return implementations.flatMap { (name, factory) ->
            listOf(
                DynamicTest.dynamicTest("$name: should store event successfully") {
                    val repoWithCleanup = factory()
                    try {
                        runTest { testStoreEvent(repoWithCleanup.repository) }
                    } finally {
                        repoWithCleanup.cleanup?.invoke()
                    }
                },
                DynamicTest.dynamicTest("$name: should get event by ID") {
                    val repoWithCleanup = factory()
                    try {
                        runTest { testGetEvent(repoWithCleanup.repository) }
                    } finally {
                        repoWithCleanup.cleanup?.invoke()
                    }
                },
                DynamicTest.dynamicTest("$name: should return null when event not found") {
                    val repoWithCleanup = factory()
                    try {
                        runTest { testGetEventNotFound(repoWithCleanup.repository) }
                    } finally {
                        repoWithCleanup.cleanup?.invoke()
                    }
                },
                DynamicTest.dynamicTest("$name: should get all events for topic") {
                    val repoWithCleanup = factory()
                    try {
                        runTest { testGetEvents(repoWithCleanup.repository) }
                    } finally {
                        repoWithCleanup.cleanup?.invoke()
                    }
                },
                DynamicTest.dynamicTest("$name: should return empty list when no events exist") {
                    val repoWithCleanup = factory()
                    try {
                        runTest { testGetEventsEmpty(repoWithCleanup.repository) }
                    } finally {
                        repoWithCleanup.cleanup?.invoke()
                    }
                },
                DynamicTest.dynamicTest("$name: should filter events by sinceEventId") {
                    val repoWithCleanup = factory()
                    try {
                        runTest { testGetEventsSinceEventId(repoWithCleanup.repository) }
                    } finally {
                        repoWithCleanup.cleanup?.invoke()
                    }
                },
                DynamicTest.dynamicTest("$name: should filter events by date") {
                    val repoWithCleanup = factory()
                    try {
                        runTest { testGetEventsByDate(repoWithCleanup.repository) }
                    } finally {
                        repoWithCleanup.cleanup?.invoke()
                    }
                },
                DynamicTest.dynamicTest("$name: should limit number of events returned") {
                    val repoWithCleanup = factory()
                    try {
                        runTest { testGetEventsWithLimit(repoWithCleanup.repository) }
                    } finally {
                        repoWithCleanup.cleanup?.invoke()
                    }
                },
                DynamicTest.dynamicTest("$name: should combine filters correctly") {
                    val repoWithCleanup = factory()
                    try {
                        runTest { testGetEventsWithCombinedFilters(repoWithCleanup.repository) }
                    } finally {
                        repoWithCleanup.cleanup?.invoke()
                    }
                },
                DynamicTest.dynamicTest("$name: should get latest event ID") {
                    val repoWithCleanup = factory()
                    try {
                        runTest { testGetLatestEventId(repoWithCleanup.repository) }
                    } finally {
                        repoWithCleanup.cleanup?.invoke()
                    }
                },
                DynamicTest.dynamicTest("$name: should return null for latest event ID when no events exist") {
                    val repoWithCleanup = factory()
                    try {
                        runTest { testGetLatestEventIdEmpty(repoWithCleanup.repository) }
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
                DynamicTest.dynamicTest("$name: should handle events with empty payload") {
                    val repoWithCleanup = factory()
                    try {
                        runTest { testEventWithEmptyPayload(repoWithCleanup.repository) }
                    } finally {
                        repoWithCleanup.cleanup?.invoke()
                    }
                },
                DynamicTest.dynamicTest("$name: should handle events with complex payload") {
                    val repoWithCleanup = factory()
                    try {
                        runTest { testEventWithComplexPayload(repoWithCleanup.repository) }
                    } finally {
                        repoWithCleanup.cleanup?.invoke()
                    }
                },
                DynamicTest.dynamicTest("$name: should maintain event order") {
                    val repoWithCleanup = factory()
                    try {
                        runTest { testEventOrder(repoWithCleanup.repository) }
                    } finally {
                        repoWithCleanup.cleanup?.invoke()
                    }
                }
            )
        }
    }

    private suspend fun testStoreEvent(repository: EventRepository) {
        val topic = "test-topic"
        val eventId = EventId.create(topic, 1L)
        val timestamp = Instant.now()
        val type = "user.created"
        val payload = mapOf("id" to "123", "name" to "Alice")

        val event = repository.storeEvent(topic, type, payload, eventId, timestamp)

        assertEquals(eventId, event.id)
        assertEquals(timestamp, event.timestamp)
        assertEquals(type, event.type)
        assertEquals(payload, event.payload)
    }

    private suspend fun testGetEvent(repository: EventRepository) {
        val topic = "get-topic"
        val eventId = EventId.create(topic, 1L)
        val timestamp = Instant.now()
        val type = "user.created"
        val payload = mapOf("id" to "123", "name" to "Alice")

        val stored = repository.storeEvent(topic, type, payload, eventId, timestamp)
        val retrieved = repository.getEvent(topic, eventId)

        assertNotNull(retrieved)
        assertEquals(stored, retrieved)
    }

    private suspend fun testGetEventNotFound(repository: EventRepository) {
        val topic = "not-found-topic"
        val eventId = EventId.create(topic, 999L)

        val retrieved = repository.getEvent(topic, eventId)
        assertNull(retrieved)
    }

    private suspend fun testGetEvents(repository: EventRepository) {
        val topic = "events-topic"
        val timestamp = Instant.now()

        val event1 = repository.storeEvent(
            topic, "user.created", mapOf("id" to "1"),
            EventId.create(topic, 1L), timestamp
        )
        val event2 = repository.storeEvent(
            topic, "user.updated", mapOf("id" to "2"),
            EventId.create(topic, 2L), timestamp
        )
        val event3 = repository.storeEvent(
            topic, "user.deleted", mapOf("id" to "3"),
            EventId.create(topic, 3L), timestamp
        )

        val events = repository.getEvents(topic)

        assertEquals(3, events.size)
        assertTrue(events.contains(event1))
        assertTrue(events.contains(event2))
        assertTrue(events.contains(event3))
    }

    private suspend fun testGetEventsEmpty(repository: EventRepository) {
        val events = repository.getEvents("empty-topic")
        assertTrue(events.isEmpty())
    }

    private suspend fun testGetEventsSinceEventId(repository: EventRepository) {
        val topic = "since-topic"
        val timestamp = Instant.now()

        repository.storeEvent(topic, "event1", mapOf("id" to "1"), EventId.create(topic, 1L), timestamp)
        repository.storeEvent(topic, "event2", mapOf("id" to "2"), EventId.create(topic, 2L), timestamp)
        repository.storeEvent(topic, "event3", mapOf("id" to "3"), EventId.create(topic, 3L), timestamp)

        val sinceEventId = EventId.create(topic, 1L)
        val events = repository.getEvents(topic, sinceEventId = sinceEventId)

        assertEquals(2, events.size)
        assertTrue(events.any { it.id.sequence == 2L })
        assertTrue(events.any { it.id.sequence == 3L })
        assertTrue(events.none { it.id.sequence == 1L })
    }

    private suspend fun testGetEventsByDate(repository: EventRepository) {
        val topic = "date-topic"
        val today = Instant.now()
        val yesterday = today.minusSeconds(86400) // 24 hours ago
        val dateFormatter = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE

        val todayDate = today.atZone(java.time.ZoneId.systemDefault()).format(dateFormatter)

        repository.storeEvent(topic, "event1", mapOf("id" to "1"), EventId.create(topic, 1L), today)
        repository.storeEvent(topic, "event2", mapOf("id" to "2"), EventId.create(topic, 2L), yesterday)
        repository.storeEvent(topic, "event3", mapOf("id" to "3"), EventId.create(topic, 3L), today)

        val events = repository.getEvents(topic, date = todayDate)

        assertEquals(2, events.size)
        assertTrue(events.all { event ->
            val eventDate = event.timestamp.atZone(java.time.ZoneId.systemDefault()).format(dateFormatter)
            eventDate == todayDate
        })
    }

    private suspend fun testGetEventsWithLimit(repository: EventRepository) {
        val topic = "limit-topic"
        val timestamp = Instant.now()

        repeat(10) { i ->
            repository.storeEvent(
                topic, "event$i", mapOf("id" to i.toString()),
                EventId.create(topic, (i + 1).toLong()), timestamp
            )
        }

        val events = repository.getEvents(topic, limit = 5)

        assertEquals(5, events.size)
    }

    private suspend fun testGetEventsWithCombinedFilters(repository: EventRepository) {
        val topic = "combined-topic"
        val timestamp = Instant.now()
        val dateFormatter = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
        val date = timestamp.atZone(java.time.ZoneId.systemDefault()).format(dateFormatter)

        repository.storeEvent(topic, "event1", mapOf("id" to "1"), EventId.create(topic, 1L), timestamp)
        repository.storeEvent(topic, "event2", mapOf("id" to "2"), EventId.create(topic, 2L), timestamp)
        repository.storeEvent(topic, "event3", mapOf("id" to "3"), EventId.create(topic, 3L), timestamp)

        val sinceEventId = EventId.create(topic, 1L)
        val events = repository.getEvents(topic, sinceEventId = sinceEventId, date = date, limit = 1)

        assertEquals(1, events.size)
        assertTrue(events.first().id.sequence > 1L)
    }

    private suspend fun testGetLatestEventId(repository: EventRepository) {
        val topic = "latest-topic"
        val timestamp = Instant.now()

        repository.storeEvent(topic, "event1", mapOf("id" to "1"), EventId.create(topic, 1L), timestamp)
        repository.storeEvent(topic, "event2", mapOf("id" to "2"), EventId.create(topic, 2L), timestamp)
        val latestEvent = repository.storeEvent(
            topic, "event3", mapOf("id" to "3"),
            EventId.create(topic, 3L), timestamp
        )

        val latestEventId = repository.getLatestEventId(topic)

        assertNotNull(latestEventId)
        assertEquals(latestEvent.id, latestEventId)
    }

    private suspend fun testGetLatestEventIdEmpty(repository: EventRepository) {
        val latestEventId = repository.getLatestEventId("empty-topic")
        assertNull(latestEventId)
    }

    private suspend fun testMultipleTopics(repository: EventRepository) {
        val topic1 = "topic-1"
        val topic2 = "topic-2"
        val timestamp = Instant.now()

        val event1 = repository.storeEvent(
            topic1, "event1", mapOf("id" to "1"),
            EventId.create(topic1, 1L), timestamp
        )
        val event2 = repository.storeEvent(
            topic2, "event2", mapOf("id" to "2"),
            EventId.create(topic2, 1L), timestamp
        )

        val events1 = repository.getEvents(topic1)
        val events2 = repository.getEvents(topic2)

        assertEquals(1, events1.size)
        assertEquals(1, events2.size)
        assertTrue(events1.contains(event1))
        assertTrue(events2.contains(event2))
    }

    private suspend fun testEventWithEmptyPayload(repository: EventRepository) {
        val topic = "empty-payload-topic"
        val eventId = EventId.create(topic, 1L)
        val timestamp = Instant.now()

        val event = repository.storeEvent(topic, "user.created", emptyMap(), eventId, timestamp)

        assertEquals(emptyMap<String, Any>(), event.payload)
        val retrieved = repository.getEvent(topic, eventId)
        assertNotNull(retrieved)
        assertEquals(emptyMap<String, Any>(), retrieved.payload)
    }

    private suspend fun testEventWithComplexPayload(repository: EventRepository) {
        val topic = "complex-payload-topic"
        val eventId = EventId.create(topic, 1L)
        val timestamp = Instant.now()
        val payload = mapOf(
            "id" to "123",
            "name" to "Alice",
            "age" to 30,
            "active" to true,
            "tags" to listOf("admin", "user"),
            "metadata" to mapOf("source" to "api", "version" to "1.0")
        )

        val event = repository.storeEvent(topic, "user.created", payload, eventId, timestamp)

        assertEquals(payload, event.payload)
        val retrieved = repository.getEvent(topic, eventId)
        assertNotNull(retrieved)
        assertEquals(payload, retrieved.payload)
    }

    private suspend fun testEventOrder(repository: EventRepository) {
        val topic = "order-topic"
        val timestamp = Instant.now()

        (1..5).forEach { i ->
            repository.storeEvent(
                topic, "event$i", mapOf("id" to i.toString()),
                EventId.create(topic, i.toLong()), timestamp
            )
        }

        val retrieved = repository.getEvents(topic)

        assertEquals(5, retrieved.size)
        // Events should be sorted by event ID (sequence)
        retrieved.forEachIndexed { index, event ->
            assertEquals((index + 1).toLong(), event.id.sequence)
        }
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

