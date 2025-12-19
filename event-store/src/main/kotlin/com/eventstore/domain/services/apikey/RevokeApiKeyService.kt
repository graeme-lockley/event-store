package com.eventstore.domain.services.apikey

import com.eventstore.domain.exceptions.ApiKeyAlreadyRevokedException
import com.eventstore.domain.exceptions.ApiKeyNotFoundException
import com.eventstore.domain.ports.outbound.ApiKeyRepository
import java.time.Instant

data class RevokeApiKeyRequest(
    val keyId: String
)

class RevokeApiKeyService(
    private val apiKeyRepository: ApiKeyRepository
) {
    suspend fun execute(request: RevokeApiKeyRequest) {
        val apiKey = apiKeyRepository.findById(request.keyId)
            ?: throw ApiKeyNotFoundException(request.keyId)

        if (apiKey.revokedAt != null) {
            throw ApiKeyAlreadyRevokedException(request.keyId)
        }

        val revokedKey = apiKey.copy(revokedAt = Instant.now())
        apiKeyRepository.save(revokedKey)
    }
}

