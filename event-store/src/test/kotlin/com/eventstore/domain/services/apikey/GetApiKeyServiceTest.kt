package com.eventstore.domain.services.apikey

import com.eventstore.domain.Application
import com.eventstore.domain.services.createApplication
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GetApiKeyServiceTest {
    private lateinit var application: Application

    @BeforeEach
    fun setup() =
        runTest {
            application = createApplication()
        }

    @Test
    fun `returns null for non-existent API key`() =
        runTest {
            val retrieved = application.getApiKey("non-existent")
            assertNull(retrieved)
        }

    @Test
    fun `gets API key by ID with all fields`() =
        runTest {
            val user =
                application.createUser(
                    email = "complete@example.com",
                    name = "Complete User",
                    password = "password123",
                )

            val expiresAt = Instant.now().plusSeconds(3600)
            val scopes = setOf("read", "write", "admin")
            val (apiKey, _) =
                application.createApiKey(
                    userId = user.id,
                    name = "Complete Key",
                    description = "Complete description",
                    expiresAt = expiresAt,
                    scopes = scopes,
                )

            val retrieved = application.getApiKey(apiKey.id)

            assertNotNull(retrieved)
            assertEquals(apiKey.id, retrieved.id)
            assertEquals(user.id, retrieved.userId)
            assertEquals("Complete Key", retrieved.name)
            assertEquals("Complete description", retrieved.description)
            assertEquals(expiresAt, retrieved.expiresAt)
            assertEquals(scopes, retrieved.scopes)
            assertNotNull(retrieved.createdAt)
            assertNull(retrieved.revokedAt)
            assertTrue(retrieved.isActive)
        }

    @Test
    fun `gets API keys by user ID`() =
        runTest {
            val user =
                application.createUser(
                    email = "multi@example.com",
                    name = "Multi Key User",
                    password = "password123",
                )

            val (key1, _) = application.createApiKey(user.id, "Key 1")
            val (key2, _) = application.createApiKey(user.id, "Key 2")

            val userKeys = application.getApiKeysByUserId(user.id)

            assertEquals(2, userKeys.size)
            assertTrue(userKeys.any { it.id == key1.id })
            assertTrue(userKeys.any { it.id == key2.id })
        }

    @Test
    fun `returns empty list for user with no API keys`() =
        runTest {
            val user =
                application.createUser(
                    email = "nokeys@example.com",
                    name = "No Keys User",
                    password = "password123",
                )

            val userKeys = application.getApiKeysByUserId(user.id)
            assertTrue(userKeys.isEmpty())
        }

    @Test
    fun `returns empty list for non-existent user`() =
        runTest {
            val userKeys = application.getApiKeysByUserId("non-existent-user-id")
            assertTrue(userKeys.isEmpty())
        }

    @Test
    fun `gets API keys for multiple users`() =
        runTest {
            val user1 =
                application.createUser(
                    email = "user1@example.com",
                    name = "User 1",
                    password = "password123",
                )
            val user2 =
                application.createUser(
                    email = "user2@example.com",
                    name = "User 2",
                    password = "password123",
                )

            val (key1, _) = application.createApiKey(user1.id, "User1 Key 1")
            val (key2, _) = application.createApiKey(user1.id, "User1 Key 2")
            val (key3, _) = application.createApiKey(user2.id, "User2 Key 1")

            val user1Keys = application.getApiKeysByUserId(user1.id)
            val user2Keys = application.getApiKeysByUserId(user2.id)

            assertEquals(2, user1Keys.size)
            assertEquals(1, user2Keys.size)
            assertTrue(user1Keys.any { it.id == key1.id })
            assertTrue(user1Keys.any { it.id == key2.id })
            assertTrue(user2Keys.any { it.id == key3.id })
        }
}
