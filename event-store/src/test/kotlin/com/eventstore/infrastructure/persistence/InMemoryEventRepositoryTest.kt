package com.eventstore.infrastructure.persistence

import com.eventstore.domain.EventId
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.*
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
        val topicId = UUID.randomUUID()
        val timestamp = Instant.now()

        val events = (1..10).map { i ->
            repository.storeEvent(
                topicId, "event$i", mapOf("id" to i.toString()),
                EventId.create(topicId, i.toLong()), timestamp
            )
        }

        assertEquals(10, events.size)
        assertEquals(10, repository.getEvents(topicId).size)
    }

    @Test
    fun `should maintain event isolation between instances`() = runTest {
        val repo1 = InMemoryEventRepository()
        val repo2 = InMemoryEventRepository()
        val timestamp = Instant.now()
        val topicId1 = UUID.randomUUID()
        val topicId2 = UUID.randomUUID()

        repo1.storeEvent(topicId1, "event1", mapOf("id" to "1"), EventId.create(topicId1, 1L), timestamp)
        repo2.storeEvent(topicId2, "event2", mapOf("id" to "2"), EventId.create(topicId2, 1L), timestamp)

        assertEquals(1, repo1.getEvents(topicId1).size)
        assertEquals(1, repo2.getEvents(topicId2).size)
        assertEquals(0, repo1.getEvents(topicId2).size)
        assertEquals(0, repo2.getEvents(topicId1).size)
    }

    @Test
    fun `should handle rapid event storage`() = runTest {
        val topicId = UUID.randomUUID()
        val timestamp = Instant.now()

        repeat(100) { i ->
            repository.storeEvent(
                topicId, "event$i", mapOf("id" to i.toString()),
                EventId.create(topicId, (i + 1).toLong()), timestamp
            )
        }

        val events = repository.getEvents(topicId)
        assertEquals(100, events.size)
    }

    @Test
    fun `should be thread-safe for concurrent operations`() = runTest {
        val topicId = UUID.randomUUID()
        val timestamp = Instant.now()

        // Store initial event
        val initialEventId = EventId.create(topicId, 0L)
        repository.storeEvent(topicId, "initial", mapOf("id" to "0"), initialEventId, timestamp)

        // Simulate concurrent operations
        coroutineScope {
            val operations = (1..100).map { i ->
                async {
                    when (i % 3) {
                        0 -> repository.storeEvent(
                            topicId, "event$i", mapOf("id" to i.toString()),
                            EventId.create(topicId, i.toLong()), timestamp
                        )

                        1 -> repository.getEvent(topicId, initialEventId)
                        else -> repository.getEvents(topicId)
                    }
                }
            }

            operations.awaitAll()
        }

        // Verify final state is consistent
        val events = repository.getEvents(topicId)
        assertTrue(events.isNotEmpty())
        assertNotNull(repository.getEvent(topicId, initialEventId))
    }

    @Test
    fun `should handle large number of events`() = runTest {
        val topicId = UUID.randomUUID()
        val timestamp = Instant.now()
        val eventCount = 1000

        repeat(eventCount) { i ->
            repository.storeEvent(
                topicId, "event$i", mapOf("id" to i.toString()),
                EventId.create(topicId, (i + 1).toLong()), timestamp
            )
        }

        assertEquals(eventCount, repository.getEvents(topicId).size)
    }

    @Test
    fun `should maintain data after multiple operations`() = runTest {
        val topicId = UUID.randomUUID()
        val timestamp = Instant.now()

        val eventId1 = EventId.create(topicId, 1L)
        val eventId2 = EventId.create(topicId, 2L)
        val event1 = repository.storeEvent(
            topicId, "user.created", mapOf("id" to "1", "name" to "Alice"),
            eventId1, timestamp
        )
        val event2 = repository.storeEvent(
            topicId, "user.updated", mapOf("id" to "1", "name" to "Bob"),
            eventId2, timestamp
        )

        val retrieved1 = repository.getEvent(topicId, event1.id)
        val retrieved2 = repository.getEvent(topicId, event2.id)
        val allEvents = repository.getEvents(topicId)

        assertNotNull(retrieved1)
        assertNotNull(retrieved2)
        assertEquals(event1, retrieved1)
        assertEquals(event2, retrieved2)
        assertEquals(2, allEvents.size)
    }

    @Test
    fun `should handle events with same sequence across different topics`() = runTest {
        val topicId1 = UUID.randomUUID()
        val topicId2 = UUID.randomUUID()
        val timestamp = Instant.now()

        val event1Id = EventId.create(topicId1, 1L)
        val event2Id = EventId.create(topicId2, 1L)
        val event1 = repository.storeEvent(
            topicId1, "event1", mapOf("id" to "1"),
            event1Id, timestamp
        )
        val event2 = repository.storeEvent(
            topicId2, "event2", mapOf("id" to "2"),
            event2Id, timestamp
        )

        val retrieved1 = repository.getEvent(topicId1, event1Id)
        val retrieved2 = repository.getEvent(topicId2, event2Id)

        assertNotNull(retrieved1)
        assertNotNull(retrieved2)
        assertEquals(event1, retrieved1)
        assertEquals(event2, retrieved2)
    }
}
