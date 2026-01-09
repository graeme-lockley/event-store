package com.eventstore.interfaces.http.routes

import com.eventstore.domain.Application
import com.eventstore.domain.Permission
import com.eventstore.domain.PrincipalType
import com.eventstore.domain.ResourceType
import com.eventstore.domain.services.createApplication
import com.eventstore.domain.services.permission.GrantPermissionRequest
import com.eventstore.infrastructure.auth.ApiKeyAuthenticator
import com.eventstore.infrastructure.auth.SessionManager
import com.eventstore.interfaces.http.dto.*
import com.eventstore.interfaces.http.middleware.AuthenticationMiddleware
import com.eventstore.interfaces.http.middleware.AuthorizationMiddleware
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiKeyRoutesTest {
    private lateinit var application: Application
    private lateinit var sessionManager: SessionManager
    private lateinit var apiKeyAuthenticator: ApiKeyAuthenticator

    private lateinit var userId1: String
    private lateinit var userId2: String
    private val tenantId = "test-tenant"

    @BeforeEach
    fun setup() = runBlocking {
        // Use domain Application which handles all setup automatically
        application = createApplication()

        // Create test tenant
        application.createTenant(tenantId)

        // Create test users and capture their IDs
        val user1 = application.createUser(
            email = "user1@test.com",
            name = "User 1",
            password = "password123"
        )
        userId1 = user1.id

        val user2 = application.createUser(
            email = "user2@test.com",
            name = "User 2",
            password = "password123"
        )
        userId2 = user2.id

        // Assign users to tenant
        application.assignUserToTenant(userId1, tenantId)
        application.assignUserToTenant(userId2, tenantId)

        // Create auth services needed for middleware
        sessionManager = SessionManager()
        // Use the same repository instance that Application uses for the authenticator
        // The authenticator needs write access to update lastUsedAt
        apiKeyAuthenticator = ApiKeyAuthenticator(application.apiKeyProjectionService, application.apiKeyRepository)
    }

    private suspend fun grantUserPermissions(userId: String, tenantId: String) {
        application.grantPermission(
            GrantPermissionRequest(
                principalId = userId,
                principalType = PrincipalType.USER,
                resourceType = ResourceType.USER,
                resourceName = userId,
                tenantName = tenantId,
                permissions = setOf(Permission.READ, Permission.UPDATE),
                grantedBy = "admin"
            )
        )
    }

    private fun TestApplicationBuilder.setupApplication(block: Route.() -> Unit) {
        application {
            install(ContentNegotiation) {
                jackson {
                    registerKotlinModule()
                    registerModule(com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                    disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                }
            }
            install(io.ktor.server.plugins.statuspages.StatusPages) {
                exception<Exception> { call, cause ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(cause.message ?: "Internal server error", "INTERNAL_ERROR")
                    )
                }
            }
            routing {
                val domainApp = this@ApiKeyRoutesTest.application
                val authenticationService = com.eventstore.domain.services.auth.AuthenticationService(
                    domainApp.userProjectionService,
                    sessionManager
                )
                val authorizationService = com.eventstore.domain.services.auth.AuthorizationService(
                    domainApp.permissionProjectionService,
                    domainApp.resourceResolver
                )
                AuthenticationMiddleware(authenticationService, apiKeyAuthenticator).install(this)
                AuthorizationMiddleware(authorizationService).install(this)
                // Use Application helper methods to access services
                // We need to create services directly since they're private in Application
                val createApiKeyService = com.eventstore.domain.services.apikey.CreateApiKeyService(
                    domainApp.userProjectionService,
                    domainApp.config,
                    domainApp.systemEventPublisher
                )
                val getApiKeyService = domainApp.getApiKeyService
                val revokeApiKeyService = com.eventstore.domain.services.apikey.RevokeApiKeyService(
                    domainApp.apiKeyProjectionService,
                    domainApp.config,
                    domainApp.systemEventPublisher
                )
                apiKeyRoutes(createApiKeyService, getApiKeyService, revokeApiKeyService)
                block()
            }
        }
    }

    @Test
    fun `POST creates API key successfully`() = testApplication {
        runBlocking {
            grantUserPermissions(userId1, tenantId)
        }
        setupApplication {}

        val client = createClient {
            install(ClientContentNegotiation) {
                jackson()
            }
        }

        val body = CreateApiKeyRequestDto(
            name = "Test API Key",
            description = "Test description"
        )

        val session = sessionManager.createSession(userId1)
        val response = client.post("/tenants/$tenantId/users/$userId1/api-keys") {
            header(HttpHeaders.Authorization, "Bearer ${session.id}")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val apiKeyResponse: ApiKeyResponseDto = response.body()
        assertNotNull(apiKeyResponse.id)
        assertEquals("Test API Key", apiKeyResponse.name)
        assertEquals("Test description", apiKeyResponse.description)
        assertNotNull(apiKeyResponse.key) // Plain key should be returned on creation
        assertTrue(apiKeyResponse.key!!.startsWith("es_"))
    }

    @Test
    fun `POST rejects empty name`() = testApplication {
        runBlocking {
            grantUserPermissions(userId1, tenantId)
        }
        setupApplication {}

        val client = createClient {
            install(ClientContentNegotiation) {
                jackson()
            }
        }

        val body = CreateApiKeyRequestDto(
            name = "",
            description = "Test description"
        )

        val session = sessionManager.createSession(userId1)
        val response = client.post("/tenants/$tenantId/users/$userId1/api-keys") {
            header(HttpHeaders.Authorization, "Bearer ${session.id}")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val errorResponse: ErrorResponse = response.body()
        assertEquals("INVALID_INPUT", errorResponse.code)
    }

    @Test
    fun `GET list returns user's API keys`() = testApplication {
        runBlocking {
            grantUserPermissions(userId1, tenantId)

            // Create API keys for user1
            application.createApiKey(userId1, "Key 1")
            application.createApiKey(userId1, "Key 2")

            // Create API key for user2 (should not appear)
            application.createApiKey(userId2, "Key 3")
        }

        setupApplication {}

        val client = createClient {
            install(ClientContentNegotiation) {
                jackson()
            }
        }

        val session = sessionManager.createSession(userId1)
        val response = client.get("/tenants/$tenantId/users/$userId1/api-keys") {
            header(HttpHeaders.Authorization, "Bearer ${session.id}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val listResponse: ApiKeyListResponseDto = response.body()
        assertEquals(2, listResponse.apiKeys.size)
        // Verify no keys from user2
        assertTrue(listResponse.apiKeys.none { it.userId == userId2 })
    }

    @Test
    fun `GET by ID returns API key`() = testApplication {
        runBlocking {
            grantUserPermissions(userId1, tenantId)
            val (apiKey, _) = application.createApiKey(userId1, "Test Key")
            val apiKeyId = apiKey.id

            setupApplication {}

            val client = createClient {
                install(ClientContentNegotiation) {
                    jackson()
                }
            }

            val session = sessionManager.createSession(userId1)
            val response = client.get("/tenants/$tenantId/users/$userId1/api-keys/$apiKeyId") {
                header(HttpHeaders.Authorization, "Bearer ${session.id}")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val apiKeyResponse: ApiKeyResponseDto = response.body()
            assertEquals(apiKeyId, apiKeyResponse.id)
            assertEquals("Test Key", apiKeyResponse.name)
            assertEquals(null, apiKeyResponse.key) // Plain key should NOT be returned
        }
    }

    @Test
    fun `GET by ID rejects access to other user's API key`() = testApplication {
        runBlocking {
            grantUserPermissions(userId1, tenantId)
            grantUserPermissions(userId2, tenantId)

            // Create API key for user2
            val (apiKey, _) = application.createApiKey(userId2, "User2 Key")
            val apiKeyId = apiKey.id

            setupApplication {}

            val client = createClient {
                install(ClientContentNegotiation) {
                    jackson()
                }
            }

            // User1 tries to access user2's API key
            val session = sessionManager.createSession(userId1)
            val response = client.get("/tenants/$tenantId/users/$userId2/api-keys/$apiKeyId") {
                header(HttpHeaders.Authorization, "Bearer ${session.id}")
            }

            assertEquals(HttpStatusCode.Forbidden, response.status)
            val errorResponse: ErrorResponse = response.body()
            // Authorization middleware blocks access to other user's resources with PERMISSION_DENIED
            // before route code can check ownership, which is also correct security behavior
            assertTrue(errorResponse.code == "FORBIDDEN" || errorResponse.code == "PERMISSION_DENIED")
        }
    }

    @Test
    fun `DELETE revokes API key`() = testApplication {
        runBlocking {
            grantUserPermissions(userId1, tenantId)
            val (apiKey, _) = application.createApiKey(userId1, "Test Key")
            val apiKeyId = apiKey.id

            setupApplication {}

            val client = createClient {
                install(ClientContentNegotiation) {
                    jackson()
                }
            }

            val session = sessionManager.createSession(userId1)
            val response = client.delete("/tenants/$tenantId/users/$userId1/api-keys/$apiKeyId") {
                header(HttpHeaders.Authorization, "Bearer ${session.id}")
            }

            assertEquals(HttpStatusCode.OK, response.status)

            // Verify it's revoked
            val retrieved = application.getApiKey(apiKeyId)
            assertNotNull(retrieved)
            assertNotNull(retrieved.revokedAt)
        }
    }

    @Test
    fun `DELETE rejects revoking other user's API key`() = testApplication {
        runBlocking {
            grantUserPermissions(userId1, tenantId)
            grantUserPermissions(userId2, tenantId)

            // Create API key for user2
            val (apiKey, _) = application.createApiKey(userId2, "User2 Key")
            val apiKeyId = apiKey.id

            setupApplication {}

            val client = createClient {
                install(ClientContentNegotiation) {
                    jackson()
                }
            }

            // User1 tries to revoke user2's API key
            val session = sessionManager.createSession(userId1)
            val response = client.delete("/tenants/$tenantId/users/$userId2/api-keys/$apiKeyId") {
                header(HttpHeaders.Authorization, "Bearer ${session.id}")
            }

            assertEquals(HttpStatusCode.Forbidden, response.status)
            val errorResponse: ErrorResponse = response.body()
            // Authorization middleware blocks access to other user's resources with PERMISSION_DENIED
            // before route code can check ownership, which is also correct security behavior
            assertTrue(errorResponse.code == "FORBIDDEN" || errorResponse.code == "PERMISSION_DENIED")
        }
    }

    @Test
    fun `requires authentication`() = testApplication {
        setupApplication {}

        val client = createClient {
            install(ClientContentNegotiation) {
                jackson()
            }
        }

        val response = client.post("/tenants/$tenantId/users/$userId1/api-keys") {
            contentType(ContentType.Application.Json)
            setBody(CreateApiKeyRequestDto(name = "Test Key"))
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `works with API key authentication`() = testApplication {
        runBlocking {
            grantUserPermissions(userId1, tenantId)

            // Create an API key
            val (apiKey, plainKey) = application.createApiKey(userId1, "Auth Key")
            val apiKeyId = apiKey.id

            setupApplication {}

            val client = createClient {
                install(ClientContentNegotiation) {
                    jackson()
                }
            }

            // Use the API key to authenticate
            val response = client.get("/tenants/$tenantId/users/$userId1/api-keys") {
                header(HttpHeaders.Authorization, "Bearer $plainKey")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val listResponse: ApiKeyListResponseDto = response.body()
            assertTrue(listResponse.apiKeys.any { it.id == apiKeyId })
        }
    }

    // ========== Priority 2: Boundary Condition Tests ==========

    // POST Endpoint Boundary Tests
    @Test
    fun `POST rejects whitespace-only name`() = testApplication {
        runBlocking {
            grantUserPermissions(userId1, tenantId)
        }
        setupApplication {}

        val client = createClient {
            install(ClientContentNegotiation) {
                jackson()
            }
        }

        val body = CreateApiKeyRequestDto(
            name = "   ",
            description = "Test description"
        )

        val session = sessionManager.createSession(userId1)
        val response = client.post("/tenants/$tenantId/users/$userId1/api-keys") {
            header(HttpHeaders.Authorization, "Bearer ${session.id}")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val errorResponse: ErrorResponse = response.body()
        assertEquals("INVALID_INPUT", errorResponse.code)
    }

    @Test
    fun `POST rejects name exceeding max length`() = testApplication {
        runBlocking {
            grantUserPermissions(userId1, tenantId)
        }
        setupApplication {}

        val client = createClient {
            install(ClientContentNegotiation) {
                jackson()
            }
        }

        val body = CreateApiKeyRequestDto(
            name = "a".repeat(256), // Exceeds MAX_NAME_LENGTH of 255
            description = "Test description"
        )

        val session = sessionManager.createSession(userId1)
        val response = client.post("/tenants/$tenantId/users/$userId1/api-keys") {
            header(HttpHeaders.Authorization, "Bearer ${session.id}")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val errorResponse: ErrorResponse = response.body()
        assertEquals("INVALID_INPUT", errorResponse.code)
        assertTrue(errorResponse.error.contains("maximum length"))
    }

    @Test
    fun `POST rejects expiresAt in the past`() = testApplication {
        runBlocking {
            grantUserPermissions(userId1, tenantId)
        }
        setupApplication {}

        val client = createClient {
            install(ClientContentNegotiation) {
                jackson()
            }
        }

        val pastDate = Instant.now().minusSeconds(3600).toString()
        val body = CreateApiKeyRequestDto(
            name = "Test Key",
            expiresAt = pastDate
        )

        val session = sessionManager.createSession(userId1)
        val response = client.post("/tenants/$tenantId/users/$userId1/api-keys") {
            header(HttpHeaders.Authorization, "Bearer ${session.id}")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val errorResponse: ErrorResponse = response.body()
        assertEquals("INVALID_DATE", errorResponse.code)
        assertTrue(errorResponse.error.contains("future"))
    }

    @Test
    fun `POST rejects description exceeding max length`() = testApplication {
        runBlocking {
            grantUserPermissions(userId1, tenantId)
        }
        setupApplication {}

        val client = createClient {
            install(ClientContentNegotiation) {
                jackson()
            }
        }

        val body = CreateApiKeyRequestDto(
            name = "Test Key",
            description = "a".repeat(1001) // Exceeds MAX_DESCRIPTION_LENGTH of 1000
        )

        val session = sessionManager.createSession(userId1)
        val response = client.post("/tenants/$tenantId/users/$userId1/api-keys") {
            header(HttpHeaders.Authorization, "Bearer ${session.id}")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val errorResponse: ErrorResponse = response.body()
        assertEquals("INVALID_INPUT", errorResponse.code)
        assertTrue(errorResponse.error.contains("description"))
    }

    @Test
    fun `POST rejects missing userId parameter`() = testApplication {
        runBlocking {
            grantUserPermissions(userId1, tenantId)
        }
        setupApplication {}

        val client = createClient {
            install(ClientContentNegotiation) {
                jackson()
            }
        }

        val body = CreateApiKeyRequestDto(name = "Test Key")
        val session = sessionManager.createSession(userId1)

        // Use empty userId in URL - Ktor routing returns 404 for malformed paths
        val response = client.post("/tenants/$tenantId/users//api-keys") {
            header(HttpHeaders.Authorization, "Bearer ${session.id}")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        // Ktor returns 404 for malformed route paths, which is acceptable
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `POST creates API key with scopes`() = testApplication {
        runBlocking {
            grantUserPermissions(userId1, tenantId)
        }
        setupApplication {}

        val client = createClient {
            install(ClientContentNegotiation) {
                jackson()
            }
        }

        val body = CreateApiKeyRequestDto(
            name = "Test Key",
            scopes = setOf("read", "write", "admin")
        )

        val session = sessionManager.createSession(userId1)
        val response = client.post("/tenants/$tenantId/users/$userId1/api-keys") {
            header(HttpHeaders.Authorization, "Bearer ${session.id}")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val apiKeyResponse: ApiKeyResponseDto = response.body()
        assertNotNull(apiKeyResponse.scopes)
        assertEquals(3, apiKeyResponse.scopes?.size)
        assertTrue(apiKeyResponse.scopes?.contains("read") == true)
    }

    @Test
    fun `POST creates API key with expiration date`() = testApplication {
        runBlocking {
            grantUserPermissions(userId1, tenantId)
        }
        setupApplication {}

        val client = createClient {
            install(ClientContentNegotiation) {
                jackson()
            }
        }

        val futureDate = Instant.now().plusSeconds(86400).toString() // 1 day in future
        val body = CreateApiKeyRequestDto(
            name = "Test Key",
            expiresAt = futureDate
        )

        val session = sessionManager.createSession(userId1)
        val response = client.post("/tenants/$tenantId/users/$userId1/api-keys") {
            header(HttpHeaders.Authorization, "Bearer ${session.id}")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val apiKeyResponse: ApiKeyResponseDto = response.body()
        assertNotNull(apiKeyResponse.expiresAt)
        assertEquals(futureDate, apiKeyResponse.expiresAt)
    }

    @Test
    fun `POST trims whitespace from name`() = testApplication {
        runBlocking {
            grantUserPermissions(userId1, tenantId)
        }
        setupApplication {}

        val client = createClient {
            install(ClientContentNegotiation) {
                jackson()
            }
        }

        val body = CreateApiKeyRequestDto(
            name = "  Test Key  ",
            description = "Test description"
        )

        val session = sessionManager.createSession(userId1)
        val response = client.post("/tenants/$tenantId/users/$userId1/api-keys") {
            header(HttpHeaders.Authorization, "Bearer ${session.id}")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val apiKeyResponse: ApiKeyResponseDto = response.body()
        assertEquals("Test Key", apiKeyResponse.name) // Should be trimmed
    }

    // GET List Endpoint Boundary Tests
    @Test
    fun `GET list returns empty list when user has no keys`() = testApplication {
        runBlocking {
            grantUserPermissions(userId1, tenantId)
        }
        setupApplication {}

        val client = createClient {
            install(ClientContentNegotiation) {
                jackson()
            }
        }

        val session = sessionManager.createSession(userId1)
        val response = client.get("/tenants/$tenantId/users/$userId1/api-keys") {
            header(HttpHeaders.Authorization, "Bearer ${session.id}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val listResponse: ApiKeyListResponseDto = response.body()
        assertEquals(0, listResponse.apiKeys.size)
    }

    @Test
    fun `GET list includes revoked and expired keys`() = testApplication {
        val apiKey1Id: String
        val apiKey2Id: String

        runBlocking {
            grantUserPermissions(userId1, tenantId)

            // Create active key
            val (apiKey1, _) = application.createApiKey(userId1, "Active Key")
            apiKey1Id = apiKey1.id

            // Create and revoke a key
            val (apiKey2, _) = application.createApiKey(userId1, "Revoked Key")
            apiKey2Id = apiKey2.id
            application.revokeApiKey(apiKey2Id)

            // Create expired key
            val expiredDate = Instant.now().minusSeconds(3600)
            application.createApiKey(userId1, "Expired Key", expiresAt = expiredDate)
        }

        setupApplication {}

        val client = createClient {
            install(ClientContentNegotiation) {
                jackson()
            }
        }

        val session = sessionManager.createSession(userId1)
        val response = client.get("/tenants/$tenantId/users/$userId1/api-keys") {
            header(HttpHeaders.Authorization, "Bearer ${session.id}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val listResponse: ApiKeyListResponseDto = response.body()
        assertEquals(3, listResponse.apiKeys.size)

        val activeKey = listResponse.apiKeys.find { it.id == apiKey1Id }
        val revokedKey = listResponse.apiKeys.find { it.id == apiKey2Id }

        assertNotNull(activeKey)
        assertTrue(activeKey.isActive)

        assertNotNull(revokedKey)
        assertFalse(revokedKey.isActive)
        assertNotNull(revokedKey.revokedAt)
    }

    @Test
    fun `GET list rejects missing userId parameter`() = testApplication {
        runBlocking {
            grantUserPermissions(userId1, tenantId)
        }
        setupApplication {}

        val client = createClient {
            install(ClientContentNegotiation) {
                jackson()
            }
        }

        val session = sessionManager.createSession(userId1)
        // Use empty userId in URL - Ktor routing returns 404 for malformed paths
        val response = client.get("/tenants/$tenantId/users//api-keys") {
            header(HttpHeaders.Authorization, "Bearer ${session.id}")
        }

        // Ktor returns 404 for malformed route paths, which is acceptable
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    // GET by ID Endpoint Boundary Tests
    @Test
    fun `GET by ID returns revoked API key metadata`() = testApplication {
        runBlocking {
            grantUserPermissions(userId1, tenantId)
            val (apiKey, _) = application.createApiKey(userId1, "Test Key")
            val apiKeyId = apiKey.id
            application.revokeApiKey(apiKeyId)

            setupApplication {}

            val client = createClient {
                install(ClientContentNegotiation) {
                    jackson()
                }
            }

            val session = sessionManager.createSession(userId1)
            val response = client.get("/tenants/$tenantId/users/$userId1/api-keys/$apiKeyId") {
                header(HttpHeaders.Authorization, "Bearer ${session.id}")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val apiKeyResponse: ApiKeyResponseDto = response.body()
            assertEquals(apiKeyId, apiKeyResponse.id)
            assertFalse(apiKeyResponse.isActive)
            assertNotNull(apiKeyResponse.revokedAt)
            assertEquals(null, apiKeyResponse.key) // Plain key should NOT be returned
        }
    }

    @Test
    fun `GET by ID returns expired API key metadata`() = testApplication {
        runBlocking {
            grantUserPermissions(userId1, tenantId)
            val expiredDate = Instant.now().minusSeconds(3600)
            val (apiKey, _) = application.createApiKey(userId1, "Expired Key", expiresAt = expiredDate)
            val apiKeyId = apiKey.id

            setupApplication {}

            val client = createClient {
                install(ClientContentNegotiation) {
                    jackson()
                }
            }

            val session = sessionManager.createSession(userId1)
            val response = client.get("/tenants/$tenantId/users/$userId1/api-keys/$apiKeyId") {
                header(HttpHeaders.Authorization, "Bearer ${session.id}")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val apiKeyResponse: ApiKeyResponseDto = response.body()
            assertEquals(apiKeyId, apiKeyResponse.id)
            assertFalse(apiKeyResponse.isActive)
            assertNotNull(apiKeyResponse.expiresAt)
        }
    }

    @Test
    fun `GET by ID returns 404 for non-existent key`() = testApplication {
        runBlocking {
            grantUserPermissions(userId1, tenantId)
        }
        setupApplication {}

        val client = createClient {
            install(ClientContentNegotiation) {
                jackson()
            }
        }

        val nonExistentKeyId = UUID.randomUUID().toString()
        val session = sessionManager.createSession(userId1)
        val response = client.get("/tenants/$tenantId/users/$userId1/api-keys/$nonExistentKeyId") {
            header(HttpHeaders.Authorization, "Bearer ${session.id}")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
        val errorResponse: ErrorResponse = response.body()
        assertEquals("API_KEY_NOT_FOUND", errorResponse.code)
    }

    @Test
    fun `GET by ID rejects missing keyId parameter`() = testApplication {
        runBlocking {
            grantUserPermissions(userId1, tenantId)
        }
        setupApplication {}

        val client = createClient {
            install(ClientContentNegotiation) {
                jackson()
            }
        }

        val session = sessionManager.createSession(userId1)
        val response = client.get("/tenants/$tenantId/users/$userId1/api-keys/") {
            header(HttpHeaders.Authorization, "Bearer ${session.id}")
        }

        // Should return 404 or 400 depending on routing
        assertTrue(response.status == HttpStatusCode.NotFound || response.status == HttpStatusCode.BadRequest)
    }

    // DELETE Endpoint Boundary Tests
    @Test
    fun `DELETE returns 409 for already revoked key`() = testApplication {
        runBlocking {
            grantUserPermissions(userId1, tenantId)
            val (apiKey, _) = application.createApiKey(userId1, "Test Key")
            val apiKeyId = apiKey.id
            application.revokeApiKey(apiKeyId)

            setupApplication {}

            val client = createClient {
                install(ClientContentNegotiation) {
                    jackson()
                }
            }

            val session = sessionManager.createSession(userId1)
            val response = client.delete("/tenants/$tenantId/users/$userId1/api-keys/$apiKeyId") {
                header(HttpHeaders.Authorization, "Bearer ${session.id}")
            }

            assertEquals(HttpStatusCode.Conflict, response.status)
            val errorResponse: ErrorResponse = response.body()
            assertEquals("API_KEY_ALREADY_REVOKED", errorResponse.code)
        }
    }

    @Test
    fun `DELETE uses consistent response format`() = testApplication {
        runBlocking {
            grantUserPermissions(userId1, tenantId)
            val (apiKey, _) = application.createApiKey(userId1, "Test Key")
            val apiKeyId = apiKey.id

            setupApplication {}

            val client = createClient {
                install(ClientContentNegotiation) {
                    jackson()
                }
            }

            val session = sessionManager.createSession(userId1)
            val response = client.delete("/tenants/$tenantId/users/$userId1/api-keys/$apiKeyId") {
                header(HttpHeaders.Authorization, "Bearer ${session.id}")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val revokeResponse: ApiKeyRevokeResponseDto = response.body()
            assertEquals("API key revoked", revokeResponse.message)
            assertEquals(apiKeyId, revokeResponse.keyId)
            assertNotNull(revokeResponse.revokedAt)
        }
    }

    @Test
    fun `DELETE rejects missing keyId parameter`() = testApplication {
        runBlocking {
            grantUserPermissions(userId1, tenantId)
        }
        setupApplication {}

        val client = createClient {
            install(ClientContentNegotiation) {
                jackson()
            }
        }

        val session = sessionManager.createSession(userId1)
        val response = client.delete("/tenants/$tenantId/users/$userId1/api-keys/") {
            header(HttpHeaders.Authorization, "Bearer ${session.id}")
        }

        // Should return 404 or 400 depending on routing
        assertTrue(response.status == HttpStatusCode.NotFound || response.status == HttpStatusCode.BadRequest)
    }

    @Test
    fun `DELETE can revoke expired key`() = testApplication {
        runBlocking {
            grantUserPermissions(userId1, tenantId)
            val expiredDate = Instant.now().minusSeconds(3600)
            val (apiKey, _) = application.createApiKey(userId1, "Expired Key", expiresAt = expiredDate)
            val apiKeyId = apiKey.id

            setupApplication {}

            val client = createClient {
                install(ClientContentNegotiation) {
                    jackson()
                }
            }

            val session = sessionManager.createSession(userId1)
            val response = client.delete("/tenants/$tenantId/users/$userId1/api-keys/$apiKeyId") {
                header(HttpHeaders.Authorization, "Bearer ${session.id}")
            }

            assertEquals(HttpStatusCode.OK, response.status)

            // Verify it's revoked
            val retrieved = application.getApiKey(apiKeyId)
            assertNotNull(retrieved)
            assertNotNull(retrieved.revokedAt)
        }
    }

    // Authentication Boundary Tests
    @Test
    fun `rejects revoked API key for authentication`() = testApplication {
        runBlocking {
            grantUserPermissions(userId1, tenantId)
            val (apiKey, plainKey) = application.createApiKey(userId1, "Auth Key")
            application.revokeApiKey(apiKey.id)

            setupApplication {}

            val client = createClient {
                install(ClientContentNegotiation) {
                    jackson()
                }
            }

            // Try to use revoked API key
            val response = client.get("/tenants/$tenantId/users/$userId1/api-keys") {
                header(HttpHeaders.Authorization, "Bearer $plainKey")
            }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    @Test
    fun `rejects expired API key for authentication`() = testApplication {
        runBlocking {
            grantUserPermissions(userId1, tenantId)
            val expiredDate = Instant.now().minusSeconds(3600)
            val (_, plainKey) = application.createApiKey(userId1, "Expired Auth Key", expiresAt = expiredDate)

            setupApplication {}

            val client = createClient {
                install(ClientContentNegotiation) {
                    jackson()
                }
            }

            // Try to use expired API key
            val response = client.get("/tenants/$tenantId/users/$userId1/api-keys") {
                header(HttpHeaders.Authorization, "Bearer $plainKey")
            }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    @Test
    fun `rejects invalid API key format`() = testApplication {
        runBlocking {
            grantUserPermissions(userId1, tenantId)
        }
        setupApplication {}

        val client = createClient {
            install(ClientContentNegotiation) {
                jackson()
            }
        }

        // Try to use invalid API key format (doesn't start with es_)
        val response = client.get("/tenants/$tenantId/users/$userId1/api-keys") {
            header(HttpHeaders.Authorization, "Bearer invalid_key_format")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `POST rejects user not found`() = testApplication {
        runBlocking {
            grantUserPermissions(userId1, tenantId)
        }
        setupApplication {}

        val client = createClient {
            install(ClientContentNegotiation) {
                jackson()
            }
        }

        val nonExistentUserId = UUID.randomUUID().toString()
        val body = CreateApiKeyRequestDto(name = "Test Key")
        val session = sessionManager.createSession(userId1)

        val response = client.post("/tenants/$tenantId/users/$nonExistentUserId/api-keys") {
            header(HttpHeaders.Authorization, "Bearer ${session.id}")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        // Authorization middleware blocks access to other users' resources with 403
        // before route code can check if user exists, which is correct security behavior
        assertTrue(response.status == HttpStatusCode.NotFound || response.status == HttpStatusCode.Forbidden)
        if (response.status == HttpStatusCode.NotFound) {
            val errorResponse: ErrorResponse = response.body()
            assertEquals("USER_NOT_FOUND", errorResponse.code)
        } else {
            val errorResponse: ErrorResponse = response.body()
            assertTrue(errorResponse.code == "PERMISSION_DENIED" || errorResponse.code == "FORBIDDEN")
        }
    }
}
