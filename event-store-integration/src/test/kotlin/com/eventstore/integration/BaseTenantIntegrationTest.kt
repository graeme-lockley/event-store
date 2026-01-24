package com.eventstore.integration

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Base class for tenant integration tests.
 * Provides common setup and teardown functionality.
 */
abstract class BaseTenantIntegrationTest {
    @TempDir
    lateinit var tempDir: Path

    protected lateinit var eventStoreHelper: EventStoreTestHelper
    protected lateinit var dataDir: Path
    protected lateinit var configDir: Path
    protected lateinit var httpClient: HttpClient
    protected lateinit var tenantClient: TenantTestClient
    protected var sessionId: String? = null

    @BeforeEach
    open fun setUp() {
        // Create separate directories for data and config for this test
        dataDir = tempDir.resolve("data")
        configDir = tempDir.resolve("config")

        // Initialize the helper with test-specific directories
        eventStoreHelper = EventStoreTestHelper(
            dataDir = dataDir,
            configDir = configDir
        )

        // Start the event-store instance
        eventStoreHelper.start()

        // Create HTTP client with JSON serialization
        httpClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                jackson()
            }
        }

        // Wait for server to be ready and bootstrap to complete
        waitForServerReady()

        // Initialize tenant client
        tenantClient = TenantTestClient(httpClient, eventStoreHelper.getBaseUrl(), sessionId)

        // Authenticate with admin credentials (required - middleware is always installed)
        runBlocking {
            authenticate()
        }
    }

    @AfterEach
    open fun tearDown() {
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
    protected fun waitForServerReady() = runBlocking {
        val baseUrl = eventStoreHelper.getBaseUrl()
        val maxAttempts = 30
        val delayMs = 200L

        var serverReady = false
        for (attempt in 0 until maxAttempts) {
            try {
                val response = httpClient.get("$baseUrl/health")
                if (response.status == HttpStatusCode.OK) {
                    // Server is ready. Wait a bit more for bootstrap projections to process
                    delay(500)
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

    /**
     * Authenticates with the default admin credentials
     */
    protected suspend fun authenticate(): String {
        val adminEmail = eventStoreHelper.getAdminEmail()
        val adminPassword = eventStoreHelper.getAdminPassword()
        sessionId = tenantClient.authenticate(adminEmail, adminPassword)
        tenantClient.sessionId = sessionId
        return sessionId!!
    }

    /**
     * Waits for projection to catch up
     */
    protected suspend fun waitForProjection(
        maxAttempts: Int = 10,
        delayMs: Long = 100,
        condition: suspend () -> Boolean
    ) {
        tenantClient.waitForProjection(maxAttempts, delayMs, condition)
    }

    /**
     * Helper to add authentication cookie to httpClient requests
     */
    protected fun HttpRequestBuilder.addAuthCookie() {
        sessionId?.let { cookie("sessionId", it) }
    }
}
