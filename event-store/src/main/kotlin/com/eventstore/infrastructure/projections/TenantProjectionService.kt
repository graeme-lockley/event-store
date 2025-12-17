package com.eventstore.infrastructure.projections

import com.eventstore.domain.Event
import com.eventstore.domain.Tenant
import com.eventstore.domain.events.TenantCreatedEvent
import com.eventstore.domain.events.TenantDeletedEvent
import com.eventstore.domain.events.TenantEventType
import com.eventstore.domain.events.TenantUpdatedEvent
import com.eventstore.domain.ports.outbound.DeliveryResult
import com.eventstore.domain.ports.outbound.TenantRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory

class TenantProjectionService(
    private val tenantRepository: TenantRepository
) {
    private val logger = LoggerFactory.getLogger(TenantProjectionService::class.java)
    private val mutex = Mutex()

    suspend fun handleEvents(events: List<Event>): DeliveryResult {
        if (events.isEmpty()) {
            return DeliveryResult(success = true)
        }

        return try {
            mutex.withLock {
                events.forEach { applyEvent(it) }
            }
            DeliveryResult(success = true)
        } catch (e: Exception) {
            logger.error("Failed to apply tenant events", e)
            DeliveryResult(success = false, error = e.message)
        }
    }

    suspend fun getTenant(tenantId: String): Tenant? {
        val tenant = tenantRepository.findById(tenantId)
        return tenant?.takeIf { it.isActive }
    }

    suspend fun getAllTenants(): List<Tenant> {
        return tenantRepository.findAll().filter { it.isActive }
    }

    suspend fun tenantExists(tenantId: String): Boolean = getTenant(tenantId) != null

    private suspend fun applyEvent(event: Event) {
        when (event.type) {
            TenantEventType.CREATED -> {
                val payload = TenantCreatedEvent.fromPayload(event.payload)
                val tenant = Tenant(
                    id = payload.tenantId,
                    name = payload.name,
                    createdAt = payload.createdAt,
                    updatedAt = null,
                    deletedAt = null,
                    quota = payload.quota,
                    metadata = payload.metadata
                )
                tenantRepository.save(tenant)
            }

            TenantEventType.UPDATED -> {
                val payload = TenantUpdatedEvent.fromPayload(event.payload)
                val existing = tenantRepository.findById(payload.tenantId)
                if (existing == null) {
                    logger.warn("Received tenant.updated for unknown tenant ${payload.tenantId}")
                    return
                }

                val updated = existing.copy(
                    name = payload.name ?: existing.name,
                    quota = payload.quota ?: existing.quota,
                    updatedAt = payload.updatedAt,
                    metadata = payload.metadata ?: existing.metadata
                )
                tenantRepository.save(updated)
            }

            TenantEventType.DELETED -> {
                val payload = TenantDeletedEvent.fromPayload(event.payload)
                val existing = tenantRepository.findById(payload.tenantId)
                if (existing == null) {
                    logger.warn("Received tenant.deleted for unknown tenant ${payload.tenantId}")
                    return
                }

                val deleted = existing.copy(
                    deletedAt = payload.deletedAt,
                    updatedAt = payload.deletedAt
                )
                tenantRepository.save(deleted)
            }

            else -> {
                logger.debug("Ignoring non-tenant event type ${event.type}")
            }
        }
    }
}
