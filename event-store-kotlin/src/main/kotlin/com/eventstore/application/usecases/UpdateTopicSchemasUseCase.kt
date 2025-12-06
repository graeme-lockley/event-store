package com.eventstore.application.usecases

import com.eventstore.application.repositories.TopicRepository
import com.eventstore.application.services.SchemaValidator
import com.eventstore.domain.Schema
import com.eventstore.domain.exceptions.TopicNotFoundException

class UpdateTopicSchemasUseCase(
    private val topicRepository: TopicRepository,
    private val schemaValidator: SchemaValidator
) {
    suspend fun execute(topicName: String, newSchemas: List<Schema>): com.eventstore.domain.Topic {
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
        val currentTopic = topicRepository.getTopic(topicName)
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
        val updatedTopic = topicRepository.updateSchemas(topicName, newSchemas)

        // Re-register schemas with validator
        schemaValidator.registerSchemas(topicName, newSchemas)

        return updatedTopic
    }
}

