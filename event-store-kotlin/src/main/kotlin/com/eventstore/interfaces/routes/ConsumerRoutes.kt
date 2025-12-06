package com.eventstore.interfaces.routes

import com.eventstore.application.repositories.ConsumerRepository
import com.eventstore.application.usecases.RegisterConsumerUseCase
import com.eventstore.application.usecases.UnregisterConsumerUseCase
import com.eventstore.interfaces.dto.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.consumerRoutes(
    registerConsumerUseCase: RegisterConsumerUseCase,
    unregisterConsumerUseCase: UnregisterConsumerUseCase,
    consumerRepository: ConsumerRepository,
    dispatcherManager: com.eventstore.infrastructure.background.DispatcherManager
) {
    route("/consumers") {
        // POST /consumers/register - Register a consumer
        post("register") {
            try {
                val request = call.receive<ConsumerRegistrationRequest>()

                if (request.callback.isBlank() || request.topics.isEmpty()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(
                            "Invalid request body. Required: callback URL and topics object",
                            "INVALID_REQUEST"
                        )
                    )
                    return@post
                }

                // Validate callback is a proper URL
                try {
                    java.net.URL(request.callback)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Invalid callback URL", "INVALID_CALLBACK")
                    )
                    return@post
                }

                val registrationRequest = com.eventstore.application.usecases.ConsumerRegistrationRequest(
                    callback = request.callback,
                    topics = request.topics
                )

                val consumerId = registerConsumerUseCase.execute(registrationRequest)

                // Start dispatchers for topics that don't have one running
                for (topic in request.topics.keys) {
                    if (!dispatcherManager.isDispatcherRunning(topic)) {
                        dispatcherManager.startDispatcher(topic)
                    }
                }

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
                    ConsumerResponse(
                        id = consumer.id,
                        callback = consumer.callback.toString(),
                        topics = consumer.topics
                    )
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
                    unregisterConsumerUseCase.execute(consumerId)
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

