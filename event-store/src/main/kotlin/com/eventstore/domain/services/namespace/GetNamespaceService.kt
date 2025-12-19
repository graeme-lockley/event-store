package com.eventstore.domain.services.namespace

import com.eventstore.infrastructure.projections.NamespaceProjectionService

class GetNamespaceService(
    private val namespaceProjectionService: NamespaceProjectionService
) {
    suspend fun getNamespace(tenantName: String, namespaceName: String) =
        namespaceProjectionService.getNamespaceByName(tenantName, namespaceName)

    suspend fun listNamespaces(tenantName: String) =
        namespaceProjectionService.getAllNamespaces().filter { it.tenantName == tenantName }
}

