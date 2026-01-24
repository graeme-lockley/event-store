package com.eventstore.domain.services.namespace

import com.eventstore.infrastructure.projections.NamespaceProjectionService
import java.util.*

class GetNamespaceService(
    private val namespaceProjectionService: NamespaceProjectionService,
) {
    suspend fun getNamespace(namespaceId: UUID) = namespaceProjectionService.getNamespaceById(namespaceId)

    suspend fun getNamespaceByName(
        tenantName: String,
        namespaceName: String,
    ) = namespaceProjectionService.getNamespaceByName(tenantName, namespaceName)

    suspend fun listNamespaces(tenantId: UUID? = null): List<com.eventstore.domain.Namespace> {
        val all = namespaceProjectionService.getAllNamespaces()
        return if (tenantId != null) {
            all.filter { it.tenantId == tenantId }
        } else {
            all
        }
    }
}
