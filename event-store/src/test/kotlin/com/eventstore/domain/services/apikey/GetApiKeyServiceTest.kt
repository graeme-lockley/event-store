package com.eventstore.domain.services.apikey

import com.eventstore.infrastructure.persistence.InMemoryApiKeyRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class GetApiKeyServiceTest {

    private lateinit var apiKeyRepository: InMemoryApiKeyRepository
    private lateinit var getApiKeyService: GetApiKeyService

    @BeforeEach
    fun setUp() {
        apiKeyRepository = InMemoryApiKeyRepository()
        getApiKeyService = GetApiKeyService(apiKeyRepository)
    }

    @Test
    fun `gets API key by ID`() = runBlocking {
        val apiKey = com.eventstore.domain.ApiKey(
            id = "test-id",
            userId = "user-id",
            keyHash = "hash",
            name = "Test Key",
            createdAt = Instant.now()
        )

        apiKeyRepository.save(apiKey)
        val retrieved = getApiKeyService.getById("test-id")

        assertNotNull(retrieved)
        assertEquals("test-id", retrieved?.id)
    }

    @Test
    fun `returns null for non-existent API key`() = runBlocking {
        val retrieved = getApiKeyService.getById("non-existent")
        assertNull(retrieved)
    }

    @Test
    fun `gets API keys by user ID`() = runBlocking {
        val apiKey1 = com.eventstore.domain.ApiKey(
            id = "key1",
            userId = "user1",
            keyHash = "hash1",
            name = "Key 1",
            createdAt = Instant.now()
        )
        val apiKey2 = com.eventstore.domain.ApiKey(
            id = "key2",
            userId = "user1",
            keyHash = "hash2",
            name = "Key 2",
            createdAt = Instant.now()
        )

        apiKeyRepository.save(apiKey1)
        apiKeyRepository.save(apiKey2)

        val userKeys = getApiKeyService.getByUserId("user1")

        assertEquals(2, userKeys.size)
        assertTrue(userKeys.any { it.id == "key1" })
        assertTrue(userKeys.any { it.id == "key2" })
    }
}

