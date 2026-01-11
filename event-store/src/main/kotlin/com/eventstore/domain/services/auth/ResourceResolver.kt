package com.eventstore.domain.services.auth

import com.eventstore.domain.exceptions.NamespaceNotFoundException
import com.eventstore.domain.exceptions.TenantNameNotFoundException
import com.eventstore.domain.exceptions.TopicNotFoundException
import com.eventstore.domain.ports.outbound.ResourceResolver
import com.eventstore.domain.ports.outbound.TopicRepository
import com.eventstore.infrastructure.projections.NamespaceProjectionService
import com.eventstore.infrastructure.projections.TenantProjectionService
import java.util.*

/**
 * Implementation of ResourceResolver that resolves human-readable names to resourceId UUIDs.
 */
class ResourceResolverImpl(
    private val tenantProjectionService: TenantProjectionService,
    private val namespaceProjectionService: NamespaceProjectionService,
    private val topicRepository: TopicRepository
) : ResourceResolver {

    override suspend fun resolveTenantName(tenantName: String): UUID {
        val tenant = tenantProjectionService.getTenantByName(tenantName)
            ?: throw TenantNameNotFoundException(tenantName)
        return tenant.tenantId
    }

    override suspend fun resolveNamespaceName(tenantId: UUID, namespaceName: String): UUID {
        val tenant = tenantProjectionService.getTenantById(tenantId)
            ?: throw TenantNameNotFoundException("tenantId: $tenantId")
        val namespace = namespaceProjectionService.getNamespaceByName(tenant.name, namespaceName)
            ?: throw NamespaceNotFoundException(namespaceName)
        return namespace.resourceId
    }

    override suspend fun resolveTopicName(
        tenantId: UUID,
        namespaceId: UUID,
        topicName: String
    ): UUID {
        val tenant = tenantProjectionService.getTenantById(tenantId)
            ?: throw TenantNameNotFoundException("tenantId: $tenantId")
        val namespace = namespaceProjectionService.getNamespaceByResourceId(tenantId, namespaceId)
            ?: throw NamespaceNotFoundException("namespaceId: $namespaceId")
        val topic = topicRepository.getTopic(topicName, tenant.name, namespace.name)
            ?: throw TopicNotFoundException(topicName)
        return topic.resourceId
    }
}




