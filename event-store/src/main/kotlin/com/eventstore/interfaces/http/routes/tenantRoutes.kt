package com.eventstore.interfaces.http.routes

import com.eventstore.domain.Quota
import com.eventstore.domain.Tenant
import com.eventstore.domain.exceptions.TenantAlreadyExistsException
import com.eventstore.domain.exceptions.TenantNotFoundException
import com.eventstore.domain.services.tenant.CreateTenantRequest
import com.eventstore.domain.services.tenant.CreateTenantService
import com.eventstore.domain.services.tenant.DeleteTenantRequest
import com.eventstore.domain.services.tenant.DeleteTenantService
import com.eventstore.domain.services.tenant.GetTenantService
import com.eventstore.domain.services.tenant.UpdateTenantRequest
import com.eventstore.domain.services.tenant.UpdateTenantService
import com.eventstore.interfaces.http.dto.ErrorResponse
import com.eventstore.interfaces.http.dto.QuotaDto
import com.eventstore.interfaces.http.dto.TenantCreateRequest
import com.eventstore.interfaces.http.dto.TenantListResponse
import com.eventstore.interfaces.http.dto.TenantResponse
import com.eventstore.interfaces.http.dto.TenantUpdateRequest
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private data class TenantDeleteRequestDto(val reason: String? = null)

fun Route.tenantRoutes(
    createTenantService: CreateTenantService,
    getTenantService: GetTenantService,
    updateTenantService: UpdateTenantService,
    deleteTenantService: DeleteTenantService
) {
    route("/tenants") {
        post {
            try {
                val body = call.receive<TenantCreateRequest>()
                if (body.id.isBlank() || body.name.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("tenantId and name are required", "INVALID_REQUEST")
                    )
                    return@post
                }

                val created = createTenantService.execute(
                    CreateTenantRequest(
                        tenantId = body.id,
                        name = body.name,
                        quota = body.quota?.toDomain(),
                        metadata = body.metadata
                    )
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
                val tenants = getTenantService.listTenants()
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
                val tenantId = call.parameters["tenantId"] ?: throw IllegalArgumentException("tenantId is required")
                val tenant = getTenantService.getTenant(tenantId) ?: throw TenantNotFoundException(tenantId)
                call.respond(HttpStatusCode.OK, tenant.toResponse())
            } catch (e: TenantNotFoundException) {
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
                val tenantId = call.parameters["tenantId"] ?: throw IllegalArgumentException("tenantId is required")
                val body = call.receive<TenantUpdateRequest>()

                val updated = updateTenantService.execute(
                    UpdateTenantRequest(
                        tenantId = tenantId,
                        name = body.name,
                        quota = body.quota?.toDomain(),
                        metadata = body.metadata
                    )
                )

                call.respond(HttpStatusCode.OK, updated.toResponse())
            } catch (e: TenantNotFoundException) {
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
                val tenantId = call.parameters["tenantId"] ?: throw IllegalArgumentException("tenantId is required")
                val body = runCatching { call.receive<TenantDeleteRequestDto>() }.getOrNull()

                deleteTenantService.execute(
                    DeleteTenantRequest(
                        tenantId = tenantId,
                        reason = body?.reason
                    )
                )

                call.respond(HttpStatusCode.OK, mapOf("message" to "Tenant '$tenantId' deleted"))
            } catch (e: TenantNotFoundException) {
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
    id = id,
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

