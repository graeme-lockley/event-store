package com.eventstore.interfaces.http.dto

import com.eventstore.domain.services.*

/**
 * Mapper to convert HTTP DTOs to domain registration requests.
 * Note: InMemoryConsumerRegistrationRequest cannot be created from HTTP
 * as closures/functions cannot be serialized. It's only available programmatically.
 */
object ConsumerRegistrationRequestMapper {
    fun toDomain(dto: ConsumerRegistrationRequestDto): ConsumerRegistrationRequest {
        return when (dto) {
            is HttpConsumerRegistrationRequestDto -> {
                HttpConsumerRegistrationRequest(
                    callbackUrl = dto.callback,
                    topics = dto.topics
                )
            }
            
            is AzureEventGridConsumerRegistrationRequestDto -> {
                AzureEventGridConsumerRegistrationRequest(
                    endpointUrl = dto.endpointUrl,
                    accessKey = dto.accessKey,
                    topics = dto.topics
                )
            }
        }
    }
}

