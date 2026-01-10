package com.eventstore.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class EventIdTest {

    @Test
    fun `should create valid EventId`() {
        val eventId = EventId.create("user-events", 42L, "default", "default")
        assertEquals("default/default/user-events/42", eventId.value)
        assertEquals("user-events", eventId.topicId)
        assertEquals(42L, eventId.sequence)
    }

    @Test
    fun `should parse topic with multiple hyphens`() {
        val eventId = EventId("default/default/my-topic-name/123")
        assertEquals("my-topic-name", eventId.topicId)
        assertEquals(123L, eventId.sequence)
        assertEquals("default", eventId.tenantId)
        assertEquals("default", eventId.namespaceId)
    }

    @Test
    fun `should extract topic correctly`() {
        val eventId = EventId("default/default/user-events/1")
        assertEquals("user-events", eventId.topicId)
        assertEquals("default", eventId.tenantId)
        assertEquals("default", eventId.namespaceId)
    }

    @Test
    fun `should extract sequence correctly`() {
        val eventId = EventId("default/default/user-events/999")
        assertEquals(999L, eventId.sequence)
        assertEquals("default", eventId.tenantId)
        assertEquals("default", eventId.namespaceId)
    }

    @Test
    fun `should throw exception for invalid format - no tenant namespace`() {
        assertThrows<IllegalArgumentException> {
            EventId("userevents123")
        }
    }

    @Test
    fun `should throw exception for invalid format - missing namespace`() {
        assertThrows<IllegalArgumentException> {
            EventId("tenant/user-events/")
        }
    }

    @Test
    fun `should throw exception for invalid format - non-numeric sequence`() {
        assertThrows<IllegalArgumentException> {
            EventId("default/default/user-events/abc")
        }
    }

    @Test
    fun `should handle different tenant and namespace`() {
        val eventId = EventId("acme/production/users/42")
        assertEquals("users", eventId.topicId)
        assertEquals(42L, eventId.sequence)
        assertEquals("acme", eventId.tenantId)
        assertEquals("production", eventId.namespaceId)
    }

    @Test
    fun `should handle large sequence numbers`() {
        val eventId = EventId.create("topic", Long.MAX_VALUE, "default", "default")
        assertEquals(Long.MAX_VALUE, eventId.sequence)
    }

    @Test
    fun `should handle zero sequence`() {
        val eventId = EventId.create("topic", 0L, "default", "default")
        assertEquals(0L, eventId.sequence)
    }
}

