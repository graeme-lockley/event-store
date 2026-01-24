package com.eventstore.interfaces.http.routes

import com.eventstore.domain.Application
import com.eventstore.domain.Quota
import com.eventstore.domain.Tenant
import com.eventstore.domain.exceptions.TenantAlreadyExistsException
import com.eventstore.domain.exceptions.TenantNameNotFoundException
import com.eventstore.domain.exceptions.TenantNotFoundException
import com.eventstore.interfaces.http.dto.*
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

private data class TenantDeleteRequestDto(val reason: String? = null)

fun Route.tenantRoutes(application: Application) {
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
            } catch (e: com.fasterxml.jackson.databind.exc.MismatchedInputException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid request format: ${e.message ?: "Malformed request body"}", "INVALID_REQUEST")
                )
            } catch (e: com.fasterxml.jackson.core.JsonParseException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid JSON syntax: ${e.message ?: "Malformed request body"}", "INVALID_JSON")
                )
            } catch (e: com.fasterxml.jackson.databind.JsonMappingException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid JSON mapping: ${e.message ?: "Malformed request body"}", "INVALID_JSON")
                )
            } catch (e: com.fasterxml.jackson.core.JsonProcessingException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid JSON: ${e.message ?: "Malformed request body"}", "INVALID_JSON")
                )
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
                // Log the exception type for debugging
                val exceptionType = e::class.simpleName
                val exceptionMessage = e.message ?: "Unknown error"
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("Failed to create tenant: $exceptionMessage (type: $exceptionType)", "TENANT_CREATE_FAILED")
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

        get("{tenantId}") {
            try {
                val tenantIdStr =
                    call.parameters["tenantId"] ?: throw IllegalArgumentException("tenantId is required")
                val tenantId = try {
                    UUID.fromString(tenantIdStr)
                } catch (e: IllegalArgumentException) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Invalid tenantId format. Expected UUID.", "INVALID_TENANT_ID")
                    )
                    return@get
                }
                val tenant = application.getTenantService.getTenant(tenantId)
                    ?: throw TenantNameNotFoundException(tenantIdStr)
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



