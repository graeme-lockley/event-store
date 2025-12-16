package com.eventstore.interfaces.http.dto

import com.eventstore.domain.services.*

/**
 * Mapper to convert HTTP DTOs to domain registration requests.
 * Note: InMemoryConsumerRegistrationRequest cannot be created from HTTP
 * as closures/functions cannot be serialized. It's only available programmatically.
 */
object ConsumerRegistrationRequestMapper {
    fun toDomain(dto: ConsumerRegistrationRequestDto): ConsumerRegistrationRequest {
        // Normalize empty strings to null for lastEventId
        fun normalizeTopics(topics: Map<String, String?>): Map<String, String?> {
            return topics.mapValues { (_, value) ->
                if (value.isNullOrBlank()) null else value
            }
        }
        
        return when (dto) {
            is HttpConsumerRegistrationRequestDto -> {
                HttpConsumerRegistrationRequest(
                    callbackUrl = dto.callback,
                    topics = normalizeTopics(dto.topics)
                )
            }
            
            is AzureEventGridConsumerRegistrationRequestDto -> {
                AzureEventGridConsumerRegistrationRequest(
                    endpointUrl = dto.endpointUrl,
                    accessKey = dto.accessKey,
                    topics = normalizeTopics(dto.topics)
                )
            }
        }
    }
}

