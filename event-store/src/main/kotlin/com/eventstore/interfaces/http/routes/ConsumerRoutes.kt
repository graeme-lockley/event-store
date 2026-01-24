package com.eventstore.interfaces.http.routes

import com.eventstore.domain.Application
import com.eventstore.interfaces.http.dto.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Route.consumerRoutes(application: Application) {
    route("/namespaces/{namespaceId}/consumers") {
        // POST /namespaces/{namespaceId}/consumers/register - Register a consumer
        post("register") {
            try {
                val namespaceIdStr =
                    call.parameters["namespaceId"] ?: throw IllegalArgumentException("namespaceId is required")
                val namespaceId =
                    try {
                        UUID.fromString(namespaceIdStr)
                    } catch (e: IllegalArgumentException) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("Invalid namespaceId format. Expected UUID.", "INVALID_NAMESPACE_ID"),
                        )
                        return@post
                    }
                // Note: namespaceId is validated but not used by domain services (consumers register by topicId UUIDs)
                val requestDto = call.receive<ConsumerRegistrationRequestDto>()

                // Convert DTO to domain request
                val registrationRequest =
                    try {
                        ConsumerRegistrationRequestMapper.toDomain(requestDto)
                    } catch (e: IllegalArgumentException) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse(e.message ?: "Invalid request", "INVALID_REQUEST"),
                        )
                        return@post
                    }

                val consumerId = application.registerConsumer(registrationRequest)

                call.respond(HttpStatusCode.Created, ConsumerRegistrationResponse(consumerId))
            } catch (e: com.eventstore.domain.exceptions.TopicNotFoundException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(e.message ?: "Topic not found", "TOPIC_NOT_FOUND"),
                )
            } catch (e: com.eventstore.domain.exceptions.InvalidConsumerRegistrationException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(e.message ?: "Invalid registration", "CONSUMER_REGISTRATION_FAILED"),
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(e.message ?: "Unknown error", "CONSUMER_REGISTRATION_FAILED"),
                )
            }
        }

        // GET /namespaces/{namespaceId}/consumers - List consumers in namespace
        get {
            try {
                val namespaceIdStr =
                    call.parameters["namespaceId"] ?: throw IllegalArgumentException("namespaceId is required")
                val namespaceId =
                    try {
                        UUID.fromString(namespaceIdStr)
                    } catch (e: IllegalArgumentException) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("Invalid namespaceId format. Expected UUID.", "INVALID_NAMESPACE_ID"),
                        )
                        return@get
                    }
                // Note: namespaceId is validated but not used by domain services (listConsumers returns all consumers)
                val consumers = application.listConsumers()
                val consumerInfo =
                    consumers.map { consumer ->
                        ConsumerResponseMapper.toDto(consumer)
                    }
                call.respond(HttpStatusCode.OK, ConsumersResponse(consumerInfo))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(e.message ?: "Unknown error", "CONSUMERS_LIST_FAILED"),
                )
            }
        }

        // DELETE /namespaces/{namespaceId}/consumers/{id} - Unregister a consumer
        delete("{id}") {
            try {
                val namespaceIdStr =
                    call.parameters["namespaceId"] ?: throw IllegalArgumentException("namespaceId is required")
                val namespaceId =
                    try {
                        UUID.fromString(namespaceIdStr)
                    } catch (e: IllegalArgumentException) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("Invalid namespaceId format. Expected UUID.", "INVALID_NAMESPACE_ID"),
                        )
                        return@delete
                    }
                // Note: namespaceId is validated but not used by domain services
                val consumerId =
                    call.parameters["id"]
                        ?: throw IllegalArgumentException("Consumer ID is required")

                try {
                    application.unregisterConsumer(consumerId)
                    call.respond(
                        HttpStatusCode.OK,
                        mapOf("message" to "Consumer $consumerId unregistered"),
                    )
                } catch (e: com.eventstore.domain.exceptions.ConsumerNotFoundException) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse(e.message ?: "Consumer not found", "CONSUMER_NOT_FOUND"),
                    )
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(e.message ?: "Unknown error", "CONSUMER_DELETE_FAILED"),
                )
            }
        }
    }
}
