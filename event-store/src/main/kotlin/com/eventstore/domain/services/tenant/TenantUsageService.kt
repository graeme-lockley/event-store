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
        // Count topics for this tenant
        val topics = topicRepository.getAllTopics()
            .count { it.tenantResourceId == tenantId }

        // Count namespaces for this tenant
        val namespaces = namespaceProjectionService.getAllNamespaces()
            .count { it.tenantId == tenantId }

        // Count consumers for this tenant (consumers are associated via topics)
        // Topics are qualified names like "tenant-name/namespace-name/topic-name"
        val consumers = consumerRepository.findAll()
            .count { consumer ->
                consumer.topics.keys.any { topicName ->
                    topicName.startsWith("$tenantName/")
                }
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
