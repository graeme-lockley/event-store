package com.eventstore.domain.services.tenant

import com.eventstore.domain.ports.outbound.ConsumerRepository
import com.eventstore.domain.ports.outbound.TopicRepository
import com.eventstore.infrastructure.projections.NamespaceProjectionService
import com.eventstore.infrastructure.projections.UserProjectionService
import java.util.UUID

data class TenantUsage(
    val topics: Int,
    val namespaces: Int,
    val consumers: Int,
    val users: Int
)

class TenantUsageService(
    private val topicRepository: TopicRepository,
    private val namespaceProjectionService: NamespaceProjectionService,
    private val consumerRepository: ConsumerRepository,
    private val userProjectionService: UserProjectionService
) {
    suspend fun getUsage(tenantId: UUID, tenantName: String): TenantUsage {
        // Count topics for this tenant (via namespaces)
        // Get all namespaces for this tenant, then count topics in those namespaces
        val tenantNamespaceIds = namespaceProjectionService.getAllNamespaces()
            .filter { it.tenantId == tenantId }
            .map { it.namespaceId }
            .toSet()
        val topics = topicRepository.getAllTopics()
            .count { it.namespaceId in tenantNamespaceIds }

        // Count namespaces for this tenant
        val namespaces = namespaceProjectionService.getAllNamespaces()
            .count { it.tenantId == tenantId }

        // Count consumers for this tenant
        // Note: Since topics are now UUIDs, we can't filter by tenant name.
        // We'll need to find topics that belong to tenant's namespaces, then find consumers subscribed to those topics.
        val tenantTopicIds = topicRepository.getAllTopics()
            .filter { it.namespaceId in tenantNamespaceIds }
            .map { it.topicId }
            .toSet()
        val consumers = consumerRepository.findAll()
            .count { consumer ->
                consumer.topics.keys.any { topicId -> topicId in tenantTopicIds }
            }

        // Count users associated with this tenant
        // Note: User associations store tenantId as the tenant name (String), not UUID
        val users = userProjectionService.getAllUsers()
            .count { user ->
                userProjectionService.getAssociations(user.id)
                    .any { it.tenantId == tenantName }
            }

        return TenantUsage(
            topics = topics,
            namespaces = namespaces,
            consumers = consumers,
            users = users
        )
    }
}
