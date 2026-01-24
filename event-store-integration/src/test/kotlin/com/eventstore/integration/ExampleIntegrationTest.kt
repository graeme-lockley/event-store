package com.eventstore.integration

import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Example integration test demonstrating how to use BaseTenantIntegrationTest.
 *
 * This test class uses the shared server instance from BaseTenantIntegrationTest,
 * which starts the server once for all tests in the class.
 */
class ExampleIntegrationTest : BaseTenantIntegrationTest() {
    @Test
    fun `example test - event-store should be running`() {
        // Verify the instance is running
        assertTrue(eventStoreHelper.isRunning())

        // Get the base URL for making HTTP requests
        val baseUrl = eventStoreHelper.getBaseUrl()
        assertNotNull(baseUrl)

        // The httpClient is already configured and ready to use
        // Example:
        // val response = httpClient.get("$baseUrl/health")
    }
}
