package com.eventstore.integration

import com.eventstore.domain.tenants.SystemTopics
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Integration tests for server restart behavior.
 *
 * These tests require a fresh server instance per test because they test
 * restart scenarios. They extend [BaseTenantIntegrationTestPerMethod] instead
 * of [BaseTenantIntegrationTest] to ensure proper isolation.
 */
class TenantRestartIntegrationTest : BaseTenantIntegrationTestPerMethod() {
    @Test
    fun `should be idempotent on restart`() =
        runBlocking {
            // Get initial tenant count
            val tenantsBefore = tenantClient.listTenants()
            val countBefore = tenantsBefore.size

            // Restart server
            eventStoreHelper.stop()
            delay(1000)
            eventStoreHelper.start()
            waitForServerReady()

            // Re-authenticate after restart
            authenticate()
            tenantClient = TenantTestClient(httpClient, eventStoreHelper.getBaseUrl(), sessionId)

            // Verify same number of tenants (no duplicates)
            val tenantsAfter = tenantClient.listTenants()
            assertEquals(countBefore, tenantsAfter.size, "Should have same number of tenants after restart")

            // Verify system tenant still exists (only once)
            val systemTenants = tenantsAfter.filter { it.name == SystemTopics.SYSTEM_TENANT_NAME }
            assertEquals(1, systemTenants.size, "Should have exactly one system tenant")
        }
}
