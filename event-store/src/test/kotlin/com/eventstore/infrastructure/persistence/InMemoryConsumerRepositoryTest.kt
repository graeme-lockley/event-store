package com.eventstore.infrastructure.persistence

import com.eventstore.domain.Consumer
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.net.URI
import kotlin.test.*

class InMemoryConsumerRepositoryTest {

    private val repository = InMemoryConsumerRepository()

    @Test
    fun `should save and find consumer`() = runTest {
        val consumer = Consumer(
            id = "consumer-1",
            callback = URI("https://example.com/webhook").toURL(),
            topics = mapOf("user-events" to null)
        )

        repository.save(consumer)
        val found = repository.findById("consumer-1")

        assertNotNull(found)
        assertEquals(consumer, found)
    }

    @Test
    fun `should return null when consumer not found`() = runTest {
        val found = repository.findById("unknown")
        assertNull(found)
    }

    @Test
    fun `should find all consumers`() = runTest {
        val consumer1 = Consumer("consumer-1", URI("https://example.com/webhook1").toURL(), mapOf("topic1" to null))
        val consumer2 = Consumer("consumer-2", URI("https://example.com/webhook2").toURL(), mapOf("topic2" to null))

        repository.save(consumer1)
        repository.save(consumer2)

        val all = repository.findAll()

        assertEquals(2, all.size)
        assertTrue(all.contains(consumer1))
        assertTrue(all.contains(consumer2))
    }

    @Test
    fun `should find consumers by topic`() = runTest {
        val consumer1 =
            Consumer("consumer-1", URI("https://example.com/webhook1").toURL(), mapOf("user-events" to null))
        val consumer2 =
            Consumer("consumer-2", URI("https://example.com/webhook2").toURL(), mapOf("order-events" to null))
        val consumer3 =
            Consumer("consumer-3", URI("https://example.com/webhook3").toURL(), mapOf("user-events" to "user-events-5"))

        repository.save(consumer1)
        repository.save(consumer2)
        repository.save(consumer3)

        val found = repository.findByTopic("user-events")

        assertEquals(2, found.size)
        assertTrue(found.contains(consumer1))
        assertTrue(found.contains(consumer3))
        assertFalse(found.contains(consumer2))
    }

    @Test
    fun `should delete consumer`() = runTest {
        val consumer = Consumer("consumer-1", URI("https://example.com/webhook").toURL(), mapOf("topic1" to null))

        repository.save(consumer)
        val deleted = repository.delete("consumer-1")

        assertTrue(deleted)
        assertNull(repository.findById("consumer-1"))
    }

    @Test
    fun `should return false when deleting non-existent consumer`() = runTest {
        val deleted = repository.delete("unknown")
        assertFalse(deleted)
    }

    @Test
    fun `should return correct count`() = runTest {
        assertEquals(0, repository.count())

        repository.save(Consumer("consumer-1", URI("https://example.com/webhook1").toURL(), mapOf("topic1" to null)))
        assertEquals(1, repository.count())

        repository.save(Consumer("consumer-2", URI("https://example.com/webhook2").toURL(), mapOf("topic2" to null)))
        assertEquals(2, repository.count())

        repository.delete("consumer-1")
        assertEquals(1, repository.count())
    }

    @Test
    fun `should handle concurrent operations`() = runTest {
        val consumers = (1..10).map { i ->
            Consumer("consumer-$i", URI("https://example.com/webhook$i").toURL(), mapOf("topic$i" to null))
        }

        consumers.forEach { repository.save(it) }

        assertEquals(10, repository.count())
        assertEquals(10, repository.findAll().size)
    }
}

