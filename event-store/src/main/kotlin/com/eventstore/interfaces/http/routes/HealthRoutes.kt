package com.eventstore.interfaces.http.routes

import com.eventstore.domain.Application
import com.eventstore.interfaces.http.dto.HealthResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.healthRoutes(application: Application) {
    get("/health") {
        val status = application.getHealthStatus()
        call.respond(
            HttpStatusCode.OK,
            HealthResponse(
                status = status.status,
                consumers = status.consumers,
                runningDispatchers = status.runningDispatchers
            )
        )
    }
}

