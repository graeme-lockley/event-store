package com.eventstore.domain

import com.eventstore.Config
import com.eventstore.domain.events.PermissionGrantedEvent
import com.eventstore.domain.events.PermissionRevokedEvent
import com.eventstore.domain.ports.outbound.*
import com.eventstore.domain.services.SystemEventPublisher
import com.eventstore.domain.services.apikey.*
import com.eventstore.domain.services.auth.ResourceResolverImpl
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
import com.eventstore.domain.services.permission.*
import com.eventstore.domain.services.tenant.*
import com.eventstore.domain.services.topic.CreateTopicService
import com.eventstore.domain.services.topic.GetTopicsService
import com.eventstore.domain.services.topic.UpdateTopicSchemasService
import com.eventstore.domain.services.user.*
import com.eventstore.domain.tenants.SystemTopics
import com.eventstore.domain.ports.outbound.EventDispatcher
import com.eventstore.infrastructure.background.AsyncDispatcherManager
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
import java.util.UUID

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
    val config: Config = Config.fromEnvironment(),
    providedDispatcherManager: EventDispatcher? = null
) {
    val dispatcherManager: EventDispatcher = providedDispatcherManager ?: SyncDispatcherManager(
        consumerRepository = consumerRepository,
        eventRepository = eventRepository
    )

    val tenantProjectionService = TenantProjectionService(tenantRepository)
    val namespaceProjectionService =
        NamespaceProjectionService(namespaceRepository, tenantProjectionService)
    val userProjectionService = UserProjectionService(userRepository)
    val permissionProjectionService = PermissionProjectionService(permissionRepository)
    val apiKeyProjectionService = ApiKeyProjectionService(apiKeyRepository)

    val resourceResolver: ResourceResolverImpl =
        ResourceResolverImpl(tenantProjectionService, namespaceProjectionService, topicRepository)

    val systemEventPublisher: SystemEventPublisher =
        SystemEventPublisher(eventRepository, topicRepository, schemaValidator, dispatcherManager)

    private val createTenantService: CreateTenantService =
        CreateTenantService(tenantProjectionService, config, systemEventPublisher)

    private val deleteTenantService: DeleteTenantService =
        DeleteTenantService(tenantProjectionService, config, systemEventPublisher)

    private val tenantUsageService: TenantUsageService =
        TenantUsageService(topicRepository, namespaceProjectionService, consumerRepository, userProjectionService)

    private val updateTenantService: UpdateTenantService =
        UpdateTenantService(tenantProjectionService, tenantUsageService, config, systemEventPublisher)

    val getTenantService: GetTenantService =
        GetTenantService(tenantProjectionService)

    private val createNamespaceService: CreateNamespaceService =
        CreateNamespaceService(tenantProjectionService, namespaceProjectionService, tenantUsageService, config, systemEventPublisher)

    private val deleteNamespaceService: DeleteNamespaceService =
        DeleteNamespaceService( namespaceProjectionService, config, systemEventPublisher)

    private val updateNamespaceService: UpdateNamespaceService =
        UpdateNamespaceService( namespaceProjectionService, config, systemEventPublisher)

    val getNamespaceService: GetNamespaceService =
        GetNamespaceService(namespaceProjectionService)

    private val createTopicService: CreateTopicService =
        CreateTopicService(topicRepository, schemaValidator, tenantProjectionService, namespaceProjectionService)

    private val getTopicsService: GetTopicsService =
        GetTopicsService(topicRepository)

    private val updateTopicSchemasService: UpdateTopicSchemasService =
        UpdateTopicSchemasService(topicRepository, schemaValidator)

    private val publishEventsService: PublishEventsService =
        PublishEventsService(topicRepository, eventRepository, schemaValidator, dispatcherManager)

    private val getEventsService: GetEventsService =
        GetEventsService(eventRepository, topicRepository)

    private val getHealthStatusService: GetHealthStatusService =
        GetHealthStatusService(consumerRepository) {
            when (dispatcherManager) {
                is SyncDispatcherManager -> dispatcherManager.getRunningDispatchers()
                is AsyncDispatcherManager -> dispatcherManager.getRunningDispatchers()
                else -> emptyList()
            }
        }

    private val registerConsumerService: RegisterConsumerService =
        RegisterConsumerService(consumerRepository, topicRepository, consumerFactory, dispatcherManager)

    private val unregisterConsumerService: UnregisterConsumerService =
        UnregisterConsumerService(consumerRepository)

    private val createApiKeyService: CreateApiKeyService =
        CreateApiKeyService(userProjectionService, config, systemEventPublisher)

    val getApiKeyService: GetApiKeyService =
        GetApiKeyService(apiKeyProjectionService)

    private val revokeApiKeyService: RevokeApiKeyService =
        RevokeApiKeyService(apiKeyProjectionService, config, systemEventPublisher)

    private val createUserService: CreateUserService =
        CreateUserService(tenantProjectionService, userProjectionService, config, systemEventPublisher)

    private val getUserService: GetUserService =
        GetUserService(userProjectionService)

    private val updateUserService: UpdateUserService =
        UpdateUserService(userProjectionService, config, systemEventPublisher)

    private val deleteUserService: DeleteUserService =
        DeleteUserService(userProjectionService, config, systemEventPublisher)

    private val assignUserToTenantService: AssignUserToTenantService =
        AssignUserToTenantService(tenantProjectionService, userProjectionService, config, systemEventPublisher)

    private val removeUserFromTenantService: RemoveUserFromTenantService =
        RemoveUserFromTenantService(userProjectionService, config, systemEventPublisher)

    private val changePasswordService: ChangePasswordService =
        ChangePasswordService(userProjectionService, config, systemEventPublisher)

    private val grantPermissionService: GrantPermissionService =
        GrantPermissionService(
            resourceResolver,
            config,
            systemEventPublisher
        )

    private val revokePermissionService: RevokePermissionService =
        RevokePermissionService(resourceResolver, config, systemEventPublisher)

    private val getPermissionsService: GetPermissionsService =
        GetPermissionsService(permissionProjectionService, resourceResolver)

    init {
        runBlocking {
            if (bootstrap) {
                val objectMapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
                BootstrapServiceImpl(
                    eventRepository,
                    topicRepository,
                    schemaValidator,
                    objectMapper
                ).run()
            }

            // Register system consumers for projection services
            // These consume from system topics in $system/$management namespace
            registerConsumerService.execute(
                InMemoryConsumerRegistrationRequest(
                    handler = { events -> tenantProjectionService.handleEvents(events) },
                    topics = mapOf(SystemTopics.TENANTS_TOPIC_NAME to null)
                ),
                tenantName = SystemTopics.SYSTEM_TENANT_NAME,
                namespaceName = SystemTopics.MANAGEMENT_NAMESPACE_NAME
            )
            registerConsumerService.execute(
                InMemoryConsumerRegistrationRequest(
                    handler = { events -> namespaceProjectionService.handleEvents(events) },
                    topics = mapOf(SystemTopics.NAMESPACES_TOPIC_NAME to null)
                ),
                tenantName = SystemTopics.SYSTEM_TENANT_NAME,
                namespaceName = SystemTopics.MANAGEMENT_NAMESPACE_NAME
            )
            registerConsumerService.execute(
                InMemoryConsumerRegistrationRequest(
                    handler = { events -> userProjectionService.handleEvents(events) },
                    topics = mapOf(SystemTopics.USERS_TOPIC_NAME to null)
                ),
                tenantName = SystemTopics.SYSTEM_TENANT_NAME,
                namespaceName = SystemTopics.MANAGEMENT_NAMESPACE_NAME
            )
            registerConsumerService.execute(
                InMemoryConsumerRegistrationRequest(
                    handler = { events -> permissionProjectionService.handleEvents(events) },
                    topics = mapOf(SystemTopics.PERMISSIONS_TOPIC_NAME to null)
                ),
                tenantName = SystemTopics.SYSTEM_TENANT_NAME,
                namespaceName = SystemTopics.MANAGEMENT_NAMESPACE_NAME
            )
            registerConsumerService.execute(
                InMemoryConsumerRegistrationRequest(
                    handler = { events -> apiKeyProjectionService.handleEvents(events) },
                    topics = mapOf(SystemTopics.API_KEYS_TOPIC_NAME to null)
                ),
                tenantName = SystemTopics.SYSTEM_TENANT_NAME,
                namespaceName = SystemTopics.MANAGEMENT_NAMESPACE_NAME
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
        tenantId: UUID,
        deletedBy: String = "system",
        reason: String? = null
    ): Boolean =
        deleteTenantService.execute(
            DeleteTenantRequest(
                tenantId = tenantId,
                deletedBy = deletedBy,
                reason = reason
            )
        )

    suspend fun updateTenant(
        tenantId: UUID,
        name: String? = null,
        quota: Quota? = null,
        metadata: Map<String, Any>? = null,
        updatedBy: String = "system"
    ): Tenant =
        updateTenantService.execute(
            UpdateTenantRequest(
                tenantId = tenantId,
                name = name,
                quota = quota,
                metadata = metadata,
                updatedBy = updatedBy
            )
        )

    suspend fun getTenant(tenantName: String): Tenant? =
        getTenantService.getTenantByName(tenantName)

    suspend fun listTenants(): List<Tenant> =
        getTenantService.listTenants()

    suspend fun createNamespace(
        tenantId: UUID,
        namespaceName: String = "default",
        description: String? = null,
        metadata: Map<String, Any> = emptyMap(),
        createdBy: String = "system"
    ) =
        createNamespaceService.execute(
            CreateNamespaceRequest(
                tenantId = tenantId,
                name = namespaceName,
                description = description,
                metadata = metadata,
                createdBy = createdBy
            )
        )

    suspend fun deleteNamespace(
        namespaceId: UUID,
        deletedBy: String = "system",
        reason: String? = null
    ): Boolean =
        deleteNamespaceService.execute(
            DeleteNamespaceRequest(
                namespaceId = namespaceId,
                deletedBy = deletedBy,
                reason = reason
            )
        )

    suspend fun updateNamespace(
        namespaceId: UUID,
        name: String? = null,
        description: String? = null,
        metadata: Map<String, Any>? = null,
        updatedBy: String = "system"
    ): Namespace =
        updateNamespaceService.execute(
            UpdateNamespaceRequest(
                namespaceId = namespaceId,
                name = name,
                description = description,
                metadata = metadata,
                updatedBy = updatedBy
            )
        )

    suspend fun getNamespace(namespaceId: UUID): Namespace? =
        getNamespaceService.getNamespace(namespaceId)

    suspend fun getNamespaceByName(tenantName: String, namespaceName: String): Namespace? =
        getNamespaceService.getNamespaceByName(tenantName, namespaceName)

    suspend fun listNamespaces(tenantId: UUID? = null): List<Namespace> =
        getNamespaceService.listNamespaces(tenantId)

    suspend fun createTopic(
        name: String,
        schemas: List<Schema>,
        tenantName: String = "default",
        namespaceName: String = "default"
    ): Topic {
        val topic = createTopicService.execute(name, schemas, tenantName, namespaceName)
        // Start dispatcher for the topic if using AsyncDispatcherManager
        if (dispatcherManager is AsyncDispatcherManager) {
            dispatcherManager.startDispatcher(topic.name)
        }
        return topic
    }

    suspend fun getTopic(
        topicName: String,
        tenantName: String = "default",
        namespaceName: String = "default"
    ): Topic =
        getTopicsService.get(topicName, tenantName, namespaceName)

    suspend fun listTopics(
        tenantName: String = "default",
        namespaceName: String = "default"
    ): List<Topic> =
        getTopicsService.list(tenantName, namespaceName)

    suspend fun updateTopicSchemas(
        topicName: String,
        schemas: List<Schema>,
        tenantName: String = "default",
        namespaceName: String = "default"
    ): Topic =
        updateTopicSchemasService.execute(topicName, schemas, tenantName, namespaceName)

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

    suspend fun getUserById(userId: String): User? =
        getUserService.getById(userId)

    suspend fun getUserByEmail(email: String): User? =
        getUserService.getByEmail(email)

    suspend fun listUsers(): List<User> =
        getUserService.list()

    suspend fun updateUser(
        userId: String,
        email: String? = null,
        name: String? = null,
        metadata: Map<String, Any>? = null,
        updatedBy: String = "system"
    ): User =
        updateUserService.execute(
            UpdateUserRequest(
                userId = userId,
                email = email,
                name = name,
                metadata = metadata,
                updatedBy = updatedBy
            )
        )

    suspend fun deleteUser(
        userId: String,
        deletedBy: String = "system",
        reason: String? = null
    ): User =
        deleteUserService.execute(
            DeleteUserRequest(
                userId = userId,
                deletedBy = deletedBy,
                reason = reason
            )
        )

    suspend fun assignUserToTenant(
        userId: String,
        tenantId: String,
        role: String? = null,
        isPrimary: Boolean = false,
        assignedBy: String = "system"
    ): Boolean =
        assignUserToTenantService.execute(
            AssignUserRequest(
                userId = userId,
                tenantId = tenantId,
                role = role,
                isPrimary = isPrimary,
                assignedBy = assignedBy
            )
        )

    suspend fun removeUserFromTenant(
        userId: String,
        tenantId: String,
        removedBy: String = "system",
        reason: String? = null
    ): Boolean =
        removeUserFromTenantService.execute(
            RemoveUserTenantRequest(
                userId = userId,
                tenantId = tenantId,
                removedBy = removedBy,
                reason = reason
            )
        )

    suspend fun changePassword(
        userId: String,
        oldPassword: String,
        newPassword: String,
        changedBy: String = "self"
    ): Boolean =
        changePasswordService.execute(
            ChangePasswordRequest(
                userId = userId,
                oldPassword = oldPassword,
                newPassword = newPassword,
                changedBy = changedBy
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

    suspend fun listConsumers(
        tenantName: String,
        namespaceName: String
    ): List<Consumer> =
        consumerRepository.findByTenantAndNamespace(tenantName, namespaceName)

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

    suspend fun grantPermission(request: GrantPermissionRequest): PermissionGrantedEvent =
        grantPermissionService.execute(request)

    suspend fun revokePermission(request: RevokePermissionRequest): PermissionRevokedEvent =
        revokePermissionService.execute(request)

    suspend fun getPermissions(request: GetPermissionsRequest): List<PermissionGrant> =
        getPermissionsService.execute(request)
}