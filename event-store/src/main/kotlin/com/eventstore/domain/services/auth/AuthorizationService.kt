package com.eventstore.domain.services.auth

import com.eventstore.domain.Permission
import com.eventstore.domain.ResourceType
import com.eventstore.domain.ports.outbound.ResourceResolver
import com.eventstore.infrastructure.projections.PermissionProjectionService
import java.util.UUID

/**
 * Service that checks if a principal has permission to perform an action on a resource.
 * Resolves human-readable names from URLs to resourceId UUIDs and checks permissions.
 */
class AuthorizationService(
    private val permissionProjectionService: PermissionProjectionService,
    private val resourceResolver: ResourceResolver
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
        resourceName: String?,  // Human-readable name from URL
        requiredPermission: Permission,
        tenantName: String,      // Human-readable tenant name from URL
        namespaceName: String? = null,
        topicName: String? = null
    ): Boolean {
        // Resolve human-readable names to resource UUIDs
        val tenantResourceId = resourceResolver.resolveTenantResourceId(tenantName)
        val namespaceResourceId = namespaceName?.let { 
            resourceResolver.resolveNamespaceResourceId(tenantResourceId, it) 
        }
        val topicResourceId = topicName?.let {
            requireNotNull(namespaceResourceId) { "Namespace required for topic" }
            resourceResolver.resolveTopicResourceId(
                tenantResourceId, 
                namespaceResourceId, 
                it
            )
        }
        
        // Determine target resourceId based on resourceType
        val targetResourceId = when (resourceType) {
            ResourceType.TENANT -> tenantResourceId
            ResourceType.NAMESPACE -> namespaceResourceId
            ResourceType.TOPIC -> topicResourceId
            else -> resourceName?.let { UUID.fromString(it) }  // For USER, CONSUMER, etc., resourceId comes from parameter
        }
        
        return permissionProjectionService.hasPermission(
            principalId = principalId,
            resourceType = resourceType,
            resourceId = targetResourceId,
            permission = requiredPermission,
            tenantResourceId = tenantResourceId,
            namespaceResourceId = namespaceResourceId,
            topicResourceId = topicResourceId
        )
    }
}

