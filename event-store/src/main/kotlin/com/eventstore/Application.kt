package com.eventstore

import com.eventstore.domain.ports.outbound.ConsumerFactory
import com.eventstore.domain.ports.outbound.*
import com.eventstore.domain.services.consumer.RegisterConsumerService
import com.eventstore.domain.services.consumer.UnregisterConsumerService
import com.eventstore.domain.services.bootstrap.BootstrapService
import com.eventstore.domain.services.event.GetEventsService
import com.eventstore.domain.services.event.PublishEventsService
import com.eventstore.domain.services.health.GetHealthStatusService
import com.eventstore.domain.services.tenant.CreateTenantService
import com.eventstore.domain.services.tenant.DeleteTenantService
import com.eventstore.domain.services.tenant.GetTenantService
import com.eventstore.domain.services.tenant.UpdateTenantService
import com.eventstore.domain.services.namespace.CreateNamespaceService
import com.eventstore.domain.services.namespace.DeleteNamespaceService
import com.eventstore.domain.services.namespace.GetNamespaceService
import com.eventstore.domain.services.namespace.UpdateNamespaceService
import com.eventstore.domain.services.namespace.CreateNamespaceRequest
import com.eventstore.domain.services.auth.AuthenticationService
import com.eventstore.domain.services.topic.CreateTopicService
import com.eventstore.domain.services.topic.GetTopicsService
import com.eventstore.domain.services.topic.UpdateTopicSchemasService
import com.eventstore.domain.services.user.AssignUserToTenantService
import com.eventstore.domain.services.user.ChangePasswordService
import com.eventstore.domain.services.user.CreateUserService
import com.eventstore.domain.services.user.DeleteUserService
import com.eventstore.domain.services.user.GetUserService
import com.eventstore.domain.services.user.RemoveUserFromTenantService
import com.eventstore.domain.services.user.UpdateUserService
import com.eventstore.infrastructure.background.DispatcherManager
import com.eventstore.infrastructure.bootstrap.BootstrapServiceImpl
import com.eventstore.infrastructure.external.JsonSchemaValidator
import com.eventstore.infrastructure.factories.ConsumerFactoryImpl
import com.eventstore.infrastructure.persistence.FileSystemEventRepository
import com.eventstore.infrastructure.persistence.FileSystemTopicRepository
import com.eventstore.infrastructure.persistence.InMemoryConsumerRepository
import com.eventstore.infrastructure.auth.SessionManager
import com.eventstore.infrastructure.projections.InMemoryNamespaceRepository
import com.eventstore.infrastructure.projections.InMemoryUserRepository
import com.eventstore.infrastructure.projections.InMemoryTenantRepository
import com.eventstore.infrastructure.projections.TenantProjectionService
import com.eventstore.infrastructure.projections.NamespaceProjectionService
import com.eventstore.infrastructure.projections.UserProjectionService
import com.eventstore.domain.tenants.SystemTopics
import com.eventstore.domain.services.consumer.InMemoryConsumerRegistrationRequest
import com.eventstore.interfaces.http.routes.consumerRoutes
import com.eventstore.interfaces.http.routes.eventRoutes
import com.eventstore.interfaces.http.routes.healthRoutes
import com.eventstore.interfaces.http.routes.namespaceRoutes
import com.eventstore.interfaces.http.routes.tenantRoutes
import com.eventstore.interfaces.http.routes.topicRoutes
import com.eventstore.interfaces.http.routes.userRoutes
import com.eventstore.interfaces.http.routes.authRoutes
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
    }

    // Initialize infrastructure components
    val dataDir = Paths.get(config.dataDir)
    val configDir = Paths.get(config.configDir)

    val topicRepository: TopicRepository = FileSystemTopicRepository(configDir, objectMapper)
    val eventRepository: EventRepository = FileSystemEventRepository(dataDir, objectMapper)
    val consumerRepository: ConsumerRepository = InMemoryConsumerRepository()
    val schemaValidator: SchemaValidator = JsonSchemaValidator(objectMapper)
    val consumerFactory: ConsumerFactory = ConsumerFactoryImpl()
    val bootstrapService: BootstrapService = BootstrapServiceImpl(
        eventRepository = eventRepository,
        topicRepository = topicRepository
    )

    // Initialize dispatcher manager
    val dispatcherManager = DispatcherManager(
        consumerRepository = consumerRepository,
        eventRepository = eventRepository
    )

    // Consumer services (used for projection registration and HTTP routes)
    val registerConsumerService =
        RegisterConsumerService(consumerRepository, topicRepository, consumerFactory, dispatcherManager)
    val unregisterConsumerService = UnregisterConsumerService(consumerRepository)

    // Create application scope for lifecycle management
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Run bootstrap before loading schemas and dispatchers
    runBlocking {
        bootstrapService.run()
    }

    // Load existing schemas on startup (non-blocking)
    environment.monitor.subscribe(ApplicationStarted) {
        applicationScope.launch {
            val topics = topicRepository.getAllTopics()
            topics.forEach { topic ->
                schemaValidator.registerSchemas(topic.name, topic.schemas)
            }
        }
    }

    // Tenant projection consumer registration
    val tenantProjectionRepository = InMemoryTenantRepository()
    val tenantProjectionService = TenantProjectionService(tenantProjectionRepository)
    val tenantTopic = SystemTopics.qualified(SystemTopics.TENANTS_TOPIC)

    val namespaceProjectionRepository = InMemoryNamespaceRepository()
    val namespaceProjectionService = NamespaceProjectionService(namespaceProjectionRepository)
    val namespaceTopic = SystemTopics.qualified(SystemTopics.NAMESPACES_TOPIC)

    val userProjectionRepository = InMemoryUserRepository()
    val userProjectionService = UserProjectionService(userProjectionRepository)
    val usersTopic = SystemTopics.qualified(SystemTopics.USERS_TOPIC)

    runBlocking {
        registerConsumerService.execute(
            InMemoryConsumerRegistrationRequest(
                handler = { events -> tenantProjectionService.handleEvents(events) },
                topics = mapOf(tenantTopic to null)
            )
        )
        registerConsumerService.execute(
            InMemoryConsumerRegistrationRequest(
                handler = { events -> namespaceProjectionService.handleEvents(events) },
                topics = mapOf(namespaceTopic to null)
            )
        )
        registerConsumerService.execute(
            InMemoryConsumerRegistrationRequest(
                handler = { events -> userProjectionService.handleEvents(events) },
                topics = mapOf(usersTopic to null)
            )
        )
    }

    // Initialize domain services
    val createTopicService = CreateTopicService(topicRepository, schemaValidator)
    val getTopicsService = GetTopicsService(topicRepository)
    val updateTopicSchemasService = UpdateTopicSchemasService(topicRepository, schemaValidator)
    val publishEventsService =
        PublishEventsService(topicRepository, eventRepository, schemaValidator, dispatcherManager)
    val getEventsService = GetEventsService(eventRepository, topicRepository)
    val getHealthStatusService = GetHealthStatusService(consumerRepository) {
        dispatcherManager.getRunningDispatchers()
    }
    val createTenantService = CreateTenantService(eventRepository, topicRepository, tenantProjectionService, config)
    val getTenantService = GetTenantService(tenantProjectionService)
    val updateTenantService = UpdateTenantService(eventRepository, topicRepository, tenantProjectionService, config)
    val deleteTenantService = DeleteTenantService(eventRepository, topicRepository, tenantProjectionService, config)
    val createNamespaceService = CreateNamespaceService(eventRepository, topicRepository, tenantProjectionService, namespaceProjectionService, config)
    val getNamespaceService = GetNamespaceService(namespaceProjectionService)
    val updateNamespaceService = UpdateNamespaceService(eventRepository, topicRepository, tenantProjectionService, namespaceProjectionService, config)
    val deleteNamespaceService = DeleteNamespaceService(eventRepository, topicRepository, tenantProjectionService, namespaceProjectionService, config)
    val createUserService = CreateUserService(eventRepository, topicRepository, tenantProjectionService, userProjectionService, config)
    val getUserService = GetUserService(userProjectionService)
    val updateUserService = UpdateUserService(eventRepository, topicRepository, userProjectionService, config)
    val deleteUserService = DeleteUserService(eventRepository, topicRepository, userProjectionService, config)
    val changePasswordService = ChangePasswordService(eventRepository, topicRepository, userProjectionService, config)
    val assignUserToTenantService = AssignUserToTenantService(eventRepository, topicRepository, tenantProjectionService, userProjectionService, config)
    val removeUserFromTenantService = RemoveUserFromTenantService(eventRepository, topicRepository, userProjectionService, config)
    val sessionManager = SessionManager()
    val authenticationService = AuthenticationService(userProjectionService, sessionManager)

    // Ensure default/default namespace exists for legacy endpoints when multi-tenant is enabled
    if (config.multiTenantEnabled) {
        runBlocking {
            if (tenantProjectionService.tenantExists("default") &&
                !namespaceProjectionService.namespaceExists("default", "default")) {
                runCatching {
                    createNamespaceService.execute(
                        CreateNamespaceRequest(
                            tenantId = "default",
                            namespaceId = "default",
                            name = "Default"
                        )
                    )
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

    // Configure routing
    routing {
        topicRoutes(createTopicService, getTopicsService, updateTopicSchemasService, dispatcherManager)
        eventRoutes(publishEventsService, getEventsService)
        consumerRoutes(registerConsumerService, unregisterConsumerService, consumerRepository)
        tenantRoutes(createTenantService, getTenantService, updateTenantService, deleteTenantService)
        namespaceRoutes(createNamespaceService, getNamespaceService, updateNamespaceService, deleteNamespaceService)
        userRoutes(createUserService, getUserService, updateUserService, deleteUserService, assignUserToTenantService, removeUserFromTenantService)
        authRoutes(authenticationService, changePasswordService)
        healthRoutes(getHealthStatusService)
    }

    // Graceful shutdown
    environment.monitor.subscribe(ApplicationStopped) {
        runBlocking {
            dispatcherManager.stopAllDispatchers()
            applicationScope.cancel()
        }
    }

    // Startup logging
    println("🚀 Event Store starting on port ${config.port}")
    println("📁 Data directory: ${config.dataDir}")
    println("📁 Config directory: ${config.configDir}")
    println("📖 API Endpoints:")
    println("   POST /topics - Create a topic with schemas")
    println("   GET  /topics - List all topics")
    println("   GET  /topics/{topic} - Get topic details")
    println("   PUT  /topics/{topic} - Update schemas")
    println("   POST /events - Publish events")
    println("   GET  /topics/{topic}/events - Retrieve events")
    println("   POST /consumers/register - Register a consumer")
    println("   GET  /consumers - List consumers")
    println("   DELETE /consumers/{id} - Unregister a consumer")
    println("   GET  /health - Health check")
}

data class RateBucket(
    val count: AtomicInteger,
    val resetAt: Long
)

