package com.eventstore.interfaces.http.routes

import com.eventstore.domain.Application
import com.eventstore.domain.Schema
import com.eventstore.interfaces.http.dto.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Route.topicRoutes(
    application: Application,
    dispatcherManager: com.eventstore.infrastructure.background.AsyncDispatcherManager
) {
    route("/topics") {
        post {
            try {
                val request = call.receive<TopicCreationRequest>()
                if (request.name.isBlank() || request.schemas.isEmpty()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Invalid request body. Required: namespaceId, name, schemas array", "INVALID_REQUEST")
                    )
                    return@post
                }
                val namespaceId = try {
                    UUID.fromString(request.namespaceId)
                } catch (e: IllegalArgumentException) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Invalid namespaceId format. Expected UUID.", "INVALID_NAMESPACE_ID")
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
                val topic = application.createTopic(request.name, schemas, namespaceId)
                call.respond(
                    HttpStatusCode.Created,
                    mapOf("message" to "Topic '${request.name}' created", "topicId" to topic.topicId.toString())
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

        get {
            try {
                val namespaceIdStr = call.request.queryParameters["namespaceId"]
                val namespaceId = namespaceIdStr?.let { 
                    try {
                        UUID.fromString(it)
                    } catch (e: IllegalArgumentException) {
                        null
                    }
                }
                val topics = application.listTopics(namespaceId)
                val response = TopicsResponse(
                    topics = topics.map { topic: com.eventstore.domain.Topic ->
                        TopicResponse(
                            topicId = topic.topicId.toString(),
                            namespaceId = topic.namespaceId.toString(),
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

        get("{topicId}") {
            try {
                val topicIdStr = call.parameters["topicId"] ?: throw IllegalArgumentException("topicId is required")
                val topicId = try {
                    UUID.fromString(topicIdStr)
                } catch (e: IllegalArgumentException) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Invalid topicId format. Expected UUID.", "INVALID_TOPIC_ID")
                    )
                    return@get
                }
                val topic = application.getTopic(topicId)
                val response = TopicResponse(
                    topicId = topic.topicId.toString(),
                    namespaceId = topic.namespaceId.toString(),
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
                call.respond(HttpStatusCode.NotFound, ErrorResponse(e.message ?: "Topic not found", "TOPIC_NOT_FOUND"))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(e.message ?: "Unknown error", "TOPIC_FETCH_FAILED")
                )
            }
        }

        put("{topicId}/schemas") {
            try {
                val topicIdStr = call.parameters["topicId"] ?: throw IllegalArgumentException("topicId is required")
                val topicId = try {
                    UUID.fromString(topicIdStr)
                } catch (e: IllegalArgumentException) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Invalid topicId format. Expected UUID.", "INVALID_TOPIC_ID")
                    )
                    return@put
                }
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
                application.updateTopicSchemas(topicId, schemas)
                call.respond(HttpStatusCode.OK, mapOf("message" to "Topic schemas updated successfully"))
            } catch (e: com.eventstore.domain.exceptions.TopicNotFoundException) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse(e.message ?: "Topic not found", "TOPIC_NOT_FOUND"))
            } catch (e: IllegalArgumentException) {
                val errorCode =
                    if (e.message?.contains("Cannot remove schemas") == true) "SCHEMA_REMOVAL_NOT_ALLOWED" else "TOPIC_UPDATE_FAILED"
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Unknown error", errorCode))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(e.message ?: "Unknown error", "TOPIC_UPDATE_FAILED")
                )
            }
        }
    }
}

