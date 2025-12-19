package com.eventstore.infrastructure.bootstrap

import com.eventstore.domain.Event
import com.eventstore.domain.EventId
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
                topicRepository.createTopic(topic, emptyList(), systemTenantId, managementNamespaceId)
            }
        }
    }

    private suspend fun bootstrapSystemTenant() {
        val timestamp = Instant.now()

        val tenantCreatedEvent = TenantCreatedEvent(
            tenantId = systemTenantId,
            name = "System Tenant",
            createdBy = "bootstrap",
            createdAt = timestamp,
            metadata = emptyMap()
        )

        val namespaceCreatedPayload = mapOf(
            "tenantId" to systemTenantId,
            "namespaceId" to managementNamespaceId,
            "name" to "Management",
            "createdBy" to "bootstrap",
            "createdAt" to timestamp.toString()
        )

        val events = mutableListOf(
            Event(
                id = EventId.create(
                    topic = tenantTopicName,
                    sequence = topicRepository.getAndIncrementSequence(
                        tenantTopicName,
                        systemTenantId,
                        managementNamespaceId
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
                        namespaceTopicName,
                        systemTenantId,
                        managementNamespaceId
                    ),
                    tenantId = systemTenantId,
                    namespaceId = managementNamespaceId
                ),
                timestamp = timestamp,
                type = "namespace.created",
                payload = namespaceCreatedPayload
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
                        usersTopicName,
                        systemTenantId,
                        managementNamespaceId
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
                        usersTopicName,
                        systemTenantId,
                        managementNamespaceId
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

        eventRepository.storeEvents(
            events,
            tenantId = systemTenantId,
            namespaceId = managementNamespaceId
        )
    }
}

