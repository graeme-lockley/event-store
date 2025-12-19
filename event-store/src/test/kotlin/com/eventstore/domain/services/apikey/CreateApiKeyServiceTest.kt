package com.eventstore.domain.services.apikey

import com.eventstore.domain.exceptions.UserNotFoundException
import com.eventstore.infrastructure.persistence.InMemoryApiKeyRepository
import com.eventstore.infrastructure.projections.InMemoryUserRepository
import com.eventstore.infrastructure.projections.UserProjectionService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class CreateApiKeyServiceTest {

    private lateinit var apiKeyRepository: InMemoryApiKeyRepository
    private lateinit var userProjectionService: UserProjectionService
    private lateinit var createApiKeyService: CreateApiKeyService

    @BeforeEach
    fun setUp() {
        apiKeyRepository = InMemoryApiKeyRepository()
        val userRepository = InMemoryUserRepository()
        userProjectionService = UserProjectionService(userRepository)
        createApiKeyService = CreateApiKeyService(apiKeyRepository, userProjectionService)
    }

    @Test
    fun `creates API key successfully`() = runBlocking {
        // Create a user first
        val userId = UUID.randomUUID().toString()
        val user = com.eventstore.domain.User(
            id = userId,
            email = "test@example.com",
            name = "Test User",
            passwordHash = "hash",
            status = com.eventstore.domain.UserStatus.ACTIVE,
            createdAt = Instant.now()
        )
        val userRepo = com.eventstore.infrastructure.projections.InMemoryUserRepository()
        userRepo.save(user)
        val testUserProjection = com.eventstore.infrastructure.projections.UserProjectionService(userRepo)
        val testService = CreateApiKeyService(apiKeyRepository, testUserProjection)
        
        val request = CreateApiKeyRequest(
            userId = userId,
            name = "Test API Key",
            description = "Test description"
        )

        val (apiKey, plainKey) = testService.execute(request)

        assertNotNull(apiKey)
        assertNotNull(plainKey)
        assertTrue(plainKey.startsWith("es_"))
        assertEquals(userId, apiKey.userId)
        assertEquals("Test API Key", apiKey.name)
        assertEquals("Test description", apiKey.description)

        // Verify it's saved
        val retrieved = apiKeyRepository.findById(apiKey.id)
        assertNotNull(retrieved)
    }

    @Test
    fun `throws exception when user does not exist`() = runBlocking {
        val request = CreateApiKeyRequest(
            userId = UUID.randomUUID().toString(),
            name = "Test API Key"
        )

        try {
            createApiKeyService.execute(request)
            fail("Should have thrown UserNotFoundException")
        } catch (e: UserNotFoundException) {
            // Expected
        }
    }

    @Test
    fun `creates API key with expiration`() = runBlocking {
        val userId = UUID.randomUUID().toString()
        val user = com.eventstore.domain.User(
            id = userId,
            email = "test@example.com",
            name = "Test User",
            passwordHash = "hash",
            status = com.eventstore.domain.UserStatus.ACTIVE,
            createdAt = Instant.now()
        )
        val userRepo = com.eventstore.infrastructure.projections.InMemoryUserRepository()
        userRepo.save(user)
        val testUserProjection = com.eventstore.infrastructure.projections.UserProjectionService(userRepo)
        val testService = CreateApiKeyService(apiKeyRepository, testUserProjection)

        val expiresAt = Instant.now().plusSeconds(3600)
        val request = CreateApiKeyRequest(
            userId = userId,
            name = "Test API Key",
            expiresAt = expiresAt
        )

        val (apiKey, _) = testService.execute(request)

        assertNotNull(apiKey.expiresAt)
        assertEquals(expiresAt, apiKey.expiresAt)
    }

    @Test
    fun `creates API key with scopes`() = runBlocking {
        val userId = UUID.randomUUID().toString()
        val user = com.eventstore.domain.User(
            id = userId,
            email = "test@example.com",
            name = "Test User",
            passwordHash = "hash",
            status = com.eventstore.domain.UserStatus.ACTIVE,
            createdAt = Instant.now()
        )
        val userRepo = com.eventstore.infrastructure.projections.InMemoryUserRepository()
        userRepo.save(user)
        val testUserProjection = com.eventstore.infrastructure.projections.UserProjectionService(userRepo)
        val testService = CreateApiKeyService(apiKeyRepository, testUserProjection)

        val scopes = setOf("read", "write")
        val request = CreateApiKeyRequest(
            userId = userId,
            name = "Test API Key",
            scopes = scopes
        )

        val (apiKey, _) = testService.execute(request)

        assertNotNull(apiKey.scopes)
        assertEquals(scopes, apiKey.scopes)
    }
}

