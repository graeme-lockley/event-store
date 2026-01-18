package com.eventstore.interfaces.http.routes

import com.eventstore.domain.Application
import com.eventstore.interfaces.http.dto.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.consumerRoutes(application: Application) {
    route("/tenants/{tenantName}/namespaces/{namespaceName}/consumers") {
        // POST /tenants/{tenantName}/namespaces/{namespaceName}/consumers/register - Register a consumer
        post("register") {
            try {
                val tenantName =
                    call.parameters["tenantName"] ?: throw IllegalArgumentException("tenantName is required")
                val namespaceName =
                    call.parameters["namespaceName"] ?: throw IllegalArgumentException("namespaceName is required")
                val requestDto = call.receive<ConsumerRegistrationRequestDto>()

                // Convert DTO to domain request
                val registrationRequest = try {
                    ConsumerRegistrationRequestMapper.toDomain(requestDto)
                } catch (e: IllegalArgumentException) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(e.message ?: "Invalid request", "INVALID_REQUEST")
                    )
                    return@post
                }

                val consumerId = application.registerConsumer(registrationRequest)

                call.respond(HttpStatusCode.Created, ConsumerRegistrationResponse(consumerId))
            } catch (e: com.eventstore.domain.exceptions.TopicNotFoundException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(e.message ?: "Topic not found", "TOPIC_NOT_FOUND")
                )
            } catch (e: com.eventstore.domain.exceptions.InvalidConsumerRegistrationException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(e.message ?: "Invalid registration", "CONSUMER_REGISTRATION_FAILED")
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(e.message ?: "Unknown error", "CONSUMER_REGISTRATION_FAILED")
                )
            }
        }

        // GET /tenants/{tenantName}/namespaces/{namespaceName}/consumers - List consumers in namespace
        get {
            try {
                val tenantName =
                    call.parameters["tenantName"] ?: throw IllegalArgumentException("tenantName is required")
                val namespaceName =
                    call.parameters["namespaceName"] ?: throw IllegalArgumentException("namespaceName is required")
                val consumers = application.listConsumers()
                val consumerInfo = consumers.map { consumer ->
                    ConsumerResponseMapper.toDto(consumer)
                }
                call.respond(HttpStatusCode.OK, ConsumersResponse(consumerInfo))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(e.message ?: "Unknown error", "CONSUMERS_LIST_FAILED")
                )
            }
        }

        // DELETE /tenants/{tenantName}/namespaces/{namespaceName}/consumers/{id} - Unregister a consumer
        delete("{id}") {
            try {
                val tenantName =
                    call.parameters["tenantName"] ?: throw IllegalArgumentException("tenantName is required")
                val namespaceName =
                    call.parameters["namespaceName"] ?: throw IllegalArgumentException("namespaceName is required")
                val consumerId = call.parameters["id"]
                    ?: throw IllegalArgumentException("Consumer ID is required")

                try {
                    application.unregisterConsumer(consumerId)
                    call.respond(
                        HttpStatusCode.OK,
                        mapOf("message" to "Consumer $consumerId unregistered")
                    )
                } catch (e: com.eventstore.domain.exceptions.ConsumerNotFoundException) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse(e.message ?: "Consumer not found", "CONSUMER_NOT_FOUND")
                    )
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(e.message ?: "Unknown error", "CONSUMER_DELETE_FAILED")
                )
            }
        }
    }
}
