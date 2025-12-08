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

    companion object {
        fun unique(schemas: List<Schema>) {
            val schemaNames = mutableSetOf<String>()

            schemas.forEach { schema ->
                if (!schemaNames.add(schema.eventType)) {
                    throw IllegalArgumentException("Duplicate eventType found: ${schema.eventType}")
                }
            }
        }
    }

}