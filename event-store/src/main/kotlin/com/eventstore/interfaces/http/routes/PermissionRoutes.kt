package com.eventstore.interfaces.http.routes

import com.eventstore.domain.Permission
import com.eventstore.domain.PrincipalType
import com.eventstore.domain.ResourceType
import com.eventstore.domain.services.permission.GetPermissionsService
import com.eventstore.domain.services.permission.GrantPermissionRequest
import com.eventstore.domain.services.permission.GrantPermissionService
import com.eventstore.domain.services.permission.RevokePermissionRequest
import com.eventstore.domain.services.permission.RevokePermissionService
import com.eventstore.interfaces.http.dto.ErrorResponse
import com.eventstore.interfaces.http.middleware.AuthenticationMiddleware
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.Instant

data class GrantPermissionRequestDto(
    val principalId: String,
    val principalType: String,
    val resourceType: String,
    val resourceName: String? = null,
    val namespaceName: String? = null,
    val topicName: String? = null,
    val permissions: List<String>,
    val expiresAt: String? = null
)

data class RevokePermissionRequestDto(
    val principalId: String,
    val principalType: String,
    val resourceType: String,
    val resourceName: String? = null,
    val namespaceName: String? = null,
    val topicName: String? = null,
    val permissions: List<String>,
    val reason: String? = null
)

data class PermissionResponseDto(
    val principalId: String,
    val principalType: String,
    val resourceType: String,
    val resourceId: String?,
    val permissions: List<String>,
    val grantedAt: String,
    val grantedBy: String
)

fun Route.permissionRoutes(
    grantPermissionService: GrantPermissionService,
    revokePermissionService: RevokePermissionService,
    getPermissionsService: GetPermissionsService
) {
    route("/tenants/{tenantName}/users/{userId}/permissions") {
        get {
            try {
                val tenantName = call.parameters["tenantName"] 
                    ?: throw IllegalArgumentException("tenantName is required")
                val userId = call.parameters["userId"]
                    ?: throw IllegalArgumentException("userId is required")

                val grants = getPermissionsService.execute(
                    com.eventstore.domain.services.permission.GetPermissionsRequest(
                        principalId = userId,
                        tenantName = tenantName
                    )
                )

                val response = grants.map { grant ->
                    PermissionResponseDto(
                        principalId = grant.principalId,
                        principalType = grant.principalType.name,
                        resourceType = grant.resourceType.name,
                        resourceId = grant.resourceId,
                        permissions = grant.permissions.map { it.name },
                        grantedAt = grant.grantedAt.toString(),
                        grantedBy = grant.grantedBy
                    )
                }

                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(e.message ?: "Failed to get permissions", "GET_PERMISSIONS_FAILED")
                )
            }
        }

        post {
            try {
                val tenantName = call.parameters["tenantName"]
                    ?: throw IllegalArgumentException("tenantName is required")
                val userId = call.parameters["userId"]
                    ?: throw IllegalArgumentException("userId is required")
                val currentUserId = call.attributes.getOrNull(AuthenticationMiddleware.UserIdKey)
                    ?: throw IllegalStateException("Authentication required")

                val body = call.receive<GrantPermissionRequestDto>()

                val principalType = try {
                    PrincipalType.valueOf(body.principalType.uppercase())
                } catch (e: IllegalArgumentException) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Invalid principalType: ${body.principalType}", "INVALID_PRINCIPAL_TYPE")
                    )
                    return@post
                }

                val resourceType = try {
                    ResourceType.valueOf(body.resourceType.uppercase())
                } catch (e: IllegalArgumentException) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Invalid resourceType: ${body.resourceType}", "INVALID_RESOURCE_TYPE")
                    )
                    return@post
                }

                val permissions = body.permissions.mapNotNull {
                    try {
                        Permission.valueOf(it.uppercase())
                    } catch (e: IllegalArgumentException) {
                        null
                    }
                }

                if (permissions.isEmpty()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("At least one valid permission is required", "INVALID_PERMISSIONS")
                    )
                    return@post
                }

                val expiresAt = body.expiresAt?.let { Instant.parse(it) }

                val event = grantPermissionService.execute(
                    GrantPermissionRequest(
                        principalId = body.principalId,
                        principalType = principalType,
                        resourceType = resourceType,
                        resourceName = body.resourceName,
                        tenantName = tenantName,
                        namespaceName = body.namespaceName,
                        topicName = body.topicName,
                        permissions = permissions.toSet(),
                        constraints = null,
                        expiresAt = expiresAt,
                        grantedBy = currentUserId
                    )
                )

                call.respond(HttpStatusCode.Created, mapOf("message" to "Permission granted"))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(e.message ?: "Failed to grant permission", "GRANT_PERMISSION_FAILED")
                )
            }
        }

        delete {
            try {
                val tenantName = call.parameters["tenantName"]
                    ?: throw IllegalArgumentException("tenantName is required")
                val userId = call.parameters["userId"]
                    ?: throw IllegalArgumentException("userId is required")
                val currentUserId = call.attributes.getOrNull(AuthenticationMiddleware.UserIdKey)
                    ?: throw IllegalStateException("Authentication required")

                val body = call.receive<RevokePermissionRequestDto>()

                val principalType = try {
                    PrincipalType.valueOf(body.principalType.uppercase())
                } catch (e: IllegalArgumentException) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Invalid principalType: ${body.principalType}", "INVALID_PRINCIPAL_TYPE")
                    )
                    return@delete
                }

                val resourceType = try {
                    ResourceType.valueOf(body.resourceType.uppercase())
                } catch (e: IllegalArgumentException) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Invalid resourceType: ${body.resourceType}", "INVALID_RESOURCE_TYPE")
                    )
                    return@delete
                }

                val permissions = body.permissions.mapNotNull {
                    try {
                        Permission.valueOf(it.uppercase())
                    } catch (e: IllegalArgumentException) {
                        null
                    }
                }

                if (permissions.isEmpty()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("At least one valid permission is required", "INVALID_PERMISSIONS")
                    )
                    return@delete
                }

                revokePermissionService.execute(
                    RevokePermissionRequest(
                        principalId = body.principalId,
                        principalType = principalType,
                        resourceType = resourceType,
                        resourceName = body.resourceName,
                        tenantName = tenantName,
                        namespaceName = body.namespaceName,
                        topicName = body.topicName,
                        permissions = permissions.toSet(),
                        revokedBy = currentUserId,
                        reason = body.reason
                    )
                )

                call.respond(HttpStatusCode.OK, mapOf("message" to "Permission revoked"))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(e.message ?: "Failed to revoke permission", "REVOKE_PERMISSION_FAILED")
                )
            }
        }
    }
}

