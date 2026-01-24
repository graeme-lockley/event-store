package com.eventstore.domain.ports.outbound

import com.eventstore.domain.Schema
import java.util.*

/**
 * Outbound port for validating events against JSON schemas.
 */
interface SchemaValidator {
    fun registerSchemas(
        topicId: UUID,
        schemas: List<Schema>,
    )

    fun validateEvent(
        topicId: UUID,
        eventType: String,
        payload: Map<String, Any>,
    )

    fun hasSchema(
        topicId: UUID,
        eventType: String,
    ): Boolean
}
