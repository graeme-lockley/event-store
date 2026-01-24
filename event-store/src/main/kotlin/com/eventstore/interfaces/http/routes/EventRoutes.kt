package com.eventstore.interfaces.http.routes

import com.eventstore.domain.Application
import com.eventstore.interfaces.http.dto.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Route.eventRoutes(application: Application) {
    route("/topics/{topicId}/events") {
        post {
            try {
                val topicIdStr = call.parameters["topicId"] ?: throw IllegalArgumentException("topicId is required")
                val topicId =
                    try {
                        UUID.fromString(topicIdStr)
                    } catch (e: IllegalArgumentException) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("Invalid topicId format. Expected UUID.", "INVALID_TOPIC_ID"),
                        )
                        return@post
                    }
                val requests = call.receive<List<EventRequest>>()
                if (requests.isEmpty()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Request body must be a non-empty array of events", "INVALID_REQUEST"),
                    )
                    return@post
                }
                requests.forEach { req ->
                    if (req.type.isBlank() || req.payload.isEmpty()) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("Each event must have type and payload", "INVALID_EVENT"),
                        )
                        return@post
                    }
                }
                val eventRequests =
                    requests.map { dto ->
                        com.eventstore.domain.services.event.EventRequest(
                            topicId = topicId,
                            type = dto.type,
                            payload = dto.payload,
                        )
                    }
                val eventIds = application.publishEvents(eventRequests)
                call.respond(HttpStatusCode.Created, EventResponse(eventIds))
            } catch (e: com.eventstore.domain.exceptions.TopicNotFoundException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(e.message ?: "Topic not found", "EVENT_PUBLISH_FAILED"),
                )
            } catch (e: com.eventstore.domain.exceptions.SchemaValidationException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(e.message ?: "Schema validation failed", "EVENT_PUBLISH_FAILED"),
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(e.message ?: "Unknown error", "EVENT_PUBLISH_FAILED"),
                )
            }
        }

        get {
            try {
                val topicIdStr = call.parameters["topicId"] ?: throw IllegalArgumentException("topicId is required")
                val topicId =
                    try {
                        UUID.fromString(topicIdStr)
                    } catch (e: IllegalArgumentException) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("Invalid topicId format. Expected UUID.", "INVALID_TOPIC_ID"),
                        )
                        return@get
                    }
                val sinceEventId = call.request.queryParameters["sinceEventId"]
                val date = call.request.queryParameters["date"]
                val limit = call.request.queryParameters["limit"]?.toIntOrNull()
                val events = application.getEvents(topicId, sinceEventId, date, limit)
                val eventDtos =
                    events.map { event ->
                        EventDto(
                            id = event.id.value,
                            timestamp = event.timestamp.toString(),
                            type = event.type,
                            payload = event.payload,
                        )
                    }
                call.respond(HttpStatusCode.OK, EventsResponse(eventDtos))
            } catch (e: com.eventstore.domain.exceptions.TopicNotFoundException) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse(e.message ?: "Topic not found", "TOPIC_NOT_FOUND"),
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(e.message ?: "Unknown error", "EVENTS_FETCH_FAILED"),
                )
            }
        }
    }
}
