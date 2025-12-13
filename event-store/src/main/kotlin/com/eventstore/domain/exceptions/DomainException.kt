package com.eventstore.domain.exceptions

/**
 * Base exception for domain errors.
 */
open class DomainException(message: String, cause: Throwable? = null) : Exception(message, cause)

class TopicNotFoundException(topic: String) : DomainException("Topic '$topic' not found")

class TopicAlreadyExistsException(topic: String) : DomainException("Topic '$topic' already exists")

class SchemaValidationException(message: String, cause: Throwable? = null) :
    DomainException("Schema validation failed: $message", cause)

class SchemaNotFoundException(topic: String, eventType: String) :
    DomainException("No schema found for topic '$topic' and type '$eventType'")

class InvalidEventPayloadException(message: String) : DomainException("Invalid event payload: $message")

class ConsumerNotFoundException(consumerId: String) : DomainException("Consumer '$consumerId' not found")

class InvalidConsumerRegistrationException(message: String) : DomainException("Invalid consumer registration: $message")

