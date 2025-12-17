package com.eventstore.domain.services.topic

import com.eventstore.domain.Schema
import com.eventstore.domain.Topic
import com.eventstore.domain.exceptions.TopicNotFoundException
import com.eventstore.domain.ports.outbound.SchemaValidator
import com.eventstore.domain.ports.outbound.TopicRepository

class UpdateTopicSchemasService(
    private val topicRepository: TopicRepository,
    private val schemaValidator: SchemaValidator
) {
    suspend fun execute(
        topicName: String,
        newSchemas: List<Schema>,
        tenantId: String = "default",
        namespaceId: String = "default"
    ): Topic {
        Schema.unique(newSchemas)

        // Validate schemas have required fields
        newSchemas.forEachIndexed { index, schema ->
            require(schema.eventType.isNotBlank()) {
                "Schema at index $index missing required 'eventType' field"
            }
            require(schema.schema.isNotBlank()) {
                "Schema at index $index missing required 'schema' field"
            }
        }

        // Load current topic
        val currentTopic = topicRepository.getTopic(topicName, tenantId, namespaceId)
            ?: throw TopicNotFoundException(topicName)

        // Extract existing eventTypes
        val existingTypes = currentTopic.schemas.map { it.eventType }.toSet()
        val newTypes = newSchemas.map { it.eventType }.toSet()

        // Verify additive constraint: all existing eventTypes must be present
        val missingTypes = existingTypes - newTypes
        if (missingTypes.isNotEmpty()) {
            throw IllegalArgumentException(
                "Cannot remove schemas. Missing eventTypes: ${missingTypes.joinToString(", ")}"
            )
        }

        // Update schemas
        val updatedTopic = topicRepository.updateSchemas(topicName, newSchemas, tenantId, namespaceId)

        // Re-register schemas with validator
        schemaValidator.registerSchemas(topicName, newSchemas)

        return updatedTopic
    }
}

