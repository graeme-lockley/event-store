package com.eventstore.domain.services.auth

import com.eventstore.domain.exceptions.NamespaceNotFoundException
import com.eventstore.domain.exceptions.TenantNotFoundException
import com.eventstore.domain.exceptions.TopicNotFoundException
import com.eventstore.domain.ports.outbound.ResourceResolver
import com.eventstore.domain.ports.outbound.TopicRepository
import com.eventstore.infrastructure.projections.NamespaceProjectionService
import com.eventstore.infrastructure.projections.TenantProjectionService
import java.util.UUID

/**
 * Implementation of ResourceResolver that resolves human-readable names to resourceId UUIDs.
 */
class ResourceResolverImpl(
    private val tenantProjectionService: TenantProjectionService,
    private val namespaceProjectionService: NamespaceProjectionService,
    private val topicRepository: TopicRepository
) : ResourceResolver {
    
    override suspend fun resolveTenantResourceId(tenantName: String): UUID {
        val tenant = tenantProjectionService.getTenantByName(tenantName)
            ?: throw TenantNotFoundException(tenantName)
        return tenant.resourceId
    }
    
    override suspend fun resolveNamespaceResourceId(tenantResourceId: UUID, namespaceName: String): UUID {
        val tenant = tenantProjectionService.getTenantByResourceId(tenantResourceId)
            ?: throw TenantNotFoundException("ResourceId: $tenantResourceId")
        val namespace = namespaceProjectionService.getNamespaceByName(tenant.name, namespaceName)
            ?: throw NamespaceNotFoundException(namespaceName)
        return namespace.resourceId
    }
    
    override suspend fun resolveTopicResourceId(
        tenantResourceId: UUID,
        namespaceResourceId: UUID,
        topicName: String
    ): UUID {
        val tenant = tenantProjectionService.getTenantByResourceId(tenantResourceId)
            ?: throw TenantNotFoundException("ResourceId: $tenantResourceId")
        val namespace = namespaceProjectionService.getNamespaceByResourceId(tenantResourceId, namespaceResourceId)
            ?: throw NamespaceNotFoundException("ResourceId: $namespaceResourceId")
        val topic = topicRepository.getTopic(topicName, tenant.name, namespace.name)
            ?: throw TopicNotFoundException(topicName)
        return topic.resourceId
    }
}

