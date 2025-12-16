package com.eventstore.interfaces.http.dto

import com.eventstore.domain.Consumer
import com.eventstore.domain.consumers.HttpConsumer

/**
 * Mapper to convert domain Consumer to HTTP DTO.
 */
object ConsumerResponseMapper {
    fun toDto(consumer: Consumer): ConsumerResponse {
        // Extract callback URL for HTTP consumers, use toString() for others
        val callback = when (consumer) {
            is HttpConsumer -> consumer.callbackUrl.toString()
            else -> consumer.toString() // Fallback for non-HTTP consumers
        }
        
        return ConsumerResponse(
            id = consumer.id,
            callback = callback,
            topics = consumer.topics
        )
    }
}

