package com.eventstore.domain.ports.outbound

import com.eventstore.domain.exceptions.NamespaceNotFoundException
import com.eventstore.domain.exceptions.TenantNotFoundException
import com.eventstore.domain.exceptions.TopicNotFoundException
import java.util.UUID

/**
 * Port for resolving human-readable resource names to stable resourceId UUIDs.
 * Used by authorization service to convert URL parameters to resource identifiers.
 */
interface ResourceResolver {
    suspend fun resolveTenantResourceId(tenantName: String): UUID
    
    suspend fun resolveNamespaceResourceId(tenantResourceId: UUID, namespaceName: String): UUID
    
    suspend fun resolveTopicResourceId(
        tenantResourceId: UUID,
        namespaceResourceId: UUID,
        topicName: String
    ): UUID
}

