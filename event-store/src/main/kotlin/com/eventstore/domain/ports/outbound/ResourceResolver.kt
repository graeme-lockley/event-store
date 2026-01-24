package com.eventstore.domain.ports.outbound

import java.util.*

/**
 * Port for resolving human-readable resource names to stable resourceId UUIDs.
 * Used by authorization service to convert URL parameters to resource identifiers.
 */
interface ResourceResolver {
    suspend fun resolveTenantName(tenantName: String): UUID

    suspend fun resolveNamespaceName(
        tenantId: UUID,
        namespaceName: String,
    ): UUID

    suspend fun resolveTopicName(
        tenantId: UUID,
        namespaceId: UUID,
        topicName: String,
    ): UUID
}
