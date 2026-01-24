package com.eventstore.integration

import com.eventstore.domain.tenants.SystemTopics
import com.eventstore.interfaces.http.dto.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import java.util.*

/**
 * HTTP client utility for tenant integration tests.
 * Provides convenient methods for tenant CRUD operations and event retrieval.
 */
class TenantTestClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    var sessionId: String? = null
) {
    /**
     * Creates a new tenant via POST /tenants
     */
    suspend fun createTenant(
        name: String,
        quota: QuotaDto? = null,
        metadata: Map<String, Any> = emptyMap()
    ): TenantResponse {
        val request = TenantCreateRequest(
            id = name, // For backward compatibility
            name = name,
            quota = quota,
            metadata = metadata
        )

        val response = httpClient.post("$baseUrl/tenants") {
            contentType(ContentType.Application.Json)
            setBody(request)
            sessionId?.let { cookie("sessionId", it) }
        }

        return when (response.status) {
            HttpStatusCode.Created -> response.body()
            else -> {
                val error = response.body<ErrorResponse>()
                throw TenantApiException(response.status, error.error, error.code)
            }
        }
    }

    /**
     * Gets a tenant by UUID via GET /tenants/{tenantId}
     */
    suspend fun getTenant(tenantId: UUID): TenantResponse {
        val response = httpClient.get("$baseUrl/tenants/${tenantId}") {
            contentType(ContentType.Application.Json)
            sessionId?.let { cookie("sessionId", it) }
        }

        return when (response.status) {
            HttpStatusCode.OK -> response.body()
            HttpStatusCode.NotFound -> throw TenantNotFoundException(tenantId)
            else -> {
                val error = response.body<ErrorResponse>()
                throw TenantApiException(response.status, error.error, error.code)
            }
        }
    }

    /**
     * Lists all tenants via GET /tenants
     */
    suspend fun listTenants(): List<TenantResponse> {
        val response = httpClient.get("$baseUrl/tenants") {
            contentType(ContentType.Application.Json)
            sessionId?.let { cookie("sessionId", it) }
        }

        return when (response.status) {
            HttpStatusCode.OK -> response.body<TenantListResponse>().tenants
            else -> {
                val error = response.body<ErrorResponse>()
                throw TenantApiException(response.status, error.error, error.code)
            }
        }
    }

    /**
     * Updates a tenant via PUT /tenants/{tenantId}
     */
    suspend fun updateTenant(
        tenantId: UUID,
        name: String? = null,
        quota: QuotaDto? = null,
        metadata: Map<String, Any>? = null
    ): TenantResponse {
        val request = TenantUpdateRequest(
            name = name,
            quota = quota,
            metadata = metadata
        )

        val response = httpClient.put("$baseUrl/tenants/${tenantId}") {
            contentType(ContentType.Application.Json)
            setBody(request)
            sessionId?.let { cookie("sessionId", it) }
        }

        return when (response.status) {
            HttpStatusCode.OK -> response.body()
            HttpStatusCode.NotFound -> throw TenantNotFoundException(tenantId)
            else -> {
                val error = response.body<ErrorResponse>()
                throw TenantApiException(response.status, error.error, error.code)
            }
        }
    }

    /**
     * Deletes a tenant via DELETE /tenants/{tenantId}
     */
    suspend fun deleteTenant(tenantId: UUID, reason: String? = null) {
        val response = httpClient.delete("$baseUrl/tenants/${tenantId}") {
            contentType(ContentType.Application.Json)
            sessionId?.let { cookie("sessionId", it) }
            reason?.let {
                setBody(mapOf("reason" to it))
            }
        }

        when (response.status) {
            HttpStatusCode.OK -> Unit
            HttpStatusCode.NotFound -> throw TenantNotFoundException(tenantId)
            else -> {
                val error = response.body<ErrorResponse>()
                throw TenantApiException(response.status, error.error, error.code)
            }
        }
    }

    /**
     * Gets tenant events from the system tenants topic
     */
    suspend fun getTenantEvents(limit: Int? = null): List<EventDto> {
        val topicId = SystemTopics.TENANTS_TOPIC_ID
        val response = httpClient.get("$baseUrl/topics/${topicId}/events") {
            contentType(ContentType.Application.Json)
            limit?.let { parameter("limit", it) }
            sessionId?.let { cookie("sessionId", it) }
        }

        return when (response.status) {
            HttpStatusCode.OK -> response.body<EventsResponse>().events
            else -> {
                val error = response.body<ErrorResponse>()
                throw TenantApiException(response.status, error.error, error.code)
            }
        }
    }

    /**
     * Authenticates and stores session ID
     */
    suspend fun authenticate(email: String, password: String): String {
        val loginRequest = LoginRequest(email = email, password = password)
        val response = httpClient.post("$baseUrl/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(loginRequest)
        }

        val loginResponse = response.body<LoginResponse>()
        sessionId = loginResponse.sessionId
        return sessionId!!
    }

    /**
     * Waits for projection to catch up by polling until condition is met
     */
    suspend fun waitForProjection(
        maxAttempts: Int = 10,
        delayMs: Long = 100,
        condition: suspend () -> Boolean
    ) {
        for (attempt in 0 until maxAttempts) {
            if (condition()) {
                return
            }
            if (attempt < maxAttempts - 1) {
                delay(delayMs)
            }
        }
        throw IllegalStateException("Projection did not reach expected state after ${maxAttempts * delayMs}ms")
    }

    /**
     * Finds tenant by name in the list
     */
    suspend fun findTenantByName(name: String): TenantResponse? {
        return listTenants().find { it.name == name }
    }

    /**
     * Gets tenant by name (searches list)
     */
    suspend fun getTenantByName(name: String): TenantResponse {
        return findTenantByName(name)
            ?: throw TenantNotFoundException(name)
    }

    /**
     * Gets tenant UUID by name from events.
     * This is a workaround since the API returns name as id in responses.
     */
    suspend fun getTenantUuidByName(name: String): UUID? {
        val events = getTenantEvents()
        // Find the tenant.created event for this tenant name
        val createdEvent = events.find { event ->
            event.type == "tenant.created" && 
            (event.payload["name"] as? String) == name
        }
        
        return createdEvent?.let { event ->
            val tenantIdStr = event.payload["tenantId"] as? String
            tenantIdStr?.let { UUID.fromString(it) }
        }
    }

    /**
     * Gets tenant UUID from a TenantResponse by looking up events
     */
    suspend fun getTenantUuid(tenant: TenantResponse): UUID {
        return getTenantUuidByName(tenant.name)
            ?: throw IllegalStateException("Cannot find UUID for tenant: ${tenant.name}")
    }
}

/**
 * Exception thrown when tenant API operations fail
 */
class TenantApiException(
    val statusCode: HttpStatusCode,
    message: String,
    val errorCode: String?
) : RuntimeException("API error ($statusCode): $message (code: $errorCode)")

/**
 * Exception thrown when tenant is not found
 */
class TenantNotFoundException(tenantId: Any) : RuntimeException("Tenant not found: $tenantId")
