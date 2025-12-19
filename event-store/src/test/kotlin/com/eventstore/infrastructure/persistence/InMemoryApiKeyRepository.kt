package com.eventstore.infrastructure.persistence

import com.eventstore.domain.ApiKey
import com.eventstore.domain.ports.outbound.ApiKeyRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemoryApiKeyRepository : ApiKeyRepository {
    private val mutex = Mutex()
    private val apiKeys = mutableMapOf<String, ApiKey>()

    override suspend fun save(apiKey: ApiKey) {
        mutex.withLock {
            apiKeys[apiKey.id] = apiKey
        }
    }

    override suspend fun findById(id: String): ApiKey? {
        return mutex.withLock {
            apiKeys[id]
        }
    }

    override suspend fun findByKeyHash(keyHash: String): ApiKey? {
        return mutex.withLock {
            apiKeys.values.find { it.keyHash == keyHash }
        }
    }

    override suspend fun findByUserId(userId: String): List<ApiKey> {
        return mutex.withLock {
            apiKeys.values.filter { it.userId == userId }
        }
    }

    override suspend fun delete(id: String) {
        mutex.withLock {
            apiKeys.remove(id)
        }
    }
}

