package com.eventstore.interfaces.http.routes

import com.eventstore.domain.exceptions.InvalidCredentialsException
import com.eventstore.domain.services.auth.AuthenticationService
import com.eventstore.domain.services.user.ChangePasswordRequest
import com.eventstore.domain.services.user.ChangePasswordService
import com.eventstore.interfaces.http.dto.ChangePasswordRequestDto
import com.eventstore.interfaces.http.dto.ErrorResponse
import com.eventstore.interfaces.http.dto.LoginRequest
import com.eventstore.interfaces.http.dto.LoginResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.authRoutes(
    authenticationService: AuthenticationService,
    changePasswordService: ChangePasswordService
) {
    route("/auth") {
        post("/login") {
            try {
                val body = call.receive<LoginRequest>()
                val (session, tenants) = authenticationService.login(body.email, body.password)
                call.response.cookies.append("sessionId", session.id, httpOnly = true)
                call.respond(HttpStatusCode.OK, LoginResponse(session.id, session.userId, tenants))
            } catch (e: InvalidCredentialsException) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse(e.message ?: "Invalid credentials", "INVALID_CREDENTIALS"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Login failed", "LOGIN_FAILED"))
            }
        }

        post("/logout") {
            val sessionId = call.request.cookies["sessionId"]
                ?: call.request.headers["Authorization"]?.removePrefix("Bearer ")
            if (sessionId != null) {
                authenticationService.logout(sessionId)
                call.response.cookies.appendExpired("sessionId")
            }
            call.respond(HttpStatusCode.OK, mapOf("message" to "Logged out"))
        }

        post("/password/change") {
            try {
                val sessionId = call.request.cookies["sessionId"]
                    ?: call.request.headers["Authorization"]?.removePrefix("Bearer ")
                    ?: throw InvalidCredentialsException()
                val session = authenticationService.getSession(sessionId) ?: throw InvalidCredentialsException()
                val body = call.receive<ChangePasswordRequestDto>()
                changePasswordService.execute(
                    ChangePasswordRequest(
                        userId = session.userId,
                        oldPassword = body.oldPassword,
                        newPassword = body.newPassword,
                        changedBy = session.userId
                    )
                )
                call.respond(HttpStatusCode.OK, mapOf("message" to "Password changed"))
            } catch (e: InvalidCredentialsException) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse(e.message ?: "Invalid credentials", "INVALID_CREDENTIALS"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Failed to change password", "PASSWORD_CHANGE_FAILED"))
            }
        }
    }
}
 