package com.eventstore.domain.services

import com.eventstore.domain.ports.outbound.ConsumerRepository

data class HealthStatus(
    val status: String,
    val consumers: Int,
    val runningDispatchers: List<String>
)

class GetHealthStatusService(
    private val consumerRepository: ConsumerRepository,
    private val runningDispatchers: suspend () -> List<String>
) {
    suspend fun execute(): HealthStatus {
        return HealthStatus(
            status = "healthy",
            consumers = consumerRepository.count(),
            runningDispatchers = runningDispatchers()
        )
    }
}

