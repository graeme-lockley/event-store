package com.eventstore.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*
import kotlin.test.assertEquals

class EventIdTest {

    @Test
    fun `should create valid EventId`() {
        val topicId = UUID.randomUUID()
        val eventId = EventId.create(topicId, 42L)
        assertEquals("$topicId/42", eventId.value)
        assertEquals(topicId, eventId.topicId)
        assertEquals(42L, eventId.sequence)
    }

    @Test
    fun `should parse EventId from string`() {
        val topicId = UUID.randomUUID()
        val eventId = EventId.fromString("$topicId/123")
        assertEquals(topicId, eventId.topicId)
        assertEquals(123L, eventId.sequence)
    }

    @Test
    fun `should extract topicId correctly`() {
        val topicId = UUID.randomUUID()
        val eventId = EventId.fromString("$topicId/1")
        assertEquals(topicId, eventId.topicId)
    }

    @Test
    fun `should extract sequence correctly`() {
        val topicId = UUID.randomUUID()
        val eventId = EventId.fromString("$topicId/999")
        assertEquals(999L, eventId.sequence)
    }

    @Test
    fun `should throw exception for invalid format - no separator`() {
        assertThrows<IllegalArgumentException> {
            EventId.fromString("invalid")
        }
    }

    @Test
    fun `should throw exception for invalid format - missing sequence`() {
        val topicId = UUID.randomUUID()
        assertThrows<IllegalArgumentException> {
            EventId.fromString("$topicId/")
        }
    }

    @Test
    fun `should throw exception for invalid format - non-numeric sequence`() {
        val topicId = UUID.randomUUID()
        assertThrows<IllegalArgumentException> {
            EventId.fromString("$topicId/abc")
        }
    }

    @Test
    fun `should throw exception for invalid UUID format`() {
        assertThrows<IllegalArgumentException> {
            EventId.fromString("invalid-uuid/42")
        }
    }

    @Test
    fun `should handle different topic IDs`() {
        val topicId1 = UUID.randomUUID()
        val topicId2 = UUID.randomUUID()
        val eventId1 = EventId.fromString("$topicId1/42")
        val eventId2 = EventId.fromString("$topicId2/42")
        assertEquals(topicId1, eventId1.topicId)
        assertEquals(topicId2, eventId2.topicId)
        assertEquals(42L, eventId1.sequence)
        assertEquals(42L, eventId2.sequence)
    }

    @Test
    fun `should handle large sequence numbers`() {
        val topicId = UUID.randomUUID()
        val eventId = EventId.create(topicId, Long.MAX_VALUE)
        assertEquals(Long.MAX_VALUE, eventId.sequence)
    }

    @Test
    fun `should handle zero sequence`() {
        val topicId = UUID.randomUUID()
        val eventId = EventId.create(topicId, 0L)
        assertEquals(0L, eventId.sequence)
    }

    @Test
    fun `should throw exception for negative sequence`() {
        val topicId = UUID.randomUUID()
        assertThrows<IllegalArgumentException> {
            EventId.create(topicId, -1L)
        }
    }

    @Test
    fun `should format value correctly`() {
        val topicId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val eventId = EventId.create(topicId, 42L)
        assertEquals("550e8400-e29b-41d4-a716-446655440000/42", eventId.value)
    }
}
