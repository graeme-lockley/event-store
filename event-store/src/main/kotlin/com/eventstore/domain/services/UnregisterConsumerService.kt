package com.eventstore.domain.services

import com.eventstore.domain.exceptions.ConsumerNotFoundException
import com.eventstore.domain.ports.outbound.ConsumerRepository

class UnregisterConsumerService(
    private val consumerRepository: ConsumerRepository
) {
    suspend fun execute(consumerId: String): Boolean {
        val removed = consumerRepository.delete(consumerId)
        if (!removed) {
            throw ConsumerNotFoundException(consumerId)
        }
        return true
    }
}

