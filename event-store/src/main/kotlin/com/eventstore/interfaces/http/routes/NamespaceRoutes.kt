package com.eventstore.interfaces.http.routes

import com.eventstore.domain.Application
import com.eventstore.domain.Namespace
import com.eventstore.domain.exceptions.NamespaceAlreadyExistsException
import com.eventstore.domain.exceptions.NamespaceNotFoundException
import com.eventstore.domain.exceptions.QuotaExceededException
import com.eventstore.domain.exceptions.TenantNotFoundException
import com.eventstore.interfaces.http.dto.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

private data class NamespaceDeleteRequestDto(val reason: String? = null)

fun Route.namespaceRoutes(
    application: Application
) {
    route("/namespaces") {
        post {
            try {
                val body = call.receive<NamespaceCreateRequest>()
                if (body.name.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("name is required", "INVALID_REQUEST")
                    )
                    return@post
                }

                val created = application.createNamespace(
                    tenantId = body.tenantId,
                    namespaceName = body.name,
                    description = body.description,
                    metadata = body.metadata
                )
                call.respond(HttpStatusCode.Created, created.toResponse())
            } catch (e: TenantNotFoundException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(e.message ?: "Tenant not found", "TENANT_NOT_FOUND")
                )
            } catch (e: NamespaceAlreadyExistsException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(e.message ?: "Namespace exists", "NAMESPACE_EXISTS")
                )
            } catch (e: QuotaExceededException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(e.message ?: "Quota exceeded", "QUOTA_EXCEEDED")
                )
            } catch (e: IllegalStateException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(e.message ?: "Feature disabled", "FEATURE_DISABLED")
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(e.message ?: "Failed to create namespace", "NAMESPACE_CREATE_FAILED")
                )
            }
        }

        get {
            try {
                val tenantIdParam = call.request.queryParameters["tenantId"]
                val tenantId = tenantIdParam?.let { UUID.fromString(it) }
                val namespaces = application.listNamespaces(tenantId)
                call.respond(HttpStatusCode.OK, NamespaceListResponse(namespaces.map { it.toResponse() }))
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid tenantId format", "INVALID_REQUEST")
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(e.message ?: "Failed to list namespaces", "NAMESPACE_LIST_FAILED")
                )
            }
        }

        get("{namespaceId}") {
            try {
                val namespaceIdStr =
                    call.parameters["namespaceId"] ?: throw IllegalArgumentException("namespaceId is required")
                val namespaceId = UUID.fromString(namespaceIdStr)
                val ns = application.getNamespace(namespaceId) ?: throw NamespaceNotFoundException(namespaceIdStr)
                call.respond(HttpStatusCode.OK, ns.toResponse())
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid namespaceId format", "INVALID_REQUEST")
                )
            } catch (e: NamespaceNotFoundException) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse(e.message ?: "Namespace not found", "NAMESPACE_NOT_FOUND")
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(e.message ?: "Failed to fetch namespace", "NAMESPACE_GET_FAILED")
                )
            }
        }

        put("{namespaceId}") {
            try {
                val namespaceIdStr =
                    call.parameters["namespaceId"] ?: throw IllegalArgumentException("namespaceId is required")
                val namespaceId = UUID.fromString(namespaceIdStr)
                val body = call.receive<NamespaceUpdateRequest>()

                val updated = application.updateNamespace(
                    namespaceId = namespaceId,
                    name = body.name,
                    description = body.description,
                    metadata = body.metadata
                )
                call.respond(HttpStatusCode.OK, updated.toResponse())
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid namespaceId format", "INVALID_REQUEST")
                )
            } catch (e: NamespaceNotFoundException) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse(e.message ?: "Namespace not found", "NAMESPACE_NOT_FOUND")
                )
            } catch (e: IllegalStateException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(e.message ?: "Feature disabled", "FEATURE_DISABLED")
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(e.message ?: "Failed to update namespace", "NAMESPACE_UPDATE_FAILED")
                )
            }
        }

        delete("{namespaceId}") {
            try {
                val namespaceIdStr =
                    call.parameters["namespaceId"] ?: throw IllegalArgumentException("namespaceId is required")
                val namespaceId = UUID.fromString(namespaceIdStr)
                val body = runCatching { call.receive<NamespaceDeleteRequestDto>() }.getOrNull()

                application.deleteNamespace(
                    namespaceId = namespaceId,
                    reason = body?.reason
                )
                call.respond(HttpStatusCode.OK, mapOf("message" to "Namespace deleted"))
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid namespaceId format", "INVALID_REQUEST")
                )
            } catch (e: NamespaceNotFoundException) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse(e.message ?: "Namespace not found", "NAMESPACE_NOT_FOUND")
                )
            } catch (e: IllegalStateException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(e.message ?: "Feature disabled", "FEATURE_DISABLED")
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(e.message ?: "Failed to delete namespace", "NAMESPACE_DELETE_FAILED")
                )
            }
        }
    }
}

private fun Namespace.toResponse(): NamespaceResponse = NamespaceResponse(
    tenantId = tenantId.toString(),
    id = namespaceId.toString(),
    name = name,
    description = description,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt?.toString(),
    deletedAt = deletedAt?.toString(),
    metadata = metadata
)



