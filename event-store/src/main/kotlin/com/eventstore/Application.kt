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
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import ch.qos.logback.classic.Level as LogbackLevel
import ch.qos.logback.classic.LoggerContext
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
    // Configure logging levels based on silent mode
    if (config.silent) {
        val loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext
        loggerContext.getLogger("com.eventstore.infrastructure.bootstrap.BootstrapServiceImpl").level = LogbackLevel.ERROR
        loggerContext.getLogger("ktor.application").level = LogbackLevel.ERROR
        loggerContext.getLogger("ktor.test").level = LogbackLevel.ERROR
    }

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
                schemaValidator.registerSchemas(topic.topicId, topic.schemas)
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

    // Ensure default/default namespace exists for legacy endpoints
    runBlocking {
        if (domainApplication.tenantProjectionService.tenantExistsByName("default") &&
            !domainApplication.namespaceProjectionService.namespaceExistsByName("default", "default")
        ) {
            runCatching {
                val defaultTenant = domainApplication.getTenant("default")
                if (defaultTenant != null) {
                    domainApplication.createNamespace(defaultTenant.tenantId, "default")
                }
            }
        }
    }

    // Configure Ktor plugins
    install(ContentNegotiation) {
        jackson {
            registerKotlinModule()
            // Fail on null for non-nullable properties
            configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES, true)
        }
    }

    // Attribute key for storing request start time
    val requestStartTimeKey = AttributeKey<Long>("RequestStartTime")

    // Interceptor to track request start time - intercept early in the call pipeline
    // Only needed if CallLogging is installed
    if (!config.silent) {
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
    }

    install(StatusPages) {
        exception<com.eventstore.domain.exceptions.TenantNameNotFoundException> { call, cause ->
            call.respond(
                HttpStatusCode.NotFound,
                com.eventstore.interfaces.http.dto.ErrorResponse(
                    error = cause.message ?: "Tenant not found",
                    code = "TENANT_NOT_FOUND"
                )
            )
        }
        exception<com.eventstore.domain.exceptions.TenantNotFoundException> { call, cause ->
            call.respond(
                HttpStatusCode.NotFound,
                com.eventstore.interfaces.http.dto.ErrorResponse(
                    error = cause.message ?: "Tenant not found",
                    code = "TENANT_NOT_FOUND"
                )
            )
        }
        // Order matters: more specific exceptions must come before their parent classes
        exception<com.fasterxml.jackson.databind.exc.MismatchedInputException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                com.eventstore.interfaces.http.dto.ErrorResponse(
                    error = "Invalid request format: ${cause.message ?: "Malformed request body"}",
                    code = "INVALID_REQUEST"
                )
            )
        }
        exception<com.fasterxml.jackson.core.JsonParseException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                com.eventstore.interfaces.http.dto.ErrorResponse(
                    error = "Invalid JSON syntax: ${cause.message ?: "Malformed request body"}",
                    code = "INVALID_JSON"
                )
            )
        }
        exception<com.fasterxml.jackson.databind.JsonMappingException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                com.eventstore.interfaces.http.dto.ErrorResponse(
                    error = "Invalid JSON mapping: ${cause.message ?: "Malformed request body"}",
                    code = "INVALID_JSON"
                )
            )
        }
        exception<com.fasterxml.jackson.core.JsonProcessingException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                com.eventstore.interfaces.http.dto.ErrorResponse(
                    error = "Invalid JSON: ${cause.message ?: "Malformed request body"}",
                    code = "INVALID_JSON"
                )
            )
        }
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

        topicRoutes(domainApplication)
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
    if (!config.silent) {
        println("🚀 Event Store starting on port ${config.port}")
        println("📁 Data directory: ${config.dataDir}")
        println("📁 Config directory: ${config.configDir}")
        println("📖 API Endpoints:")
        println("   Tenant Management:")
        println("     POST   /tenants - Create tenant")
        println("     GET    /tenants - List all tenants")
        println("     GET    /tenants/{tenantId} - Get tenant by UUID")
        println("     PUT    /tenants/{tenantId} - Update tenant")
        println("     DELETE /tenants/{tenantId} - Delete tenant")
        println("   Namespace Management:")
        println("     POST   /namespaces - Create namespace")
        println("     GET    /namespaces - List namespaces (optional ?tenantId query param)")
        println("     GET    /namespaces/{namespaceId} - Get namespace")
        println("     PUT    /namespaces/{namespaceId} - Update namespace")
        println("     DELETE /namespaces/{namespaceId} - Delete namespace")
        println("   Topic Management:")
        println("     POST   /topics - Create topic")
        println("     GET    /topics - List topics (optional ?namespaceId query param)")
        println("     GET    /topics/{topicId} - Get topic")
        println("     PUT    /topics/{topicId}/schemas - Update topic schemas")
        println("   Event Operations:")
        println("     POST   /topics/{topicId}/events - Publish events")
        println("     GET    /topics/{topicId}/events - Retrieve events")
        println("   Consumer Management:")
        println("     POST   /namespaces/{namespaceId}/consumers/register - Register consumer")
        println("     GET    /namespaces/{namespaceId}/consumers - List consumers")
        println("     DELETE /namespaces/{namespaceId}/consumers/{id} - Unregister consumer")
        println("   User Management:")
        println("     POST   /users - Create user")
        println("     GET    /users - List all users")
        println("     GET    /users/{userId} - Get user")
        println("     PUT    /users/{userId} - Update user")
        println("     DELETE /users/{userId} - Delete user")
        println("     POST   /users/{userId}/tenants/{tenantId} - Assign user to tenant")
        println("     DELETE /users/{userId}/tenants/{tenantId} - Remove user from tenant")
        println("   API Key Management:")
        println("     POST   /tenants/{tenantId}/users/{userId}/api-keys - Create API key")
        println("     GET    /tenants/{tenantId}/users/{userId}/api-keys - List API keys for user")
        println("     GET    /tenants/{tenantId}/users/{userId}/api-keys/{keyId} - Get API key")
        println("     DELETE /tenants/{tenantId}/users/{userId}/api-keys/{keyId} - Revoke API key")
        println("   Authentication:")
        println("     POST   /auth/login - Login")
        println("     POST   /auth/logout - Logout")
        println("     POST   /auth/password/change - Change password")
        println("   Permission Management:")
        println("     GET    /tenants/{tenantId}/users/{userId}/permissions - Get permissions")
        println("     POST   /tenants/{tenantId}/users/{userId}/permissions - Grant permissions")
        println("     DELETE /tenants/{tenantId}/users/{userId}/permissions - Revoke permissions")
        println("   Health:")
        println("     GET    /health - Health check")
    }
}

data class RateBucket(
    val count: AtomicInteger,
    val resetAt: Long
)
