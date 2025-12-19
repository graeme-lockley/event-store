package com.eventstore.interfaces.http.routes

import com.eventstore.domain.services.event.GetEventsService
import com.eventstore.domain.services.event.PublishEventsService
import com.eventstore.interfaces.http.dto.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.eventRoutes(
    publishEventsService: PublishEventsService,
    getEventsService: GetEventsService
) {
    route("/tenants/{tenantName}/namespaces/{namespaceName}") {
        route("/events") {
            post {
                try {
                    val tenantName = call.parameters["tenantName"] ?: throw IllegalArgumentException("tenantName is required")
                    val namespaceName = call.parameters["namespaceName"] ?: throw IllegalArgumentException("namespaceName is required")
                    val requests = call.receive<List<EventRequest>>()
                    if (requests.isEmpty()) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Request body must be a non-empty array of events", "INVALID_REQUEST"))
                        return@post
                    }
                    requests.forEach { req ->
                        if (req.topic.isBlank() || req.type.isBlank() || req.payload.isEmpty()) {
                            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Each event must have topic, type, and payload", "INVALID_EVENT"))
                            return@post
                        }
                    }
                    val eventRequests = requests.map { dto ->
                        com.eventstore.domain.services.event.EventRequest(
                            topic = dto.topic,
                            type = dto.type,
                            payload = dto.payload,
                            tenantId = tenantName,
                            namespaceId = namespaceName
                        )
                    }
                    val eventIds = publishEventsService.execute(eventRequests)
                    call.respond(HttpStatusCode.Created, EventResponse(eventIds))
                } catch (e: com.eventstore.domain.exceptions.TopicNotFoundException) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Topic not found", "EVENT_PUBLISH_FAILED"))
                } catch (e: com.eventstore.domain.exceptions.SchemaValidationException) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Schema validation failed", "EVENT_PUBLISH_FAILED"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Unknown error", "EVENT_PUBLISH_FAILED"))
                }
            }
        }

        route("/topics/{topic}/events") {
            get {
                try {
                    val tenantName = call.parameters["tenantName"] ?: throw IllegalArgumentException("tenantName is required")
                    val namespaceName = call.parameters["namespaceName"] ?: throw IllegalArgumentException("namespaceName is required")
                    val topic = call.parameters["topic"] ?: throw IllegalArgumentException("Topic parameter is required")
                    val sinceEventId = call.request.queryParameters["sinceEventId"]
                    val date = call.request.queryParameters["date"]
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull()
                    val events = getEventsService.execute(topic, sinceEventId, date, limit, tenantName, namespaceName)
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
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(e.message ?: "Topic not found", "TOPIC_NOT_FOUND"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Unknown error", "EVENTS_FETCH_FAILED"))
                }
            }
        }
    }
}

