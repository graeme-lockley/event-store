package com.eventstore.interfaces.http.middleware

import com.eventstore.domain.services.auth.AuthenticationService
import io.ktor.http.*
import io.ktor.server.application.*
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
}

