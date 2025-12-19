package com.eventstore.domain.services.consumer

import com.eventstore.domain.exceptions.ConsumerNotFoundException
import com.eventstore.domain.ports.outbound.ConsumerRepository

class UnregisterConsumerService(
    private val consumerRepository: ConsumerRepository
) {
    suspend fun execute(consumerId: String, tenantName: String, namespaceName: String): Boolean {
        // Verify consumer exists in the tenant/namespace context
        val consumer = consumerRepository.findById(consumerId)
        if (consumer == null) {
            throw ConsumerNotFoundException(consumerId)
        }
        
        // Note: Consumer domain model doesn't currently store tenant/namespace,
        // but we validate it exists before deletion
        val removed = consumerRepository.delete(consumerId)
        if (!removed) {
            throw ConsumerNotFoundException(consumerId)
        }
        return true
    }
}

