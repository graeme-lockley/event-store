package com.eventstore.interfaces.routes

import com.eventstore.application.usecases.GetHealthStatusUseCase
import com.eventstore.interfaces.dto.HealthResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.healthRoutes(getHealthStatusUseCase: GetHealthStatusUseCase) {
    get("/health") {
        val status = getHealthStatusUseCase.execute()
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

