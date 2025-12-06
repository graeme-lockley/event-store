package com.eventstore.application.usecases

import com.eventstore.application.repositories.ConsumerRepository

data class HealthStatus(
    val status: String,
    val consumers: Int,
    val runningDispatchers: List<String>
)

class GetHealthStatusUseCase(
    private val consumerRepository: ConsumerRepository,
    private val runningDispatchers: () -> List<String>
) {
    suspend fun execute(): HealthStatus {
        return HealthStatus(
            status = "healthy",
            consumers = consumerRepository.count(),
            runningDispatchers = runningDispatchers()
        )
    }
}

