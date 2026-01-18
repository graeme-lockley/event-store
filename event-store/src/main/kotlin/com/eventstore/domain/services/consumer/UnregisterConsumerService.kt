package com.eventstore.domain.services.consumer

import com.eventstore.domain.exceptions.ConsumerNotFoundException
import com.eventstore.domain.ports.outbound.ConsumerRepository

class UnregisterConsumerService(
    private val consumerRepository: ConsumerRepository
) {
    suspend fun execute(consumerId: String): Boolean {
        // Verify consumer exists
        val consumer = consumerRepository.findById(consumerId)
        if (consumer == null) {
            throw ConsumerNotFoundException(consumerId)
        }

        val removed = consumerRepository.delete(consumerId)
        if (!removed) {
            throw ConsumerNotFoundException(consumerId)
        }
        return true
    }
}

