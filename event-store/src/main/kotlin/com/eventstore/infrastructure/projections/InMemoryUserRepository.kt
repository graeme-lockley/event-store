package com.eventstore.infrastructure.projections

import com.eventstore.domain.User
import com.eventstore.domain.UserTenantAssociation
import com.eventstore.domain.ports.outbound.UserRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemoryUserRepository : UserRepository {
    private val mutex = Mutex()
    private val users = mutableMapOf<String, User>()
    private val associations = mutableMapOf<String, MutableList<UserTenantAssociation>>()

    override suspend fun save(user: User) {
        mutex.withLock { users[user.id] = user }
    }

    override suspend fun findById(id: String): User? = mutex.withLock { users[id] }

    override suspend fun findByEmail(email: String): User? = mutex.withLock { users.values.find { it.email == email } }

    override suspend fun findAll(): List<User> = mutex.withLock { users.values.toList() }

    override suspend fun saveAssociation(association: UserTenantAssociation) {
        mutex.withLock {
            associations.getOrPut(association.userId) { mutableListOf() }
                .removeIf { it.tenantId == association.tenantId && it.isPrimary == association.isPrimary }
            associations.getOrPut(association.userId) { mutableListOf() }.add(association)
        }
    }

    override suspend fun getAssociations(userId: String): List<UserTenantAssociation> =
        mutex.withLock { associations[userId]?.toList() ?: emptyList() }
}

