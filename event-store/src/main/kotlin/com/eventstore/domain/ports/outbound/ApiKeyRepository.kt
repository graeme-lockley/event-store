package com.eventstore.domain.ports.outbound

import com.eventstore.domain.ApiKey

interface ApiKeyRepository {
    suspend fun save(apiKey: ApiKey)
    suspend fun findById(id: String): ApiKey?
    suspend fun findByKeyHash(keyHash: String): ApiKey?
    suspend fun findByUserId(userId: String): List<ApiKey>
    suspend fun delete(id: String)
}

