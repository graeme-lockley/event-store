package com.eventstore.interfaces.http.middleware

import com.eventstore.domain.services.auth.AuthenticationService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*

class AuthenticationMiddleware(
    private val authenticationService: AuthenticationService
) {
    companion object {
        val UserIdKey = AttributeKey<String>("userId")
    }

    fun install(route: Route) {
        route.intercept(ApplicationCallPipeline.Features) {
            // Skip authentication for public endpoints
            val path = call.request.path()
            if (isPublicEndpoint(path)) {
                proceed()
                return@intercept
            }

            val sessionId = call.request.headers["Authorization"]?.removePrefix("Bearer ")
                ?: call.request.cookies["sessionId"]
            if (sessionId == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Missing session"))
                finish()
                return@intercept
            }

            val session = authenticationService.getSession(sessionId)
            if (session == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid session"))
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

