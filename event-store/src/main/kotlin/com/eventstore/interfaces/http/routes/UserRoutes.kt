package com.eventstore.interfaces.http.routes

import com.eventstore.domain.Application
import com.eventstore.domain.User
import com.eventstore.domain.exceptions.UserAlreadyExistsException
import com.eventstore.domain.exceptions.UserNotFoundException
import com.eventstore.interfaces.http.dto.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.userRoutes(
    application: Application
) {
    // Global user management routes
    route("/users") {
        post {
            try {
                val body = call.receive<UserCreateRequest>()
                val created = application.createUser(
                    email = body.email,
                    name = body.name,
                    password = body.password,
                    metadata = body.metadata,
                    primaryTenantId = body.primaryTenantId
                )
                
                // Optionally assign to tenant if provided
                body.primaryTenantId?.let { tenantId ->
                    application.assignUserToTenant(
                        userId = created.id,
                        tenantId = tenantId,
                        isPrimary = true
                    )
                }
                
                call.respond(HttpStatusCode.Created, created.toResponse())
            } catch (e: UserAlreadyExistsException) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "User exists", "USER_EXISTS"))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(e.message ?: "Failed to create user", "USER_CREATE_FAILED")
                )
            }
        }

        get {
            val users = application.listUsers()
            call.respond(HttpStatusCode.OK, UserListResponse(users.map { it.toResponse() }))
        }

        get("{userId}") {
            try {
                val userId = call.parameters["userId"] ?: throw IllegalArgumentException("userId is required")
                val user = application.getUserById(userId) ?: throw UserNotFoundException(userId)
                call.respond(HttpStatusCode.OK, user.toResponse())
            } catch (e: UserNotFoundException) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse(e.message ?: "User not found", "USER_NOT_FOUND"))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(e.message ?: "Failed to fetch user", "USER_GET_FAILED")
                )
            }
        }

        put("{userId}") {
            try {
                val userId = call.parameters["userId"] ?: throw IllegalArgumentException("userId is required")
                val body = call.receive<UserUpdateRequest>()
                val updated = application.updateUser(
                    userId = userId,
                    email = body.email,
                    name = body.name,
                    metadata = body.metadata
                )
                call.respond(HttpStatusCode.OK, updated.toResponse())
            } catch (e: UserNotFoundException) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse(e.message ?: "User not found", "USER_NOT_FOUND"))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(e.message ?: "Failed to update user", "USER_UPDATE_FAILED")
                )
            }
        }

        delete("{userId}") {
            try {
                val userId = call.parameters["userId"] ?: throw IllegalArgumentException("userId is required")
                application.deleteUser(userId = userId)
                call.respond(HttpStatusCode.OK, mapOf("message" to "User '$userId' deleted"))
            } catch (e: UserNotFoundException) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse(e.message ?: "User not found", "USER_NOT_FOUND"))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(e.message ?: "Failed to delete user", "USER_DELETE_FAILED")
                )
            }
        }
    }
    
    // Tenant assignment routes - separate route blocks
    post("/users/{userId}/tenants/{tenantId}") {
        try {
            val userId = call.parameters["userId"] ?: throw IllegalArgumentException("userId is required")
            val tenantId = call.parameters["tenantId"] ?: throw IllegalArgumentException("tenantId is required")
            val body = runCatching { call.receive<AssignUserTenantRequest>() }.getOrNull()
            
            application.assignUserToTenant(
                userId = userId,
                tenantId = tenantId,
                role = body?.role,
                isPrimary = body?.isPrimary ?: false
            )
            call.respond(
                HttpStatusCode.OK,
                mapOf("message" to "User '$userId' assigned to tenant '$tenantId'")
            )
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(e.message ?: "Failed to assign tenant", "USER_ASSIGN_TENANT_FAILED")
            )
        }
    }

    delete("/users/{userId}/tenants/{tenantId}") {
        try {
            val userId = call.parameters["userId"] ?: throw IllegalArgumentException("userId is required")
            val tenantId = call.parameters["tenantId"] ?: throw IllegalArgumentException("tenantId is required")
            application.removeUserFromTenant(
                userId = userId,
                tenantId = tenantId
            )
            call.respond(HttpStatusCode.OK, mapOf("message" to "User '$userId' removed from tenant '$tenantId'"))
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(e.message ?: "Failed to remove tenant", "USER_REMOVE_TENANT_FAILED")
            )
        }
    }
}

private fun User.toResponse(): UserResponse = UserResponse(
    id = id,
    email = email,
    name = name,
    status = status.name,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt?.toString(),
    lastLoginAt = lastLoginAt?.toString(),
    emailVerified = emailVerified,
    primaryTenantId = primaryTenantId,
    metadata = metadata
)
