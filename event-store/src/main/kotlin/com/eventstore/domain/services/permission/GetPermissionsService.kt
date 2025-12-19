package com.eventstore.domain.services.permission

import com.eventstore.domain.PermissionGrant
import com.eventstore.domain.ports.outbound.ResourceResolver
import com.eventstore.infrastructure.projections.PermissionProjectionService
import java.util.UUID

data class GetPermissionsRequest(
    val principalId: String,
    val tenantName: String,
    val namespaceName: String? = null,
    val topicName: String? = null
)

class GetPermissionsService(
    private val permissionProjectionService: PermissionProjectionService,
    private val resourceResolver: ResourceResolver
) {
    suspend fun execute(request: GetPermissionsRequest): List<PermissionGrant> {
        // Resolve tenant resourceId
        val tenantResourceId = resourceResolver.resolveTenantResourceId(request.tenantName)
        
        // Resolve namespace resourceId if provided
        val namespaceResourceId = request.namespaceName?.let {
            resourceResolver.resolveNamespaceResourceId(tenantResourceId, it)
        }
        
        // Resolve topic resourceId if provided
        val topicResourceId = request.topicName?.let {
            requireNotNull(namespaceResourceId) { "Namespace required when getting topic permissions" }
            resourceResolver.resolveTopicResourceId(tenantResourceId, namespaceResourceId, it)
        }
        
        return permissionProjectionService.getPermissionGrants(
            principalId = request.principalId,
            tenantResourceId = tenantResourceId,
            namespaceResourceId = namespaceResourceId,
            topicResourceId = topicResourceId
        )
    }
}

