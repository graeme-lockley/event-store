package com.eventstore.interfaces.http.middleware

import com.eventstore.domain.Permission
import com.eventstore.domain.ResourceType
import com.eventstore.domain.services.auth.AuthorizationService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*

/**
 * Middleware that extracts tenant/namespace/topic context from URLs and checks permissions.
 * Should be installed after AuthenticationMiddleware to ensure userId is available.
 */
class AuthorizationMiddleware(
    private val authorizationService: AuthorizationService
) {
    companion object {
        val TenantNameKey = AttributeKey<String>("tenantName")
        val NamespaceNameKey = AttributeKey<String>("namespaceName")
        val TopicNameKey = AttributeKey<String>("topicName")
    }

    fun install(route: Route) {
        route.intercept(ApplicationCallPipeline.Call) {
            // Skip authorization for public endpoints
            val path = call.request.path()
            if (isPublicEndpoint(path)) {
                proceed()
                return@intercept
            }

            // Get userId from authentication middleware
            val userId = call.attributes.getOrNull(AuthenticationMiddleware.UserIdKey)
                ?: run {
                    call.respond(HttpStatusCode.Unauthorized, com.eventstore.interfaces.http.dto.ErrorResponse("Authentication required", "AUTH_REQUIRED"))
                    finish()
                    return@intercept
                }

            // Extract context from URL path
            val (context, permissionPair) = extractContextAndPermission(path, call.request.httpMethod)
            val tenantName = context.first
            val namespaceName = context.second
            val topicName = context.third
            val resourceType = permissionPair.first
            val requiredPermission = permissionPair.second

            if (tenantName == null) {
                // No tenant context - allow (might be system-level endpoint)
                proceed()
                return@intercept
            }

            // Store context in call attributes
            call.attributes.put(TenantNameKey, tenantName)
            if (namespaceName != null) {
                call.attributes.put(NamespaceNameKey, namespaceName)
            }
            if (topicName != null) {
                call.attributes.put(TopicNameKey, topicName)
            }

            // Check permission
            val hasPermission = if (resourceType != null && requiredPermission != null) {
                authorizationService.checkPermission(
                    principalId = userId,
                    resourceType = resourceType,
                    resourceName = extractResourceName(path, resourceType),
                    requiredPermission = requiredPermission,
                    tenantName = tenantName,
                    namespaceName = namespaceName,
                    topicName = topicName
                )
            } else {
                // No specific permission required - allow
                true
            }

            if (!hasPermission) {
                call.respond(HttpStatusCode.Forbidden, com.eventstore.interfaces.http.dto.ErrorResponse("Permission denied", "PERMISSION_DENIED"))
                finish()
                return@intercept
            }

            proceed()
        }
    }

    private fun isPublicEndpoint(path: String): Boolean {
        return path.startsWith("/auth/login") ||
               path.startsWith("/auth/logout") ||
               path.startsWith("/health") ||
               path == "/" ||
               path == "/favicon.ico"
    }

    private fun extractContextAndPermission(
        path: String,
        method: HttpMethod
    ): Pair<Triple<String?, String?, String?>, Pair<ResourceType?, Permission?>> {
        // Extract tenant/namespace/topic names from URL patterns
        val tenantMatch = Regex("/tenants/([^/]+)").find(path)
        val tenantName = tenantMatch?.groupValues?.get(1)

        val namespaceMatch = Regex("/tenants/[^/]+/namespaces/([^/]+)").find(path)
        val namespaceName = namespaceMatch?.groupValues?.get(1)

        val topicMatch = Regex("/tenants/[^/]+/namespaces/[^/]+/topics/([^/]+)").find(path)
        val topicName = topicMatch?.groupValues?.get(1)

        // Determine resource type and required permission from path and method
        val (resourceType, requiredPermission) = when {
            path.matches(Regex("/tenants/?$")) -> {
                when (method) {
                    HttpMethod.Post -> ResourceType.TENANT to Permission.CREATE
                    HttpMethod.Get -> ResourceType.TENANT to Permission.LIST
                    else -> null to null
                }
            }
            path.matches(Regex("/tenants/[^/]+/?$")) -> {
                when (method) {
                    HttpMethod.Get -> ResourceType.TENANT to Permission.READ
                    HttpMethod.Put -> ResourceType.TENANT to Permission.UPDATE
                    HttpMethod.Delete -> ResourceType.TENANT to Permission.DELETE
                    else -> null to null
                }
            }
            path.matches(Regex("/tenants/[^/]+/namespaces/?$")) -> {
                when (method) {
                    HttpMethod.Post -> ResourceType.NAMESPACE to Permission.CREATE
                    HttpMethod.Get -> ResourceType.NAMESPACE to Permission.LIST
                    else -> null to null
                }
            }
            path.matches(Regex("/tenants/[^/]+/namespaces/[^/]+/?$")) -> {
                when (method) {
                    HttpMethod.Get -> ResourceType.NAMESPACE to Permission.READ
                    HttpMethod.Put -> ResourceType.NAMESPACE to Permission.UPDATE
                    HttpMethod.Delete -> ResourceType.NAMESPACE to Permission.DELETE
                    else -> null to null
                }
            }
            path.matches(Regex("/tenants/[^/]+/namespaces/[^/]+/topics/?$")) -> {
                when (method) {
                    HttpMethod.Post -> ResourceType.TOPIC to Permission.CREATE
                    HttpMethod.Get -> ResourceType.TOPIC to Permission.LIST
                    else -> null to null
                }
            }
            path.matches(Regex("/tenants/[^/]+/namespaces/[^/]+/topics/[^/]+/?$")) -> {
                when (method) {
                    HttpMethod.Get -> ResourceType.TOPIC to Permission.READ
                    HttpMethod.Put -> ResourceType.TOPIC to Permission.UPDATE
                    HttpMethod.Delete -> ResourceType.TOPIC to Permission.DELETE
                    else -> null to null
                }
            }
            path.matches(Regex("/tenants/[^/]+/namespaces/[^/]+/topics/[^/]+/schemas")) -> {
                when (method) {
                    HttpMethod.Put -> ResourceType.TOPIC to Permission.SCHEMA_MANAGE
                    else -> null to null
                }
            }
            path.matches(Regex("/tenants/[^/]+/namespaces/[^/]+/events")) -> {
                when (method) {
                    HttpMethod.Post -> ResourceType.EVENT to Permission.CREATE
                    HttpMethod.Get -> ResourceType.EVENT to Permission.READ
                    else -> null to null
                }
            }
            path.matches(Regex("/tenants/[^/]+/users")) -> {
                when (method) {
                    HttpMethod.Post -> ResourceType.USER to Permission.CREATE
                    HttpMethod.Get -> ResourceType.USER to Permission.LIST
                    else -> null to null
                }
            }
            path.matches(Regex("/tenants/[^/]+/users/[^/]+")) -> {
                when (method) {
                    HttpMethod.Get -> ResourceType.USER to Permission.READ
                    HttpMethod.Put -> ResourceType.USER to Permission.UPDATE
                    HttpMethod.Delete -> ResourceType.USER to Permission.DELETE
                    else -> null to null
                }
            }
            path.matches(Regex("/tenants/[^/]+/users/[^/]+/api-keys/?$")) -> {
                when (method) {
                    HttpMethod.Post -> ResourceType.USER to Permission.UPDATE  // Creating API keys for a user
                    HttpMethod.Get -> ResourceType.USER to Permission.READ    // Listing API keys
                    else -> null to null
                }
            }
            path.matches(Regex("/tenants/[^/]+/users/[^/]+/api-keys/[^/]+/?$")) -> {
                when (method) {
                    HttpMethod.Get -> ResourceType.USER to Permission.READ
                    HttpMethod.Delete -> ResourceType.USER to Permission.UPDATE
                    else -> null to null
                }
            }
            else -> null to null
        }

        return Triple(tenantName, namespaceName, topicName) to Pair(resourceType, requiredPermission)
    }

    private fun extractResourceName(path: String, resourceType: ResourceType): String? {
        return when (resourceType) {
            ResourceType.TENANT -> Regex("/tenants/([^/]+)").find(path)?.groupValues?.get(1)
            ResourceType.NAMESPACE -> Regex("/tenants/[^/]+/namespaces/([^/]+)").find(path)?.groupValues?.get(1)
            ResourceType.TOPIC -> Regex("/tenants/[^/]+/namespaces/[^/]+/topics/([^/]+)").find(path)?.groupValues?.get(1)
            ResourceType.USER -> Regex("/tenants/[^/]+/users/([^/]+)").find(path)?.groupValues?.get(1)
            else -> null
        }
    }
}

