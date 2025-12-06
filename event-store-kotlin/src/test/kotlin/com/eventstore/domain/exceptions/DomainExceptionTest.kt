package com.eventstore.domain.exceptions

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DomainExceptionTest {

    @Test
    fun `TopicNotFoundException should contain topic name`() {
        val exception = TopicNotFoundException("user-events")
        assertEquals("Topic 'user-events' not found", exception.message)
    }

    @Test
    fun `TopicAlreadyExistsException should contain topic name`() {
        val exception = TopicAlreadyExistsException("user-events")
        assertEquals("Topic 'user-events' already exists", exception.message)
    }

    @Test
    fun `SchemaValidationException should contain error message`() {
        val exception = SchemaValidationException("Invalid property")
        assertEquals("Schema validation failed: Invalid property", exception.message)
    }

    @Test
    fun `SchemaValidationException should preserve cause`() {
        val cause = IllegalArgumentException("Root cause")
        val exception = SchemaValidationException("Invalid property", cause)
        assertEquals(cause, exception.cause)
    }

    @Test
    fun `SchemaNotFoundException should contain topic and eventType`() {
        val exception = SchemaNotFoundException("user-events", "user.created")
        assertEquals("No schema found for topic 'user-events' and type 'user.created'", exception.message)
    }

    @Test
    fun `InvalidEventPayloadException should contain message`() {
        val exception = InvalidEventPayloadException("Payload must be an object")
        assertEquals("Invalid event payload: Payload must be an object", exception.message)
    }

    @Test
    fun `ConsumerNotFoundException should contain consumer ID`() {
        val exception = ConsumerNotFoundException("consumer-123")
        assertEquals("Consumer 'consumer-123' not found", exception.message)
    }

    @Test
    fun `InvalidConsumerRegistrationException should contain message`() {
        val exception = InvalidConsumerRegistrationException("Invalid callback URL")
        assertEquals("Invalid consumer registration: Invalid callback URL", exception.message)
    }
}

