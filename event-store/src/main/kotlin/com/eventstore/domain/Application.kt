package com.eventstore.domain

import com.eventstore.Config
import com.eventstore.domain.ports.outbound.ApiKeyRepository
import com.eventstore.domain.ports.outbound.ConsumerFactory
import com.eventstore.domain.ports.outbound.ConsumerRepository
import com.eventstore.domain.ports.outbound.EventRepository
import com.eventstore.domain.ports.outbound.NamespaceRepository
import com.eventstore.domain.ports.outbound.PermissionRepository
import com.eventstore.domain.ports.outbound.SchemaValidator
import com.eventstore.domain.ports.outbound.TenantRepository
import com.eventstore.domain.ports.outbound.TopicRepository
import com.eventstore.domain.ports.outbound.UserRepository
import com.eventstore.domain.services.consumer.InMemoryConsumerRegistrationRequest
import com.eventstore.domain.services.consumer.RegisterConsumerService
import com.eventstore.domain.services.namespace.CreateNamespaceRequest
import com.eventstore.domain.services.namespace.CreateNamespaceService
import com.eventstore.domain.services.namespace.DeleteNamespaceRequest
import com.eventstore.domain.services.namespace.DeleteNamespaceService
import com.eventstore.domain.services.namespace.GetNamespaceService
import com.eventstore.domain.services.namespace.UpdateNamespaceRequest
import com.eventstore.domain.services.namespace.UpdateNamespaceService
import com.eventstore.domain.services.tenant.CreateTenantRequest
import com.eventstore.domain.services.tenant.CreateTenantService
import com.eventstore.domain.services.tenant.DeleteTenantRequest
import com.eventstore.domain.services.tenant.DeleteTenantService
import com.eventstore.domain.services.tenant.GetTenantService
import com.eventstore.domain.services.tenant.UpdateTenantRequest
import com.eventstore.domain.services.tenant.UpdateTenantService
import com.eventstore.domain.services.topic.CreateTopicService
import com.eventstore.domain.tenants.SystemTopics
import com.eventstore.infrastructure.background.AsyncDispatcherManager
import com.eventstore.infrastructure.background.SyncDispatcherManager
import com.eventstore.infrastructure.bootstrap.BootstrapServiceImpl
import com.eventstore.infrastructure.external.JsonSchemaValidator
import com.eventstore.infrastructure.factories.ConsumerFactoryImpl
import com.eventstore.infrastructure.persistence.FileSystemApiKeyRepository
import com.eventstore.infrastructure.persistence.InMemoryApiKeyRepository
import com.eventstore.infrastructure.persistence.InMemoryConsumerRepository
import com.eventstore.infrastructure.persistence.InMemoryEventRepository
import com.eventstore.infrastructure.persistence.InMemoryTopicRepository
import com.eventstore.infrastructure.projections.InMemoryNamespaceRepository
import com.eventstore.infrastructure.projections.InMemoryPermissionRepository
import com.eventstore.infrastructure.projections.InMemoryTenantRepository
import com.eventstore.infrastructure.projections.InMemoryUserRepository
import com.eventstore.infrastructure.projections.PermissionProjectionService
import com.eventstore.infrastructure.projections.UserProjectionService
import kotlinx.coroutines.runBlocking

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
    val namespaceProjectionService = com.eventstore.infrastructure.projections.NamespaceProjectionService(namespaceRepository)
    val userProjectionService = UserProjectionService(userRepository)
    val permissionProjectionService = PermissionProjectionService(permissionRepository)

    val createTenantService: CreateTenantService =
        CreateTenantService(eventRepository, topicRepository, tenantProjectionService, config, dispatcherManager)

    val deleteTenantService: DeleteTenantService =
        DeleteTenantService(eventRepository, topicRepository, tenantProjectionService, config, dispatcherManager)

    val updateTenantService: UpdateTenantService =
        UpdateTenantService(eventRepository, topicRepository, tenantProjectionService, config, dispatcherManager)

    val getTenantService: GetTenantService =
        GetTenantService(tenantProjectionService)

    val createNamespaceService: CreateNamespaceService =
        CreateNamespaceService(eventRepository, topicRepository, tenantProjectionService, namespaceProjectionService, config, dispatcherManager)

    val deleteNamespaceService: DeleteNamespaceService =
        DeleteNamespaceService(eventRepository, topicRepository, tenantProjectionService, namespaceProjectionService, config, dispatcherManager)

    val updateNamespaceService: UpdateNamespaceService =
        UpdateNamespaceService(eventRepository, topicRepository, tenantProjectionService, namespaceProjectionService, config, dispatcherManager)

    val getNamespaceService: GetNamespaceService =
        GetNamespaceService(namespaceProjectionService)

    val createTopicService: CreateTopicService =
        CreateTopicService(topicRepository, schemaValidator, tenantProjectionService, namespaceProjectionService)

    val registerConsumerService: RegisterConsumerService =
        RegisterConsumerService(consumerRepository,  topicRepository, consumerFactory, dispatcherManager)

    init {
        runBlocking {
            if (bootstrap) {
                BootstrapServiceImpl(eventRepository, topicRepository, apiKeyRepository, null, false).run()
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
        namespaceName: String = "default"
    ) =
        createNamespaceService.execute(CreateNamespaceRequest(tenantName, namespaceName))

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
}