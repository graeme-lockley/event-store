package com.eventstore

import com.eventstore.domain.Application as DomainApplication
import com.eventstore.domain.services.auth.AuthenticationService
import com.eventstore.domain.services.auth.AuthorizationService
import com.eventstore.domain.services.namespace.CreateNamespaceRequest
import com.eventstore.domain.tenants.SystemTopics
import com.eventstore.infrastructure.auth.ApiKeyAuthenticator
import com.eventstore.infrastructure.auth.SessionManager
import com.eventstore.infrastructure.background.AsyncDispatcherManager
import com.eventstore.infrastructure.external.JsonSchemaValidator
import com.eventstore.infrastructure.persistence.FileSystemEventRepository
import com.eventstore.infrastructure.persistence.FileSystemTopicRepository
import com.eventstore.infrastructure.persistence.InMemoryApiKeyRepository
import com.eventstore.infrastructure.persistence.InMemoryConsumerRepository
import com.eventstore.infrastructure.projections.*
import com.eventstore.interfaces.http.middleware.AuthenticationMiddleware
import com.eventstore.interfaces.http.middleware.AuthorizationMiddleware
import com.eventstore.interfaces.http.routes.*
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.coroutines.*
import org.slf4j.event.Level
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

fun main(args: Array<String>) {
    val config = Config.fromEnvironment()

    embeddedServer(Netty, port = config.port) {
        configureApplication(config)
    }.start(wait = true)
}

fun Application.configureApplication(config: Config) {
    // Configure Jackson ObjectMapper
    val objectMapper = jacksonObjectMapper().apply {
        registerKotlinModule()
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    // Initialize infrastructure components
    val dataDir = Paths.get(config.dataDir)
    val configDir = Paths.get(config.configDir)

    val topicRepository = FileSystemTopicRepository(configDir, objectMapper)
    val eventRepository = FileSystemEventRepository(dataDir, objectMapper)
    val consumerRepository = InMemoryConsumerRepository()
    val schemaValidator = JsonSchemaValidator(objectMapper)
    val consumerFactory = com.eventstore.infrastructure.factories.ConsumerFactoryImpl()

    // Initialize dispatcher manager for production
    val dispatcherManager = AsyncDispatcherManager(
        consumerRepository = consumerRepository,
        eventRepository = eventRepository
    )

    // Create domain Application with FileSystem repositories and AsyncDispatcherManager
    val domainApplication = DomainApplication(
        bootstrap = true,
        topicRepository = topicRepository,
        eventRepository = eventRepository,
        consumerRepository = consumerRepository,
        schemaValidator = schemaValidator,
        consumerFactory = consumerFactory,
        config = config,
        providedDispatcherManager = dispatcherManager
    )

    // Create application scope for lifecycle management
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Load existing schemas on startup (non-blocking)
    environment.monitor.subscribe(ApplicationStarted) {
        applicationScope.launch {
            val topics = topicRepository.getAllTopics()
            topics.forEach { topic ->
                schemaValidator.registerSchemas(topic.name, topic.schemas)
            }
        }
    }

    // Create auth services needed for middleware
    val sessionManager = SessionManager()
    val authenticationService = AuthenticationService(domainApplication.userProjectionService, sessionManager)
    val authorizationService = AuthorizationService(
        permissionProjectionService = domainApplication.permissionProjectionService,
        resourceResolver = domainApplication.resourceResolver
    )
    val apiKeyAuthenticator = ApiKeyAuthenticator(
        domainApplication.apiKeyProjectionService,
        domainApplication.apiKeyRepository
    )

    // Ensure default/default namespace exists for legacy endpoints when multi-tenant is enabled
    if (config.multiTenantEnabled) {
        runBlocking {
            if (domainApplication.tenantProjectionService.tenantExistsByName("default") &&
                !domainApplication.namespaceProjectionService.namespaceExistsByName("default", "default")
            ) {
                runCatching {
                    domainApplication.createNamespace("default", "default")
                }
            }
        }
    }

    // Configure Ktor plugins
    install(ContentNegotiation) {
        jackson {
            registerKotlinModule()
        }
    }

    // Attribute key for storing request start time
    val requestStartTimeKey = AttributeKey<Long>("RequestStartTime")

    // Interceptor to track request start time - intercept early in the call pipeline
    intercept(ApplicationCallPipeline.Call) {
        call.attributes.put(requestStartTimeKey, System.currentTimeMillis())
    }

    install(CallLogging) {
        level = Level.INFO
        format { call ->
            val status = call.response.status()
            val httpMethod = call.request.httpMethod.value
            val startTime = call.attributes.getOrNull(requestStartTimeKey) ?: System.currentTimeMillis()
            val duration = System.currentTimeMillis() - startTime
            "$httpMethod ${call.request.path()} - $status - ${duration}ms"
        }
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            val status = when (cause) {
                is com.eventstore.domain.exceptions.TopicNotFoundException -> HttpStatusCode.NotFound
                is com.eventstore.domain.exceptions.ConsumerNotFoundException -> HttpStatusCode.NotFound
                is com.eventstore.domain.exceptions.EventStorageException -> HttpStatusCode.InternalServerError
                is com.eventstore.domain.exceptions.TopicConfigException -> HttpStatusCode.InternalServerError
                is IllegalArgumentException -> HttpStatusCode.BadRequest
                else -> HttpStatusCode.InternalServerError
            }
            call.respond(
                status,
                com.eventstore.interfaces.http.dto.ErrorResponse(
                    error = cause.message ?: "Unknown error",
                    code = null
                )
            )
        }
    }

    // Middleware: Body size limiting
    intercept(ApplicationCallPipeline.Plugins) {
        if (call.request.httpMethod == HttpMethod.Post) {
            val contentLength = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: 0L
            if (contentLength > config.maxBodyBytes) {
                call.respond(
                    HttpStatusCode.PayloadTooLarge,
                    com.eventstore.interfaces.http.dto.ErrorResponse(
                        error = "Payload too large",
                        code = "PAYLOAD_TOO_LARGE"
                    )
                )
                return@intercept
            }
        }
    }

    // Middleware: Rate limiting (in-memory per IP per route)
    val rateBuckets = ConcurrentHashMap<String, RateBucket>()

    // Periodic cleanup of expired rate limit buckets
    applicationScope.launch {
        while (isActive) {
            delay(60_000) // Every minute
            val now = System.currentTimeMillis()
            rateBuckets.entries.removeAll { it.value.resetAt < now }
        }
    }

    intercept(ApplicationCallPipeline.Plugins) {
        val ip = call.request.headers["X-Forwarded-For"]?.split(",")?.firstOrNull()?.trim()
            ?: call.request.local.remoteHost
            ?: "unknown"
        val route = call.request.path()
        val key = "$ip:$route"

        val now = System.currentTimeMillis()
        val bucket = rateBuckets.compute(key) { _, existing ->
            if (existing == null || now > existing.resetAt) {
                RateBucket(count = AtomicInteger(0), resetAt = now + 60_000)
            } else {
                existing
            }
        }!!

        val count = bucket.count.incrementAndGet()
        if (count > config.rateLimitPerMinute) {
            val retryAfter = ((bucket.resetAt - now) / 1000).coerceAtLeast(1)
            call.response.headers.append(HttpHeaders.RetryAfter, retryAfter.toString())
            call.respond(
                HttpStatusCode.TooManyRequests,
                com.eventstore.interfaces.http.dto.ErrorResponse(
                    error = "Too many requests",
                    code = "RATE_LIMITED"
                )
            )
            return@intercept
        }
    }

    // Install middleware
    val authenticationMiddleware = AuthenticationMiddleware(authenticationService, apiKeyAuthenticator)
    val authorizationMiddleware = AuthorizationMiddleware(authorizationService)

    // Configure routing - routes now use domain Application instance
    routing {
        // Install authentication middleware
        authenticationMiddleware.install(this)

        // Install authorization middleware
        authorizationMiddleware.install(this)

        topicRoutes(domainApplication, dispatcherManager)
        eventRoutes(domainApplication)
        consumerRoutes(domainApplication)
        tenantRoutes(domainApplication)
        namespaceRoutes(domainApplication)
        userRoutes(domainApplication)
        apiKeyRoutes(domainApplication)
        authRoutes(authenticationService, domainApplication)
        permissionRoutes(domainApplication)
        healthRoutes(domainApplication)
    }

    // Graceful shutdown
    environment.monitor.subscribe(ApplicationStopped) {
        runBlocking {
            if (dispatcherManager is AsyncDispatcherManager) {
                dispatcherManager.stopAllDispatchers()
            }
            applicationScope.cancel()
        }
    }

    // Startup logging
    println("🚀 Event Store starting on port ${config.port}")
    println("📁 Data directory: ${config.dataDir}")
    println("📁 Config directory: ${config.configDir}")
    println("📖 API Endpoints:")
    println("   Tenant Management:")
    println("     POST   /tenants - Create tenant")
    println("     GET    /tenants/{tenantName} - Get tenant")
    println("     PUT    /tenants/{tenantName} - Update tenant")
    println("     DELETE /tenants/{tenantName} - Delete tenant")
    println("   Namespace Management:")
    println("     POST   /tenants/{tenantName}/namespaces - Create namespace")
    println("     GET    /tenants/{tenantName}/namespaces/{namespaceName} - Get namespace")
    println("     PUT    /tenants/{tenantName}/namespaces/{namespaceName} - Update namespace")
    println("     DELETE /tenants/{tenantName}/namespaces/{namespaceName} - Delete namespace")
    println("   Topic Management:")
    println("     POST   /tenants/{tenantName}/namespaces/{namespaceName}/topics - Create topic")
    println("     GET    /tenants/{tenantName}/namespaces/{namespaceName}/topics - List topics")
    println("     GET    /tenants/{tenantName}/namespaces/{namespaceName}/topics/{topic} - Get topic")
    println("     PUT    /tenants/{tenantName}/namespaces/{namespaceName}/topics/{topic} - Update schemas")
    println("   Event Operations:")
    println("     POST   /tenants/{tenantName}/namespaces/{namespaceName}/events - Publish events")
    println("     GET    /tenants/{tenantName}/namespaces/{namespaceName}/topics/{topic}/events - Retrieve events")
    println("   Consumer Management:")
    println("     POST   /tenants/{tenantName}/namespaces/{namespaceName}/consumers/register - Register consumer")
    println("     GET    /tenants/{tenantName}/namespaces/{namespaceName}/consumers - List consumers")
    println("     DELETE /tenants/{tenantName}/namespaces/{namespaceName}/consumers/{id} - Unregister consumer")
    println("   User Management:")
    println("     POST   /tenants/{tenantId}/users - Create user")
    println("     GET    /tenants/{tenantId}/users - List users")
    println("     GET    /tenants/{tenantId}/users/{userId} - Get user")
    println("     PUT    /tenants/{tenantId}/users/{userId} - Update user")
    println("     DELETE /tenants/{tenantId}/users/{userId} - Delete user")
    println("     POST   /tenants/{tenantId}/users/{userId}/tenants - Assign user to tenant")
    println("     DELETE /tenants/{tenantId}/users/{userId}/tenants/{tenantId} - Remove user from tenant")
    println("   API Key Management:")
    println("     POST   /tenants/{tenantId}/users/{userId}/api-keys - Create API key")
    println("     GET    /tenants/{tenantId}/users/{userId}/api-keys - List API keys")
    println("     GET    /tenants/{tenantId}/users/{userId}/api-keys/{keyId} - Get API key")
    println("     DELETE /tenants/{tenantId}/users/{userId}/api-keys/{keyId} - Revoke API key")
    println("   Authentication:")
    println("     POST   /auth/login - Login")
    println("     POST   /auth/logout - Logout")
    println("     POST   /auth/password/change - Change password")
    println("   Permission Management:")
    println("     GET    /tenants/{tenantName}/users/{userId}/permissions - Get permissions")
    println("     POST   /tenants/{tenantName}/users/{userId}/permissions - Grant permissions")
    println("     DELETE /tenants/{tenantName}/users/{userId}/permissions - Revoke permissions")
    println("   GET  /health - Health check")
}

data class RateBucket(
    val count: AtomicInteger,
    val resetAt: Long
)
