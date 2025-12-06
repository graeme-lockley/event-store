package com.eventstore.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class EventIdTest {
    
    @Test
    fun `should create valid EventId`() {
        val eventId = EventId.create("user-events", 42L)
        assertEquals("user-events-42", eventId.value)
        assertEquals("user-events", eventId.topic)
        assertEquals(42L, eventId.sequence)
    }
    
    @Test
    fun `should parse topic with multiple hyphens`() {
        val eventId = EventId("my-topic-name-123")
        assertEquals("my-topic-name", eventId.topic)
        assertEquals(123L, eventId.sequence)
    }
    
    @Test
    fun `should extract topic correctly`() {
        val eventId = EventId("user-events-1")
        assertEquals("user-events", eventId.topic)
    }
    
    @Test
    fun `should extract sequence correctly`() {
        val eventId = EventId("user-events-999")
        assertEquals(999L, eventId.sequence)
    }
    
    @Test
    fun `should throw exception for invalid format - no hyphen`() {
        assertThrows<IllegalArgumentException> {
            EventId("userevents123")
        }
    }
    
    @Test
    fun `should throw exception for invalid format - no sequence`() {
        assertThrows<IllegalArgumentException> {
            EventId("user-events-")
        }
    }
    
    @Test
    fun `should throw exception for invalid format - non-numeric sequence`() {
        assertThrows<IllegalArgumentException> {
            EventId("user-events-abc")
        }
    }
    
    @Test
    fun `should handle large sequence numbers`() {
        val eventId = EventId.create("topic", Long.MAX_VALUE)
        assertEquals(Long.MAX_VALUE, eventId.sequence)
    }
    
    @Test
    fun `should handle zero sequence`() {
        val eventId = EventId.create("topic", 0L)
        assertEquals(0L, eventId.sequence)
    }
}

