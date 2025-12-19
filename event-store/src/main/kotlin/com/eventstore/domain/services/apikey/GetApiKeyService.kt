package com.eventstore.domain.services.apikey

import com.eventstore.domain.ApiKey
import com.eventstore.domain.ports.outbound.ApiKeyRepository

class GetApiKeyService(
    private val apiKeyRepository: ApiKeyRepository
) {
    suspend fun getById(keyId: String): ApiKey? {
        return apiKeyRepository.findById(keyId)
    }

    suspend fun getByUserId(userId: String): List<ApiKey> {
        return apiKeyRepository.findByUserId(userId)
    }
}

