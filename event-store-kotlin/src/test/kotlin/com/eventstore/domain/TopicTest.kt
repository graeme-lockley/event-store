package com.eventstore.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TopicTest {

    @Test
    fun `should create valid topic`() {
        val schemas = listOf(
            Schema(eventType = "user.created", properties = mapOf("id" to "string"))
        )
        val topic = Topic("user-events", 0L, schemas)

        assertEquals("user-events", topic.name)
        assertEquals(0L, topic.sequence)
        assertEquals(schemas, topic.schemas)
    }

    @Test
    fun `should throw exception for blank topic name`() {
        val schemas = listOf(Schema(eventType = "user.created"))

        assertThrows<IllegalArgumentException> {
            Topic("", 0L, schemas)
        }
    }

    @Test
    fun `should throw exception for negative sequence`() {
        val schemas = listOf(Schema(eventType = "user.created"))

        assertThrows<IllegalArgumentException> {
            Topic("user-events", -1L, schemas)
        }
    }

    @Test
    fun `should calculate next sequence`() {
        val topic = Topic("user-events", 5L, emptyList())
        assertEquals(6L, topic.nextSequence())
    }

    @Test
    fun `should update sequence`() {
        val topic = Topic("user-events", 5L, emptyList())
        val updated = topic.updateSequence(10L)

        assertEquals(10L, updated.sequence)
        assertEquals("user-events", updated.name)
        assertEquals(emptyList(), updated.schemas)
    }

    @Test
    fun `should update schemas`() {
        val originalSchemas = listOf(Schema(eventType = "user.created"))
        val newSchemas = listOf(
            Schema(eventType = "user.created"),
            Schema(eventType = "user.updated")
        )

        val topic = Topic("user-events", 0L, originalSchemas)
        val updated = topic.updateSchemas(newSchemas)

        assertEquals(newSchemas, updated.schemas)
        assertEquals("user-events", updated.name)
        assertEquals(0L, updated.sequence)
    }

    @Test
    fun `should accept empty schemas list`() {
        val topic = Topic("user-events", 0L, emptyList())
        assertTrue(topic.schemas.isEmpty())
    }

    @Test
    fun `should accept zero sequence`() {
        val topic = Topic("user-events", 0L, emptyList())
        assertEquals(0L, topic.sequence)
    }
}

