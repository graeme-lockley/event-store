package com.eventstore.integration

import com.eventstore.interfaces.http.dto.LoginRequest
import com.eventstore.interfaces.http.dto.LoginResponse
import com.eventstore.interfaces.http.dto.TenantListResponse
import io.ktor.client.call.body
import io.ktor.client.request.cookie
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration test that authenticates via the API and retrieves the list of tenants.
 *
 * This test:
 * 1. Uses a shared event-store instance (from BaseTenantIntegrationTest)
 * 2. Logs in using the bootstrapped admin credentials via HTTP API
 * 3. Retrieves the tenant list using the authenticated session
 * 4. Verifies the system tenant is in the list
 */
class TenantListIntegrationTest : BaseTenantIntegrationTest() {
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
            val testSessionId =
                setCookieHeader?.substringAfter("sessionId=")?.substringBefore(";")
                    ?: loginResponse.body<LoginResponse>().sessionId

            val loginResponseBody = loginResponse.body<LoginResponse>()

            // Verify login response
            assertNotNull(testSessionId, "Session ID should not be null")
            assertNotNull(loginResponseBody.userId, "User ID should not be null")
            assertEquals("admin-system", loginResponseBody.userId, "User ID should be admin-system")
            assertTrue(loginResponseBody.tenants.isNotEmpty(), "Admin user should have at least one tenant")

            // Step 2: Retrieve tenant list using the authenticated session
            // Include the sessionId cookie in the request
            val tenantListResponse =
                httpClient.get("$baseUrl/tenants") {
                    contentType(ContentType.Application.Json)
                    cookie("sessionId", testSessionId!!)
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
