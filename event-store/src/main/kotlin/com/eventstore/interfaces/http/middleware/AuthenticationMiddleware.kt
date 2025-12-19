package com.eventstore.interfaces.http.middleware

import com.eventstore.domain.exceptions.InvalidApiKeyException
import com.eventstore.domain.services.auth.AuthenticationService
import com.eventstore.infrastructure.auth.ApiKeyAuthenticator
import com.eventstore.interfaces.http.dto.ErrorResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*

class AuthenticationMiddleware(
    private val authenticationService: AuthenticationService,
    private val apiKeyAuthenticator: ApiKeyAuthenticator? = null
) {
    companion object {
        val UserIdKey = AttributeKey<String>("userId")
        val ApiKeyIdKey = AttributeKey<String>("apiKeyId")
    }

    fun install(route: Route) {
        route.intercept(ApplicationCallPipeline.Features) {
            // Skip authentication for public endpoints
            val path = call.request.path()
            if (isPublicEndpoint(path)) {
                proceed()
                return@intercept
            }

            val authHeader = call.request.headers["Authorization"]
            val bearerToken = authHeader?.removePrefix("Bearer ")?.trim()
            val sessionId = bearerToken ?: call.request.cookies["sessionId"]

            if (sessionId == null) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Missing authentication", "AUTH_REQUIRED"))
                finish()
                return@intercept
            }

            // Check if it's an API key (starts with "es_")
            if (bearerToken != null && bearerToken.startsWith("es_") && apiKeyAuthenticator != null) {
                try {
                    val authResult = apiKeyAuthenticator.authenticate(bearerToken)
                    call.attributes.put(UserIdKey, authResult.userId)
                    call.attributes.put(ApiKeyIdKey, authResult.apiKeyId)
                    proceed()
                    return@intercept
                } catch (e: InvalidApiKeyException) {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse(e.message ?: "Invalid API key", "INVALID_API_KEY"))
                    finish()
                    return@intercept
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Authentication failed", "AUTH_FAILED"))
                    finish()
                    return@intercept
                }
            }

            // Fall back to session-based authentication
            val session = authenticationService.getSession(sessionId)
            if (session == null) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid session", "INVALID_SESSION"))
                finish()
                return@intercept
            }

            call.attributes.put(UserIdKey, session.userId)
        }
    }

    private fun isPublicEndpoint(path: String): Boolean {
        return path.startsWith("/auth/login") ||
               path.startsWith("/auth/logout") ||
               path.startsWith("/health") ||
               path == "/" ||
               path == "/favicon.ico"
    }
}

