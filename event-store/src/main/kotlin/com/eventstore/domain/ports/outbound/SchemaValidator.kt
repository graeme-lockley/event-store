package com.eventstore.domain.ports.outbound

import com.eventstore.domain.Schema

/**
 * Outbound port for validating events against JSON schemas.
 */
interface SchemaValidator {
    fun registerSchemas(topic: String, schemas: List<Schema>)
    fun validateEvent(topic: String, eventType: String, payload: Map<String, Any>)
    fun hasSchema(topic: String, eventType: String): Boolean
}

