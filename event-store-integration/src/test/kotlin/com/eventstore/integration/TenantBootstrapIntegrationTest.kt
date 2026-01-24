package com.eventstore.integration

import com.eventstore.domain.tenants.SystemTopics
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Integration tests for bootstrap and system tenant functionality.
 * Tests scenarios from Section 4 of TENANT_INTEGRATION_TEST_SCENARIOS.md
 */
class TenantBootstrapIntegrationTest : BaseTenantIntegrationTest() {
    // Section 4.1: System Tenant Bootstrap

    @Test
    fun `should bootstrap system tenant on startup`() {
        runBlocking {
            // System tenant should exist after bootstrap (done in setUp)
            val tenants = tenantClient.listTenants()
            val systemTenant = tenants.find { it.name == SystemTopics.SYSTEM_TENANT_NAME }

            assertNotNull(systemTenant, "System tenant should exist after bootstrap")
            assertEquals(SystemTopics.SYSTEM_TENANT_NAME, systemTenant.name)
            assertNotNull(systemTenant.createdAt)
        }
    }

    @Test
    fun `should bootstrap system namespace`() {
        runBlocking {
            // Verify system namespace exists by checking if we can access system topics
            // Since we can't directly query namespaces via API in this test, we verify
            // by checking that system topics are accessible
            val events = tenantClient.getTenantEvents(limit = 1)
            // If we can get events from tenants topic, the namespace exists
            assertNotNull(events, "Should be able to access system topics")
        }
    }

    @Test
    fun `should bootstrap system topics`() {
        runBlocking {
            // Verify system topics exist by trying to access them
            val tenantsTopicId = SystemTopics.TENANTS_TOPIC_ID

            val response =
                httpClient.get("${eventStoreHelper.getBaseUrl()}/topics/$tenantsTopicId/events") {
                    contentType(ContentType.Application.Json)
                    parameter("limit", 1)
                    addAuthCookie()
                }

            assertEquals(HttpStatusCode.OK, response.status, "Should be able to access tenants topic")
        }
    }
}
