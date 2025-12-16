package com.eventstore.domain

import com.eventstore.domain.consumers.HttpConsumer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.URI
import kotlin.test.assertEquals

class ConsumerTest {

    @Test
    fun `should create valid HTTP consumer`() {
        val callback = URI("https://example.com/webhook").toURL()
        val topics = mapOf("user-events" to null)

        val consumer = HttpConsumer("consumer-123", callback, topics)

        assertEquals("consumer-123", consumer.id)
        assertEquals(callback, consumer.callbackUrl)
        assertEquals(topics, consumer.topics)
        assertEquals(ConsumerType.HTTP, consumer.getType())
    }

    @Test
    fun `should throw exception for blank consumer ID`() {
        val callback = URI("https://example.com/webhook").toURL()
        val topics = mapOf("user-events" to null)

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
        val topics = mapOf("user-events" to "user-events-4")
        val consumer = HttpConsumer("consumer-123", callback, topics)

        val updated = consumer.withUpdatedLastEventId("user-events", "user-events-5")

        assertEquals("user-events-5", updated.topics["user-events"])
        assertEquals("consumer-123", updated.id)
        assertEquals(callback, (updated as HttpConsumer).callbackUrl)
    }

    @Test
    fun `should handle multiple topics`() {
        val callback = URI("https://example.com/webhook").toURL()
        val topics = mapOf(
            "user-events" to null,
            "order-events" to "order-events-10"
        )

        val consumer = HttpConsumer("consumer-123", callback, topics)

        assertEquals(2, consumer.topics.size)
        assertEquals(null, consumer.topics["user-events"])
        assertEquals("order-events-10", consumer.topics["order-events"])
    }

    @Test
    fun `should preserve other topics when updating one`() {
        val callback = URI("https://example.com/webhook").toURL()
        val topics = mapOf(
            "user-events" to "user-events-4",
            "order-events" to "order-events-10"
        )
        val consumer = HttpConsumer("consumer-123", callback, topics)

        val updated = consumer.withUpdatedLastEventId("user-events", "user-events-5")

        assertEquals("user-events-5", updated.topics["user-events"])
        assertEquals("order-events-10", updated.topics["order-events"])
        assertEquals(2, updated.topics.size)
    }
}
