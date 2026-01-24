package com.eventstore.domain.services.apikey

import com.eventstore.domain.Application
import com.eventstore.domain.exceptions.ApiKeyAlreadyRevokedException
import com.eventstore.domain.exceptions.ApiKeyNotFoundException
import com.eventstore.domain.services.createApplication
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Instant
import kotlin.test.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RevokeApiKeyServiceTest {
    private lateinit var application: Application

    @BeforeEach
    fun setup() =
        runTest {
            application = createApplication()
        }

    @Test
    fun `revokes API key successfully`() =
        runTest {
            val user =
                application.createUser(
                    email = "test@example.com",
                    name = "Test User",
                    password = "password123",
                )

            val (apiKey, _) = application.createApiKey(user.id, "Test Key")

            // Verify key is active before revocation
            val beforeRevoke = application.getApiKey(apiKey.id)
            assertNotNull(beforeRevoke)
            assertTrue(beforeRevoke!!.isActive)
            assertNull(beforeRevoke.revokedAt)

            val beforeRevokeTime = Instant.now()
            // Revoke the key
            application.revokeApiKey(apiKey.id)
            val afterRevokeTime = Instant.now()

            // Verify key is revoked with timestamp
            val afterRevoke = application.getApiKey(apiKey.id)
            assertNotNull(afterRevoke)
            val revoked = afterRevoke!!
            assertFalse(revoked.isActive)
            assertNotNull(revoked.revokedAt)
            val revokedAt = revoked.revokedAt!!
            assertTrue(revokedAt.isAfter(beforeRevokeTime) || revokedAt == beforeRevokeTime)
            assertTrue(revokedAt.isBefore(afterRevokeTime) || revokedAt == afterRevokeTime)
        }

    @Test
    fun `throws exception when API key does not exist`() =
        runTest {
            assertFailsWith<ApiKeyNotFoundException> {
                application.revokeApiKey("non-existent")
            }
        }

    @Test
    fun `throws exception when API key already revoked`() =
        runTest {
            val user =
                application.createUser(
                    email = "revoked@example.com",
                    name = "Revoked User",
                    password = "password123",
                )

            val (apiKey, _) = application.createApiKey(user.id, "To Be Revoked")

            // Revoke once
            application.revokeApiKey(apiKey.id)

            // Try to revoke again
            assertFailsWith<ApiKeyAlreadyRevokedException> {
                application.revokeApiKey(apiKey.id)
            }
        }

    @Test
    fun `revoked API key is still in list but marked as inactive`() =
        runTest {
            val user =
                application.createUser(
                    email = "timestamp@example.com",
                    name = "Timestamp User",
                    password = "password123",
                )

            val (apiKey, _) = application.createApiKey(user.id, "Timestamp Key")

            val beforeRevoke = Instant.now()
            application.revokeApiKey(apiKey.id)
            val afterRevoke = Instant.now()

            val revoked = application.getApiKey(apiKey.id)
            assertNotNull(revoked)
            val revokedKey = revoked!!
            assertNotNull(revokedKey.revokedAt)
            assertTrue(revokedKey.revokedAt!!.isAfter(beforeRevoke))
            assertTrue(revokedKey.revokedAt!!.isBefore(afterRevoke) || revokedKey.revokedAt == afterRevoke)
        }
}
