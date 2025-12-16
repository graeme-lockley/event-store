package com.eventstore.domain.ports.outbound

import com.eventstore.domain.Consumer
import com.eventstore.domain.services.ConsumerRegistrationRequest

/**
 * Factory interface for creating Consumer instances based on registration requests.
 * This allows for dependency injection and testability.
 */
interface ConsumerFactory {
    /**
     * Creates a Consumer instance from a registration request.
     * The factory determines the consumer type from the request type and creates
     * the appropriate concrete implementation.
     */
    fun create(request: ConsumerRegistrationRequest): Consumer
}

