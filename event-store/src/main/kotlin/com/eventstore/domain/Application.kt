package com.eventstore.domain

import com.eventstore.Config
import com.eventstore.domain.ports.outbound.*
import com.eventstore.domain.services.SystemEventPublisher
import com.eventstore.domain.services.apikey.*
import com.eventstore.domain.services.consumer.ConsumerRegistrationRequest
import com.eventstore.domain.services.consumer.InMemoryConsumerRegistrationRequest
import com.eventstore.domain.services.consumer.RegisterConsumerService
import com.eventstore.domain.services.consumer.UnregisterConsumerService
import com.eventstore.domain.services.event.EventRequest
import com.eventstore.domain.services.event.GetEventsService
import com.eventstore.domain.services.event.PublishEventsService
import com.eventstore.domain.services.health.GetHealthStatusService
import com.eventstore.domain.services.health.HealthStatus
import com.eventstore.domain.services.namespace.*
import com.eventstore.domain.services.tenant.*
import com.eventstore.domain.services.topic.CreateTopicService
import com.eventstore.domain.services.user.CreateUserRequest
import com.eventstore.domain.services.user.CreateUserService
import com.eventstore.domain.tenants.SystemTopics
import com.eventstore.infrastructure.background.SyncDispatcherManager
import com.eventstore.infrastructure.bootstrap.BootstrapServiceImpl
import com.eventstore.infrastructure.external.JsonSchemaValidator
import com.eventstore.infrastructure.factories.ConsumerFactoryImpl
import com.eventstore.infrastructure.persistence.InMemoryApiKeyRepository
import com.eventstore.infrastructure.persistence.InMemoryConsumerRepository
import com.eventstore.infrastructure.persistence.InMemoryEventRepository
import com.eventstore.infrastructure.persistence.InMemoryTopicRepository
import com.eventstore.infrastructure.projections.*
import kotlinx.coroutines.runBlocking
import java.time.Instant

class Application(
    val bootstrap: Boolean = false,
    val tenantRepository: TenantRepository = InMemoryTenantRepository(),
    val namespaceRepository: NamespaceRepository = InMemoryNamespaceRepository(),
    val topicRepository: TopicRepository = InMemoryTopicRepository(),
    val eventRepository: EventRepository = InMemoryEventRepository(),
    val consumerRepository: ConsumerRepository = InMemoryConsumerRepository(),
    val userRepository: UserRepository = InMemoryUserRepository(),
    val permissionRepository: PermissionRepository = InMemoryPermissionRepository(),
    val apiKeyRepository: ApiKeyRepository = InMemoryApiKeyRepository(),
    val consumerFactory: ConsumerFactory = ConsumerFactoryImpl(),
    val schemaValidator: SchemaValidator = JsonSchemaValidator(),
    val config: Config = Config.fromEnvironment()
) {
//    val dispatcherManager = AsyncDispatcherManager(
//        consumerRepository = consumerRepository,
//        eventRepository = eventRepository
//    )

    val dispatcherManager = SyncDispatcherManager(
        consumerRepository = consumerRepository,
        eventRepository = eventRepository
    )

    val tenantProjectionService = com.eventstore.infrastructure.projections.TenantProjectionService(tenantRepository)
    val namespaceProjectionService =
        com.eventstore.infrastructure.projections.NamespaceProjectionService(namespaceRepository)
    val userProjectionService = UserProjectionService(userRepository)
    val permissionProjectionService = PermissionProjectionService(permissionRepository)

    val systemEventPublisher: SystemEventPublisher =
        SystemEventPublisher(eventRepository, topicRepository, schemaValidator, dispatcherManager)

    private val createTenantService: CreateTenantService =
        CreateTenantService(tenantProjectionService, config, systemEventPublisher)

    private val deleteTenantService: DeleteTenantService =
        DeleteTenantService(tenantProjectionService, config, systemEventPublisher)

    private val updateTenantService: UpdateTenantService =
        UpdateTenantService(tenantProjectionService, config, systemEventPublisher)

    val getTenantService: GetTenantService =
        GetTenantService(tenantProjectionService)

    private val createNamespaceService: CreateNamespaceService =
        CreateNamespaceService(tenantProjectionService, namespaceProjectionService, config, systemEventPublisher)

    private val deleteNamespaceService: DeleteNamespaceService =
        DeleteNamespaceService(tenantProjectionService, namespaceProjectionService, config, systemEventPublisher)

    private val updateNamespaceService: UpdateNamespaceService =
        UpdateNamespaceService(tenantProjectionService, namespaceProjectionService, config, systemEventPublisher)

    val getNamespaceService: GetNamespaceService =
        GetNamespaceService(namespaceProjectionService)

    private val createTopicService: CreateTopicService =
        CreateTopicService(topicRepository, schemaValidator, tenantProjectionService, namespaceProjectionService)

    private val publishEventsService: PublishEventsService =
        PublishEventsService(topicRepository, eventRepository, schemaValidator, dispatcherManager)

    private val getEventsService: GetEventsService =
        GetEventsService(eventRepository, topicRepository)

    private val getHealthStatusService: GetHealthStatusService =
        GetHealthStatusService(consumerRepository) {
            dispatcherManager.getRunningDispatchers()
        }

    private val registerConsumerService: RegisterConsumerService =
        RegisterConsumerService(consumerRepository, topicRepository, consumerFactory, dispatcherManager)

    private val unregisterConsumerService: UnregisterConsumerService =
        UnregisterConsumerService(consumerRepository)

    private val createApiKeyService: CreateApiKeyService =
        CreateApiKeyService(apiKeyRepository, userProjectionService)

    val getApiKeyService: GetApiKeyService =
        GetApiKeyService(apiKeyRepository)

    private val revokeApiKeyService: RevokeApiKeyService =
        RevokeApiKeyService(apiKeyRepository)

    private val createUserService: CreateUserService =
        CreateUserService(eventRepository, topicRepository, tenantProjectionService, userProjectionService, config)

    init {
        runBlocking {
            if (bootstrap) {
                val objectMapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
                BootstrapServiceImpl(
                    eventRepository,
                    topicRepository,
                    schemaValidator,
                    objectMapper,
                    apiKeyRepository,
                    null,
                    false
                ).run()
            }

            // Register system consumers for projection services
            // These consume from system topics in $system/$management namespace
            registerConsumerService.execute(
                InMemoryConsumerRegistrationRequest(
                    handler = { events -> tenantProjectionService.handleEvents(events) },
                    topics = mapOf(SystemTopics.TENANTS_TOPIC to null)
                ),
                tenantName = SystemTopics.SYSTEM_TENANT_ID,
                namespaceName = SystemTopics.MANAGEMENT_NAMESPACE_ID
            )
            registerConsumerService.execute(
                InMemoryConsumerRegistrationRequest(
                    handler = { events -> namespaceProjectionService.handleEvents(events) },
                    topics = mapOf(SystemTopics.NAMESPACES_TOPIC to null)
                ),
                tenantName = SystemTopics.SYSTEM_TENANT_ID,
                namespaceName = SystemTopics.MANAGEMENT_NAMESPACE_ID
            )
            registerConsumerService.execute(
                InMemoryConsumerRegistrationRequest(
                    handler = { events -> userProjectionService.handleEvents(events) },
                    topics = mapOf(SystemTopics.USERS_TOPIC to null)
                ),
                tenantName = SystemTopics.SYSTEM_TENANT_ID,
                namespaceName = SystemTopics.MANAGEMENT_NAMESPACE_ID
            )
            registerConsumerService.execute(
                InMemoryConsumerRegistrationRequest(
                    handler = { events -> permissionProjectionService.handleEvents(events) },
                    topics = mapOf(SystemTopics.PERMISSIONS_TOPIC to null)
                ),
                tenantName = SystemTopics.SYSTEM_TENANT_ID,
                namespaceName = SystemTopics.MANAGEMENT_NAMESPACE_ID
            )
        }
    }

    suspend fun createTenant(
        name: String,
        quota: Quota? = null,
        metadata: Map<String, Any> = emptyMap(),
        createdBy: String = "system"
    ): Tenant =
        createTenantService.execute(
            CreateTenantRequest(
                name = name,
                quota = quota,
                metadata = metadata,
                createdBy = createdBy
            )
        )

    suspend fun deleteTenant(
        tenantName: String,
        deletedBy: String = "system",
        reason: String? = null
    ): Boolean =
        deleteTenantService.execute(
            DeleteTenantRequest(
                tenantName = tenantName,
                deletedBy = deletedBy,
                reason = reason
            )
        )

    suspend fun updateTenant(
        tenantName: String,
        name: String? = null,
        quota: Quota? = null,
        metadata: Map<String, Any>? = null,
        updatedBy: String = "system"
    ): Tenant =
        updateTenantService.execute(
            UpdateTenantRequest(
                tenantName = tenantName,
                name = name,
                quota = quota,
                metadata = metadata,
                updatedBy = updatedBy
            )
        )

    suspend fun getTenant(tenantName: String): Tenant? =
        getTenantService.getTenant(tenantName)

    suspend fun listTenants(): List<Tenant> =
        getTenantService.listTenants()

    suspend fun createNamespace(
        tenantName: String,
        namespaceName: String = "default",
        description: String? = null,
        metadata: Map<String, Any> = emptyMap(),
        createdBy: String = "system"
    ) =
        createNamespaceService.execute(
            CreateNamespaceRequest(
                tenantName = tenantName,
                name = namespaceName,
                description = description,
                metadata = metadata,
                createdBy = createdBy
            )
        )

    suspend fun deleteNamespace(
        tenantName: String,
        namespaceName: String,
        deletedBy: String = "system",
        reason: String? = null
    ): Boolean =
        deleteNamespaceService.execute(
            DeleteNamespaceRequest(
                tenantName = tenantName,
                namespaceName = namespaceName,
                deletedBy = deletedBy,
                reason = reason
            )
        )

    suspend fun updateNamespace(
        tenantName: String,
        namespaceName: String,
        name: String? = null,
        description: String? = null,
        metadata: Map<String, Any>? = null,
        updatedBy: String = "system"
    ): Namespace =
        updateNamespaceService.execute(
            UpdateNamespaceRequest(
                tenantName = tenantName,
                namespaceName = namespaceName,
                name = name,
                description = description,
                metadata = metadata,
                updatedBy = updatedBy
            )
        )

    suspend fun getNamespace(tenantName: String, namespaceName: String): Namespace? =
        getNamespaceService.getNamespace(tenantName, namespaceName)

    suspend fun listNamespaces(tenantName: String): List<Namespace> =
        getNamespaceService.listNamespaces(tenantName)

    suspend fun createTopic(
        name: String,
        schemas: List<Schema>,
        tenantName: String = "default",
        namespaceName: String = "default"
    ): Topic =
        createTopicService.execute(name, schemas, tenantName, namespaceName)

    suspend fun createUser(
        email: String,
        name: String,
        password: String,
        status: UserStatus = UserStatus.ACTIVE,
        createdBy: String = "system",
        metadata: Map<String, Any> = emptyMap(),
        primaryTenantId: String? = null
    ): User =
        createUserService.execute(
            CreateUserRequest(
                email = email,
                name = name,
                password = password,
                status = status,
                createdBy = createdBy,
                metadata = metadata,
                primaryTenantId = primaryTenantId
            )
        )

    suspend fun createApiKey(
        userId: String,
        name: String,
        description: String? = null,
        expiresAt: Instant? = null,
        scopes: Set<String>? = null
    ): Pair<ApiKey, String> =
        createApiKeyService.execute(
            CreateApiKeyRequest(
                userId = userId,
                name = name,
                description = description,
                expiresAt = expiresAt,
                scopes = scopes
            )
        )

    suspend fun getApiKey(keyId: String): ApiKey? =
        getApiKeyService.getById(keyId)

    suspend fun getApiKeysByUserId(userId: String): List<ApiKey> =
        getApiKeyService.getByUserId(userId)

    suspend fun revokeApiKey(keyId: String) =
        revokeApiKeyService.execute(RevokeApiKeyRequest(keyId = keyId))

    suspend fun registerConsumer(
        request: ConsumerRegistrationRequest,
        tenantName: String,
        namespaceName: String
    ): String =
        registerConsumerService.execute(request, tenantName, namespaceName)

    suspend fun unregisterConsumer(
        consumerId: String,
        tenantName: String,
        namespaceName: String
    ): Boolean =
        unregisterConsumerService.execute(consumerId, tenantName, namespaceName)

    suspend fun publishEvents(
        requests: List<EventRequest>
    ): List<String> =
        publishEventsService.execute(requests)

    suspend fun getEvents(
        topic: String,
        sinceEventId: String? = null,
        date: String? = null,
        limit: Int? = null,
        tenantName: String = "default",
        namespaceName: String = "default"
    ): List<Event> =
        getEventsService.execute(topic, sinceEventId, date, limit, tenantName, namespaceName)

    suspend fun getHealthStatus(): HealthStatus =
        getHealthStatusService.execute()
}