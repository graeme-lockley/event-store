package com.eventstore.infrastructure.persistence

import com.eventstore.domain.ApiKey
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileSystemApiKeyRepositoryTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var repository: FileSystemApiKeyRepository
    private val objectMapper = jacksonObjectMapper().apply {
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    @BeforeEach
    fun setUp() {
        repository = FileSystemApiKeyRepository(tempDir, objectMapper)
    }

    @Test
    fun `should persist API keys to file system`() = runBlocking {
        val apiKey = ApiKey(
            id = "test-id",
            userId = "user-id",
            keyHash = "hash123",
            name = "Test Key",
            createdAt = Instant.now()
        )

        repository.save(apiKey)

        val apiKeysFile = tempDir.resolve("api-keys.json")
        assertTrue(Files.exists(apiKeysFile))
    }

    @Test
    fun `should read API keys from file system`() = runBlocking {
        val apiKey = ApiKey(
            id = "test-id",
            userId = "user-id",
            keyHash = "hash123",
            name = "Test Key",
            createdAt = Instant.now()
        )

        repository.save(apiKey)

        // Create a new repository instance to verify it reads from disk
        val newRepository = FileSystemApiKeyRepository(tempDir, objectMapper)
        val retrieved = newRepository.findById("test-id")

        assertNotNull(retrieved)
        assertEquals("test-id", retrieved?.id)
        assertEquals("user-id", retrieved?.userId)
        assertEquals("hash123", retrieved?.keyHash)
        assertEquals("Test Key", retrieved?.name)
    }

    @Test
    fun `should find API key by ID`() = runBlocking {
        val apiKey = ApiKey(
            id = "test-id",
            userId = "user-id",
            keyHash = "hash123",
            name = "Test Key",
            createdAt = Instant.now()
        )

        repository.save(apiKey)
        val retrieved = repository.findById("test-id")

        assertNotNull(retrieved)
        assertEquals("test-id", retrieved?.id)
    }

    @Test
    fun `should return null for non-existent API key`() = runBlocking {
        val retrieved = repository.findById("non-existent")
        assertNull(retrieved)
    }

    @Test
    fun `should find API key by key hash`() = runBlocking {
        val apiKey = ApiKey(
            id = "test-id",
            userId = "user-id",
            keyHash = "hash123",
            name = "Test Key",
            createdAt = Instant.now()
        )

        repository.save(apiKey)
        val retrieved = repository.findByKeyHash("hash123")

        assertNotNull(retrieved)
        assertEquals("hash123", retrieved?.keyHash)
    }

    @Test
    fun `should find API keys by user ID`() = runBlocking {
        val apiKey1 = ApiKey(
            id = "key1",
            userId = "user1",
            keyHash = "hash1",
            name = "Key 1",
            createdAt = Instant.now()
        )
        val apiKey2 = ApiKey(
            id = "key2",
            userId = "user1",
            keyHash = "hash2",
            name = "Key 2",
            createdAt = Instant.now()
        )
        val apiKey3 = ApiKey(
            id = "key3",
            userId = "user2",
            keyHash = "hash3",
            name = "Key 3",
            createdAt = Instant.now()
        )

        repository.save(apiKey1)
        repository.save(apiKey2)
        repository.save(apiKey3)

        val userKeys = repository.findByUserId("user1")

        assertEquals(2, userKeys.size)
        assertTrue(userKeys.any { it.id == "key1" })
        assertTrue(userKeys.any { it.id == "key2" })
    }

    @Test
    fun `should update existing API key`() = runBlocking {
        val apiKey = ApiKey(
            id = "test-id",
            userId = "user-id",
            keyHash = "hash123",
            name = "Test Key",
            createdAt = Instant.now()
        )

        repository.save(apiKey)

        val updated = apiKey.copy(name = "Updated Key", lastUsedAt = Instant.now())
        repository.save(updated)

        val retrieved = repository.findById("test-id")
        assertNotNull(retrieved)
        assertEquals("Updated Key", retrieved?.name)
        assertNotNull(retrieved?.lastUsedAt)
    }

    @Test
    fun `should delete API key`() = runBlocking {
        val apiKey = ApiKey(
            id = "test-id",
            userId = "user-id",
            keyHash = "hash123",
            name = "Test Key",
            createdAt = Instant.now()
        )

        repository.save(apiKey)
        repository.delete("test-id")

        val retrieved = repository.findById("test-id")
        assertNull(retrieved)
    }

    @Test
    fun `should handle empty file`() = runBlocking {
        val apiKeysFile = tempDir.resolve("api-keys.json")
        Files.writeString(apiKeysFile, "")

        val newRepository = FileSystemApiKeyRepository(tempDir, objectMapper)
        val retrieved = newRepository.findById("test-id")
        assertNull(retrieved)
    }
}

