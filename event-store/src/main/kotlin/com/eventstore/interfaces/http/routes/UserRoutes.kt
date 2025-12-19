package com.eventstore.interfaces.http.routes

import com.eventstore.domain.User
import com.eventstore.domain.exceptions.UserAlreadyExistsException
import com.eventstore.domain.exceptions.UserNotFoundException
import com.eventstore.domain.services.user.AssignUserRequest
import com.eventstore.domain.services.user.AssignUserToTenantService
import com.eventstore.domain.services.user.CreateUserRequest
import com.eventstore.domain.services.user.CreateUserService
import com.eventstore.domain.services.user.DeleteUserRequest
import com.eventstore.domain.services.user.DeleteUserService
import com.eventstore.domain.services.user.GetUserService
import com.eventstore.domain.services.user.RemoveUserFromTenantService
import com.eventstore.domain.services.user.RemoveUserTenantRequest
import com.eventstore.domain.services.user.UpdateUserRequest
import com.eventstore.domain.services.user.UpdateUserService
import com.eventstore.interfaces.http.dto.AssignUserTenantRequest
import com.eventstore.interfaces.http.dto.ErrorResponse
import com.eventstore.interfaces.http.dto.UserCreateRequest
import com.eventstore.interfaces.http.dto.UserListResponse
import com.eventstore.interfaces.http.dto.UserResponse
import com.eventstore.interfaces.http.dto.UserUpdateRequest
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.userRoutes(
    createUserService: CreateUserService,
    getUserService: GetUserService,
    updateUserService: UpdateUserService,
    deleteUserService: DeleteUserService,
    assignUserToTenantService: AssignUserToTenantService,
    removeUserFromTenantService: RemoveUserFromTenantService
) {
    route("/tenants/{tenantId}/users") {
        post {
            try {
                val tenantId = call.parameters["tenantId"] ?: throw IllegalArgumentException("tenantId is required")
                val body = call.receive<UserCreateRequest>()
                val created = createUserService.execute(
                    CreateUserRequest(
                        email = body.email,
                        name = body.name,
                        password = body.password,
                        metadata = body.metadata,
                        primaryTenantId = body.primaryTenantId ?: tenantId
                    )
                )
                assignUserToTenantService.execute(
                    AssignUserRequest(
                        userId = created.id,
                        tenantId = tenantId,
                        isPrimary = body.primaryTenantId == tenantId || body.primaryTenantId == null
                    )
                )
                call.respond(HttpStatusCode.Created, created.toResponse())
            } catch (e: UserAlreadyExistsException) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "User exists", "USER_EXISTS"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Failed to create user", "USER_CREATE_FAILED"))
            }
        }

        get {
            val users = getUserService.list()
            call.respond(HttpStatusCode.OK, UserListResponse(users.map { it.toResponse() }))
        }

        get("{userId}") {
            try {
                val userId = call.parameters["userId"] ?: throw IllegalArgumentException("userId is required")
                val user = getUserService.getById(userId) ?: throw UserNotFoundException(userId)
                call.respond(HttpStatusCode.OK, user.toResponse())
            } catch (e: UserNotFoundException) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse(e.message ?: "User not found", "USER_NOT_FOUND"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Failed to fetch user", "USER_GET_FAILED"))
            }
        }

        put("{userId}") {
            try {
                val userId = call.parameters["userId"] ?: throw IllegalArgumentException("userId is required")
                val body = call.receive<UserUpdateRequest>()
                val updated = updateUserService.execute(
                    UpdateUserRequest(
                        userId = userId,
                        email = body.email,
                        name = body.name,
                        metadata = body.metadata
                    )
                )
                call.respond(HttpStatusCode.OK, updated.toResponse())
            } catch (e: UserNotFoundException) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse(e.message ?: "User not found", "USER_NOT_FOUND"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Failed to update user", "USER_UPDATE_FAILED"))
            }
        }

        delete("{userId}") {
            try {
                val userId = call.parameters["userId"] ?: throw IllegalArgumentException("userId is required")
                deleteUserService.execute(DeleteUserRequest(userId = userId))
                call.respond(HttpStatusCode.OK, mapOf("message" to "User '$userId' deleted"))
            } catch (e: UserNotFoundException) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse(e.message ?: "User not found", "USER_NOT_FOUND"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Failed to delete user", "USER_DELETE_FAILED"))
            }
        }

        post("{userId}/tenants") {
            try {
                val userId = call.parameters["userId"] ?: throw IllegalArgumentException("userId is required")
                val body = call.receive<AssignUserTenantRequest>()
                assignUserToTenantService.execute(
                    AssignUserRequest(
                        userId = userId,
                        tenantId = body.tenantId,
                        role = body.role,
                        isPrimary = body.isPrimary
                    )
                )
                call.respond(HttpStatusCode.OK, mapOf("message" to "User '$userId' assigned to tenant '${body.tenantId}'"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Failed to assign tenant", "USER_ASSIGN_TENANT_FAILED"))
            }
        }

        delete("{userId}/tenants/{tenantId}") {
            try {
                val userId = call.parameters["userId"] ?: throw IllegalArgumentException("userId is required")
                val tenantId = call.parameters["tenantId"] ?: throw IllegalArgumentException("tenantId is required")
                removeUserFromTenantService.execute(
                    RemoveUserTenantRequest(
                        userId = userId,
                        tenantId = tenantId
                    )
                )
                call.respond(HttpStatusCode.OK, mapOf("message" to "User '$userId' removed from tenant '$tenantId'"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Failed to remove tenant", "USER_REMOVE_TENANT_FAILED"))
            }
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
 