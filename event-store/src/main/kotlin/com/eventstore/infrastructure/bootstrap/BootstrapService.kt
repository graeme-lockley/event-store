package com.eventstore.infrastructure.bootstrap

import com.eventstore.domain.Event
import com.eventstore.domain.EventId
import com.eventstore.domain.Permission
import com.eventstore.domain.PrincipalType
import com.eventstore.domain.ResourceType
import com.eventstore.domain.events.NamespaceCreatedEvent
import com.eventstore.domain.events.NamespaceEventType
import com.eventstore.domain.events.PermissionEventType
import com.eventstore.domain.events.PermissionGrantedEvent
import com.eventstore.domain.events.TenantCreatedEvent
import com.eventstore.domain.events.TenantEventType
import com.eventstore.domain.events.UserCreatedEvent
import com.eventstore.domain.events.UserEventType
import com.eventstore.domain.events.UserTenantAssignedEvent
import com.eventstore.domain.UserStatus
import com.eventstore.domain.ports.outbound.EventRepository
import com.eventstore.domain.ports.outbound.TopicRepository
import com.eventstore.domain.services.bootstrap.BootstrapService
import com.eventstore.domain.tenants.SystemTopics
import org.mindrot.jbcrypt.BCrypt
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

class BootstrapServiceImpl(
    private val eventRepository: EventRepository,
    private val topicRepository: TopicRepository
) : BootstrapService {

    private val logger = LoggerFactory.getLogger(BootstrapServiceImpl::class.java)

    private val systemTenantId = SystemTopics.SYSTEM_TENANT_ID
    private val managementNamespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID

    private val tenantTopicName = SystemTopics.TENANTS_TOPIC
    private val namespaceTopicName = SystemTopics.NAMESPACES_TOPIC
    private val usersTopicName = SystemTopics.USERS_TOPIC
    private val systemTopics = listOf(
        tenantTopicName,
        namespaceTopicName,
        SystemTopics.USERS_TOPIC,
        SystemTopics.PERMISSIONS_TOPIC,
        SystemTopics.API_KEYS_TOPIC
    )

    override suspend fun run() {
        logger.info("Starting bootstrap process")

        ensureSystemTopics()

        val systemTenantExists = eventRepository.getEvents(
            topic = tenantTopicName,
            limit = 1,
            tenantId = systemTenantId,
            namespaceId = managementNamespaceId
        ).isNotEmpty()
        if (systemTenantExists) {
            logger.info("Bootstrap skipped: system tenant already initialized")
            return
        }

        logger.info("System tenant not found. Bootstrapping system tenant and management namespace.")
        bootstrapSystemTenant()
        logger.info("Bootstrap completed")
    }

    private suspend fun ensureSystemTopics() {
        for (topic in systemTopics) {
            if (!topicRepository.topicExists(topic, systemTenantId, managementNamespaceId)) {
                logger.info("Creating system topic: $topic")
                // Generate resourceIds for system topics (they're in system tenant/namespace)
                // Note: These are temporary UUIDs - the actual resourceIds will be set when tenant/namespace are created
                val topicResourceId = UUID.randomUUID()
                val systemTenantResourceId = UUID.randomUUID() // Temporary - will be replaced when tenant is created
                val managementNamespaceResourceId = UUID.randomUUID() // Temporary - will be replaced when namespace is created
                topicRepository.createTopic(
                    resourceId = topicResourceId,
                    tenantResourceId = systemTenantResourceId,
                    namespaceResourceId = managementNamespaceResourceId,
                    name = topic,
                    schemas = emptyList(),
                    tenantName = systemTenantId,
                    namespaceName = managementNamespaceId
                )
            }
        }
    }

    private suspend fun bootstrapSystemTenant() {
        val timestamp = Instant.now()
        val systemTenantResourceId = UUID.randomUUID()
        val managementNamespaceResourceId = UUID.randomUUID()

        val tenantCreatedEvent = TenantCreatedEvent(
            resourceId = systemTenantResourceId,
            name = systemTenantId,
            createdBy = "bootstrap",
            createdAt = timestamp,
            metadata = emptyMap()
        )

        val namespaceCreatedEvent = NamespaceCreatedEvent(
            resourceId = managementNamespaceResourceId,
            tenantResourceId = systemTenantResourceId,
            tenantName = systemTenantId,
            name = managementNamespaceId,
            description = "System management namespace",
            createdBy = "bootstrap",
            createdAt = timestamp,
            metadata = emptyMap()
        )

        val events = mutableListOf(
            Event(
                id = EventId.create(
                    topic = tenantTopicName,
                    sequence = topicRepository.getAndIncrementSequence(
                        topicName = tenantTopicName,
                        tenantName = systemTenantId,
                        namespaceName = managementNamespaceId
                    ),
                    tenantId = systemTenantId,
                    namespaceId = managementNamespaceId
                ),
                timestamp = timestamp,
                type = TenantEventType.CREATED,
                payload = tenantCreatedEvent.toPayload()
            ),
            Event(
                id = EventId.create(
                    topic = namespaceTopicName,
                    sequence = topicRepository.getAndIncrementSequence(
                        topicName = namespaceTopicName,
                        tenantName = systemTenantId,
                        namespaceName = managementNamespaceId
                    ),
                    tenantId = systemTenantId,
                    namespaceId = managementNamespaceId
                ),
                timestamp = timestamp,
                type = NamespaceEventType.CREATED,
                payload = namespaceCreatedEvent.toPayload()
            )
        )

        val adminEmail = System.getenv("SYSTEM_ADMIN_EMAIL") ?: "admin@system"
        val adminPassword = System.getenv("SYSTEM_ADMIN_PASSWORD") ?: "admin123"
        val adminId = "admin-system"
        val adminCreated = UserCreatedEvent(
            userId = adminId,
            email = adminEmail,
            name = "System Admin",
            passwordHash = BCrypt.hashpw(adminPassword, BCrypt.gensalt()),
            status = UserStatus.ACTIVE,
            createdBy = "bootstrap",
            createdAt = timestamp,
            metadata = emptyMap()
        )
        events.add(
            Event(
                id = EventId.create(
                    topic = usersTopicName,
                    sequence = topicRepository.getAndIncrementSequence(
                        topicName = usersTopicName,
                        tenantName = systemTenantId,
                        namespaceName = managementNamespaceId
                    ),
                    tenantId = systemTenantId,
                    namespaceId = managementNamespaceId
                ),
                timestamp = timestamp,
                type = UserEventType.CREATED,
                payload = adminCreated.toPayload()
            )
        )
        events.add(
            Event(
                id = EventId.create(
                    topic = usersTopicName,
                    sequence = topicRepository.getAndIncrementSequence(
                        topicName = usersTopicName,
                        tenantName = systemTenantId,
                        namespaceName = managementNamespaceId
                    ),
                    tenantId = systemTenantId,
                    namespaceId = managementNamespaceId
                ),
                timestamp = timestamp,
                type = UserEventType.TENANT_ASSIGNED,
                payload = UserTenantAssignedEvent(
                    userId = adminId,
                    tenantId = systemTenantId,
                    role = "admin",
                    assignedBy = "bootstrap",
                    assignedAt = timestamp,
                    isPrimary = true
                ).toPayload()
            )
        )

        // Grant admin user all permissions in system tenant
        val allPermissions = setOf(
            Permission.CREATE, Permission.READ, Permission.LIST, Permission.UPDATE, Permission.DELETE,
            Permission.ADMIN, Permission.PERMISSION_GRANT, Permission.PERMISSION_REVOKE,
            Permission.SCHEMA_MANAGE, Permission.READ_HISTORY, Permission.READ_EXPORT,
            Permission.WRITE_ADMIN, Permission.REPLAY, Permission.PURGE,
            Permission.ACTIVATE, Permission.SUSPEND, Permission.PASSWORD_RESET, Permission.MANAGE
        )
        
        val permissionGranted = PermissionGrantedEvent(
            principalId = adminId,
            principalType = PrincipalType.USER,
            resourceType = ResourceType.TENANT,
            resourceId = null,  // null = all tenants (global admin)
            tenantResourceId = systemTenantResourceId.toString(),
            namespaceResourceId = null,
            topicResourceId = null,
            permissions = allPermissions,
            constraints = null,
            grantedBy = "bootstrap",
            grantedAt = timestamp,
            expiresAt = null
        )
        
        events.add(
            Event(
                id = EventId.create(
                    topic = SystemTopics.PERMISSIONS_TOPIC,
                    sequence = topicRepository.getAndIncrementSequence(
                        SystemTopics.PERMISSIONS_TOPIC,
                        systemTenantId,
                        managementNamespaceId
                    ),
                    tenantId = systemTenantId,
                    namespaceId = managementNamespaceId
                ),
                timestamp = timestamp,
                type = PermissionEventType.GRANTED,
                payload = permissionGranted.toPayload()
            )
        )

        eventRepository.storeEvents(
            events,
            tenantId = systemTenantId,
            namespaceId = managementNamespaceId
        )
    }
}

