package com.eventstore.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import kotlin.test.assertEquals

class EventTest {

    @Test
    fun `should create valid event`() {
        val eventId = EventId.create("user-events", 1L)
        val timestamp = Instant.now()
        val payload = mapOf("id" to "123", "name" to "Alice")

        val event = Event(eventId, timestamp, "user.created", payload)

        assertEquals(eventId, event.id)
        assertEquals(timestamp, event.timestamp)
        assertEquals("user.created", event.type)
        assertEquals(payload, event.payload)
    }

    @Test
    fun `should throw exception for blank event type`() {
        val eventId = EventId.create("user-events", 1L)
        val timestamp = Instant.now()
        val payload = mapOf<String, Any>()

        assertThrows<IllegalArgumentException> {
            Event(eventId, timestamp, "", payload)
        }
    }

    @Test
    fun `should throw exception for whitespace-only event type`() {
        val eventId = EventId.create("user-events", 1L)
        val timestamp = Instant.now()
        val payload = mapOf<String, Any>()

        assertThrows<IllegalArgumentException> {
            Event(eventId, timestamp, "   ", payload)
        }
    }

    @Test
    fun `should accept empty payload`() {
        val eventId = EventId.create("user-events", 1L)
        val timestamp = Instant.now()
        val payload = emptyMap<String, Any>()

        val event = Event(eventId, timestamp, "user.created", payload)
        assertEquals(emptyMap(), event.payload)
    }

    @Test
    fun `should accept complex payload`() {
        val eventId = EventId.create("user-events", 1L)
        val timestamp = Instant.now()
        val payload = mapOf(
            "id" to "123",
            "name" to "Alice",
            "age" to 30,
            "active" to true,
            "tags" to listOf("admin", "user")
        )

        val event = Event(eventId, timestamp, "user.created", payload)
        assertEquals(payload, event.payload)
    }
}

