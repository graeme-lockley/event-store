package com.eventstore.infrastructure.persistence

import com.eventstore.domain.Consumer
import com.eventstore.domain.consumers.HttpConsumer
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.net.URI
import kotlin.test.*

class InMemoryConsumerRepositoryTest {

    private val repository = InMemoryConsumerRepository()

    @Test
    fun `should save and find consumer`() = runTest {
        val consumer = HttpConsumer(
            id = "consumer-1",
            callbackUrl = URI("https://example.com/webhook").toURL(),
            topics = mapOf("user-events" to null)
        )

        repository.save(consumer)
        val found = repository.findById("consumer-1")

        assertNotNull(found)
        assertEquals(consumer.id, found.id)
        assertEquals(consumer.topics, found.topics)
    }

    @Test
    fun `should return null when consumer not found`() = runTest {
        val found = repository.findById("unknown")
        assertNull(found)
    }

    @Test
    fun `should find all consumers`() = runTest {
        val consumer1 = HttpConsumer("consumer-1", URI("https://example.com/webhook1").toURL(), mapOf("topic1" to null))
        val consumer2 = HttpConsumer("consumer-2", URI("https://example.com/webhook2").toURL(), mapOf("topic2" to null))

        repository.save(consumer1)
        repository.save(consumer2)

        val all = repository.findAll()

        assertEquals(2, all.size)
        assertTrue(all.any { it.id == consumer1.id })
        assertTrue(all.any { it.id == consumer2.id })
    }

    @Test
    fun `should find consumers by topic`() = runTest {
        val consumer1 =
            HttpConsumer("consumer-1", URI("https://example.com/webhook1").toURL(), mapOf("user-events" to null))
        val consumer2 =
            HttpConsumer("consumer-2", URI("https://example.com/webhook2").toURL(), mapOf("order-events" to null))
        val consumer3 =
            HttpConsumer("consumer-3", URI("https://example.com/webhook3").toURL(), mapOf("user-events" to "user-events-5"))

        repository.save(consumer1)
        repository.save(consumer2)
        repository.save(consumer3)

        val found = repository.findByTopic("user-events")

        assertEquals(2, found.size)
        assertTrue(found.any { it.id == consumer1.id })
        assertTrue(found.any { it.id == consumer3.id })
        assertFalse(found.any { it.id == consumer2.id })
    }

    @Test
    fun `should delete consumer`() = runTest {
        val consumer = HttpConsumer("consumer-1", URI("https://example.com/webhook").toURL(), mapOf("topic1" to null))

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

        repository.save(HttpConsumer("consumer-1", URI("https://example.com/webhook1").toURL(), mapOf("topic1" to null)))
        assertEquals(1, repository.count())

        repository.save(HttpConsumer("consumer-2", URI("https://example.com/webhook2").toURL(), mapOf("topic2" to null)))
        assertEquals(2, repository.count())

        repository.delete("consumer-1")
        assertEquals(1, repository.count())
    }

    @Test
    fun `should handle concurrent operations`() = runTest {
        val consumers = (1..10).map { i ->
            HttpConsumer("consumer-$i", URI("https://example.com/webhook$i").toURL(), mapOf("topic$i" to null))
        }

        consumers.forEach { repository.save(it) }

        assertEquals(10, repository.count())
        assertEquals(10, repository.findAll().size)
    }
}
