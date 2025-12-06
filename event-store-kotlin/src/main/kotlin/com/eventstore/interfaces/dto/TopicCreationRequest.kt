package com.eventstore.interfaces.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class TopicCreationRequest(
    val name: String,
    val schemas: List<SchemaDto>
)

data class SchemaDto(
    @JsonProperty("eventType")
    val eventType: String,
    val type: String = "object",
    @JsonProperty("\$schema")
    val schema: String = "https://json-schema.org/draft/2020-12/schema",
    val properties: Map<String, Any> = emptyMap(),
    val required: List<String> = emptyList()
)

