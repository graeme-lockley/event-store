package com.eventstore.domain.services.topic

import com.eventstore.domain.Schema
import com.eventstore.domain.Topic
import com.eventstore.domain.exceptions.TopicAlreadyExistsException
import com.eventstore.domain.ports.outbound.SchemaValidator
import com.eventstore.domain.ports.outbound.TopicRepository
import com.eventstore.infrastructure.projections.NamespaceProjectionService
import java.util.*

class CreateTopicService(
    private val topicRepository: TopicRepository,
    private val schemaValidator: SchemaValidator,
    private val namespaceProjectionService: NamespaceProjectionService
) {
    suspend fun execute(
        name: String,
        schemas: List<Schema>,
        namespaceId: UUID
    ): Topic {
        // Rule C-6: At least one schema must be provided
        require(schemas.isNotEmpty()) {
            "At least one schema is required when creating a topic"
        }

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

        // Validate namespace exists
        namespaceProjectionService.getNamespaceById(namespaceId)
            ?: throw com.eventstore.domain.exceptions.NamespaceNotFoundException(namespaceId.toString())

        // Generate topicId for topic (UUIDs are globally unique, so no need to check for duplicates)
        val topicId = UUID.randomUUID()

        // Create topic
        val topic = topicRepository.createTopic(
            topicId = topicId,
            namespaceId = namespaceId,
            name = name,
            schemas = schemas
        )

        // Register schemas with validator
        schemaValidator.registerSchemas(topicId, schemas)

        return topic
    }
}

