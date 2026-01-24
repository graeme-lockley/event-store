package com.eventstore.interfaces.http.dto

import com.eventstore.domain.services.consumer.AzureEventGridConsumerRegistrationRequest
import com.eventstore.domain.services.consumer.ConsumerRegistrationRequest
import com.eventstore.domain.services.consumer.HttpConsumerRegistrationRequest
import java.util.*

object ConsumerRegistrationRequestMapper {
    fun toDomain(dto: ConsumerRegistrationRequestDto): ConsumerRegistrationRequest {
        // Normalize empty strings to null for lastEventId
        // Convert topicId strings to UUIDs
        fun normalizeTopics(topics: Map<String, String?>): Map<UUID, String?> {
            return topics.mapNotNull { (key, value) ->
                try {
                    UUID.fromString(key) to (if (value.isNullOrBlank()) null else value)
                } catch (e: IllegalArgumentException) {
                    null // Skip invalid UUIDs
                }
            }.toMap()
        }

        return when (dto) {
            is HttpConsumerRegistrationRequestDto -> {
                HttpConsumerRegistrationRequest(
                    callbackUrl = dto.callback,
                    topics = normalizeTopics(dto.topics),
                )
            }

            is AzureEventGridConsumerRegistrationRequestDto -> {
                AzureEventGridConsumerRegistrationRequest(
                    endpointUrl = dto.endpointUrl,
                    accessKey = dto.accessKey,
                    topics = normalizeTopics(dto.topics),
                )
            }
        }
    }
}
