package com.eventstore.interfaces.http.routes

import com.eventstore.domain.Schema
import com.eventstore.domain.services.CreateTopicService
import com.eventstore.domain.services.GetTopicsService
import com.eventstore.domain.services.UpdateTopicSchemasService
import com.eventstore.interfaces.http.dto.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.topicRoutes(
    createTopicService: CreateTopicService,
    getTopicsService: GetTopicsService,
    updateTopicSchemasService: UpdateTopicSchemasService,
    dispatcherManager: com.eventstore.infrastructure.background.DispatcherManager
) {
    route("/topics") {
        // POST /topics - Create a new topic
        post {
            try {
                val request = call.receive<TopicCreationRequest>()

                if (request.name.isBlank() || request.schemas.isEmpty()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Invalid request body. Required: name, schemas array", "INVALID_REQUEST")
                    )
                    return@post
                }

                val schemas = request.schemas.map { dto ->
                    Schema(
                        eventType = dto.eventType,
                        type = dto.type,
                        schema = dto.schema,
                        properties = dto.properties,
                        required = dto.required
                    )
                }

                createTopicService.execute(request.name, schemas)

                // Start dispatcher for the new topic
                dispatcherManager.startDispatcher(request.name)

                call.respond(
                    HttpStatusCode.Created,
                    mapOf("message" to "Topic '${request.name}' created successfully")
                )
            } catch (e: com.eventstore.domain.exceptions.TopicAlreadyExistsException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(e.message ?: "Topic already exists", "TOPIC_CREATION_FAILED")
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(e.message ?: "Unknown error", "TOPIC_CREATION_FAILED")
                )
            }
        }

        // GET /topics - List all topics
        get {
            try {
                val topics = getTopicsService.execute()
                val response = TopicsResponse(
                    topics = topics.map { topic ->
                        TopicResponse(
                            name = topic.name,
                            sequence = topic.sequence,
                            schemas = topic.schemas.map { schema ->
                                SchemaDto(
                                    eventType = schema.eventType,
                                    type = schema.type,
                                    schema = schema.schema,
                                    properties = schema.properties,
                                    required = schema.required
                                )
                            }
                        )
                    }
                )
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(e.message ?: "Unknown error", "TOPICS_LIST_FAILED")
                )
            }
        }

        // GET /topics/{topic} - Get topic details
        get("{topic}") {
            try {
                val topicName = call.parameters["topic"]
                    ?: throw IllegalArgumentException("Topic parameter is required")

                val topic = getTopicsService.execute(topicName)
                val response = TopicResponse(
                    name = topic.name,
                    sequence = topic.sequence,
                    schemas = topic.schemas.map { schema ->
                        SchemaDto(
                            eventType = schema.eventType,
                            type = schema.type,
                            schema = schema.schema,
                            properties = schema.properties,
                            required = schema.required
                        )
                    }
                )
                call.respond(HttpStatusCode.OK, response)
            } catch (e: com.eventstore.domain.exceptions.TopicNotFoundException) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse(e.message ?: "Topic not found", "TOPIC_NOT_FOUND")
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(e.message ?: "Unknown error", "TOPIC_FETCH_FAILED")
                )
            }
        }

        // PUT /topics/{topic} - Update schemas
        put("{topic}") {
            try {
                val topicName = call.parameters["topic"]
                    ?: throw IllegalArgumentException("Topic parameter is required")

                val request = call.receive<TopicUpdateRequest>()

                if (request.schemas.isEmpty()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Invalid request body. Required: schemas array", "INVALID_REQUEST")
                    )
                    return@put
                }

                val schemas = request.schemas.map { dto ->
                    Schema(
                        eventType = dto.eventType,
                        type = dto.type,
                        schema = dto.schema,
                        properties = dto.properties,
                        required = dto.required
                    )
                }

                updateTopicSchemasService.execute(topicName, schemas)

                call.respond(
                    HttpStatusCode.OK,
                    mapOf("message" to "Topic '$topicName' schemas updated successfully")
                )
            } catch (e: com.eventstore.domain.exceptions.TopicNotFoundException) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse(e.message ?: "Topic not found", "TOPIC_NOT_FOUND")
                )
            } catch (e: IllegalArgumentException) {
                val errorCode = if (e.message?.contains("Cannot remove schemas") == true) {
                    "SCHEMA_REMOVAL_NOT_ALLOWED"
                } else {
                    "TOPIC_UPDATE_FAILED"
                }
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(e.message ?: "Unknown error", errorCode)
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(e.message ?: "Unknown error", "TOPIC_UPDATE_FAILED")
                )
            }
        }
    }
}

