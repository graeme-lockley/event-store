package com.eventstore.interfaces.http.routes

import com.eventstore.domain.Application
import com.eventstore.domain.Quota
import com.eventstore.domain.Tenant
import com.eventstore.domain.exceptions.TenantAlreadyExistsException
import com.eventstore.domain.exceptions.TenantNameNotFoundException
import com.eventstore.interfaces.http.dto.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

private data class TenantDeleteRequestDto(val reason: String? = null)

fun Route.tenantRoutes(
    application: Application
) {
    route("/tenants") {
        post {
            try {
                val body = call.receive<TenantCreateRequest>()
                if (body.name.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("name is required", "INVALID_REQUEST")
                    )
                    return@post
                }

                val created = application.createTenant(
                    name = body.name,
                    quota = body.quota?.toDomain(),
                    metadata = body.metadata
                )

                call.respond(HttpStatusCode.Created, created.toResponse())
            } catch (e: TenantAlreadyExistsException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(e.message ?: "Tenant already exists", "TENANT_EXISTS")
                )
            } catch (e: IllegalStateException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(e.message ?: "Tenant operations disabled", "FEATURE_DISABLED")
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(e.message ?: "Failed to create tenant", "TENANT_CREATE_FAILED")
                )
            }
        }

        get {
            try {
                val tenants = application.listTenants()
                call.respond(HttpStatusCode.OK, TenantListResponse(tenants.map { it.toResponse() }))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(e.message ?: "Failed to list tenants", "TENANT_LIST_FAILED")
                )
            }
        }

        get("{tenantName}") {
            try {
                val tenantName =
                    call.parameters["tenantName"] ?: throw IllegalArgumentException("tenantName is required")
                val tenant = application.getTenant(tenantName) ?: throw TenantNameNotFoundException(tenantName)
                call.respond(HttpStatusCode.OK, tenant.toResponse())
            } catch (e: TenantNameNotFoundException) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse(e.message ?: "Tenant not found", "TENANT_NOT_FOUND")
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(e.message ?: "Failed to fetch tenant", "TENANT_GET_FAILED")
                )
            }
        }

        put("{tenantId}") {
            try {
                val tenantId =
                    call.parameters["tenantId"] ?: throw IllegalArgumentException("tenantId is required")
                val body = call.receive<TenantUpdateRequest>()

                val updated = application.updateTenant(
                    tenantId = UUID.fromString(tenantId),
                    name = body.name,
                    quota = body.quota?.toDomain(),
                    metadata = body.metadata
                )

                call.respond(HttpStatusCode.OK, updated.toResponse())
            } catch (e: TenantNameNotFoundException) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse(e.message ?: "Tenant not found", "TENANT_NOT_FOUND")
                )
            } catch (e: IllegalStateException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(e.message ?: "Tenant operations disabled", "FEATURE_DISABLED")
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(e.message ?: "Failed to update tenant", "TENANT_UPDATE_FAILED")
                )
            }
        }

        delete("{tenantId}") {
            try {
                val tenantId =
                    call.parameters["tenantId"] ?: throw IllegalArgumentException("tenantId is required")
                val body = runCatching { call.receive<TenantDeleteRequestDto>() }.getOrNull()

                application.deleteTenant(
                    tenantId = UUID.fromString(tenantId),
                    reason = body?.reason
                )

                call.respond(HttpStatusCode.OK, mapOf("message" to "Tenant '$tenantId' deleted"))
            } catch (e: TenantNameNotFoundException) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse(e.message ?: "Tenant not found", "TENANT_NOT_FOUND")
                )
            } catch (e: IllegalStateException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(e.message ?: "Tenant operations disabled", "FEATURE_DISABLED")
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(e.message ?: "Failed to delete tenant", "TENANT_DELETE_FAILED")
                )
            }
        }
    }
}

private fun Tenant.toResponse(): TenantResponse = TenantResponse(
    id = name,
    name = name,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt?.toString(),
    deletedAt = deletedAt?.toString(),
    quota = quota?.toDto(),
    metadata = metadata
)

private fun QuotaDto.toDomain(): Quota = Quota(
    maxTopics = maxTopics,
    maxNamespaces = maxNamespaces,
    maxEventsPerDay = maxEventsPerDay,
    maxConsumers = maxConsumers,
    maxUsers = maxUsers,
    maxEventSizeBytes = maxEventSizeBytes
)

private fun Quota.toDto(): QuotaDto = QuotaDto(
    maxTopics = maxTopics,
    maxNamespaces = maxNamespaces,
    maxEventsPerDay = maxEventsPerDay,
    maxConsumers = maxConsumers,
    maxUsers = maxUsers,
    maxEventSizeBytes = maxEventSizeBytes
)



