package com.eventstore.domain.services

import com.eventstore.domain.Schema
import com.eventstore.domain.Topic
import com.eventstore.domain.exceptions.TopicAlreadyExistsException
import com.eventstore.domain.ports.outbound.SchemaValidator
import com.eventstore.domain.ports.outbound.TopicRepository

class CreateTopicService(
    private val topicRepository: TopicRepository,
    private val schemaValidator: SchemaValidator
) {
    suspend fun execute(name: String, schemas: List<Schema>): Topic {
        Schema.unique(schemas)

        // Validate schemas have required fields
        schemas.forEachIndexed { index, schema ->
            require(schema.eventType.isNotBlank()) {
                "Schema at index $index missing required 'eventType' field"
            }
            require(schema.schema.isNotBlank()) {
                "Schema at index $index missing required 'schema' field"
            }
        }

        // Check if topic already exists
        if (topicRepository.topicExists(name)) {
            throw TopicAlreadyExistsException(name)
        }

        // Create topic
        val topic = topicRepository.createTopic(name, schemas)

        // Register schemas with validator
        schemaValidator.registerSchemas(name, schemas)

        return topic
    }
}

