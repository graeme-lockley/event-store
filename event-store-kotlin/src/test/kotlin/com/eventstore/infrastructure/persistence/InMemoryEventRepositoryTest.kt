package com.eventstore.infrastructure.persistence

import com.eventstore.domain.EventId
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Implementation-specific tests for InMemoryEventRepository.
 * These tests verify behavior unique to the in-memory implementation.
 */
class InMemoryEventRepositoryTest {

    private val repository = InMemoryEventRepository()

    @Test
    fun `should handle concurrent event storage`() = runTest {
        val topic = "concurrent-topic"
        val timestamp = Instant.now()

        val events = (1..10).map { i ->
            repository.storeEvent(
                topic, "event$i", mapOf("id" to i.toString()),
                EventId.create(topic, i.toLong()), timestamp
            )
        }

        assertEquals(10, events.size)
        assertEquals(10, repository.getEvents(topic).size)
    }

    @Test
    fun `should maintain event isolation between instances`() = runTest {
        val repo1 = InMemoryEventRepository()
        val repo2 = InMemoryEventRepository()
        val timestamp = Instant.now()

        repo1.storeEvent("topic-1", "event1", mapOf("id" to "1"), EventId.create("topic-1", 1L), timestamp)
        repo2.storeEvent("topic-2", "event2", mapOf("id" to "2"), EventId.create("topic-2", 1L), timestamp)

        assertEquals(1, repo1.getEvents("topic-1").size)
        assertEquals(1, repo2.getEvents("topic-2").size)
        assertEquals(0, repo1.getEvents("topic-2").size)
        assertEquals(0, repo2.getEvents("topic-1").size)
    }

    @Test
    fun `should handle rapid event storage`() = runTest {
        val topic = "rapid-storage-topic"
        val timestamp = Instant.now()

        repeat(100) { i ->
            repository.storeEvent(
                topic, "event$i", mapOf("id" to i.toString()),
                EventId.create(topic, (i + 1).toLong()), timestamp
            )
        }

        val events = repository.getEvents(topic)
        assertEquals(100, events.size)
    }

    @Test
    fun `should be thread-safe for concurrent operations`() = runTest {
        val topic = "concurrent-ops-topic"
        val timestamp = Instant.now()

        // Store initial event
        repository.storeEvent(topic, "initial", mapOf("id" to "0"), EventId.create(topic, 0L), timestamp)

        // Simulate concurrent operations
        coroutineScope {
            val operations = (1..100).map { i ->
                async {
                    when (i % 3) {
                        0 -> repository.storeEvent(
                            topic, "event$i", mapOf("id" to i.toString()),
                            EventId.create(topic, i.toLong()), timestamp
                        )
                        1 -> repository.getEvent(topic, EventId.create(topic, 0L))
                        else -> repository.getEvents(topic)
                    }
                }
            }

            operations.awaitAll()
        }

        // Verify final state is consistent
        val events = repository.getEvents(topic)
        assertTrue(events.isNotEmpty())
        assertNotNull(repository.getEvent(topic, EventId.create(topic, 0L)))
    }

    @Test
    fun `should handle large number of events`() = runTest {
        val topic = "large-topic"
        val timestamp = Instant.now()
        val eventCount = 1000

        repeat(eventCount) { i ->
            repository.storeEvent(
                topic, "event$i", mapOf("id" to i.toString()),
                EventId.create(topic, (i + 1).toLong()), timestamp
            )
        }

        assertEquals(eventCount, repository.getEvents(topic).size)
    }

    @Test
    fun `should maintain data after multiple operations`() = runTest {
        val topic = "persistence-test-topic"
        val timestamp = Instant.now()

        val event1 = repository.storeEvent(
            topic, "user.created", mapOf("id" to "1", "name" to "Alice"),
            EventId.create(topic, 1L), timestamp
        )
        val event2 = repository.storeEvent(
            topic, "user.updated", mapOf("id" to "1", "name" to "Bob"),
            EventId.create(topic, 2L), timestamp
        )

        val retrieved1 = repository.getEvent(topic, event1.id)
        val retrieved2 = repository.getEvent(topic, event2.id)
        val allEvents = repository.getEvents(topic)

        assertNotNull(retrieved1)
        assertNotNull(retrieved2)
        assertEquals(event1, retrieved1)
        assertEquals(event2, retrieved2)
        assertEquals(2, allEvents.size)
    }

    @Test
    fun `should handle events with same sequence across different topics`() = runTest {
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

        val retrieved1 = repository.getEvent(topic1, EventId.create(topic1, 1L))
        val retrieved2 = repository.getEvent(topic2, EventId.create(topic2, 1L))

        assertNotNull(retrieved1)
        assertNotNull(retrieved2)
        assertEquals(event1, retrieved1)
        assertEquals(event2, retrieved2)
    }
}

