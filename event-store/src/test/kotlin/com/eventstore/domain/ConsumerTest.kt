package com.eventstore.domain

import com.eventstore.domain.consumers.HttpConsumer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.URI
import java.util.*
import kotlin.test.assertEquals

class ConsumerTest {
    @Test
    fun `should create valid HTTP consumer`() {
        val callback = URI("https://example.com/webhook").toURL()
        val topicId = UUID.randomUUID()
        val topics = mapOf(topicId to null)

        val consumer = HttpConsumer("consumer-123", callback, topics)

        assertEquals("consumer-123", consumer.id)
        assertEquals(callback, consumer.callbackUrl)
        assertEquals(topics, consumer.topics)
        assertEquals(ConsumerType.HTTP, consumer.getType())
    }

    @Test
    fun `should throw exception for blank consumer ID`() {
        val callback = URI("https://example.com/webhook").toURL()
        val topicId = UUID.randomUUID()
        val topics = mapOf(topicId to null)

        assertThrows<IllegalArgumentException> {
            HttpConsumer("", callback, topics)
        }
    }

    @Test
    fun `should throw exception for empty topics`() {
        val callback = URI("https://example.com/webhook").toURL()

        assertThrows<IllegalArgumentException> {
            HttpConsumer("consumer-123", callback, emptyMap())
        }
    }

    @Test
    fun `should update last event ID for topic`() {
        val callback = URI("https://example.com/webhook").toURL()
        val topicId = UUID.randomUUID()
        val topics = mapOf(topicId to "event-4")
        val consumer = HttpConsumer("consumer-123", callback, topics)

        val updated = consumer.withUpdatedLastEventId(topicId, "event-5")

        assertEquals("event-5", updated.topics[topicId])
        assertEquals("consumer-123", updated.id)
        assertEquals(callback, (updated as HttpConsumer).callbackUrl)
    }

    @Test
    fun `should handle multiple topics`() {
        val callback = URI("https://example.com/webhook").toURL()
        val topicId1 = UUID.randomUUID()
        val topicId2 = UUID.randomUUID()
        val topics =
            mapOf(
                topicId1 to null,
                topicId2 to "event-10",
            )

        val consumer = HttpConsumer("consumer-123", callback, topics)

        assertEquals(2, consumer.topics.size)
        assertEquals(null, consumer.topics[topicId1])
        assertEquals("event-10", consumer.topics[topicId2])
    }

    @Test
    fun `should preserve other topics when updating one`() {
        val callback = URI("https://example.com/webhook").toURL()
        val topicId1 = UUID.randomUUID()
        val topicId2 = UUID.randomUUID()
        val topics =
            mapOf(
                topicId1 to "event-4",
                topicId2 to "event-10",
            )
        val consumer = HttpConsumer("consumer-123", callback, topics)

        val updated = consumer.withUpdatedLastEventId(topicId1, "event-5")

        assertEquals("event-5", updated.topics[topicId1])
        assertEquals("event-10", updated.topics[topicId2])
        assertEquals(2, updated.topics.size)
    }
}
