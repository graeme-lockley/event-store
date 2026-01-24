package com.eventstore.domain.services.auth

import com.eventstore.domain.Permission
import com.eventstore.domain.ResourceType
import com.eventstore.domain.ports.outbound.ResourceResolver
import com.eventstore.infrastructure.projections.PermissionProjectionService
import java.util.*

/**
 * Service that checks if a principal has permission to perform an action on a resource.
 * Resolves human-readable names from URLs to resourceId UUIDs and checks permissions.
 */
class AuthorizationService(
    private val permissionProjectionService: PermissionProjectionService,
    private val resourceResolver: ResourceResolver,
) {
    /**
     * Check if a principal has permission to perform an action.
     *
     * @param principalId UUID of the user/API key/role/group
     * @param resourceType Type of resource being accessed
     * @param resourceName Human-readable name from URL (optional, for specific resource checks)
     * @param requiredPermission Permission required to perform the action
     * @param tenantName Human-readable tenant name from URL
     * @param namespaceName Human-readable namespace name from URL (optional)
     * @param topicName Human-readable topic name from URL (optional)
     * @return true if permission is granted, false otherwise
     */
    suspend fun checkPermission(
        principalId: String,
        resourceType: ResourceType,
        // Human-readable name from URL
        resourceName: String?,
        requiredPermission: Permission,
        // Human-readable tenant name from URL
        tenantName: String,
        namespaceName: String? = null,
        topicName: String? = null,
    ): Boolean {
        // Resolve human-readable names to resource UUIDs
        val tenantResourceId = resourceResolver.resolveTenantName(tenantName)
        val namespaceResourceId =
            namespaceName?.let {
                resourceResolver.resolveNamespaceName(tenantResourceId, it)
            }
        // If topicName is provided, it should be a UUID string (API uses UUIDs)
        val topicId =
            topicName?.let {
                requireNotNull(namespaceResourceId) { "Namespace required for topic" }
                // topicName is actually a UUID string when provided from API
                try {
                    UUID.fromString(it)
                } catch (e: IllegalArgumentException) {
                    throw com.eventstore.domain.exceptions.TopicNotFoundException(it)
                }
            }

        // Determine target resourceId based on resourceType
        // For CREATE operations, resourceId should be null (creating a new resource)
        // For other operations, use the resolved resourceId
        val targetResourceId =
            when (resourceType) {
                ResourceType.TENANT -> {
                    // For tenant operations, if resourceName is null, it's a CREATE operation
                    // Otherwise, use the resolved tenantResourceId
                    if (resourceName == null) null else tenantResourceId
                }
                ResourceType.NAMESPACE -> namespaceResourceId
                ResourceType.TOPIC -> topicId
                else -> resourceName?.let { UUID.fromString(it) } // For USER, CONSUMER, etc., resourceId comes from parameter
            }

        return permissionProjectionService.hasPermission(
            principalId = principalId,
            resourceType = resourceType,
            resourceId = targetResourceId,
            permission = requiredPermission,
            tenantResourceId = tenantResourceId,
            namespaceResourceId = namespaceResourceId,
            topicId = topicId,
        )
    }
}
