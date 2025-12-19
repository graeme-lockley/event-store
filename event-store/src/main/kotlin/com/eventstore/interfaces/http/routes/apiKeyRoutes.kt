package com.eventstore.interfaces.http.routes

import com.eventstore.domain.exceptions.ApiKeyAlreadyRevokedException
import com.eventstore.domain.exceptions.ApiKeyNotFoundException
import com.eventstore.domain.exceptions.UserNotFoundException
import com.eventstore.domain.services.apikey.CreateApiKeyRequest
import com.eventstore.domain.services.apikey.CreateApiKeyService
import com.eventstore.domain.services.apikey.GetApiKeyService
import com.eventstore.domain.services.apikey.RevokeApiKeyRequest
import com.eventstore.domain.services.apikey.RevokeApiKeyService
import com.eventstore.interfaces.http.dto.ApiKeyListResponseDto
import com.eventstore.interfaces.http.dto.ApiKeyResponseDto
import com.eventstore.interfaces.http.dto.ApiKeyRevokeResponseDto
import com.eventstore.interfaces.http.dto.CreateApiKeyRequestDto
import com.eventstore.interfaces.http.dto.ErrorResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.Instant

// Validation constants
private const val MAX_NAME_LENGTH = 255
private const val MAX_DESCRIPTION_LENGTH = 1000

// Helper function for parameter validation
private fun ApplicationCall.requireParameter(name: String): String {
    val value = parameters[name]
    if (value.isNullOrBlank()) {
        throw IllegalArgumentException("Missing required parameter: $name")
    }
    return value
}

fun Route.apiKeyRoutes(
    createApiKeyService: CreateApiKeyService,
    getApiKeyService: GetApiKeyService,
    revokeApiKeyService: RevokeApiKeyService
) {
    route("/tenants/{tenantId}/users/{userId}/api-keys") {
        post {
            try {
                val userId = call.requireParameter("userId")
                val body = call.receive<CreateApiKeyRequestDto>()

                // Validate name
                val trimmedName = body.name.trim()
                when {
                    trimmedName.isBlank() -> {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("name is required and cannot be empty", "INVALID_INPUT")
                        )
                        return@post
                    }
                    trimmedName.length > MAX_NAME_LENGTH -> {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("name exceeds maximum length of $MAX_NAME_LENGTH characters", "INVALID_INPUT")
                        )
                        return@post
                    }
                }

                // Validate description length if provided
                body.description?.let { desc ->
                    if (desc.length > MAX_DESCRIPTION_LENGTH) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("description exceeds maximum length of $MAX_DESCRIPTION_LENGTH characters", "INVALID_INPUT")
                        )
                        return@post
                    }
                }

                // Validate expiresAt
                val expiresAt = body.expiresAt?.let {
                    try {
                        val parsed = Instant.parse(it)
                        if (parsed.isBefore(Instant.now())) {
                            call.respond(
                                HttpStatusCode.BadRequest,
                                ErrorResponse("expiresAt must be in the future", "INVALID_DATE")
                            )
                            return@post
                        }
                        parsed
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("Invalid expiresAt format. Use ISO 8601 format.", "INVALID_DATE_FORMAT")
                        )
                        return@post
                    }
                }

                val (apiKey, plainKey) = createApiKeyService.execute(
                    CreateApiKeyRequest(
                        userId = userId,
                        name = trimmedName,
                        description = body.description?.takeIf { it.isNotBlank() },
                        expiresAt = expiresAt,
                        scopes = body.scopes
                    )
                )

                call.respond(HttpStatusCode.Created, apiKey.toResponse(plainKey))
            } catch (e: UserNotFoundException) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse(e.message ?: "User not found", "USER_NOT_FOUND"))
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(e.message ?: "Invalid request parameter", "INVALID_PARAMETER")
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(e.message ?: "Failed to create API key", "API_KEY_CREATE_FAILED")
                )
            }
        }

        get {
            try {
                val userId = call.requireParameter("userId")
                val apiKeys = getApiKeyService.getByUserId(userId)
                call.respond(HttpStatusCode.OK, ApiKeyListResponseDto(apiKeys.map { it.toResponse() }))
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(e.message ?: "Invalid request parameter", "INVALID_PARAMETER")
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(e.message ?: "Failed to list API keys", "API_KEY_LIST_FAILED")
                )
            }
        }

        get("{keyId}") {
            try {
                val userId = call.requireParameter("userId")
                val keyId = call.requireParameter("keyId")
                val apiKey = getApiKeyService.getById(keyId) ?: throw ApiKeyNotFoundException(keyId)
                
                // Validate ownership - ensure API key belongs to the user in the URL
                if (apiKey.userId != userId) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse("API key does not belong to this user", "FORBIDDEN")
                    )
                    return@get
                }
                
                call.respond(HttpStatusCode.OK, apiKey.toResponse())
            } catch (e: ApiKeyNotFoundException) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse(e.message ?: "API key not found", "API_KEY_NOT_FOUND"))
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(e.message ?: "Invalid request parameter", "INVALID_PARAMETER")
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(e.message ?: "Failed to fetch API key", "API_KEY_GET_FAILED")
                )
            }
        }

        delete("{keyId}") {
            try {
                val userId = call.requireParameter("userId")
                val keyId = call.requireParameter("keyId")
                
                // Validate ownership before revoking
                val apiKey = getApiKeyService.getById(keyId)
                    ?: throw ApiKeyNotFoundException(keyId)
                
                if (apiKey.userId != userId) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse("API key does not belong to this user", "FORBIDDEN")
                    )
                    return@delete
                }
                
                revokeApiKeyService.execute(RevokeApiKeyRequest(keyId = keyId))
                
                // Get updated API key to get revokedAt timestamp
                val revokedApiKey = getApiKeyService.getById(keyId)
                call.respond(
                    HttpStatusCode.OK,
                    ApiKeyRevokeResponseDto(
                        message = "API key revoked",
                        keyId = keyId,
                        revokedAt = revokedApiKey?.revokedAt?.toString() ?: Instant.now().toString()
                    )
                )
            } catch (e: ApiKeyNotFoundException) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse(e.message ?: "API key not found", "API_KEY_NOT_FOUND"))
            } catch (e: ApiKeyAlreadyRevokedException) {
                call.respond(
                    HttpStatusCode.Conflict,
                    ErrorResponse(e.message ?: "API key already revoked", "API_KEY_ALREADY_REVOKED")
                )
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(e.message ?: "Invalid request parameter", "INVALID_PARAMETER")
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(e.message ?: "Failed to revoke API key", "API_KEY_REVOKE_FAILED")
                )
            }
        }
    }
}

private fun com.eventstore.domain.ApiKey.toResponse(plainKey: String? = null): ApiKeyResponseDto = ApiKeyResponseDto(
    id = id,
    userId = userId,
    name = name,
    description = description,
    createdAt = createdAt.toString(),
    expiresAt = expiresAt?.toString(),
    lastUsedAt = lastUsedAt?.toString(),
    revokedAt = revokedAt?.toString(),
    scopes = scopes,
    isActive = isActive,
    key = plainKey
)

