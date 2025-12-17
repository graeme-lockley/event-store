package com.eventstore.interfaces.http.routes

import com.eventstore.domain.ports.outbound.ConsumerRepository
import com.eventstore.domain.services.consumer.RegisterConsumerService
import com.eventstore.domain.services.consumer.UnregisterConsumerService
import com.eventstore.interfaces.http.dto.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.consumerRoutes(
    registerConsumerService: RegisterConsumerService,
    unregisterConsumerService: UnregisterConsumerService,
    consumerRepository: ConsumerRepository
) {
    route("/consumers") {
        // POST /consumers/register - Register a consumer
        post("register") {
            try {
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

                val consumerId = registerConsumerService.execute(registrationRequest)

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

        // GET /consumers - List all consumers
        get {
            try {
                val consumers = consumerRepository.findAll()
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

        // DELETE /consumers/{id} - Unregister a consumer
        delete("{id}") {
            try {
                val consumerId = call.parameters["id"]
                    ?: throw IllegalArgumentException("Consumer ID is required")

                try {
                    unregisterConsumerService.execute(consumerId)
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
