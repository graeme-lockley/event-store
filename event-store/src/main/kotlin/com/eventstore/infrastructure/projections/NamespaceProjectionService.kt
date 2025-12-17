package com.eventstore.infrastructure.projections

import com.eventstore.domain.Event
import com.eventstore.domain.Namespace
import com.eventstore.domain.events.NamespaceCreatedEvent
import com.eventstore.domain.events.NamespaceDeletedEvent
import com.eventstore.domain.events.NamespaceEventType
import com.eventstore.domain.events.NamespaceUpdatedEvent
import com.eventstore.domain.ports.outbound.DeliveryResult
import com.eventstore.domain.ports.outbound.NamespaceRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory

class NamespaceProjectionService(
    private val namespaceRepository: NamespaceRepository
) {
    private val logger = LoggerFactory.getLogger(NamespaceProjectionService::class.java)
    private val mutex = Mutex()

    suspend fun handleEvents(events: List<Event>): DeliveryResult {
        if (events.isEmpty()) return DeliveryResult(success = true)

        return try {
            mutex.withLock { events.forEach { applyEvent(it) } }
            DeliveryResult(success = true)
        } catch (e: Exception) {
            logger.error("Failed to apply namespace events", e)
            DeliveryResult(success = false, error = e.message)
        }
    }

    suspend fun getNamespace(tenantId: String, namespaceId: String): Namespace? {
        return namespaceRepository.findById(tenantId, namespaceId)?.takeIf { it.isActive }
    }

    suspend fun getAllNamespaces(): List<Namespace> =
        namespaceRepository.findAll().filter { it.isActive }

    suspend fun namespaceExists(tenantId: String, namespaceId: String): Boolean =
        getNamespace(tenantId, namespaceId) != null

    private suspend fun applyEvent(event: Event) {
        when (event.type) {
            NamespaceEventType.CREATED -> {
                val payload = NamespaceCreatedEvent.fromPayload(event.payload)
                val ns = Namespace(
                    tenantId = payload.tenantId,
                    id = payload.namespaceId,
                    name = payload.name,
                    description = payload.description,
                    createdAt = payload.createdAt,
                    updatedAt = null,
                    deletedAt = null,
                    metadata = payload.metadata
                )
                namespaceRepository.save(ns)
            }

            NamespaceEventType.UPDATED -> {
                val payload = NamespaceUpdatedEvent.fromPayload(event.payload)
                val existing = namespaceRepository.findById(payload.tenantId, payload.namespaceId)
                if (existing == null) {
                    logger.warn("Received namespace.updated for unknown namespace ${payload.tenantId}/${payload.namespaceId}")
                    return
                }
                val updated = existing.copy(
                    name = payload.name ?: existing.name,
                    description = payload.description ?: existing.description,
                    updatedAt = payload.updatedAt,
                    metadata = payload.metadata ?: existing.metadata
                )
                namespaceRepository.save(updated)
            }

            NamespaceEventType.DELETED -> {
                val payload = NamespaceDeletedEvent.fromPayload(event.payload)
                val existing = namespaceRepository.findById(payload.tenantId, payload.namespaceId)
                if (existing == null) {
                    logger.warn("Received namespace.deleted for unknown namespace ${payload.tenantId}/${payload.namespaceId}")
                    return
                }
                val deleted = existing.copy(deletedAt = payload.deletedAt, updatedAt = payload.deletedAt)
                namespaceRepository.save(deleted)
            }

            else -> logger.debug("Ignoring non-namespace event type ${event.type}")
        }
    }
}

