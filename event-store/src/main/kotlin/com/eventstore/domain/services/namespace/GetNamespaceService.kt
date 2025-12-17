package com.eventstore.domain.services.namespace

import com.eventstore.infrastructure.projections.NamespaceProjectionService

class GetNamespaceService(
    private val namespaceProjectionService: NamespaceProjectionService
) {
    suspend fun getNamespace(tenantId: String, namespaceId: String) =
        namespaceProjectionService.getNamespace(tenantId, namespaceId)

    suspend fun listNamespaces(tenantId: String) =
        namespaceProjectionService.getAllNamespaces().filter { it.tenantId == tenantId }
}

