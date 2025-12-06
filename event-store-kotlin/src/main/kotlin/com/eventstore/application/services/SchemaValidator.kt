package com.eventstore.application.services

import com.eventstore.domain.Schema

/**
 * Service interface for validating events against JSON schemas.
 */
interface SchemaValidator {
    fun registerSchemas(topic: String, schemas: List<Schema>)
    fun validateEvent(topic: String, eventType: String, payload: Map<String, Any>)
    fun hasSchema(topic: String, eventType: String): Boolean
}

