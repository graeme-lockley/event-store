package com.eventstore.domain.services.apikey

import com.eventstore.domain.ApiKey
import com.eventstore.infrastructure.projections.ApiKeyProjectionService

class GetApiKeyService(
    private val apiKeyProjectionService: ApiKeyProjectionService,
) {
    suspend fun getById(keyId: String): ApiKey? {
        return apiKeyProjectionService.getApiKey(keyId)
    }

    suspend fun getByUserId(userId: String): List<ApiKey> {
        return apiKeyProjectionService.getApiKeysByUserId(userId)
    }
}
