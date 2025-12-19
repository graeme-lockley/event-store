package com.eventstore.interfaces.http.middleware

import com.eventstore.domain.Event
import com.eventstore.domain.EventId
import com.eventstore.domain.Permission
import com.eventstore.domain.PrincipalType
import com.eventstore.domain.ResourceType
import com.eventstore.domain.events.PermissionEventType
import com.eventstore.domain.events.PermissionGrantedEvent
import com.eventstore.domain.events.TenantCreatedEvent
import com.eventstore.domain.events.TenantEventType
import com.eventstore.domain.tenants.SystemTopics
import com.eventstore.domain.services.auth.AuthorizationService
import com.eventstore.domain.services.auth.AuthenticationService
import com.eventstore.domain.ports.outbound.ResourceResolver
import com.eventstore.infrastructure.auth.Session
import com.eventstore.infrastructure.auth.SessionManager
import com.eventstore.infrastructure.projections.InMemoryNamespaceRepository
import com.eventstore.infrastructure.projections.InMemoryPermissionRepository
import com.eventstore.infrastructure.projections.InMemoryTenantRepository
import com.eventstore.infrastructure.projections.InMemoryUserRepository
import com.eventstore.infrastructure.projections.NamespaceProjectionService
import com.eventstore.infrastructure.projections.PermissionProjectionService
import com.eventstore.infrastructure.projections.TenantProjectionService
import com.eventstore.infrastructure.projections.UserProjectionService
import com.eventstore.infrastructure.persistence.InMemoryTopicRepository
import com.eventstore.domain.services.auth.ResourceResolverImpl
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.serialization.jackson.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Integration tests for AuthorizationMiddleware.
 * Tests the middleware's behavior in a real HTTP request/response cycle.
 */
class AuthorizationMiddlewareIntegrationTest {
    private lateinit var sessionManager: SessionManager
    private lateinit var authenticationService: AuthenticationService
    private lateinit var authorizationService: AuthorizationService
    private lateinit var permissionProjectionService: PermissionProjectionService
    private lateinit var tenantProjectionService: TenantProjectionService
    private lateinit var namespaceProjectionService: NamespaceProjectionService
    private lateinit var topicRepository: InMemoryTopicRepository
    
    private val testUserId = "test-user-id"
    private val testTenantName = "test-tenant"
    private val testNamespaceName = "test-namespace"
    private val testTopicName = "test-topic"
    private lateinit var testSessionId: String
    private lateinit var testTenantResourceId: UUID

    @BeforeEach
    fun setup() {
        sessionManager = SessionManager()
        val userProjectionService = UserProjectionService(InMemoryUserRepository())
        authenticationService = AuthenticationService(
            userProjectionService = userProjectionService,
            sessionManager = sessionManager
        )
        
        tenantProjectionService = TenantProjectionService(InMemoryTenantRepository())
        namespaceProjectionService = NamespaceProjectionService(InMemoryNamespaceRepository())
        permissionProjectionService = PermissionProjectionService(InMemoryPermissionRepository())
        topicRepository = InMemoryTopicRepository()
        
        val resourceResolver: ResourceResolver = ResourceResolverImpl(
            tenantProjectionService = tenantProjectionService,
            namespaceProjectionService = namespaceProjectionService,
            topicRepository = topicRepository
        )
        
        authorizationService = AuthorizationService(
            permissionProjectionService = permissionProjectionService,
            resourceResolver = resourceResolver
        )

        // Create a test session
        val session = sessionManager.createSession(testUserId)
        testSessionId = session.id

        // Create a test tenant
        testTenantResourceId = UUID.randomUUID()
        runBlocking {
            val tenantEvent = Event(
                id = EventId.create(
                    topic = SystemTopics.TENANTS_TOPIC,
                    sequence = 1,
                    tenantId = SystemTopics.SYSTEM_TENANT_ID,
                    namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
                ),
                timestamp = Instant.now(),
                type = TenantEventType.CREATED,
                payload = TenantCreatedEvent(
                    resourceId = testTenantResourceId,
                    name = testTenantName,
                    createdAt = Instant.now()
                ).toPayload()
            )
            tenantProjectionService.handleEvents(listOf(tenantEvent))
        }
    }

    private fun TestApplicationBuilder.setupApplication(block: Route.() -> Unit) {
        application {
            install(ContentNegotiation) {
                jackson {
                    registerKotlinModule()
                }
            }
            install(StatusPages) {
                exception<com.eventstore.domain.exceptions.TenantNotFoundException> { call, cause ->
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to (cause.message ?: "Tenant not found")))
                }
                exception<com.eventstore.domain.exceptions.NamespaceNotFoundException> { call, cause ->
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to (cause.message ?: "Namespace not found")))
                }
                exception<com.eventstore.domain.exceptions.TopicNotFoundException> { call, cause ->
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to (cause.message ?: "Topic not found")))
                }
            }
            routing {
                AuthenticationMiddleware(authenticationService).install(this)
                AuthorizationMiddleware(authorizationService).install(this)
                block()
            }
        }
    }

    @Test
    fun `allows access to public endpoints without authentication`() = testApplication {
        setupApplication {
            get("/health") {
                call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
            }
        }

        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `returns 401 when no session provided for protected endpoint`() = testApplication {
        setupApplication {
            get("/tenants/$testTenantName") {
                call.respond(HttpStatusCode.OK, mapOf("name" to testTenantName))
            }
        }

        val response = client.get("/tenants/$testTenantName")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `returns 401 when invalid session provided`() = testApplication {
        setupApplication {
            get("/tenants/$testTenantName") {
                call.respond(HttpStatusCode.OK, mapOf("name" to testTenantName))
            }
        }

        val response = client.get("/tenants/$testTenantName") {
            header(HttpHeaders.Authorization, "Bearer invalid-session")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `returns 403 when permission denied`() = testApplication {
        setupApplication {
            get("/tenants/$testTenantName") {
                call.respond(HttpStatusCode.OK, mapOf("name" to testTenantName))
            }
        }

        val response = client.get("/tenants/$testTenantName") {
            header(HttpHeaders.Authorization, "Bearer $testSessionId")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `allows access when permission granted`() = testApplication {
        // Grant permission
        runBlocking {
            val grantEvent = Event(
                id = EventId.create(
                    topic = SystemTopics.PERMISSIONS_TOPIC,
                    sequence = 1,
                    tenantId = SystemTopics.SYSTEM_TENANT_ID,
                    namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
                ),
                timestamp = Instant.now(),
                type = PermissionEventType.GRANTED,
                payload = PermissionGrantedEvent(
                    principalId = testUserId,
                    principalType = PrincipalType.USER,
                    resourceType = ResourceType.TENANT,
                    resourceId = testTenantResourceId.toString(),
                    tenantResourceId = testTenantResourceId.toString(),
                    permissions = setOf(Permission.READ),
                    grantedBy = "admin",
                    grantedAt = Instant.now()
                ).toPayload()
            )
            permissionProjectionService.handleEvents(listOf(grantEvent))
        }

        setupApplication {
            get("/tenants/$testTenantName") {
                call.respond(HttpStatusCode.OK, mapOf("name" to testTenantName))
            }
        }

        val response = client.get("/tenants/$testTenantName") {
            header(HttpHeaders.Authorization, "Bearer $testSessionId")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `extracts tenant context from URL`() = testApplication {
        runBlocking {
            val grantEvent = Event(
                id = EventId.create(
                    topic = SystemTopics.PERMISSIONS_TOPIC,
                    sequence = 1,
                    tenantId = SystemTopics.SYSTEM_TENANT_ID,
                    namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
                ),
                timestamp = Instant.now(),
                type = PermissionEventType.GRANTED,
                payload = PermissionGrantedEvent(
                    principalId = testUserId,
                    principalType = PrincipalType.USER,
                    resourceType = ResourceType.TENANT,
                    resourceId = testTenantResourceId.toString(),
                    tenantResourceId = testTenantResourceId.toString(),
                    permissions = setOf(Permission.READ),
                    grantedBy = "admin",
                    grantedAt = Instant.now()
                ).toPayload()
            )
            permissionProjectionService.handleEvents(listOf(grantEvent))
        }

        setupApplication {
            get("/tenants/$testTenantName") {
                val tenantName = call.attributes.getOrNull(AuthorizationMiddleware.TenantNameKey)
                assertEquals(testTenantName, tenantName)
                call.respond(HttpStatusCode.OK, mapOf("tenantName" to tenantName))
            }
        }

        val response = client.get("/tenants/$testTenantName") {
            header(HttpHeaders.Authorization, "Bearer $testSessionId")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `extracts namespace context from URL`() = testApplication {
        val namespaceResourceId = UUID.randomUUID()
        runBlocking {
            // Create namespace
            val namespaceEvent = Event(
                id = EventId.create(
                    topic = SystemTopics.NAMESPACES_TOPIC,
                    sequence = 1,
                    tenantId = SystemTopics.SYSTEM_TENANT_ID,
                    namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
                ),
                timestamp = Instant.now(),
                type = com.eventstore.domain.events.NamespaceEventType.CREATED,
                payload = com.eventstore.domain.events.NamespaceCreatedEvent(
                    resourceId = namespaceResourceId,
                    tenantResourceId = testTenantResourceId,
                    tenantName = testTenantName,
                    name = testNamespaceName,
                    createdAt = Instant.now()
                ).toPayload()
            )
            namespaceProjectionService.handleEvents(listOf(namespaceEvent))

            // Grant permission
            val grantEvent = Event(
                id = EventId.create(
                    topic = SystemTopics.PERMISSIONS_TOPIC,
                    sequence = 1,
                    tenantId = SystemTopics.SYSTEM_TENANT_ID,
                    namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
                ),
                timestamp = Instant.now(),
                type = PermissionEventType.GRANTED,
                payload = PermissionGrantedEvent(
                    principalId = testUserId,
                    principalType = PrincipalType.USER,
                    resourceType = ResourceType.NAMESPACE,
                    resourceId = namespaceResourceId.toString(),
                    tenantResourceId = testTenantResourceId.toString(),
                    namespaceResourceId = namespaceResourceId.toString(),
                    permissions = setOf(Permission.READ),
                    grantedBy = "admin",
                    grantedAt = Instant.now()
                ).toPayload()
            )
            permissionProjectionService.handleEvents(listOf(grantEvent))
        }

        setupApplication {
            get("/tenants/$testTenantName/namespaces/$testNamespaceName") {
                val tenantName = call.attributes.getOrNull(AuthorizationMiddleware.TenantNameKey)
                val namespaceName = call.attributes.getOrNull(AuthorizationMiddleware.NamespaceNameKey)
                assertEquals(testTenantName, tenantName)
                assertEquals(testNamespaceName, namespaceName)
                call.respond(HttpStatusCode.OK, mapOf(
                    "tenantName" to tenantName,
                    "namespaceName" to namespaceName
                ))
            }
        }

        val response = client.get("/tenants/$testTenantName/namespaces/$testNamespaceName") {
            header(HttpHeaders.Authorization, "Bearer $testSessionId")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `maps HTTP methods to correct permissions`() = testApplication {
        runBlocking {
            // Grant different permissions
            val readGrant = Event(
                id = EventId.create(
                    topic = SystemTopics.PERMISSIONS_TOPIC,
                    sequence = 1,
                    tenantId = SystemTopics.SYSTEM_TENANT_ID,
                    namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
                ),
                timestamp = Instant.now(),
                type = PermissionEventType.GRANTED,
                payload = PermissionGrantedEvent(
                    principalId = testUserId,
                    principalType = PrincipalType.USER,
                    resourceType = ResourceType.TENANT,
                    resourceId = testTenantResourceId.toString(),
                    tenantResourceId = testTenantResourceId.toString(),
                    permissions = setOf(Permission.READ),
                    grantedBy = "admin",
                    grantedAt = Instant.now()
                ).toPayload()
            )
            permissionProjectionService.handleEvents(listOf(readGrant))
        }

        setupApplication {
            get("/tenants/$testTenantName") {
                call.respond(HttpStatusCode.OK, mapOf("name" to testTenantName))
            }
            put("/tenants/$testTenantName") {
                call.respond(HttpStatusCode.OK, mapOf("name" to testTenantName))
            }
        }

        // GET should succeed (READ permission granted)
        val getResponse = client.get("/tenants/$testTenantName") {
            header(HttpHeaders.Authorization, "Bearer $testSessionId")
        }
        assertEquals(HttpStatusCode.OK, getResponse.status)

        // PUT should fail (UPDATE permission not granted)
        val putResponse = client.put("/tenants/$testTenantName") {
            header(HttpHeaders.Authorization, "Bearer $testSessionId")
        }
        assertEquals(HttpStatusCode.Forbidden, putResponse.status)
    }

    @Test
    fun `allows ADMIN permission to access all operations`() = testApplication {
        runBlocking {
            val grantEvent = Event(
                id = EventId.create(
                    topic = SystemTopics.PERMISSIONS_TOPIC,
                    sequence = 1,
                    tenantId = SystemTopics.SYSTEM_TENANT_ID,
                    namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
                ),
                timestamp = Instant.now(),
                type = PermissionEventType.GRANTED,
                payload = PermissionGrantedEvent(
                    principalId = testUserId,
                    principalType = PrincipalType.USER,
                    resourceType = ResourceType.TENANT,
                    resourceId = testTenantResourceId.toString(),
                    tenantResourceId = testTenantResourceId.toString(),
                    permissions = setOf(Permission.ADMIN),
                    grantedBy = "admin",
                    grantedAt = Instant.now()
                ).toPayload()
            )
            permissionProjectionService.handleEvents(listOf(grantEvent))
        }

        setupApplication {
            get("/tenants/$testTenantName") {
                call.respond(HttpStatusCode.OK, mapOf("name" to testTenantName))
            }
            put("/tenants/$testTenantName") {
                call.respond(HttpStatusCode.OK, mapOf("name" to testTenantName))
            }
            delete("/tenants/$testTenantName") {
                call.respond(HttpStatusCode.OK, mapOf("name" to testTenantName))
            }
        }

        // All operations should succeed with ADMIN permission
        val getResponse = client.get("/tenants/$testTenantName") {
            header(HttpHeaders.Authorization, "Bearer $testSessionId")
        }
        assertEquals(HttpStatusCode.OK, getResponse.status)

        val putResponse = client.put("/tenants/$testTenantName") {
            header(HttpHeaders.Authorization, "Bearer $testSessionId")
        }
        assertEquals(HttpStatusCode.OK, putResponse.status)

        val deleteResponse = client.delete("/tenants/$testTenantName") {
            header(HttpHeaders.Authorization, "Bearer $testSessionId")
        }
        assertEquals(HttpStatusCode.OK, deleteResponse.status)
    }

    @Test
    fun `allows access with session cookie`() = testApplication {
        runBlocking {
            val grantEvent = Event(
                id = EventId.create(
                    topic = SystemTopics.PERMISSIONS_TOPIC,
                    sequence = 1,
                    tenantId = SystemTopics.SYSTEM_TENANT_ID,
                    namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
                ),
                timestamp = Instant.now(),
                type = PermissionEventType.GRANTED,
                payload = PermissionGrantedEvent(
                    principalId = testUserId,
                    principalType = PrincipalType.USER,
                    resourceType = ResourceType.TENANT,
                    resourceId = testTenantResourceId.toString(),
                    tenantResourceId = testTenantResourceId.toString(),
                    permissions = setOf(Permission.READ),
                    grantedBy = "admin",
                    grantedAt = Instant.now()
                ).toPayload()
            )
            permissionProjectionService.handleEvents(listOf(grantEvent))
        }

        setupApplication {
            get("/tenants/$testTenantName") {
                call.respond(HttpStatusCode.OK, mapOf("name" to testTenantName))
            }
        }

        val response = client.get("/tenants/$testTenantName") {
            cookie("sessionId", testSessionId)
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    // ========== Edge Case Tests ==========

    @Test
    fun `handles URLs with trailing slashes`() = testApplication {
        runBlocking {
            val grantEvent = Event(
                id = EventId.create(
                    topic = SystemTopics.PERMISSIONS_TOPIC,
                    sequence = 1,
                    tenantId = SystemTopics.SYSTEM_TENANT_ID,
                    namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
                ),
                timestamp = Instant.now(),
                type = PermissionEventType.GRANTED,
                payload = PermissionGrantedEvent(
                    principalId = testUserId,
                    principalType = PrincipalType.USER,
                    resourceType = ResourceType.TENANT,
                    resourceId = testTenantResourceId.toString(),
                    tenantResourceId = testTenantResourceId.toString(),
                    permissions = setOf(Permission.READ),
                    grantedBy = "admin",
                    grantedAt = Instant.now()
                ).toPayload()
            )
            permissionProjectionService.handleEvents(listOf(grantEvent))
        }

        setupApplication {
            get("/tenants/$testTenantName/") {
                call.respond(HttpStatusCode.OK, mapOf("name" to testTenantName))
            }
        }

        val response = client.get("/tenants/$testTenantName/") {
            header(HttpHeaders.Authorization, "Bearer $testSessionId")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `handles URLs with query parameters`() = testApplication {
        runBlocking {
            val grantEvent = Event(
                id = EventId.create(
                    topic = SystemTopics.PERMISSIONS_TOPIC,
                    sequence = 1,
                    tenantId = SystemTopics.SYSTEM_TENANT_ID,
                    namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
                ),
                timestamp = Instant.now(),
                type = PermissionEventType.GRANTED,
                payload = PermissionGrantedEvent(
                    principalId = testUserId,
                    principalType = PrincipalType.USER,
                    resourceType = ResourceType.TENANT,
                    resourceId = testTenantResourceId.toString(),
                    tenantResourceId = testTenantResourceId.toString(),
                    permissions = setOf(Permission.READ),
                    grantedBy = "admin",
                    grantedAt = Instant.now()
                ).toPayload()
            )
            permissionProjectionService.handleEvents(listOf(grantEvent))
        }

        setupApplication {
            get("/tenants/$testTenantName") {
                call.respond(HttpStatusCode.OK, mapOf("name" to testTenantName))
            }
        }

        val response = client.get("/tenants/$testTenantName?filter=active&limit=10") {
            header(HttpHeaders.Authorization, "Bearer $testSessionId")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `handles global permissions for all resources in a tenant`() = testApplication {
        // Global permission (resourceId = null) means all resources of that type within the tenant context
        runBlocking {
            // Grant global permission for all tenants in the tenant context (resourceId = null means all tenant resources)
            val grantEvent = Event(
                id = EventId.create(
                    topic = SystemTopics.PERMISSIONS_TOPIC,
                    sequence = 1,
                    tenantId = SystemTopics.SYSTEM_TENANT_ID,
                    namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
                ),
                timestamp = Instant.now(),
                type = PermissionEventType.GRANTED,
                payload = PermissionGrantedEvent(
                    principalId = testUserId,
                    principalType = PrincipalType.USER,
                    resourceType = ResourceType.TENANT,
                    resourceId = null, // null = all tenant resources of this type in the tenant context
                    tenantResourceId = testTenantResourceId.toString(),
                    permissions = setOf(Permission.READ),
                    grantedBy = "admin",
                    grantedAt = Instant.now()
                ).toPayload()
            )
            permissionProjectionService.handleEvents(listOf(grantEvent))
        }

        setupApplication {
            get("/tenants/$testTenantName") {
                call.respond(HttpStatusCode.OK, mapOf("name" to testTenantName))
            }
        }

        // Should have access to the tenant (global permission applies to all resources in tenant context)
        val response = client.get("/tenants/$testTenantName") {
            header(HttpHeaders.Authorization, "Bearer $testSessionId")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `handles multiple permissions granted`() = testApplication {
        runBlocking {
            val grantEvent = Event(
                id = EventId.create(
                    topic = SystemTopics.PERMISSIONS_TOPIC,
                    sequence = 1,
                    tenantId = SystemTopics.SYSTEM_TENANT_ID,
                    namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
                ),
                timestamp = Instant.now(),
                type = PermissionEventType.GRANTED,
                payload = PermissionGrantedEvent(
                    principalId = testUserId,
                    principalType = PrincipalType.USER,
                    resourceType = ResourceType.TENANT,
                    resourceId = testTenantResourceId.toString(),
                    tenantResourceId = testTenantResourceId.toString(),
                    permissions = setOf(Permission.READ, Permission.UPDATE, Permission.DELETE),
                    grantedBy = "admin",
                    grantedAt = Instant.now()
                ).toPayload()
            )
            permissionProjectionService.handleEvents(listOf(grantEvent))
        }

        setupApplication {
            get("/tenants/$testTenantName") {
                call.respond(HttpStatusCode.OK, mapOf("name" to testTenantName))
            }
            put("/tenants/$testTenantName") {
                call.respond(HttpStatusCode.OK, mapOf("name" to testTenantName))
            }
            delete("/tenants/$testTenantName") {
                call.respond(HttpStatusCode.OK, mapOf("name" to testTenantName))
            }
        }

        // All operations should succeed
        assertEquals(HttpStatusCode.OK, client.get("/tenants/$testTenantName") {
            header(HttpHeaders.Authorization, "Bearer $testSessionId")
        }.status)
        assertEquals(HttpStatusCode.OK, client.put("/tenants/$testTenantName") {
            header(HttpHeaders.Authorization, "Bearer $testSessionId")
        }.status)
        assertEquals(HttpStatusCode.OK, client.delete("/tenants/$testTenantName") {
            header(HttpHeaders.Authorization, "Bearer $testSessionId")
        }.status)
    }

    @Test
    fun `handles unsupported HTTP methods`() = testApplication {
        runBlocking {
            val grantEvent = Event(
                id = EventId.create(
                    topic = SystemTopics.PERMISSIONS_TOPIC,
                    sequence = 1,
                    tenantId = SystemTopics.SYSTEM_TENANT_ID,
                    namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
                ),
                timestamp = Instant.now(),
                type = PermissionEventType.GRANTED,
                payload = PermissionGrantedEvent(
                    principalId = testUserId,
                    principalType = PrincipalType.USER,
                    resourceType = ResourceType.TENANT,
                    resourceId = testTenantResourceId.toString(),
                    tenantResourceId = testTenantResourceId.toString(),
                    permissions = setOf(Permission.READ),
                    grantedBy = "admin",
                    grantedAt = Instant.now()
                ).toPayload()
            )
            permissionProjectionService.handleEvents(listOf(grantEvent))
        }

        setupApplication {
            route("/tenants/$testTenantName") {
                get { call.respond(HttpStatusCode.OK, mapOf("name" to testTenantName)) }
                // PATCH is not mapped to a permission, so should allow if no permission required
                patch { call.respond(HttpStatusCode.OK, mapOf("name" to testTenantName)) }
            }
        }

        // GET should work (READ permission)
        assertEquals(HttpStatusCode.OK, client.get("/tenants/$testTenantName") {
            header(HttpHeaders.Authorization, "Bearer $testSessionId")
        }.status)

        // PATCH should work (no permission mapping, so allows)
        assertEquals(HttpStatusCode.OK, client.patch("/tenants/$testTenantName") {
            header(HttpHeaders.Authorization, "Bearer $testSessionId")
        }.status)
    }

    @Test
    fun `handles Authorization header taking precedence over cookie`() = testApplication {
        val otherUserId = "other-user-id"
        val otherSession = sessionManager.createSession(otherUserId)
        
        runBlocking {
            // Grant permission only to testUserId
            val grantEvent = Event(
                id = EventId.create(
                    topic = SystemTopics.PERMISSIONS_TOPIC,
                    sequence = 1,
                    tenantId = SystemTopics.SYSTEM_TENANT_ID,
                    namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
                ),
                timestamp = Instant.now(),
                type = PermissionEventType.GRANTED,
                payload = PermissionGrantedEvent(
                    principalId = testUserId,
                    principalType = PrincipalType.USER,
                    resourceType = ResourceType.TENANT,
                    resourceId = testTenantResourceId.toString(),
                    tenantResourceId = testTenantResourceId.toString(),
                    permissions = setOf(Permission.READ),
                    grantedBy = "admin",
                    grantedAt = Instant.now()
                ).toPayload()
            )
            permissionProjectionService.handleEvents(listOf(grantEvent))
        }

        setupApplication {
            get("/tenants/$testTenantName") {
                val userId = call.attributes.getOrNull(AuthenticationMiddleware.UserIdKey)
                call.respond(HttpStatusCode.OK, mapOf("userId" to userId))
            }
        }

        // Authorization header should take precedence over cookie
        val response = client.get("/tenants/$testTenantName") {
            header(HttpHeaders.Authorization, "Bearer $testSessionId")
            cookie("sessionId", otherSession.id)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        // Should use testUserId from Authorization header, not otherUserId from cookie
    }

    @Test
    fun `handles empty Bearer token`() = testApplication {
        setupApplication {
            get("/tenants/$testTenantName") {
                call.respond(HttpStatusCode.OK, mapOf("name" to testTenantName))
            }
        }

        val response = client.get("/tenants/$testTenantName") {
            header(HttpHeaders.Authorization, "Bearer ")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `handles malformed Authorization header`() = testApplication {
        setupApplication {
            get("/tenants/$testTenantName") {
                call.respond(HttpStatusCode.OK, mapOf("name" to testTenantName))
            }
        }

        val response = client.get("/tenants/$testTenantName") {
            header(HttpHeaders.Authorization, "InvalidFormat $testSessionId")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `handles non-existent tenant gracefully`() = testApplication {
        runBlocking {
            val grantEvent = Event(
                id = EventId.create(
                    topic = SystemTopics.PERMISSIONS_TOPIC,
                    sequence = 1,
                    tenantId = SystemTopics.SYSTEM_TENANT_ID,
                    namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
                ),
                timestamp = Instant.now(),
                type = PermissionEventType.GRANTED,
                payload = PermissionGrantedEvent(
                    principalId = testUserId,
                    principalType = PrincipalType.USER,
                    resourceType = ResourceType.TENANT,
                    resourceId = UUID.randomUUID().toString(),
                    tenantResourceId = UUID.randomUUID().toString(),
                    permissions = setOf(Permission.READ),
                    grantedBy = "admin",
                    grantedAt = Instant.now()
                ).toPayload()
            )
            permissionProjectionService.handleEvents(listOf(grantEvent))
        }

        setupApplication {
            get("/tenants/non-existent-tenant") {
                call.respond(HttpStatusCode.OK, mapOf("name" to "non-existent"))
            }
        }

        // Should fail when trying to resolve non-existent tenant
        // ResourceResolver throws TenantNotFoundException, which should be caught and return 404
        val response = client.get("/tenants/non-existent-tenant") {
            header(HttpHeaders.Authorization, "Bearer $testSessionId")
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `handles topic routes with schema management permission`() = testApplication {
        val namespaceResourceId = UUID.randomUUID()
        val topicResourceId = UUID.randomUUID()
        runBlocking {
            // Create namespace and topic
            val namespaceEvent = Event(
                id = EventId.create(
                    topic = SystemTopics.NAMESPACES_TOPIC,
                    sequence = 1,
                    tenantId = SystemTopics.SYSTEM_TENANT_ID,
                    namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
                ),
                timestamp = Instant.now(),
                type = com.eventstore.domain.events.NamespaceEventType.CREATED,
                payload = com.eventstore.domain.events.NamespaceCreatedEvent(
                    resourceId = namespaceResourceId,
                    tenantResourceId = testTenantResourceId,
                    tenantName = testTenantName,
                    name = testNamespaceName,
                    createdAt = Instant.now()
                ).toPayload()
            )
            namespaceProjectionService.handleEvents(listOf(namespaceEvent))

            // Create topic
            topicRepository.createTopic(
                resourceId = topicResourceId,
                tenantResourceId = testTenantResourceId,
                namespaceResourceId = namespaceResourceId,
                name = testTopicName,
                schemas = emptyList(),
                tenantName = testTenantName,
                namespaceName = testNamespaceName
            )

            // Grant SCHEMA_MANAGE permission
            val grantEvent = Event(
                id = EventId.create(
                    topic = SystemTopics.PERMISSIONS_TOPIC,
                    sequence = 1,
                    tenantId = SystemTopics.SYSTEM_TENANT_ID,
                    namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
                ),
                timestamp = Instant.now(),
                type = PermissionEventType.GRANTED,
                payload = PermissionGrantedEvent(
                    principalId = testUserId,
                    principalType = PrincipalType.USER,
                    resourceType = ResourceType.TOPIC,
                    resourceId = topicResourceId.toString(),
                    tenantResourceId = testTenantResourceId.toString(),
                    namespaceResourceId = namespaceResourceId.toString(),
                    permissions = setOf(Permission.SCHEMA_MANAGE),
                    grantedBy = "admin",
                    grantedAt = Instant.now()
                ).toPayload()
            )
            permissionProjectionService.handleEvents(listOf(grantEvent))
        }

        setupApplication {
            put("/tenants/$testTenantName/namespaces/$testNamespaceName/topics/$testTopicName/schemas") {
                call.respond(HttpStatusCode.OK, mapOf("message" to "Schemas updated"))
            }
        }

        val response = client.put("/tenants/$testTenantName/namespaces/$testNamespaceName/topics/$testTopicName/schemas") {
            header(HttpHeaders.Authorization, "Bearer $testSessionId")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `handles LIST permission for collection endpoints`() = testApplication {
        runBlocking {
            val grantEvent = Event(
                id = EventId.create(
                    topic = SystemTopics.PERMISSIONS_TOPIC,
                    sequence = 1,
                    tenantId = SystemTopics.SYSTEM_TENANT_ID,
                    namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
                ),
                timestamp = Instant.now(),
                type = PermissionEventType.GRANTED,
                payload = PermissionGrantedEvent(
                    principalId = testUserId,
                    principalType = PrincipalType.USER,
                    resourceType = ResourceType.TENANT,
                    resourceId = null, // All tenants
                    tenantResourceId = testTenantResourceId.toString(),
                    permissions = setOf(Permission.LIST),
                    grantedBy = "admin",
                    grantedAt = Instant.now()
                ).toPayload()
            )
            permissionProjectionService.handleEvents(listOf(grantEvent))
        }

        setupApplication {
            get("/tenants") {
                call.respond(HttpStatusCode.OK, mapOf("tenants" to emptyList<String>()))
            }
        }

        val response = client.get("/tenants") {
            header(HttpHeaders.Authorization, "Bearer $testSessionId")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `handles CREATE permission for collection endpoints`() = testApplication {
        runBlocking {
            val grantEvent = Event(
                id = EventId.create(
                    topic = SystemTopics.PERMISSIONS_TOPIC,
                    sequence = 1,
                    tenantId = SystemTopics.SYSTEM_TENANT_ID,
                    namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
                ),
                timestamp = Instant.now(),
                type = PermissionEventType.GRANTED,
                payload = PermissionGrantedEvent(
                    principalId = testUserId,
                    principalType = PrincipalType.USER,
                    resourceType = ResourceType.TENANT,
                    resourceId = null, // All tenants
                    tenantResourceId = testTenantResourceId.toString(),
                    permissions = setOf(Permission.CREATE),
                    grantedBy = "admin",
                    grantedAt = Instant.now()
                ).toPayload()
            )
            permissionProjectionService.handleEvents(listOf(grantEvent))
        }

        setupApplication {
            post("/tenants") {
                call.respond(HttpStatusCode.Created, mapOf("message" to "Created"))
            }
        }

        val response = client.post("/tenants") {
            header(HttpHeaders.Authorization, "Bearer $testSessionId")
        }
        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `handles event routes correctly`() = testApplication {
        val namespaceResourceId = UUID.randomUUID()
        runBlocking {
            // Create namespace
            val namespaceEvent = Event(
                id = EventId.create(
                    topic = SystemTopics.NAMESPACES_TOPIC,
                    sequence = 1,
                    tenantId = SystemTopics.SYSTEM_TENANT_ID,
                    namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
                ),
                timestamp = Instant.now(),
                type = com.eventstore.domain.events.NamespaceEventType.CREATED,
                payload = com.eventstore.domain.events.NamespaceCreatedEvent(
                    resourceId = namespaceResourceId,
                    tenantResourceId = testTenantResourceId,
                    tenantName = testTenantName,
                    name = testNamespaceName,
                    createdAt = Instant.now()
                ).toPayload()
            )
            namespaceProjectionService.handleEvents(listOf(namespaceEvent))

            // Grant EVENT CREATE permission
            val grantEvent = Event(
                id = EventId.create(
                    topic = SystemTopics.PERMISSIONS_TOPIC,
                    sequence = 1,
                    tenantId = SystemTopics.SYSTEM_TENANT_ID,
                    namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
                ),
                timestamp = Instant.now(),
                type = PermissionEventType.GRANTED,
                payload = PermissionGrantedEvent(
                    principalId = testUserId,
                    principalType = PrincipalType.USER,
                    resourceType = ResourceType.EVENT,
                    resourceId = null, // All events
                    tenantResourceId = testTenantResourceId.toString(),
                    namespaceResourceId = namespaceResourceId.toString(),
                    permissions = setOf(Permission.CREATE),
                    grantedBy = "admin",
                    grantedAt = Instant.now()
                ).toPayload()
            )
            permissionProjectionService.handleEvents(listOf(grantEvent))
        }

        setupApplication {
            post("/tenants/$testTenantName/namespaces/$testNamespaceName/events") {
                call.respond(HttpStatusCode.Created, mapOf("eventIds" to emptyList<String>()))
            }
        }

        val response = client.post("/tenants/$testTenantName/namespaces/$testNamespaceName/events") {
            header(HttpHeaders.Authorization, "Bearer $testSessionId")
        }
        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `handles user routes correctly`() = testApplication {
        val userResourceId = UUID.randomUUID()
        runBlocking {
            // Grant USER READ permission
            val grantEvent = Event(
                id = EventId.create(
                    topic = SystemTopics.PERMISSIONS_TOPIC,
                    sequence = 1,
                    tenantId = SystemTopics.SYSTEM_TENANT_ID,
                    namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
                ),
                timestamp = Instant.now(),
                type = PermissionEventType.GRANTED,
                payload = PermissionGrantedEvent(
                    principalId = testUserId,
                    principalType = PrincipalType.USER,
                    resourceType = ResourceType.USER,
                    resourceId = userResourceId.toString(),
                    tenantResourceId = testTenantResourceId.toString(),
                    permissions = setOf(Permission.READ),
                    grantedBy = "admin",
                    grantedAt = Instant.now()
                ).toPayload()
            )
            permissionProjectionService.handleEvents(listOf(grantEvent))
        }

        setupApplication {
            get("/tenants/$testTenantName/users/$userResourceId") {
                call.respond(HttpStatusCode.OK, mapOf("userId" to userResourceId.toString()))
            }
        }

        val response = client.get("/tenants/$testTenantName/users/$userResourceId") {
            header(HttpHeaders.Authorization, "Bearer $testSessionId")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `handles topic extraction from URL`() = testApplication {
        val namespaceResourceId = UUID.randomUUID()
        val topicResourceId = UUID.randomUUID()
        runBlocking {
            // Create namespace
            val namespaceEvent = Event(
                id = EventId.create(
                    topic = SystemTopics.NAMESPACES_TOPIC,
                    sequence = 1,
                    tenantId = SystemTopics.SYSTEM_TENANT_ID,
                    namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
                ),
                timestamp = Instant.now(),
                type = com.eventstore.domain.events.NamespaceEventType.CREATED,
                payload = com.eventstore.domain.events.NamespaceCreatedEvent(
                    resourceId = namespaceResourceId,
                    tenantResourceId = testTenantResourceId,
                    tenantName = testTenantName,
                    name = testNamespaceName,
                    createdAt = Instant.now()
                ).toPayload()
            )
            namespaceProjectionService.handleEvents(listOf(namespaceEvent))

            // Create topic
            topicRepository.createTopic(
                resourceId = topicResourceId,
                tenantResourceId = testTenantResourceId,
                namespaceResourceId = namespaceResourceId,
                name = testTopicName,
                schemas = emptyList(),
                tenantName = testTenantName,
                namespaceName = testNamespaceName
            )

            // Grant TOPIC READ permission
            val grantEvent = Event(
                id = EventId.create(
                    topic = SystemTopics.PERMISSIONS_TOPIC,
                    sequence = 1,
                    tenantId = SystemTopics.SYSTEM_TENANT_ID,
                    namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
                ),
                timestamp = Instant.now(),
                type = PermissionEventType.GRANTED,
                payload = PermissionGrantedEvent(
                    principalId = testUserId,
                    principalType = PrincipalType.USER,
                    resourceType = ResourceType.TOPIC,
                    resourceId = topicResourceId.toString(),
                    tenantResourceId = testTenantResourceId.toString(),
                    namespaceResourceId = namespaceResourceId.toString(),
                    permissions = setOf(Permission.READ),
                    grantedBy = "admin",
                    grantedAt = Instant.now()
                ).toPayload()
            )
            permissionProjectionService.handleEvents(listOf(grantEvent))
        }

        setupApplication {
            get("/tenants/$testTenantName/namespaces/$testNamespaceName/topics/$testTopicName") {
                val tenantName = call.attributes.getOrNull(AuthorizationMiddleware.TenantNameKey)
                val namespaceName = call.attributes.getOrNull(AuthorizationMiddleware.NamespaceNameKey)
                val topicName = call.attributes.getOrNull(AuthorizationMiddleware.TopicNameKey)
                assertEquals(testTenantName, tenantName)
                assertEquals(testNamespaceName, namespaceName)
                assertEquals(testTopicName, topicName)
                call.respond(HttpStatusCode.OK, mapOf(
                    "tenantName" to tenantName,
                    "namespaceName" to namespaceName,
                    "topicName" to topicName
                ))
            }
        }

        val response = client.get("/tenants/$testTenantName/namespaces/$testNamespaceName/topics/$testTopicName") {
            header(HttpHeaders.Authorization, "Bearer $testSessionId")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `handles permission denied for specific resource when global permission exists`() = testApplication {
        val otherTenantName = "other-tenant"
        val otherTenantResourceId = UUID.randomUUID()
        runBlocking {
            // Create another tenant
            val otherTenantEvent = Event(
                id = EventId.create(
                    topic = SystemTopics.TENANTS_TOPIC,
                    sequence = 2,
                    tenantId = SystemTopics.SYSTEM_TENANT_ID,
                    namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
                ),
                timestamp = Instant.now(),
                type = TenantEventType.CREATED,
                payload = TenantCreatedEvent(
                    resourceId = otherTenantResourceId,
                    name = otherTenantName,
                    createdAt = Instant.now()
                ).toPayload()
            )
            tenantProjectionService.handleEvents(listOf(otherTenantEvent))

            // Grant permission only for testTenantResourceId (not global)
            val grantEvent = Event(
                id = EventId.create(
                    topic = SystemTopics.PERMISSIONS_TOPIC,
                    sequence = 1,
                    tenantId = SystemTopics.SYSTEM_TENANT_ID,
                    namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
                ),
                timestamp = Instant.now(),
                type = PermissionEventType.GRANTED,
                payload = PermissionGrantedEvent(
                    principalId = testUserId,
                    principalType = PrincipalType.USER,
                    resourceType = ResourceType.TENANT,
                    resourceId = testTenantResourceId.toString(), // Specific tenant only
                    tenantResourceId = testTenantResourceId.toString(),
                    permissions = setOf(Permission.READ),
                    grantedBy = "admin",
                    grantedAt = Instant.now()
                ).toPayload()
            )
            permissionProjectionService.handleEvents(listOf(grantEvent))
        }

        setupApplication {
            get("/tenants/{tenantName}") {
                val tenantName = call.parameters["tenantName"]
                call.respond(HttpStatusCode.OK, mapOf("name" to tenantName))
            }
        }

        // Should have access to testTenantName
        val response1 = client.get("/tenants/$testTenantName") {
            header(HttpHeaders.Authorization, "Bearer $testSessionId")
        }
        assertEquals(HttpStatusCode.OK, response1.status)

        // Should NOT have access to otherTenantName
        val response2 = client.get("/tenants/$otherTenantName") {
            header(HttpHeaders.Authorization, "Bearer $testSessionId")
        }
        assertEquals(HttpStatusCode.Forbidden, response2.status)
    }

    @Test
    fun `handles system-level endpoints without tenant context`() = testApplication {
        setupApplication {
            // Endpoint without tenant context
            get("/system/info") {
                call.respond(HttpStatusCode.OK, mapOf("version" to "1.0.0"))
            }
        }

        // Should allow access (no tenant context = system-level endpoint)
        val response = client.get("/system/info") {
            header(HttpHeaders.Authorization, "Bearer $testSessionId")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `handles expired permissions`() = testApplication {
        runBlocking {
            val expiredAt = Instant.now().minusSeconds(3600) // Expired 1 hour ago
            val grantEvent = Event(
                id = EventId.create(
                    topic = SystemTopics.PERMISSIONS_TOPIC,
                    sequence = 1,
                    tenantId = SystemTopics.SYSTEM_TENANT_ID,
                    namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
                ),
                timestamp = Instant.now(),
                type = PermissionEventType.GRANTED,
                payload = PermissionGrantedEvent(
                    principalId = testUserId,
                    principalType = PrincipalType.USER,
                    resourceType = ResourceType.TENANT,
                    resourceId = testTenantResourceId.toString(),
                    tenantResourceId = testTenantResourceId.toString(),
                    permissions = setOf(Permission.READ),
                    grantedBy = "admin",
                    grantedAt = Instant.now(),
                    expiresAt = expiredAt
                ).toPayload()
            )
            permissionProjectionService.handleEvents(listOf(grantEvent))
        }

        setupApplication {
            get("/tenants/$testTenantName") {
                call.respond(HttpStatusCode.OK, mapOf("name" to testTenantName))
            }
        }

        // Expired permission should be denied
        val response = client.get("/tenants/$testTenantName") {
            header(HttpHeaders.Authorization, "Bearer $testSessionId")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `handles URLs with special characters`() = testApplication {
        val tenantNameWithSpecialChars = "tenant-name-with-dashes"
        val tenantResourceId = UUID.randomUUID()
        runBlocking {
            // Create tenant with special characters
            val tenantEvent = Event(
                id = EventId.create(
                    topic = SystemTopics.TENANTS_TOPIC,
                    sequence = 1,
                    tenantId = SystemTopics.SYSTEM_TENANT_ID,
                    namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
                ),
                timestamp = Instant.now(),
                type = TenantEventType.CREATED,
                payload = TenantCreatedEvent(
                    resourceId = tenantResourceId,
                    name = tenantNameWithSpecialChars,
                    createdAt = Instant.now()
                ).toPayload()
            )
            tenantProjectionService.handleEvents(listOf(tenantEvent))

            // Grant permission
            val grantEvent = Event(
                id = EventId.create(
                    topic = SystemTopics.PERMISSIONS_TOPIC,
                    sequence = 1,
                    tenantId = SystemTopics.SYSTEM_TENANT_ID,
                    namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
                ),
                timestamp = Instant.now(),
                type = PermissionEventType.GRANTED,
                payload = PermissionGrantedEvent(
                    principalId = testUserId,
                    principalType = PrincipalType.USER,
                    resourceType = ResourceType.TENANT,
                    resourceId = tenantResourceId.toString(),
                    tenantResourceId = tenantResourceId.toString(),
                    permissions = setOf(Permission.READ),
                    grantedBy = "admin",
                    grantedAt = Instant.now()
                ).toPayload()
            )
            permissionProjectionService.handleEvents(listOf(grantEvent))
        }

        setupApplication {
            get("/tenants/{tenantName}") {
                val tenantName = call.parameters["tenantName"]
                call.respond(HttpStatusCode.OK, mapOf("name" to tenantName))
            }
        }

        val response = client.get("/tenants/$tenantNameWithSpecialChars") {
            header(HttpHeaders.Authorization, "Bearer $testSessionId")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }
}

