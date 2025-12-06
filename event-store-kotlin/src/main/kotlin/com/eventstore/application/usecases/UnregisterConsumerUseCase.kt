package com.eventstore.application.usecases

import com.eventstore.application.repositories.ConsumerRepository
import com.eventstore.domain.exceptions.ConsumerNotFoundException

class UnregisterConsumerUseCase(
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

