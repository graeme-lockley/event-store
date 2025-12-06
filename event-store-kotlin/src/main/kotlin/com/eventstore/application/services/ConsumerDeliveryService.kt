package com.eventstore.application.services

import com.eventstore.domain.Consumer
import com.eventstore.domain.Event

/**
 * Service interface for delivering events to consumers via webhooks.
 */
interface ConsumerDeliveryService {
    suspend fun deliverEvents(consumer: Consumer, events: List<Event>): DeliveryResult
}

data class DeliveryResult(
    val success: Boolean,
    val error: String? = null
)

