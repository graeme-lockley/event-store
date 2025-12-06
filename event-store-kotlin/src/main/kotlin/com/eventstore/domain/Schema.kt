package com.eventstore.domain

/**
 * Domain entity representing a JSON schema for event validation.
 */
data class Schema(
    val eventType: String,
    val type: String = "object",
    val schema: String = "https://json-schema.org/draft/2020-12/schema",
    val properties: Map<String, Any> = emptyMap(),
    val required: List<String> = emptyList(),
    val additionalProperties: Map<String, Any> = emptyMap()
) {
    init {
        require(eventType.isNotBlank()) { "eventType is required" }
        require(schema.isNotBlank()) { "schema is required" }
    }
}

