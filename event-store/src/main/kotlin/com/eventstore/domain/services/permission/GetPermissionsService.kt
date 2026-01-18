package com.eventstore.domain.services.permission

import com.eventstore.domain.PermissionGrant
import com.eventstore.domain.ports.outbound.ResourceResolver
import com.eventstore.infrastructure.projections.PermissionProjectionService
import java.util.*

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
        val tenantResourceId = resourceResolver.resolveTenantName(request.tenantName)

        // Resolve namespace resourceId if provided
        val namespaceResourceId = request.namespaceName?.let {
            resourceResolver.resolveNamespaceName(tenantResourceId, it)
        }

        // If topicName is provided, it should be a UUID string (API uses UUIDs)
        val topicId = request.topicName?.let {
            requireNotNull(namespaceResourceId) { "Namespace required when getting topic permissions" }
            // topicName is actually a UUID string when provided from API
            try {
                UUID.fromString(it)
            } catch (e: IllegalArgumentException) {
                throw com.eventstore.domain.exceptions.TopicNotFoundException(it)
            }
        }

        return permissionProjectionService.getPermissionGrants(
            principalId = request.principalId,
            tenantResourceId = tenantResourceId,
            namespaceResourceId = namespaceResourceId,
            topicId = topicId
        )
    }
}



