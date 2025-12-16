package com.eventstore.interfaces.http.dto

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * DTO for consumer registration requests with type discrimination.
 * The HTTP layer uses this to deserialize requests based on the "type" field.
 * If "type" is not provided, defaults to HTTP consumer for backward compatibility.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
    defaultImpl = HttpConsumerRegistrationRequestDto::class
)
@JsonSubTypes(
    JsonSubTypes.Type(value = HttpConsumerRegistrationRequestDto::class, name = "http"),
    JsonSubTypes.Type(value = AzureEventGridConsumerRegistrationRequestDto::class, name = "azure-event-grid")
)
sealed interface ConsumerRegistrationRequestDto {
    val topics: Map<String, String?>
}

/**
 * HTTP consumer registration DTO.
 * For backward compatibility, this is also the default when no type is specified.
 */
data class HttpConsumerRegistrationRequestDto(
    val callback: String,
    override val topics: Map<String, String?>
) : ConsumerRegistrationRequestDto

/**
 * Azure Event Grid consumer registration DTO (for future implementation).
 */
data class AzureEventGridConsumerRegistrationRequestDto(
    val endpointUrl: String,
    val accessKey: String,
    override val topics: Map<String, String?>
) : ConsumerRegistrationRequestDto
