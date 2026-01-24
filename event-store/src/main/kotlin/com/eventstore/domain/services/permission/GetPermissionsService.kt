package com.eventstore.domain.services.permission

import com.eventstore.domain.PermissionGrant
import com.eventstore.infrastructure.projections.PermissionProjectionService
import java.util.*

data class GetPermissionsRequest(
    val principalId: String,
    val tenantId: UUID,
    // UUID string for filtering by specific resource (namespaceId, topicId, etc.)
    val resourceId: String? = null,
)

class GetPermissionsService(
    private val permissionProjectionService: PermissionProjectionService,
) {
    suspend fun execute(request: GetPermissionsRequest): List<PermissionGrant> {
        // Use tenantId directly (no resolution needed)
        val tenantResourceId = request.tenantId

        // Parse resourceId if provided (must be UUID string)
        val resourceId =
            request.resourceId?.let {
                try {
                    UUID.fromString(it)
                } catch (e: IllegalArgumentException) {
                    throw IllegalArgumentException("resourceId must be a valid UUID: $it")
                }
            }

        return permissionProjectionService.getPermissionGrants(
            principalId = request.principalId,
            tenantResourceId = tenantResourceId,
            // If resourceId is provided, filter by it
            namespaceResourceId = resourceId,
            // If resourceId is provided, filter by it
            topicId = resourceId,
        )
    }
}
