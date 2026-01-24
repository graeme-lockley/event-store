package com.eventstore.infrastructure.projections

import com.eventstore.domain.Event
import com.eventstore.domain.Permission
import com.eventstore.domain.PermissionGrant
import com.eventstore.domain.ResourceType
import com.eventstore.domain.events.PermissionEventType
import com.eventstore.domain.events.PermissionGrantedEvent
import com.eventstore.domain.events.PermissionRevokedEvent
import com.eventstore.domain.ports.outbound.DeliveryResult
import com.eventstore.domain.ports.outbound.PermissionRepository
import com.eventstore.domain.tenants.SystemTopics
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.*

/**
 * Projection service that rebuilds permission grants from events.
 * Implements caching and permission inheritance (tenant → namespace → topic).
 */
class PermissionProjectionService(
    private val permissionRepository: PermissionRepository
) {
    private val logger = LoggerFactory.getLogger(PermissionProjectionService::class.java)
    private val mutex = Mutex()

    // Cache key: principalId:tenantResourceId:namespaceResourceId:topicId
    private val cache = mutableMapOf<String, List<PermissionGrant>>()
    private val cacheMutex = Mutex()

    suspend fun handleEvents(events: List<Event>): DeliveryResult {
        if (events.isEmpty()) {
            return DeliveryResult(success = true)
        }

        return try {
            mutex.withLock {
                events.forEach { applyEvent(it) }
            }
            DeliveryResult(success = true)
        } catch (e: Exception) {
            logger.error("Failed to apply permission events", e)
            DeliveryResult(success = false, error = e.message)
        }
    }

    /**
     * Get all permission grants for a principal in a given context.
     * Returns grants that match the context (including inherited permissions).
     */
    suspend fun getPermissionGrants(
        principalId: String,
        tenantResourceId: UUID?,
        namespaceResourceId: UUID?,
        topicId: UUID?
    ): List<PermissionGrant> {
        val cacheKey = buildCacheKey(principalId, tenantResourceId, namespaceResourceId, topicId)

        return cacheMutex.withLock {
            cache[cacheKey] ?: run {
                val grants =
                    calculateEffectivePermissions(principalId, tenantResourceId, namespaceResourceId, topicId)
                cache[cacheKey] = grants
                grants
            }
        }
    }

    /**
     * Check if a principal has a specific permission for a resource.
     */
    suspend fun hasPermission(
        principalId: String,
        resourceType: ResourceType,
        resourceId: UUID?,
        permission: Permission,
        tenantResourceId: UUID,
        namespaceResourceId: UUID? = null,
        topicId: UUID? = null
    ): Boolean {
        val grants = getPermissionGrants(principalId, tenantResourceId, namespaceResourceId, topicId)

        return grants.any { grant ->
            grant.resourceType == resourceType &&
                    (grant.permissions.contains(permission) || grant.permissions.contains(Permission.ADMIN)) &&
                    // Check resource scope: null = all resources, or specific match
                    (grant.resourceId == null || grant.resourceId == resourceId?.toString())
        }
    }

    private suspend fun calculateEffectivePermissions(
        principalId: String,
        tenantResourceId: UUID?,
        namespaceResourceId: UUID?,
        topicId: UUID?
    ): List<PermissionGrant> {
        val allGrants = permissionRepository.findByPrincipal(principalId)
        val now = Instant.now()

        // Filter grants that are:
        // 1. Not expired
        // 2. Match the tenant context
        // 3. Match the namespace/topic context if applicable
        return allGrants.filter { grant ->
            // Check expiration
            val notExpired = grant.expiresAt?.let { it.isBefore(now) } != true

            // Check tenant match - grant must match the query's tenant context
            // If tenantResourceId is null in query, we don't filter by tenant (return all)
            // Otherwise, grant's tenantResourceId must match the query's tenantResourceId
            // Special case: If grant has resourceId=null (global admin) and is for system tenant,
            // treat it as global admin that applies to all tenants
            val isGlobalAdminGrant = grant.resourceId == null &&
                    grant.resourceType == ResourceType.TENANT &&
                    grant.tenantResourceId == com.eventstore.domain.tenants.SystemTopics.SYSTEM_TENANT_ID.toString()
            val tenantMatches = tenantResourceId == null ||
                    grant.tenantResourceId == tenantResourceId.toString() ||
                    isGlobalAdminGrant

            // Check namespace match (if namespace context provided)
            val namespaceMatches = namespaceResourceId == null ||
                    grant.namespaceResourceId == null ||
                    grant.namespaceResourceId == namespaceResourceId.toString()

            // Check topic match (if topic context provided)
            val topicMatches = topicId == null ||
                    grant.topicId == null ||
                    grant.topicId == topicId.toString()

            notExpired && tenantMatches && namespaceMatches && topicMatches
        }
    }

    private fun buildCacheKey(
        principalId: String,
        tenantResourceId: UUID?,
        namespaceResourceId: UUID?,
        topicId: UUID?
    ): String {
        return "$principalId:${tenantResourceId}:${namespaceResourceId}:${topicId}"
    }

    private suspend fun invalidateCache(principalId: String) {
        cacheMutex.withLock {
            cache.keys.removeAll { it.startsWith("$principalId:") }
        }
    }

    private suspend fun applyEvent(event: Event) {
        when (event.type) {
            PermissionEventType.GRANTED -> {
                val payload = PermissionGrantedEvent.fromPayload(event.payload)
                val grant = PermissionGrant(
                    principalId = payload.principalId,
                    principalType = payload.principalType,
                    resourceType = payload.resourceType,
                    resourceId = payload.resourceId,
                    tenantResourceId = payload.tenantResourceId,
                    namespaceResourceId = payload.namespaceResourceId,
                    topicId = payload.topicId,
                    permissions = payload.permissions,
                    grantedBy = payload.grantedBy,
                    grantedAt = payload.grantedAt,
                    expiresAt = payload.expiresAt
                )
                permissionRepository.save(grant)
                invalidateCache(payload.principalId)
            }

            PermissionEventType.REVOKED -> {
                val payload = PermissionRevokedEvent.fromPayload(event.payload)
                // Find matching grants and remove the revoked permissions
                val grants = permissionRepository.findByPrincipal(payload.principalId)
                grants.filter { grant ->
                    grant.resourceType == payload.resourceType &&
                            grant.resourceId == payload.resourceId &&
                            grant.tenantResourceId == payload.tenantResourceId &&
                            grant.namespaceResourceId == payload.namespaceResourceId &&
                            grant.topicId == payload.topicId &&
                            grant.permissions.intersect(payload.permissions).isNotEmpty()
                }.forEach { grant ->
                    // Remove revoked permissions from the grant
                    val remainingPermissions = grant.permissions - payload.permissions
                    if (remainingPermissions.isEmpty()) {
                        // If no permissions remain, delete the grant
                        permissionRepository.delete(grant)
                    } else {
                        // Update the grant with remaining permissions
                        val updatedGrant = grant.copy(permissions = remainingPermissions)
                        permissionRepository.save(updatedGrant)
                    }
                }
                invalidateCache(payload.principalId)
            }

            else -> {
                logger.debug("Ignoring non-permission event type ${event.type}")
            }
        }
    }
}

