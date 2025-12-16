package com.eventstore.domain

/**
 * Enum representing the different consumer delivery protocol types.
 */
enum class ConsumerType {
    HTTP,
    IN_MEMORY,
    AZURE_EVENT_GRID  // For future implementation
}

