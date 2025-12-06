package com.eventstore.interfaces.http.routes

import com.eventstore.domain.services.GetEventsService
import com.eventstore.domain.services.PublishEventsService
import com.eventstore.interfaces.http.dto.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.eventRoutes(
    publishEventsService: PublishEventsService,
    getEventsService: GetEventsService,
    dispatcherManager: com.eventstore.infrastructure.background.DispatcherManager
) {
    route("/events") {
        // POST /events - Publish events
        post {
            try {
                val requests = call.receive<List<EventRequest>>()

                if (requests.isEmpty()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Request body must be a non-empty array of events", "INVALID_REQUEST")
                    )
                    return@post
                }

                // Validate all events first
                for (request in requests) {
                    if (request.topic.isBlank() || request.type.isBlank() || request.payload.isEmpty()) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("Each event must have topic, type, and payload", "INVALID_EVENT")
                        )
                        return@post
                    }
                }

                val eventRequests = requests.map { dto ->
                    com.eventstore.domain.services.EventRequest(
                        topic = dto.topic,
                        type = dto.type,
                        payload = dto.payload
                    )
                }

                val eventIds = publishEventsService.execute(eventRequests)

                // Trigger immediate delivery for each topic that received events
                val topicsWithEvents = requests.map { it.topic }.distinct()
                for (topic in topicsWithEvents) {
                    dispatcherManager.triggerDelivery(topic)
                }

                call.respond(HttpStatusCode.Created, EventResponse(eventIds))
            } catch (e: com.eventstore.domain.exceptions.TopicNotFoundException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(e.message ?: "Topic not found", "EVENT_PUBLISH_FAILED")
                )
            } catch (e: com.eventstore.domain.exceptions.SchemaValidationException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(e.message ?: "Schema validation failed", "EVENT_PUBLISH_FAILED")
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(e.message ?: "Unknown error", "EVENT_PUBLISH_FAILED")
                )
            }
        }
    }

    route("/topics/{topic}/events") {
        // GET /topics/{topic}/events - Retrieve events
        get {
            try {
                val topic = call.parameters["topic"]
                    ?: throw IllegalArgumentException("Topic parameter is required")

                val sinceEventId = call.request.queryParameters["sinceEventId"]
                val date = call.request.queryParameters["date"]
                val limit = call.request.queryParameters["limit"]?.toIntOrNull()

                val events = getEventsService.execute(topic, sinceEventId, date, limit)

                val eventDtos = events.map { event ->
                    EventDto(
                        id = event.id.value,
                        timestamp = event.timestamp.toString(),
                        type = event.type,
                        payload = event.payload
                    )
                }

                call.respond(HttpStatusCode.OK, EventsResponse(eventDtos))
            } catch (e: com.eventstore.domain.exceptions.TopicNotFoundException) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse(e.message ?: "Topic not found", "TOPIC_NOT_FOUND")
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(e.message ?: "Unknown error", "EVENTS_FETCH_FAILED")
                )
            }
        }
    }
}

