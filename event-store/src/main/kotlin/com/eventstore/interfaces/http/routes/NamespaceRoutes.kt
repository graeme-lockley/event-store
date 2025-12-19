package com.eventstore.interfaces.http.routes

import com.eventstore.domain.Namespace
import com.eventstore.domain.exceptions.NamespaceAlreadyExistsException
import com.eventstore.domain.exceptions.NamespaceNotFoundException
import com.eventstore.domain.exceptions.TenantNotFoundException
import com.eventstore.domain.services.namespace.CreateNamespaceRequest
import com.eventstore.domain.services.namespace.CreateNamespaceService
import com.eventstore.domain.services.namespace.DeleteNamespaceRequest
import com.eventstore.domain.services.namespace.DeleteNamespaceService
import com.eventstore.domain.services.namespace.GetNamespaceService
import com.eventstore.domain.services.namespace.UpdateNamespaceRequest
import com.eventstore.domain.services.namespace.UpdateNamespaceService
import com.eventstore.interfaces.http.dto.ErrorResponse
import com.eventstore.interfaces.http.dto.NamespaceCreateRequest
import com.eventstore.interfaces.http.dto.NamespaceListResponse
import com.eventstore.interfaces.http.dto.NamespaceResponse
import com.eventstore.interfaces.http.dto.NamespaceUpdateRequest
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private data class NamespaceDeleteRequestDto(val reason: String? = null)

fun Route.namespaceRoutes(
    createNamespaceService: CreateNamespaceService,
    getNamespaceService: GetNamespaceService,
    updateNamespaceService: UpdateNamespaceService,
    deleteNamespaceService: DeleteNamespaceService
) {
    route("/tenants/{tenantName}/namespaces") {
        post {
            try {
                val tenantName = call.parameters["tenantName"] ?: throw IllegalArgumentException("tenantName is required")
                val body = call.receive<NamespaceCreateRequest>()
                if (body.name.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("name is required", "INVALID_REQUEST")
                    )
                    return@post
                }

                val created = createNamespaceService.execute(
                    CreateNamespaceRequest(
                        tenantName = tenantName,
                        name = body.name,
                        description = body.description,
                        metadata = body.metadata
                    )
                )
                call.respond(HttpStatusCode.Created, created.toResponse())
            } catch (e: TenantNotFoundException) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Tenant not found", "TENANT_NOT_FOUND"))
            } catch (e: NamespaceAlreadyExistsException) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Namespace exists", "NAMESPACE_EXISTS"))
            } catch (e: IllegalStateException) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Feature disabled", "FEATURE_DISABLED"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Failed to create namespace", "NAMESPACE_CREATE_FAILED"))
            }
        }

        get {
            try {
                val tenantName = call.parameters["tenantName"] ?: throw IllegalArgumentException("tenantName is required")
                val namespaces = getNamespaceService.listNamespaces(tenantName)
                call.respond(HttpStatusCode.OK, NamespaceListResponse(namespaces.map { it.toResponse() }))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Failed to list namespaces", "NAMESPACE_LIST_FAILED"))
            }
        }

        get("{namespaceName}") {
            try {
                val tenantName = call.parameters["tenantName"] ?: throw IllegalArgumentException("tenantName is required")
                val namespaceName = call.parameters["namespaceName"] ?: throw IllegalArgumentException("namespaceName is required")
                val ns = getNamespaceService.getNamespace(tenantName, namespaceName) ?: throw NamespaceNotFoundException(namespaceName)
                call.respond(HttpStatusCode.OK, ns.toResponse())
            } catch (e: NamespaceNotFoundException) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse(e.message ?: "Namespace not found", "NAMESPACE_NOT_FOUND"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Failed to fetch namespace", "NAMESPACE_GET_FAILED"))
            }
        }

        put("{namespaceName}") {
            try {
                val tenantName = call.parameters["tenantName"] ?: throw IllegalArgumentException("tenantName is required")
                val namespaceName = call.parameters["namespaceName"] ?: throw IllegalArgumentException("namespaceName is required")
                val body = call.receive<NamespaceUpdateRequest>()

                val updated = updateNamespaceService.execute(
                    UpdateNamespaceRequest(
                        tenantName = tenantName,
                        namespaceName = namespaceName,
                        name = body.name,
                        description = body.description,
                        metadata = body.metadata
                    )
                )
                call.respond(HttpStatusCode.OK, updated.toResponse())
            } catch (e: NamespaceNotFoundException) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse(e.message ?: "Namespace not found", "NAMESPACE_NOT_FOUND"))
            } catch (e: TenantNotFoundException) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Tenant not found", "TENANT_NOT_FOUND"))
            } catch (e: IllegalStateException) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Feature disabled", "FEATURE_DISABLED"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Failed to update namespace", "NAMESPACE_UPDATE_FAILED"))
            }
        }

        delete("{namespaceName}") {
            try {
                val tenantName = call.parameters["tenantName"] ?: throw IllegalArgumentException("tenantName is required")
                val namespaceName = call.parameters["namespaceName"] ?: throw IllegalArgumentException("namespaceName is required")
                val body = runCatching { call.receive<NamespaceDeleteRequestDto>() }.getOrNull()

                deleteNamespaceService.execute(
                    DeleteNamespaceRequest(
                        tenantName = tenantName,
                        namespaceName = namespaceName,
                        reason = body?.reason
                    )
                )
                call.respond(HttpStatusCode.OK, mapOf("message" to "Namespace '$namespaceName' deleted"))
            } catch (e: NamespaceNotFoundException) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse(e.message ?: "Namespace not found", "NAMESPACE_NOT_FOUND"))
            } catch (e: TenantNotFoundException) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Tenant not found", "TENANT_NOT_FOUND"))
            } catch (e: IllegalStateException) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Feature disabled", "FEATURE_DISABLED"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Failed to delete namespace", "NAMESPACE_DELETE_FAILED"))
            }
        }
    }
}

private fun Namespace.toResponse(): NamespaceResponse = NamespaceResponse(
    tenantId = tenantName,  // Use tenantName as tenantId for API compatibility
    id = name,  // Use name as id for API compatibility
    name = name,
    description = description,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt?.toString(),
    deletedAt = deletedAt?.toString(),
    metadata = metadata
)
