package com.eventstore.integration

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Example integration test demonstrating how to use EventStoreTestHelper.
 */
class ExampleIntegrationTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var eventStoreHelper: EventStoreTestHelper
    private lateinit var dataDir: Path
    private lateinit var configDir: Path
    
    @BeforeEach
    fun setUp() {
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
    }
    
    @AfterEach
    fun tearDown() {
        // Stop the event-store instance
        if (eventStoreHelper.isRunning()) {
            eventStoreHelper.stop()
        }
    }
    
    @Test
    fun `example test - event-store should be running`() {
        // Verify the instance is running
        assertTrue(eventStoreHelper.isRunning())
        
        // Get the base URL for making HTTP requests
        val baseUrl = eventStoreHelper.getBaseUrl()
        assertNotNull(baseUrl)
        
        // You can now make HTTP requests to the event-store instance
        // Example:
        // val client = HttpClient(CIO) { ... }
        // val response = client.get("$baseUrl/health")
    }
}

