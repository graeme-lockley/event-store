package com.eventstore.domain.services.topic

import com.eventstore.domain.Schema
import com.eventstore.domain.Topic
import com.eventstore.domain.exceptions.TopicAlreadyExistsException
import com.eventstore.domain.ports.outbound.SchemaValidator
import com.eventstore.domain.ports.outbound.TopicRepository
import com.eventstore.infrastructure.projections.NamespaceProjectionService
import com.eventstore.infrastructure.projections.TenantProjectionService
import java.util.UUID

class CreateTopicService(
    private val topicRepository: TopicRepository,
    private val schemaValidator: SchemaValidator,
    private val tenantProjectionService: TenantProjectionService,
    private val namespaceProjectionService: NamespaceProjectionService
) {
    suspend fun execute(
        name: String,
        schemas: List<Schema>,
        tenantName: String = "default",
        namespaceName: String = "default"
    ): Topic {
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

        // Resolve tenant and namespace to get resourceIds
        val tenant = tenantProjectionService.getTenantByName(tenantName)
            ?: throw com.eventstore.domain.exceptions.TenantNotFoundException(tenantName)
        val namespace = namespaceProjectionService.getNamespaceByName(tenantName, namespaceName)
            ?: throw com.eventstore.domain.exceptions.NamespaceNotFoundException(namespaceName)

        // Check if topic already exists
        if (topicRepository.topicExists(name, tenantName, namespaceName)) {
            throw TopicAlreadyExistsException(name)
        }

        // Generate resourceId for topic
        val resourceId = UUID.randomUUID()

        // Create topic
        val topic = topicRepository.createTopic(
            resourceId = resourceId,
            tenantResourceId = tenant.resourceId,
            namespaceResourceId = namespace.resourceId,
            name = name,
            schemas = schemas,
            tenantName = tenantName,
            namespaceName = namespaceName
        )

        // Register schemas with validator
        schemaValidator.registerSchemas(name, schemas)

        return topic
    }
}

