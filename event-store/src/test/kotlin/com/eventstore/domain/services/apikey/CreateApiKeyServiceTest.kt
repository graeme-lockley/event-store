package com.eventstore.domain.services.apikey

import com.eventstore.domain.Application
import com.eventstore.domain.exceptions.UserNotFoundException
import com.eventstore.domain.services.createApplication
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CreateApiKeyServiceTest {
    private lateinit var application: Application

    @BeforeEach
    fun setup() =
        runTest {
            application = createApplication()
        }

    @Test
    fun `creates API key successfully`() =
        runTest {
            val user =
                application.createUser(
                    email = "test@example.com",
                    name = "Test User",
                    password = "password123",
                )

            val (apiKey, plainKey) =
                application.createApiKey(
                    userId = user.id,
                    name = "Test API Key",
                    description = "Test description",
                )

            assertNotNull(apiKey)
            assertNotNull(plainKey)
            assertTrue(plainKey.startsWith("es_"))
            assertEquals(user.id, apiKey.userId)
            assertEquals("Test API Key", apiKey.name)
            assertEquals("Test description", apiKey.description)

            // Verify it's saved (via projection)
            val retrieved = application.getApiKey(apiKey.id)
            assertNotNull(retrieved)
        }

    @Test
    fun `throws exception when user does not exist`() =
        runTest {
            assertFailsWith<UserNotFoundException> {
                application.createApiKey(
                    userId = "non-existent-user-id",
                    name = "Test API Key",
                )
            }
        }

    @Test
    fun `creates API key with expiration`() =
        runTest {
            val user =
                application.createUser(
                    email = "test@example.com",
                    name = "Test User",
                    password = "password123",
                )

            val expiresAt = Instant.now().plusSeconds(3600)
            val (apiKey, _) =
                application.createApiKey(
                    userId = user.id,
                    name = "Test API Key",
                    expiresAt = expiresAt,
                )

            assertNotNull(apiKey.expiresAt)
            assertEquals(expiresAt, apiKey.expiresAt)
        }

    @Test
    fun `creates API key with scopes`() =
        runTest {
            val user =
                application.createUser(
                    email = "test@example.com",
                    name = "Test User",
                    password = "password123",
                )

            val scopes = setOf("read", "write")
            val (apiKey, _) =
                application.createApiKey(
                    userId = user.id,
                    name = "Test API Key",
                    scopes = scopes,
                )

            assertNotNull(apiKey.scopes)
            assertEquals(scopes, apiKey.scopes)
        }

    @Test
    fun `creates multiple API keys for same user with unique IDs and plain keys`() =
        runTest {
            val user =
                application.createUser(
                    email = "multi@example.com",
                    name = "Multi Key User",
                    password = "password123",
                )

            val (key1, plainKey1) = application.createApiKey(user.id, "Key 1")
            val (key2, plainKey2) = application.createApiKey(user.id, "Key 2")
            val (key3, plainKey3) = application.createApiKey(user.id, "Key 3")

            // Verify all keys are retrievable
            val userKeys = application.getApiKeysByUserId(user.id)
            assertEquals(3, userKeys.size)
            assertTrue(userKeys.any { it.id == key1.id })
            assertTrue(userKeys.any { it.id == key2.id })
            assertTrue(userKeys.any { it.id == key3.id })

            // Verify each API key has a unique ID
            val ids = setOf(key1.id, key2.id, key3.id)
            assertEquals(3, ids.size, "Each API key should have a unique ID")

            // Verify each API key has a unique plain key
            val plainKeys = setOf(plainKey1, plainKey2, plainKey3)
            assertEquals(3, plainKeys.size, "Each API key should have a unique plain key")
        }
}
