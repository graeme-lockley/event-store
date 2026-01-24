package com.eventstore.integration

import com.eventstore.interfaces.http.dto.LoginRequest
import com.eventstore.interfaces.http.dto.LoginResponse
import com.eventstore.interfaces.http.dto.TenantListResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.cookie
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration test that authenticates via the API and retrieves the list of tenants.
 *
 * This test:
 * 1. Starts an event-store instance (which automatically bootstraps an admin user)
 * 2. Waits for bootstrap to complete
 * 3. Logs in using the bootstrapped admin credentials via HTTP API
 * 4. Retrieves the tenant list using the authenticated session
 * 5. Verifies the system tenant is in the list
 */
class TenantListIntegrationTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var eventStoreHelper: EventStoreTestHelper
    private lateinit var dataDir: Path
    private lateinit var configDir: Path
    private lateinit var httpClient: HttpClient
    private var sessionId: String? = null

    @BeforeEach
    fun setUp() {
        // Create separate directories for data and config for this test
        dataDir = tempDir.resolve("data")
        configDir = tempDir.resolve("config")

        // Initialize the helper with test-specific directories
        eventStoreHelper =
            EventStoreTestHelper(
                dataDir = dataDir,
                configDir = configDir,
            )

        // Start the event-store instance
        eventStoreHelper.start()

        // Create HTTP client with JSON serialization
        httpClient =
            HttpClient(CIO) {
                install(ContentNegotiation) {
                    jackson()
                }
            }

        // Wait for server to be ready and bootstrap to complete
        waitForServerReady()
    }

    @AfterEach
    fun tearDown() {
        // Close HTTP client
        httpClient.close()

        // Stop the event-store instance
        if (eventStoreHelper.isRunning()) {
            eventStoreHelper.stop()
        }
    }

    /**
     * Waits for the server to be ready and bootstrap to complete.
     * Polls the health endpoint until it responds successfully.
     */
    private fun waitForServerReady() =
        runBlocking {
            val baseUrl = eventStoreHelper.getBaseUrl()
            val maxAttempts = 30
            val delayMs = 200L

            var serverReady = false
            for (attempt in 0 until maxAttempts) {
                try {
                    val response = httpClient.get("$baseUrl/health")
                    if (response.status == HttpStatusCode.OK) {
                        // Server is ready. We are not going to wait a bit more for bootstrap projections to process
                        // as the processing of bootstrap projections is quick and should be done by now.

                        // delay(500)
                        serverReady = true
                        break
                    }
                } catch (_: Exception) {
                    // Server not ready yet, continue waiting
                }
                if (attempt < maxAttempts - 1) {
                    println("Waiting for server to be ready... (attempt ${attempt + 1}/$maxAttempts)")
                    delay(delayMs)
                }
            }
            if (!serverReady) {
                throw IllegalStateException("Server failed to become ready after ${maxAttempts * delayMs}ms")
            }
        }

    @Test
    fun `should login and retrieve tenant list using authenticated session`() {
        runBlocking {
            val baseUrl = eventStoreHelper.getBaseUrl()
            val adminEmail = eventStoreHelper.getAdminEmail()
            val adminPassword = eventStoreHelper.getAdminPassword()

            // Step 1: Login to get session cookie
            val loginRequest =
                LoginRequest(
                    email = adminEmail,
                    password = adminPassword,
                )

            val loginResponse =
                httpClient.post("$baseUrl/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody(loginRequest)
                }

            // Extract sessionId from response cookie
            val setCookieHeader = loginResponse.headers["Set-Cookie"]
            sessionId = setCookieHeader?.substringAfter("sessionId=")?.substringBefore(";")
                ?: loginResponse.body<LoginResponse>().sessionId

            val loginResponseBody = loginResponse.body<LoginResponse>()

            // Verify login response
            assertNotNull(sessionId, "Session ID should not be null")
            assertNotNull(loginResponseBody.userId, "User ID should not be null")
            assertEquals("admin-system", loginResponseBody.userId, "User ID should be admin-system")
            assertTrue(loginResponseBody.tenants.isNotEmpty(), "Admin user should have at least one tenant")

            // Step 2: Retrieve tenant list using the authenticated session
            // Include the sessionId cookie in the request
            val tenantListResponse =
                httpClient.get("$baseUrl/tenants") {
                    contentType(ContentType.Application.Json)
                    cookie("sessionId", sessionId!!)
                }.body<TenantListResponse>()

            // Verify tenant list response
            assertNotNull(tenantListResponse.tenants, "Tenants list should not be null")
            assertTrue(tenantListResponse.tenants.isNotEmpty(), "Tenant list should not be empty")

            // Verify the system tenant ($system) is in the list
            val systemTenant = tenantListResponse.tenants.find { it.name == "\$system" }
            assertNotNull(systemTenant, "System tenant (\$system) should be in the tenant list")

            // Verify tenant structure
            assertEquals("\$system", systemTenant.id, "System tenant ID should match name")
            assertEquals("\$system", systemTenant.name, "System tenant name should be \$system")
            assertNotNull(systemTenant.createdAt, "System tenant should have createdAt timestamp")
        }
    }
}
