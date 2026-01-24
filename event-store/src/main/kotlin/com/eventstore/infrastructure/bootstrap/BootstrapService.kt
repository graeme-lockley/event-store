package com.eventstore.infrastructure.bootstrap

import com.eventstore.domain.*
import com.eventstore.domain.events.*
import com.eventstore.domain.ports.outbound.EventRepository
import com.eventstore.domain.ports.outbound.SchemaValidator
import com.eventstore.domain.ports.outbound.TopicRepository
import com.eventstore.domain.services.bootstrap.BootstrapService
import com.eventstore.domain.tenants.SystemTopics
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.mindrot.jbcrypt.BCrypt
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.*

class BootstrapServiceImpl(
    private val eventRepository: EventRepository,
    private val topicRepository: TopicRepository,
    private val schemaValidator: SchemaValidator,
    private val objectMapper: ObjectMapper,
) : BootstrapService {
    private val logger = LoggerFactory.getLogger(BootstrapServiceImpl::class.java)

    private val systemTopics =
        listOf(
            Pair(SystemTopics.TENANTS_TOPIC_NAME, SystemTopics.TENANTS_TOPIC_ID),
            Pair(SystemTopics.NAMESPACES_TOPIC_NAME, SystemTopics.NAMESPACES_TOPIC_ID),
            Pair(SystemTopics.USERS_TOPIC_NAME, SystemTopics.USERS_TOPIC_ID),
            Pair(SystemTopics.PERMISSIONS_TOPIC_NAME, SystemTopics.PERMISSIONS_TOPIC_ID),
            Pair(SystemTopics.API_KEYS_TOPIC_NAME, SystemTopics.API_KEYS_TOPIC_ID),
        )

    override suspend fun run() {
        logger.info("Starting bootstrap process")

        ensureSystemTopics()

        val systemTenantExists =
            eventRepository.getEvents(
                topicId = SystemTopics.TENANTS_TOPIC_ID,
                limit = 1,
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
            val (topicName, topicId) = topic
            if (!topicRepository.topicExists(topicId)) {
                logger.info("Creating system topic: $topicName (ID: $topicId)")
                // System topics use the MANAGEMENT_NAMESPACE_ID
                val schemas = getSchemasForTopic(topicName)
                topicRepository.createTopic(
                    topicId = topicId,
                    namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID,
                    name = topicName,
                    schemas = schemas,
                )
                // Register schemas with validator
                schemaValidator.registerSchemas(topicId, schemas)
                logger.info("Registered ${schemas.size} schemas for topic: $topicName")
            }
        }
    }

    private fun getSchemasForTopic(topic: String): List<Schema> {
        val resourcePath =
            when (topic) {
                SystemTopics.TENANTS_TOPIC_NAME -> "/schemas/system/tenants.json"
                SystemTopics.NAMESPACES_TOPIC_NAME -> "/schemas/system/namespaces.json"
                SystemTopics.USERS_TOPIC_NAME -> "/schemas/system/users.json"
                SystemTopics.PERMISSIONS_TOPIC_NAME -> "/schemas/system/permissions.json"
                SystemTopics.API_KEYS_TOPIC_NAME -> "/schemas/system/api-keys.json"
                else -> null
            }

        if (resourcePath == null) {
            return emptyList()
        }

        return try {
            val inputStream =
                BootstrapServiceImpl::class.java.getResourceAsStream(resourcePath)
                    ?: throw IllegalStateException("Schema resource not found: $resourcePath")
            val schemas: List<Schema> = objectMapper.readValue(inputStream)
            logger.info("Loaded ${schemas.size} schemas from $resourcePath")
            schemas
        } catch (e: Exception) {
            logger.error("Failed to load schemas from $resourcePath", e)
            throw IllegalStateException("Failed to load schemas for topic $topic", e)
        }
    }

    private suspend fun bootstrapSystemTenant() {
        val timestamp = Instant.now()

        val tenantCreatedEvent =
            TenantCreatedEvent(
                tenantId = SystemTopics.SYSTEM_TENANT_ID,
                name = SystemTopics.SYSTEM_TENANT_NAME,
                createdBy = "bootstrap",
                createdAt = timestamp,
                metadata = emptyMap(),
            )

        val namespaceCreatedEventPayload =
            NamespaceCreatedEvent(
                namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID,
                tenantId = SystemTopics.SYSTEM_TENANT_ID,
                name = SystemTopics.MANAGEMENT_NAMESPACE_NAME,
                description = "System management namespace",
                createdBy = "bootstrap",
                createdAt = timestamp,
                metadata = emptyMap(),
            ).toPayload().toMutableMap()
        namespaceCreatedEventPayload["tenantName"] = SystemTopics.SYSTEM_TENANT_NAME // Include for projection service

        val events =
            mutableListOf(
                Event(
                    id =
                        EventId.create(
                            topicId = SystemTopics.TENANTS_TOPIC_ID,
                            sequence = topicRepository.getAndIncrementSequence(SystemTopics.TENANTS_TOPIC_ID),
                        ),
                    timestamp = timestamp,
                    type = TenantEventType.CREATED,
                    payload = tenantCreatedEvent.toPayload(),
                ),
                Event(
                    id =
                        EventId.create(
                            topicId = SystemTopics.NAMESPACES_TOPIC_ID,
                            sequence = topicRepository.getAndIncrementSequence(SystemTopics.NAMESPACES_TOPIC_ID),
                        ),
                    timestamp = timestamp,
                    type = NamespaceEventType.CREATED,
                    payload = namespaceCreatedEventPayload,
                ),
            )

        val adminEmail = System.getenv("SYSTEM_ADMIN_EMAIL") ?: "admin@system"
        val adminPassword = System.getenv("SYSTEM_ADMIN_PASSWORD") ?: "admin123"
        val adminId = "admin-system"
        val adminCreated =
            UserCreatedEvent(
                userId = adminId,
                email = adminEmail,
                name = "System Admin",
                passwordHash = BCrypt.hashpw(adminPassword, BCrypt.gensalt()),
                status = UserStatus.ACTIVE,
                createdBy = "bootstrap",
                createdAt = timestamp,
                metadata = emptyMap(),
            )
        events.add(
            Event(
                id =
                    EventId.create(
                        topicId = SystemTopics.USERS_TOPIC_ID,
                        sequence = topicRepository.getAndIncrementSequence(SystemTopics.USERS_TOPIC_ID),
                    ),
                timestamp = timestamp,
                type = UserEventType.CREATED,
                payload = adminCreated.toPayload(),
            ),
        )
        events.add(
            Event(
                id =
                    EventId.create(
                        topicId = SystemTopics.USERS_TOPIC_ID,
                        sequence = topicRepository.getAndIncrementSequence(SystemTopics.USERS_TOPIC_ID),
                    ),
                timestamp = timestamp,
                type = UserEventType.TENANT_ASSIGNED,
                payload =
                    UserTenantAssignedEvent(
                        userId = adminId,
                        tenantId = SystemTopics.SYSTEM_TENANT_NAME,
                        role = "admin",
                        assignedBy = "bootstrap",
                        assignedAt = timestamp,
                        isPrimary = true,
                    ).toPayload(),
            ),
        )

        // Grant admin user all permissions in system tenant
        val allPermissions =
            setOf(
                Permission.CREATE, Permission.READ, Permission.LIST, Permission.UPDATE, Permission.DELETE,
                Permission.ADMIN, Permission.PERMISSION_GRANT, Permission.PERMISSION_REVOKE,
                Permission.SCHEMA_MANAGE, Permission.READ_HISTORY, Permission.READ_EXPORT,
                Permission.WRITE_ADMIN, Permission.REPLAY, Permission.PURGE,
                Permission.ACTIVATE, Permission.SUSPEND, Permission.PASSWORD_RESET, Permission.MANAGE,
            )

        val permissionGranted =
            PermissionGrantedEvent(
                principalId = adminId,
                principalType = PrincipalType.USER,
                resourceType = ResourceType.TENANT,
                // null = all tenants (global admin)
                resourceId = null,
                tenantResourceId = SystemTopics.SYSTEM_TENANT_ID.toString(),
                namespaceResourceId = null,
                topicId = null,
                permissions = allPermissions,
                grantedBy = "bootstrap",
                grantedAt = timestamp,
                expiresAt = null,
            )

        events.add(
            Event(
                id =
                    EventId.create(
                        topicId = SystemTopics.PERMISSIONS_TOPIC_ID,
                        sequence = topicRepository.getAndIncrementSequence(SystemTopics.PERMISSIONS_TOPIC_ID),
                    ),
                timestamp = timestamp,
                type = PermissionEventType.GRANTED,
                payload = permissionGranted.toPayload(),
            ),
        )

        eventRepository.storeEvents(events)
    }
}
