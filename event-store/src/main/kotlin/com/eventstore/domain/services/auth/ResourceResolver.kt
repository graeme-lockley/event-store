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
        return namespace.namespaceId
    }

    override suspend fun resolveTopicName(
        tenantId: UUID,
        namespaceId: UUID,
        topicName: String
    ): UUID {
        tenantProjectionService.getTenantById(tenantId)
            ?: throw TenantNameNotFoundException("tenantId: $tenantId")
        namespaceProjectionService.getNamespaceById(tenantId, namespaceId)
            ?: throw NamespaceNotFoundException("namespaceId: $namespaceId")

        // Note: This method may be deprecated in the future if API only uses UUIDs
        // For now, we need to find topic by name within the namespace
        // Since we don't have a name-based lookup anymore, we'd need to list topics by namespace and filter by name
        // However, for backward compatibility, keeping this method but it may not work with new architecture
        // If topics are only looked up by UUID, this method should be removed or throw UnsupportedOperationException
        throw UnsupportedOperationException("Topic lookup by name is no longer supported. Use topicId UUID instead.")
    }
}




